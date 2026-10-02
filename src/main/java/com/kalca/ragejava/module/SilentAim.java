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
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Silent aim: the server is told you're looking at a target, but your camera never moves.
 *
 * The client picks a target every tick (smallest angular offset from the crosshair inside
 * the FOV cone, within Max Distance, optionally with a clear line) and remembers the angles
 * to it. An outbound Netty handler then intercepts the C03 movement packet the client sends
 * each tick and swaps its yaw/pitch for those target angles before the packet is serialised.
 *
 * Because rotationYaw/rotationPitch on the local player are left alone, nothing on screen
 * moves - but the server builds its hit-detection ray from the rotation it received, so an
 * attack lands on the target even though your crosshair was elsewhere.
 *
 * Only the rotation-bearing packets are rewritten:
 *   - C06 (position + look) and C05 (look only) get the aimed angles.
 *   - C04 (position only) and the bare C03 carry no rotation, so there is nothing to aim;
 *     the server simply keeps the last rotation it was sent, which is already the aimed one.
 *
 * The target scan runs on the client thread at the START of the tick, before the player
 * movement packet is built, so the rotation on the wire matches this tick's target.
 */
public class SilentAim extends Module {

    private static final String HANDLER_NAME = "ragejava_silentaim";

    private final SliderSetting fovSetting = new SliderSetting("FOV", 45, 1, 180, 1);
    private final SliderSetting maxDistanceSetting = new SliderSetting("Max Distance", 4.5, 0.5, 20.0, 0.5);
    private final BooleanSetting wallCheckSetting = new BooleanSetting("Wall Check", true);

    private final Minecraft mc = Minecraft.getMinecraft();

    // Written on the client thread, read on the Netty event loop - keep these volatile.
    private volatile float aimYaw;
    private volatile float aimPitch;
    private volatile boolean hasTarget;

    private SilentAimHandler handler;
    private Channel boundChannel;

    public SilentAim() {
        super("SilentAim", Category.COMBAT);
        settings.add(fovSetting);
        settings.add(maxDistanceSetting);
        settings.add(wallCheckSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) {
            hasTarget = false;
            return;
        }
        armChannel();
        updateTarget();
    }

    private void updateTarget() {
        hasTarget = false;
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;

        float curYaw = mc.thePlayer.rotationYaw;
        float curPitch = mc.thePlayer.rotationPitch;
        float fov = (float) fovSetting.getValue();
        double maxDistanceSq = maxDistanceSetting.getValue() * maxDistanceSetting.getValue();

        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double pz = mc.thePlayer.posZ;

        float targetYaw = 0.0F;
        float targetPitch = 0.0F;
        double bestScore = Double.MAX_VALUE;
        boolean found = false;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == mc.thePlayer || entity.isDead || !(entity instanceof EntityLivingBase)) continue;

            double dx = entity.posX - px;
            double dy = (entity.posY + entity.getEyeHeight()) - py;
            double dz = entity.posZ - pz;
            double hor = Math.sqrt(dx * dx + dz * dz);
            if (hor < 0.01D && Math.abs(dy) < 0.01D) continue;

            double distSq = hor * hor + dy * dy;
            if (distSq > maxDistanceSq) continue;

            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, hor));

            float dYaw = Math.abs(wrapDegrees(yaw - curYaw));
            float dPitch = Math.abs(pitch - curPitch);
            if (dYaw > fov || dPitch > fov) continue;

            double score = dYaw + dPitch;
            if (score < bestScore) {
                if (wallCheckSetting.getValue() && !hasLineOfSight(entity)) continue;
                bestScore = score;
                targetYaw = yaw;
                targetPitch = pitch;
                found = true;
            }
        }

        if (found) {
            aimYaw = targetYaw;
            aimPitch = targetPitch;
            hasTarget = true;
        }
    }

    /**
     * Traces eye-to-eye and reports whether the segment is clear of solid blocks. Liquids
     * and blocks with no collision box (grass, flowers) are stepped over rather than
     * counted as cover.
     */
    private boolean hasLineOfSight(Entity target) {
        Vec3 eyes = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 targetEyes = new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, targetEyes, false, true, false);
        return hit == null;
    }

    private float wrapDegrees(float degrees) {
        float d = degrees % 360.0F;
        if (d >= 180.0F) d -= 360.0F;
        if (d < -180.0F) d += 360.0F;
        return d;
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

    @Override
    public void onEnable() {
        hasTarget = false;
        armChannel();
    }

    @Override
    public void onDisable() {
        hasTarget = false;
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

    private class SilentAimHandler extends ChannelOutboundHandlerAdapter {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (isEnabled() && hasTarget && msg instanceof C03PacketPlayer) {
                C03PacketPlayer packet = (C03PacketPlayer) msg;
                float yaw = aimYaw;
                float pitch = aimPitch;
                if (packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
                    msg = new C03PacketPlayer.C06PacketPlayerPosLook(
                            packet.getPositionX(), packet.getPositionY(), packet.getPositionZ(),
                            yaw, pitch, packet.isOnGround());
                } else if (packet instanceof C03PacketPlayer.C05PacketPlayerLook) {
                    msg = new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, packet.isOnGround());
                }
                // C04 (position only) and the bare C03 carry no rotation to rewrite.
            }
            ctx.write(msg, promise);
        }
    }
}