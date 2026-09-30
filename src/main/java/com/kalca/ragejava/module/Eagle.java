package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.SliderSetting;
import com.kalca.ragejava.util.PlayerUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class Eagle extends Module {

    private final SliderSetting distanceSetting = new SliderSetting("Distance", 0.3, 0.1, 1.0, 0.05);
    private final SliderSetting delaySetting = new SliderSetting("Delay", 200, 0, 1000, 10);

    private final Minecraft mc = Minecraft.getMinecraft();
    private long edgeSince = -1;
    private boolean autoSneaking;

    public Eagle() {
        super("Eagle", Category.MOVEMENT);
        settings.add(distanceSetting);
        settings.add(delaySetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (atEdge()) {
            if (edgeSince == -1) edgeSince = System.currentTimeMillis();
            if (System.currentTimeMillis() - edgeSince >= (long) delaySetting.getValue()) {
                setSneak(true);
            }
        } else {
            edgeSince = -1;
            setSneak(false);
        }
    }

    private boolean atEdge() {
        EntityPlayer player = mc.thePlayer;
        if (!player.onGround || !PlayerUtil.isMoving(player)) return false;

        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (forward == 0 && strafe == 0) return false;

        float rad = (float) Math.toRadians(player.rotationYaw);
        double mx = forward * -MathHelper.sin(rad) + strafe * MathHelper.cos(rad);
        double mz = forward * MathHelper.cos(rad) + strafe * MathHelper.sin(rad);
        double len = MathHelper.sqrt_double(mx * mx + mz * mz);
        if (len < 1.0E-4D) return false;
        mx /= len;
        mz /= len;

        double d = distanceSetting.getValue() + 0.05D;
        double px = player.posX + mx * d;
        double pz = player.posZ + mz * d;
        double py = player.posY - 0.05D;

        BlockPos ahead = new BlockPos(px, py, pz);
        Block aheadBlock = mc.theWorld.getBlockState(ahead).getBlock();
        if (!aheadBlock.isAir(mc.theWorld, ahead)) return false;

        BlockPos below = ahead.down();
        Block belowBlock = mc.theWorld.getBlockState(below).getBlock();
        return belowBlock.isAir(mc.theWorld, below);
    }

    private void setSneak(boolean state) {
        if (autoSneaking == state) return;
        if (!state && mc.gameSettings.keyBindSneak.isKeyDown()) return;
        autoSneaking = state;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), state);
    }

    @Override
    public void onEnable() {
        edgeSince = -1;
    }

    @Override
    public void onDisable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        autoSneaking = false;
        edgeSince = -1;
    }
}