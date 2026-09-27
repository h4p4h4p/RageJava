package com.kalca.voidfulenhancements.module;

import com.kalca.voidfulenhancements.gui.Theme;
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
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

public class LagRange extends Module {

    private static final String HANDLER_NAME = "voidful_lagrange";

    private final SliderSetting rangeSetting = new SliderSetting("Range", 4.0, 1.0, 16.0, 0.5);
    private final SliderSetting maxChokeSetting = new SliderSetting("MaxChoke", 400, 0, 2000, 50);
    private final SliderSetting cooldownSetting = new SliderSetting("Cooldown", 500, 0, 3000, 50);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Object lock = new Object();
    private final List<Pending> pending = new ArrayList<>();

    private volatile boolean choking;
    private LagRangeHandler handler;
    private Channel boundChannel;
    private long chokeStart;
    private long cooldownUntil;

    private boolean serverSet;
    private double serverX;
    private double serverY;
    private double serverZ;

    public LagRange() {
        super("LagRange", Category.COMBAT);
        settings.add(rangeSetting);
        settings.add(maxChokeSetting);
        settings.add(cooldownSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        armChannel();
        if (boundChannel == null) return;

        long now = System.currentTimeMillis();
        if (!choking) {
            serverX = mc.thePlayer.posX;
            serverY = mc.thePlayer.posY;
            serverZ = mc.thePlayer.posZ;
            serverSet = true;
        }

        if (enemyNear()) {
            if (!choking && now >= cooldownUntil) {
                choking = true;
                chokeStart = now;
            } else if (choking) {
                long max = (long) maxChokeSetting.getValue();
                if (max > 0L && now - chokeStart >= max) {
                    releaseBuffer(true);
                }
            }
        } else if (choking) {
            releaseBuffer(false);
        }
    }

    public boolean hasServerPos() {
        return serverSet;
    }

    public double getServerX() {
        return serverX;
    }

    public double getServerY() {
        return serverY;
    }

    public double getServerZ() {
        return serverZ;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null || !serverSet) return;

        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox();

        AxisAlignedBB serverBox = AxisAlignedBB.fromBounds(
                bb.minX - mc.thePlayer.posX + serverX,
                bb.minY - mc.thePlayer.posY + serverY,
                bb.minZ - mc.thePlayer.posZ + serverZ,
                bb.maxX - mc.thePlayer.posX + serverX,
                bb.maxY - mc.thePlayer.posY + serverY,
                bb.maxZ - mc.thePlayer.posZ + serverZ);
        if (RenderUtil.pointNearBox(camX, camY, camZ, serverBox, 1.2D)) return;

        int r = (Theme.ACCENT >> 16) & 0xFF;
        int g = (Theme.ACCENT >> 8) & 0xFF;
        int b = Theme.ACCENT & 0xFF;
        int a = 255;

        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        double pad = 0.1D;
        AxisAlignedBB box = AxisAlignedBB.fromBounds(
                bb.minX - mc.thePlayer.posX + serverX - camX - pad,
                bb.minY - mc.thePlayer.posY + serverY - camY - pad,
                bb.minZ - mc.thePlayer.posZ + serverZ - camZ - pad,
                bb.maxX - mc.thePlayer.posX + serverX - camX + pad,
                bb.maxY - mc.thePlayer.posY + serverY - camY + pad,
                bb.maxZ - mc.thePlayer.posZ + serverZ - camZ + pad);
        RenderUtil.drawOutlinedBox(tessellator, box, r, g, b, a);
        GlStateManager.enableTexture2D();
    }

    private boolean enemyNear() {
        double range = rangeSetting.getValue();
        double rangeSq = range * range;
        List<EntityPlayer> players = mc.theWorld.playerEntities;
        for (int i = 0; i < players.size(); i++) {
            EntityPlayer other = players.get(i);
            if (other == mc.thePlayer || other.isDead) continue;
            double dx = other.posX - mc.thePlayer.posX;
            double dy = other.posY - mc.thePlayer.posY;
            double dz = other.posZ - mc.thePlayer.posZ;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) return true;
        }
        return false;
    }

    private void releaseBuffer(boolean startCooldown) {
        choking = false;
        if (startCooldown) {
            cooldownUntil = System.currentTimeMillis() + (long) cooldownSetting.getValue();
        }

        List<Pending> batch;
        synchronized (lock) {
            if (pending.isEmpty()) return;
            batch = new ArrayList<>(pending);
            pending.clear();
        }

        final Channel channel = boundChannel;
        final LagRangeHandler current = handler;
        if (channel == null || current == null || !channel.isActive()) return;
        channel.eventLoop().execute(() -> current.release(batch));
    }

    private void armChannel() {
        if (mc.getNetHandler() == null) return;
        NetworkManager manager = mc.getNetHandler().getNetworkManager();
        if (manager == null) return;
        Channel channel = manager.channel();
        if (channel == null || !channel.isActive()) return;

        if (handler == null) {
            handler = new LagRangeHandler();
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
            Channel previous = boundChannel;
            channel.eventLoop().execute(() -> {
                try {
                    if (previous != null && previous.isActive()) previous.pipeline().remove(HANDLER_NAME);
                } catch (Exception ignored) {
                }
            });
            boundChannel = channel;
            if (channel.pipeline().get(HANDLER_NAME) == null) {
                channel.eventLoop().execute(() -> {
                    try {
                        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private void clearPending() {
        List<Pending> batch;
        synchronized (lock) {
            if (pending.isEmpty()) return;
            batch = new ArrayList<>(pending);
            pending.clear();
        }
        final LagRangeHandler current = handler;
        if (current == null) return;
        current.release(batch);
    }

    @Override
    public void onEnable() {
        choking = false;
        cooldownUntil = 0L;
        if (mc.thePlayer != null) {
            serverX = mc.thePlayer.posX;
            serverY = mc.thePlayer.posY;
            serverZ = mc.thePlayer.posZ;
            serverSet = true;
        }
        armChannel();
    }

    @Override
    public void onDisable() {
        choking = false;
        serverSet = false;
        clearPending();
        handler = null;
        final Channel channel = boundChannel;
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
                    }
                    return;
                }
                if (msg instanceof C02PacketUseEntity) {
                    C02PacketUseEntity packet = (C02PacketUseEntity) msg;
                    if (packet.getAction() == C02PacketUseEntity.Action.ATTACK) {
                        synchronized (lock) {
                            pending.add(new Pending(msg, promise));
                        }
                        LagRange.this.releaseBuffer(false);
                        return;
                    }
                }
            }
            ctx.write(msg, promise);
        }

        void release(List<Pending> batch) {
            ChannelHandlerContext ctx = context;
            if (ctx == null) return;
            for (int i = 0; i < batch.size(); i++) {
                Pending entry = batch.get(i);
                ctx.write(entry.msg, entry.promise);
            }
            ctx.flush();
        }
    }
}