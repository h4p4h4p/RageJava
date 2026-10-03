package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.BooleanSetting;
import com.kalca.ragejava.settings.SliderSetting;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Hits register on target without your view moving.
 *
 * <p>The server builds its hit ray from the rotation it was sent, not from your camera, so this never
 * touches the local player's {@code rotationYaw}/{@code rotationPitch}. Every tick it picks the target
 * closest to the crosshair within range, publishes the angles to it, and an outbound Netty handler
 * swaps those angles onto the movement packet before it is serialised.
 *
 * <p>Rewriting only {@code C05}/{@code C06} is not enough on its own. Vanilla emits a look packet only
 * when the player's rotation actually changed, so clicking without moving the mouse puts no rotation on
 * the wire and the server resolves the hit against stale aim. So the handler also injects a {@code C05}
 * ahead of every attack while a target is held - the only point where ordering actually matters, since
 * the server has to see the aim before it sees the hit.
 */
public class SilentAim extends Module {

    private static final String HANDLER_NAME = "ragejava_silentaim";

    private final SliderSetting maxDistanceSetting = new SliderSetting("Max Distance", 4.5, 0.5, 20.0, 0.5);
    private final BooleanSetting holdAttackSetting = new BooleanSetting("Hold Attack", false);
    private final BooleanSetting wallCheckSetting = new BooleanSetting("Wall Check", true);

    private final Minecraft mc = Minecraft.getMinecraft();

    private volatile float aimYaw;
    private volatile float aimPitch;
    private volatile boolean hasTarget;
    private volatile EntityLivingBase bestTarget;

    private Channel boundChannel;
    private SilentAimHandler handler;

    public SilentAim() {
        super("SilentAim", Category.COMBAT);
        settings.add(maxDistanceSetting);
        settings.add(holdAttackSetting);
        settings.add(wallCheckSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        // START, before the movement packet is assembled, so the angles on the wire match this tick.
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;

        armChannel();
        updateTarget();
        applyThirdPersonVisuals();
    }

    @Override
    public void onEnable() {
        hasTarget = false;
        bestTarget = null;
        armChannel();
        updateTarget();
    }

    @Override
    public void onDisable() {
        hasTarget = false;
        bestTarget = null;
        SilentAimHandler h = handler;
        handler = null;
        Channel channel = boundChannel;
        boundChannel = null;
        if (channel != null && channel.isActive()) {
            channel.eventLoop().execute(() -> {
                try {
                    if (channel.pipeline().get(HANDLER_NAME) != null) channel.pipeline().remove(HANDLER_NAME);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void updateTarget() {
        Entity self = mc.thePlayer;
        if (self == null || mc.theWorld == null) {
            releaseTarget();
            return;
        }

        // When gated, hold idle until the attack key is down so nothing is ever rewritten otherwise.
        if (holdAttackSetting.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            releaseTarget();
            return;
        }

        double range = maxDistanceSetting.getValue();
        double rangeSq = range * range;

        double eyeX = self.posX;
        double eyeY = self.posY + self.getEyeHeight();
        double eyeZ = self.posZ;

        EntityLivingBase best = null;
        double bestScore = Double.MAX_VALUE;
        float bestYaw = 0.0F;
        float bestPitch = 0.0F;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase target = (EntityLivingBase) entity;
            if (target == self || target.isDead) continue;

            if (target.getDistanceSqToEntity(self) > rangeSq) continue;

            double targetEyeY = target.posY + target.getEyeHeight();
            double dx = target.posX - eyeX;
            double dy = targetEyeY - eyeY;
            double dz = target.posZ - eyeZ;
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            float yaw = (float) (Math.toDegrees(Math.atan2(dx, dz)) - 90.0D);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
            float deltaYaw = wrapDegrees(yaw - self.rotationYaw);
            float deltaPitch = wrapDegrees(pitch - self.rotationPitch);

            // No FOV gate by design: anything in range is eligible and the smallest angular offset
            // from the crosshair wins, so it converges on whatever you are closest to looking at.
            double score = Math.abs(deltaYaw) + Math.abs(deltaPitch);
            if (score >= bestScore) continue;

            if (wallCheckSetting.getValue()
                    && !hasLineOfSight(eyeX, eyeY, eyeZ, target.posX, targetEyeY, target.posZ)) {
                continue;
            }

            bestScore = score;
            best = target;
            bestYaw = yaw;
            bestPitch = pitch;
        }

        if (best == null) {
            releaseTarget();
            return;
        }

        aimYaw = bestYaw;
        aimPitch = bestPitch;
        bestTarget = best;
        hasTarget = true;
    }

    private void releaseTarget() {
        hasTarget = false;
        bestTarget = null;
    }

    /** Eye-to-eye raytrace, matching AimAssist: ignores liquids and blocks without collision. */
    private boolean hasLineOfSight(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        if (mc.theWorld == null) return true;
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(
                new Vec3(fromX, fromY, fromZ), new Vec3(toX, toY, toZ), false, true, false);
        return hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private void armChannel() {
        if (mc.getNetHandler() == null) return;
        NetworkManager manager = mc.getNetHandler().getNetworkManager();
        if (manager == null) return;
        Channel channel = manager.channel();
        if (channel == null || !channel.isActive()) return;

        if (handler == null) {
            handler = new SilentAimHandler();
            boundChannel = channel;
            if (channel.pipeline().get(HANDLER_NAME) == null) {
                channel.eventLoop().execute(() -> {
                    try {
                        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                    } catch (Exception ignored) {
                    }
                });
            }
        } else if (boundChannel != channel) {
            final Channel old = boundChannel;
            boundChannel = channel;
            channel.eventLoop().execute(() -> {
                try {
                    if (old != null && old.isActive()) old.pipeline().remove(HANDLER_NAME);
                } catch (Exception ignored) {
                }
            });
            try {
                if (channel.pipeline().get(HANDLER_NAME) == null) {
                    channel.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    /**
     * Shows the aimed rotation client-side so the camera moves like AimAssist does.
     * This updates the local player's rotations while a target is held.
     */
    private void applyThirdPersonVisuals() {
        if (!hasTarget) return;
        if (mc.thePlayer == null) return;
        mc.thePlayer.rotationYaw = aimYaw;
        mc.thePlayer.rotationYawHead = aimYaw;
        mc.thePlayer.renderYawOffset = aimYaw;
        mc.thePlayer.rotationPitch = aimPitch;
    }

    private class SilentAimHandler extends ChannelOutboundHandlerAdapter {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (isEnabled() && hasTarget) {
                if (msg instanceof C03PacketPlayer.C05PacketPlayerLook) {
                    C03PacketPlayer.C05PacketPlayerLook packet = (C03PacketPlayer.C05PacketPlayerLook) msg;
                    ctx.write(new C03PacketPlayer.C05PacketPlayerLook(aimYaw, aimPitch, packet.isOnGround()), promise);
                    return;
                }
                if (msg instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
                    C03PacketPlayer.C06PacketPlayerPosLook packet = (C03PacketPlayer.C06PacketPlayerPosLook) msg;
                    ctx.write(new C03PacketPlayer.C06PacketPlayerPosLook(packet.getPositionX(), packet.getPositionY(),
                            packet.getPositionZ(), aimYaw, aimPitch, packet.isOnGround()), promise);
                    return;
                }
                if (msg instanceof C02PacketUseEntity) {
                    C02PacketUseEntity packet = (C02PacketUseEntity) msg;
                    // Only correct the aim when the click is aimed at the entity we picked, so an
                    // unrelated interaction keeps vanilla's rotation and cannot desync.
                    if (packet.getAction() == C02PacketUseEntity.Action.ATTACK && isTrackedTarget(packet)) {
                        ctx.write(new C03PacketPlayer.C05PacketPlayerLook(aimYaw, aimPitch, onGround()));
                    }
                }
            }
            ctx.write(msg, promise);
        }

        private boolean isTrackedTarget(C02PacketUseEntity packet) {
            Entity self = mc.thePlayer;
            if (self == null || mc.theWorld == null) return false;
            EntityLivingBase tracked = bestTarget;
            if (tracked == null) return false;
            return packet.getEntityFromWorld(mc.theWorld) == tracked;
        }

        private boolean onGround() {
            Entity self = mc.thePlayer;
            return self != null && self.onGround;
        }
    }
}