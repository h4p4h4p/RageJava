package com.kalca.voidfulenhancements.module;

import com.kalca.voidfulenhancements.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class BlockHit extends Module {

    private final SliderSetting chanceSetting = new SliderSetting("Chance", 80, 0, 100, 1);

    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean prevAttackDown;
    private boolean pendingReblock;
    private int reblockIn;

    public BlockHit() {
        super("BlockHit", Category.COMBAT);
        settings.add(chanceSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!mc.inGameHasFocus) return;

        EntityPlayer player = mc.thePlayer;
        boolean attackDown = Keyboard.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());

        if (attackDown && !prevAttackDown && player.isBlocking() && player.getHeldItem() != null && rollChance()) {
            unblock(player);
            pendingReblock = true;
            reblockIn = 3;
        }
        prevAttackDown = attackDown;

        if (pendingReblock && mc.playerController != null) {
            reblockIn--;
            boolean useDown = Keyboard.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode());
            if (useDown && player.getHeldItem() != null && !player.isUsingItem() && (!attackDown || reblockIn <= 0)) {
                mc.playerController.sendUseItem(player, mc.theWorld, player.getHeldItem());
                pendingReblock = false;
            } else if (!useDown) {
                pendingReblock = false;
            }
        }
    }

    private boolean rollChance() {
        return Math.random() * 100.0D < chanceSetting.getValue();
    }

    private void unblock(EntityPlayer player) {
        if (mc.playerController != null) {
            mc.playerController.onStoppedUsingItem(player);
        }
        player.stopUsingItem();
    }

    @Override
    public void onEnable() {
        prevAttackDown = false;
        pendingReblock = false;
    }

    @Override
    public void onDisable() {
        prevAttackDown = false;
        pendingReblock = false;
    }
}