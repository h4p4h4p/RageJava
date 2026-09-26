package com.kalca.voidfulenhancements.module;

import com.kalca.voidfulenhancements.gui.Theme;
import com.kalca.voidfulenhancements.settings.ModeSetting;
import com.kalca.voidfulenhancements.util.RenderUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayDeque;

public class Blink extends Module {

    public static final String MODE_INBOUND = "Inbound";
    public static final String MODE_OUTBOUND = "Outbound";
    public static final String MODE_BOTH = "Both";

    private static final String HANDLER_NAME = "voidful_blink";

    private final ModeSetting modeSetting = new ModeSetting("Mode", new String[]{MODE_INBOUND, MODE_OUTBOUND, MODE_BOTH}, 1);

    private final Minecraft mc = Minecraft.getMinecraft();
    private BlinkHandler handler;
    private Channel boundChannel;
    private boolean anchorSet;
    private double anchorX;
    private double anchorY;
    private double anchorZ;

    public Blink() {
        super("Blink", Category.MOVEMENT);
        settings.add(modeSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public boolean hasAnchor() {
        return anchorSet;
    }

    public double getAnchorX() {
        return anchorX;
    }

    public double getAnchorY() {
        return anchorY;
    }

    public double getAnchorZ() {
        return anchorZ;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        armChannel();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;

        double ax = hasAnchor() ? anchorX : mc.thePlayer.posX;
        double ay = hasAnchor() ? anchorY : mc.thePlayer.posY;
        double az = hasAnchor() ? anchorZ : mc.thePlayer.posZ;
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox();

        AxisAlignedBB anchorBox = AxisAlignedBB.fromBounds(
                bb.minX - mc.thePlayer.posX + ax,
                bb.minY - mc.thePlayer.posY + ay,
                bb.minZ - mc.thePlayer.posZ + az,
                bb.maxX - mc.thePlayer.posX + ax,
                bb.maxY - mc.thePlayer.posY + ay,
                bb.maxZ - mc.thePlayer.posZ + az);
        if (RenderUtil.pointNearBox(camX, camY, camZ, anchorBox, 1.2D)) return;

        int r = (Theme.ACCENT >> 16) & 0xFF;
        int g = (Theme.ACCENT >> 8) & 0xFF;
        int b = Theme.ACCENT & 0xFF;
        int a = 255;

        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        double pad = 0.1D;
        AxisAlignedBB box = AxisAlignedBB.fromBounds(
                bb.minX - mc.thePlayer.posX + ax - camX - pad,
                bb.minY - mc.thePlayer.posY + ay - camY - pad,
                bb.minZ - mc.thePlayer.posZ + az - camZ - pad,
                bb.maxX - mc.thePlayer.posX + ax - camX + pad,
                bb.maxY - mc.thePlayer.posY + ay - camY + pad,
                bb.maxZ - mc.thePlayer.posZ + az - camZ + pad);
        RenderUtil.drawOutlinedBox(tessellator, box, r, g, b, a);
        GlStateManager.enableTexture2D();
    }

    private String mode() {
        return modeSetting.getValue();
    }

    private void armChannel() {
        if (mc.getNetHandler() == null) return;
        NetworkManager manager = mc.getNetHandler().getNetworkManager();
        if (manager == null) return;
        Channel channel = manager.channel();
        if (channel == null || !channel.isActive()) return;

        if (handler == null) {
            handler = new BlinkHandler();
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
            channel.eventLoop().execute(() -> {
                try {
                    if (boundChannel != null && boundChannel.isActive()) boundChannel.pipeline().remove(HANDLER_NAME);
                } catch (Exception ignored) {
                }
            });
            boundChannel = channel;
            try {
                if (channel.pipeline().get(HANDLER_NAME) == null) {
                    channel.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private boolean shouldHoldInbound() {
        String m = mode();
        return m.equals(MODE_INBOUND) || m.equals(MODE_BOTH);
    }

    private boolean shouldHoldOutbound() {
        String m = mode();
        return m.equals(MODE_OUTBOUND) || m.equals(MODE_BOTH);
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            anchorX = mc.thePlayer.posX;
            anchorY = mc.thePlayer.posY;
            anchorZ = mc.thePlayer.posZ;
            anchorSet = true;
        }
        armChannel();
    }

    @Override
    public void onDisable() {
        BlinkHandler h = handler;
        handler = null;
        if (h != null) h.flush();
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

    private class BlinkHandler extends ChannelDuplexHandler {

        private final ArrayDeque<Packet> inbound = new ArrayDeque<>();
        private final ArrayDeque<Packet> outbound = new ArrayDeque<>();
        private ChannelHandlerContext savedCtx;
        private volatile boolean flushing;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            savedCtx = ctx;
            if (!flushing && shouldHoldInbound() && msg instanceof Packet) {
                inbound.add((Packet) msg);
                return;
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            savedCtx = ctx;
            if (!flushing && shouldHoldOutbound() && msg instanceof C03PacketPlayer) {
                outbound.add((Packet) msg);
                promise.setSuccess();
                return;
            }
            ctx.write(msg, promise);
        }

        void flush() {
            Channel channel = boundChannel;
            if (channel == null) return;
            channel.eventLoop().execute(() -> flushOnLoop());
        }

        private void flushOnLoop() {
            ChannelHandlerContext ctx = savedCtx;
            if (ctx == null) return;
            flushing = true;
            try {
                if (shouldHoldOutbound()) {
                    while (!outbound.isEmpty()) {
                        Packet p = outbound.poll();
                        if (p != null) ctx.writeAndFlush(p);
                    }
                }
                if (shouldHoldInbound()) {
                    while (!inbound.isEmpty()) {
                        Packet p = inbound.poll();
                        if (p != null) ctx.fireChannelRead(p);
                    }
                }
            } finally {
                flushing = false;
            }
        }
    }
}