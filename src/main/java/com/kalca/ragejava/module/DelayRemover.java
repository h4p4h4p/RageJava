package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.BooleanSetting;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class DelayRemover extends Module {

    private static final String HANDLER_NAME = "ragejava_delayremover";

    private final BooleanSetting jumpSetting = new BooleanSetting("Jump", true);
    private final BooleanSetting breakSetting = new BooleanSetting("Break", true);
    private final BooleanSetting hitSetting = new BooleanSetting("Hit", true);
    private final BooleanSetting rehitSetting = new BooleanSetting("ReHit", false);

    private final Minecraft mc = Minecraft.getMinecraft();
    private DelayRemoverHandler handler;
    private Channel boundChannel;

    private volatile Entity pendingAttack;
    private boolean wasOnGround;
    private long lastJumpAt;
    private long lastHitPong;
    private long lastBreakPong;

    public DelayRemover() {
        super("DelayRemover", Category.COMBAT);
        settings.add(jumpSetting);
        settings.add(breakSetting);
        settings.add(hitSetting);
        settings.add(rehitSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        armChannel();
        handleJump();
        handleBreak();
        handleHit();
    }

    private void handleJump() {
        if (!jumpSetting.getValue()) return;
        EntityPlayer player = mc.thePlayer;
        boolean onGround = player.onGround;

        if (wasOnGround && !onGround && player.motionY > 0.0D) {
            lastJumpAt = System.currentTimeMillis();
        }
        wasOnGround = onGround;
        if (onGround) {
            lastJumpAt = 0;
            return;
        }
        if (lastJumpAt == 0) return;

        long since = System.currentTimeMillis() - lastJumpAt;
        if (since < 40 || since > 200) return;
        lastJumpAt = 0;
        sendPlayerPing();
    }

    private void handleBreak() {
        if (!breakSetting.getValue()) return;
        if (mc.playerController == null || !mc.playerController.getIsHittingBlock()) return;
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        EnumFacing side = mc.objectMouseOver.sideHit;
        if (pos == null || side == null) return;
        if (mc.theWorld.getBlockState(pos).getBlock().isAir(mc.theWorld, pos)) return;

        long now = System.currentTimeMillis();
        if (now - lastBreakPong < 150) return;
        lastBreakPong = now;

        NetworkManager nm = mc.getNetHandler().getNetworkManager();
        if (nm == null) return;
        nm.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, side));
        nm.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, side));
        mc.thePlayer.swingItem();
    }

    private void handleHit() {
        if (!hitSetting.getValue()) return;
        Entity target = pendingAttack;
        if (target == null) return;
        pendingAttack = null;

        long now = System.currentTimeMillis();
        if (now - lastHitPong < 100) return;
        lastHitPong = now;

        if (rehitSetting.getValue() && mc.playerController != null) {
            mc.playerController.attackEntity(mc.thePlayer, target);
            sendPlayerPing();
            if (target.isEntityAlive()) {
                mc.playerController.attackEntity(mc.thePlayer, target);
            }
        } else {
            mc.thePlayer.swingItem();
            sendPlayerPing();
        }
    }

    private void sendPlayerPing() {
        NetworkManager nm = mc.getNetHandler() == null ? null : mc.getNetHandler().getNetworkManager();
        if (nm == null) return;
        EntityPlayer player = mc.thePlayer;
        nm.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch, player.onGround));
    }

    private void armChannel() {
        if (mc.getNetHandler() == null) return;
        NetworkManager manager = mc.getNetHandler().getNetworkManager();
        if (manager == null) return;
        Channel channel = manager.channel();
        if (channel == null || !channel.isActive()) return;

        if (handler == null) {
            handler = new DelayRemoverHandler();
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

    @Override
    public void onEnable() {
        wasOnGround = mc.thePlayer != null && mc.thePlayer.onGround;
        armChannel();
    }

    @Override
    public void onDisable() {
        handler = null;
        pendingAttack = null;
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

    private class DelayRemoverHandler extends ChannelDuplexHandler {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof C02PacketUseEntity) {
                C02PacketUseEntity packet = (C02PacketUseEntity) msg;
                if (packet.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    Entity target = packet.getEntityFromWorld(mc.theWorld);
                    if (target != null) pendingAttack = target;
                }
            }
            ctx.write(msg, promise);
        }
    }
}