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

    private final BooleanSetting jumpSetting = new BooleanSetting("Remove Jump Tick Delay", true);
    private final BooleanSetting hitRegSetting = new BooleanSetting("Enable 1.7 Hit Regulation", false);

    private final Minecraft mc = Minecraft.getMinecraft();
    private DelayRemoverHandler handler;
    private Channel boundChannel;

    private volatile Entity pendingAttack;
    private boolean wasOnGround;
    private long lastJumpAt;
    private long lastHitPong;

    public DelayRemover() {
        super("DelayRemover", Category.COMBAT);
        settings.add(jumpSetting);
        settings.add(hitRegSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        armChannel();
        handleJump();
        handleHitRegulation();
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

    private void handleHitRegulation() {
        if (!hitRegSetting.getValue()) return;
        if (pendingAttack != null) {
            // Remove hurt time from the target entity (1.7 style - no invulnerability)
            if (pendingAttack instanceof net.minecraft.entity.EntityLivingBase) {
                net.minecraft.entity.EntityLivingBase target = (net.minecraft.entity.EntityLivingBase) pendingAttack;
                if (target.hurtTime > 0) {
                    target.hurtTime = 0;
                }
                pendingAttack = null;
            }
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