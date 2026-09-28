package com.kalca.voidfulenhancements.module;

import com.kalca.voidfulenhancements.gui.Theme;
import com.kalca.voidfulenhancements.settings.BooleanSetting;
import com.kalca.voidfulenhancements.settings.ModeSetting;
import com.kalca.voidfulenhancements.settings.SliderSetting;
import com.kalca.voidfulenhancements.util.RenderUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lag switch in the sense of Blink: the server's idea of where you are is frozen while you
 * move freely. Attacks are deliberately NOT buffered, so a hit sent during the window is
 * validated against the frozen position and then the queue is flushed afterwards.
 *
 * <p>Seven settings, all of which change what the module does. The safety limits that used to
 * be sliders (queue cap, cooldown, max choke, hurt grace) are constants now, because they were
 * never worth tuning per fight and a slider someone widens too far is a good way to get kicked.
 */
public class LagRange extends Module {

    private static final String HANDLER_NAME = "voidful_lagrange";

    public static final String RENDER_BOX = "Box";
    public static final String RENDER_FAKE_PLAYER = "Fake Player";
    public static final String RENDER_TRAIL = "Trail";

    /** Hard cap on buffered movement packets, so a missed release can never balloon the queue. */
    private static final int MAX_QUEUE = 20;
    /** Minimum gap between the end of one pulse and the start of the next. */
    private static final long COOLDOWN = 250L;
    /** A single pulse can never last longer than this, whatever the sliders say. */
    private static final long MAX_CHOKE = 600L;
    /** Refuse to start a pulse within this long of taking damage. */
    private static final long HURT_GRACE = 100L;
    private static final int MAX_TRAIL = 96;

    private final SliderSetting minRangeSetting = new SliderSetting("Min Range", 1.5D, 0.0D, 12.0D, 0.25D);
    private final SliderSetting maxRangeSetting = new SliderSetting("Max Range", 4.5D, 0.0D, 12.0D, 0.25D);
    private final SliderSetting delayMinSetting = new SliderSetting("Delay Min", 150D, 0.0D, 2000.0D, 25.0D);
    private final SliderSetting delayMaxSetting = new SliderSetting("Delay Max", 400D, 0.0D, 2000.0D, 25.0D);
    private final SliderSetting hitWindowSetting = new SliderSetting("Hit Window", 200D, 0.0D, 2000.0D, 25.0D);
    private final BooleanSetting inboundSetting = new BooleanSetting("Inbound", true);
    private final ModeSetting renderModeSetting = new ModeSetting("Render Mode", new String[]{RENDER_BOX, RENDER_FAKE_PLAYER, RENDER_TRAIL}, 0);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final Object lock = new Object();
    private final List<Pending> pending = new ArrayList<>();
    private final List<Object> inbound = new ArrayList<>();
    private final double[] trail = new double[MAX_TRAIL * 3];

    private volatile boolean choking;
    private volatile boolean overflowPending;
    private volatile boolean urgentFlush;
    private volatile long attackAt;
    private volatile double serverX;
    private volatile double serverY;
    private volatile double serverZ;
    private volatile float serverYaw;
    private volatile boolean serverSet;
    private volatile boolean packetSeen;
    private volatile int queueSize;

    private LagRangeHandler handler;
    private Channel boundChannel;
    private long chokeStart;
    private long releaseAt;
    private long cooldownUntil;
    private long hurtAt;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYaw;

    public LagRange() {
        super("LagRange", Category.COMBAT);
        settings.add(minRangeSetting);
        settings.add(maxRangeSetting);
        settings.add(delayMinSetting);
        settings.add(delayMaxSetting);
        settings.add(hitWindowSetting);
        settings.add(inboundSetting);
        settings.add(renderModeSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public String getTag() {
        if (!isEnabled()) return null;
        return queueSize + " / " + MAX_QUEUE;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        armChannel();
        long now = System.currentTimeMillis();

        if (urgentFlush) {
            urgentFlush = false;
            releaseBuffer();
            return;
        }

        if (mc.thePlayer.hurtTime != 0) hurtAt = now;
        if (!choking && !packetSeen) sampleServerPos();

        boolean inBand = shouldLag();
        if (!isMoving()) inBand = false;

        if (!choking) {
            if (inBand && now >= cooldownUntil) startChoke(now);
        } else {
            boolean cycleDone = now >= releaseAt;
            boolean hitDone = attackAt != 0L
                    && now - attackAt >= (long) hitWindowSetting.getValue();

            if (!inBand || cycleDone || hitDone || overflowPending || now - chokeStart >= MAX_CHOKE) {
                releaseBuffer();
            }
        }
    }

    private void startChoke(long now) {
        choking = true;
        chokeStart = now;
        releaseAt = now + nextDelay();
        attackAt = 0L;
        overflowPending = false;
    }

    private long nextDelay() {
        int min = (int) delayMinSetting.getValue();
        int max = (int) delayMaxSetting.getValue();
        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }

    private boolean isMoving() {
        return mc.thePlayer.moveForward != 0 || mc.thePlayer.moveStrafing != 0;
    }

    private void sampleServerPos() {
        if (mc.thePlayer == null) return;
        serverX = mc.thePlayer.posX;
        serverY = mc.thePlayer.posY;
        serverZ = mc.thePlayer.posZ;
        serverYaw = mc.thePlayer.rotationYaw;
        serverSet = true;
    }

    /**
     * The enemy is always measured from their eyes to the nearest point of the <em>server</em> box,
     * since that is the position they are being told about. The two gates are not optional: the
     * ghost has to be genuinely closer, and we must not be fresh off a hit.
     */
    private boolean shouldLag() {
        EntityPlayer target = nearestPlayer();
        if (target == null) return false;

        double min = minRangeSetting.getValue();
        double max = maxRangeSetting.getValue();
        if (max < min) {
            double swap = min;
            min = max;
            max = swap;
        }

        double toServer = boxDistance(target.posX, target.posY + target.getEyeHeight(), target.posZ, serverBox());
        if (toServer < min || toServer > max) return false;

        double toClient = boxDistance(target.posX, target.posY + target.getEyeHeight(), target.posZ,
                mc.thePlayer.getEntityBoundingBox());
        if (toServer >= toClient) return false;

        return hurtAt == 0L || System.currentTimeMillis() - hurtAt >= HURT_GRACE;
    }

    private AxisAlignedBB serverBox() {
        return serverBoxOffset(serverX, serverY, serverZ);
    }

    private static double boxDistance(double px, double py, double pz, AxisAlignedBB box) {
        double dx = Math.max(Math.max(box.minX - px, 0.0D), Math.max(px - box.maxX, 0.0D));
        double dy = Math.max(Math.max(box.minY - py, 0.0D), Math.max(py - box.maxY, 0.0D));
        double dz = Math.max(Math.max(box.minZ - pz, 0.0D), Math.max(pz - box.maxZ, 0.0D));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private EntityPlayer nearestPlayer() {
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        List<EntityPlayer> players = mc.theWorld.playerEntities;
        EntityPlayer best = null;
        double bestSq = Double.MAX_VALUE;
        for (int i = 0; i < players.size(); i++) {
            EntityPlayer other = players.get(i);
            if (other == mc.thePlayer || other.isDead) continue;
            double dx = other.posX - mc.thePlayer.posX;
            double dy = other.posY - mc.thePlayer.posY;
            double dz = other.posZ - mc.thePlayer.posZ;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = other;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ render

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null || !serverSet) return;

        double gx = lerpX += (serverX - lerpX) * 0.3D;
        double gy = lerpY += (serverY - lerpY) * 0.3D;
        double gz = lerpZ += (serverZ - lerpZ) * 0.3D;
        lerpYaw += MathHelper.wrapAngleTo180_float(serverYaw - lerpYaw) * 0.3F;

        renderGhost(gx, gy, gz, lerpYaw);
    }

    private void renderGhost(double gx, double gy, double gz, float yaw) {
        String mode = renderModeSetting.getValue();
        if (RENDER_TRAIL.equals(mode)) renderTrail();
        if (RENDER_FAKE_PLAYER.equals(mode)) {
            renderFakePlayer(gx, gy, gz, yaw);
            return;
        }
        renderBox(gx, gy, gz);
    }

    private void renderBox(double gx, double gy, double gz) {
        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;
        AxisAlignedBB box = serverBoxOffset(gx, gy, gz);
        if (RenderUtil.pointNearBox(camX, camY, camZ, box, 1.2D)) return;

        int r = (Theme.ACCENT >> 16) & 0xFF;
        int g = (Theme.ACCENT >> 8) & 0xFF;
        int b = Theme.ACCENT & 0xFF;

        GlStateManager.disableTexture2D();
        double pad = 0.1D;
        AxisAlignedBB draw = box.expand(pad, pad, pad).offset(-camX, -camY, -camZ);
        RenderUtil.drawOutlinedBox(Tessellator.getInstance(), draw, r, g, b, 255);
        GlStateManager.enableTexture2D();
    }

    private void renderFakePlayer(double gx, double gy, double gz, float yaw) {
        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;
        if (RenderUtil.pointNearBox(camX, camY, camZ, serverBoxOffset(gx, gy, gz), 1.2D)) return;

        float prevYaw = mc.thePlayer.rotationYaw;
        float prevHead = mc.thePlayer.rotationYawHead;
        float prevOffset = mc.thePlayer.renderYawOffset;
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationYawHead = yaw;
        mc.thePlayer.renderYawOffset = yaw;

        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            mc.getRenderManager().doRenderEntity(mc.thePlayer, gx, gy, gz, yaw, 1.0F, true);
        } finally {
            GL11.glPopAttrib();
            GlStateManager.popMatrix();
            mc.thePlayer.rotationYaw = prevYaw;
            mc.thePlayer.rotationYawHead = prevHead;
            mc.thePlayer.renderYawOffset = prevOffset;
        }
    }

    private void renderTrail() {
        int count = buildTrail();
        if (count < 2) return;

        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;

        int r = (Theme.ACCENT >> 16) & 0xFF;
        int g = (Theme.ACCENT >> 8) & 0xFF;
        int b = Theme.ACCENT & 0xFF;

        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        mc.entityRenderer.disableLightmap();
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(r / 255.0F, g / 255.0F, b / 255.0F, 0.85F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i < count; i++) {
            int o = i * 3;
            GL11.glVertex3d(trail[o] - camX, trail[o + 1] - camY, trail[o + 2] - camZ);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.entityRenderer.enableLightmap();
        GL11.glPopAttrib();
        GlStateManager.popMatrix();
    }

    private int buildTrail() {
        int count = 0;
        synchronized (lock) {
            int size = pending.size();
            for (int i = 0; i < size && count < MAX_TRAIL; i++) {
                Object msg = pending.get(i).msg;
                if (!(msg instanceof C03PacketPlayer)) continue;
                C03PacketPlayer c03 = (C03PacketPlayer) msg;
                if (!c03.isMoving()) continue;
                int o = count * 3;
                trail[o] = c03.getPositionX();
                trail[o + 1] = c03.getPositionY();
                trail[o + 2] = c03.getPositionZ();
                count++;
            }
        }
        return count;
    }

    private AxisAlignedBB serverBoxOffset(double gx, double gy, double gz) {
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox();
        return AxisAlignedBB.fromBounds(
                bb.minX - mc.thePlayer.posX + gx,
                bb.minY - mc.thePlayer.posY + gy,
                bb.minZ - mc.thePlayer.posZ + gz,
                bb.maxX - mc.thePlayer.posX + gx,
                bb.maxY - mc.thePlayer.posY + gy,
                bb.maxZ - mc.thePlayer.posZ + gz);
    }

    // ------------------------------------------------------------------ buffer control

    private void releaseBuffer() {
        choking = false;
        overflowPending = false;
        urgentFlush = false;
        attackAt = 0L;
        cooldownUntil = System.currentTimeMillis() + COOLDOWN;

        List<Pending> out;
        List<Object> in;
        synchronized (lock) {
            out = pending.isEmpty() ? null : new ArrayList<>(pending);
            in = inbound.isEmpty() ? null : new ArrayList<>(inbound);
            pending.clear();
            inbound.clear();
            queueSize = 0;
        }
        if (out == null && in == null) return;
        dispatch(out, in);
    }

    private void dispatch(final List<Pending> out, final List<Object> in) {
        final LagRangeHandler current = handler;
        final Channel channel = boundChannel;
        if (current == null || channel == null || !channel.isActive()) {
            if (out != null) {
                for (int i = 0; i < out.size(); i++) out.get(i).promise.setSuccess();
            }
            return;
        }
        channel.eventLoop().execute(() -> current.flush(out, in));
    }

    private boolean holdOutbound(Object msg) {
        if (msg instanceof C03PacketPlayer) return true;
        if (msg instanceof C02PacketUseEntity) {
            return ((C02PacketUseEntity) msg).getAction() != C02PacketUseEntity.Action.ATTACK;
        }
        return msg instanceof C07PacketPlayerDigging
                || msg instanceof C08PacketPlayerBlockPlacement
                || msg instanceof C0EPacketClickWindow
                || msg instanceof C0DPacketCloseWindow;
    }

    private void trackServerPos(C03PacketPlayer c03) {
        if (c03.isMoving()) {
            serverX = c03.getPositionX();
            serverY = c03.getPositionY();
            serverZ = c03.getPositionZ();
        }
        if (c03.getRotating()) serverYaw = c03.getYaw();
        serverSet = true;
        packetSeen = true;
    }

    private boolean isCriticalInbound(Object msg) {
        if (msg instanceof S08PacketPlayerPosLook) return true;
        if (msg instanceof S12PacketEntityVelocity) {
            return mc.thePlayer != null && ((S12PacketEntityVelocity) msg).getEntityID() == mc.thePlayer.getEntityId();
        }
        if (msg instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) msg;
            return packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F;
        }
        return false;
    }

    // ------------------------------------------------------------------ channel plumbing

    private void armChannel() {
        if (mc.getNetHandler() == null) return;
        NetworkManager manager = mc.getNetHandler().getNetworkManager();
        if (manager == null) return;
        Channel channel = manager.channel();
        if (channel == null || !channel.isActive()) return;

        if (handler == null) {
            handler = new LagRangeHandler();
            boundChannel = channel;
            install(channel);
        } else if (boundChannel != channel) {
            final Channel previous = boundChannel;
            if (previous != null && previous.isActive()) {
                previous.eventLoop().execute(() -> {
                    try {
                        if (previous.pipeline().get(HANDLER_NAME) != null) previous.pipeline().remove(HANDLER_NAME);
                    } catch (Exception ignored) {
                    }
                });
            }
            boundChannel = channel;
            install(channel);
        }
    }

    private void install(final Channel channel) {
        channel.eventLoop().execute(() -> {
            try {
                if (channel.pipeline().get(HANDLER_NAME) == null) {
                    channel.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                }
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void onEnable() {
        choking = false;
        overflowPending = false;
        urgentFlush = false;
        attackAt = 0L;
        hurtAt = 0L;
        cooldownUntil = 0L;
        packetSeen = false;
        queueSize = 0;
        sampleServerPos();
        lerpX = serverX;
        lerpY = serverY;
        lerpZ = serverZ;
        lerpYaw = serverYaw;
        armChannel();
    }

    @Override
    public void onDisable() {
        choking = false;
        overflowPending = false;
        urgentFlush = false;
        attackAt = 0L;
        serverSet = false;
        packetSeen = false;
        hurtAt = 0L;
        queueSize = 0;

        List<Pending> out;
        List<Object> in;
        synchronized (lock) {
            out = pending.isEmpty() ? null : new ArrayList<>(pending);
            in = inbound.isEmpty() ? null : new ArrayList<>(inbound);
            pending.clear();
            inbound.clear();
        }

        final LagRangeHandler current = handler;
        handler = null;
        final Channel channel = boundChannel;
        boundChannel = null;

        if (current == null || channel == null || !channel.isActive()) {
            if (out != null) {
                for (int i = 0; i < out.size(); i++) out.get(i).promise.setSuccess();
            }
            return;
        }

        channel.eventLoop().execute(() -> {
            current.flush(out, in);
            try {
                if (channel.pipeline().get(HANDLER_NAME) != null) channel.pipeline().remove(HANDLER_NAME);
            } catch (Exception ignored) {
            }
        });
    }

    private static class Pending {
        final Object msg;
        final ChannelPromise promise;

        Pending(Object msg, ChannelPromise promise) {
            this.msg = msg;
            this.promise = promise;
        }
    }

    private class LagRangeHandler extends ChannelDuplexHandler {

        private ChannelHandlerContext context;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            this.context = ctx;
            super.handlerAdded(ctx);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (choking) {
                if (msg instanceof C03PacketPlayer) {
                    synchronized (lock) {
                        pending.add(new Pending(msg, promise));
                        queueSize = pending.size();
                        if (queueSize > MAX_QUEUE) overflowPending = true;
                    }
                    return;
                }
                if (msg instanceof C02PacketUseEntity) {
                    if (((C02PacketUseEntity) msg).getAction() == C02PacketUseEntity.Action.ATTACK) {
                        attackAt = System.currentTimeMillis();
                        ctx.write(msg, promise);
                        return;
                    }
                }
                if (msg instanceof C0BPacketEntityAction) {
                    urgentFlush = true;
                    ctx.write(msg, promise);
                    return;
                }
                if (holdOutbound(msg)) {
                    synchronized (lock) {
                        pending.add(new Pending(msg, promise));
                        queueSize = pending.size();
                    }
                    return;
                }
            } else if (msg instanceof C03PacketPlayer) {
                trackServerPos((C03PacketPlayer) msg);
            }
            ctx.write(msg, promise);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (choking && inboundSetting.getValue() && msg instanceof Packet) {
                if (isCriticalInbound(msg)) {
                    urgentFlush = true;
                } else {
                    synchronized (lock) {
                        inbound.add(msg);
                    }
                    return;
                }
            }
            ctx.fireChannelRead(msg);
        }

        void flush(List<Pending> out, List<Object> in) {
            ChannelHandlerContext ctx = context;
            if (ctx == null) {
                if (out != null) {
                    for (int i = 0; i < out.size(); i++) out.get(i).promise.setSuccess();
                }
                return;
            }
            if (out != null) {
                for (int i = 0; i < out.size(); i++) {
                    Pending entry = out.get(i);
                    ctx.write(entry.msg, entry.promise);
                }
            }
            if (in != null) {
                for (int i = 0; i < in.size(); i++) {
                    ctx.fireChannelRead(in.get(i));
                }
            }
            ctx.flush();
        }
    }
}
