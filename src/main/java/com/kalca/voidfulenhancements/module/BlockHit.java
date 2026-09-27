package com.kalca.voidfulenhancements.module;

import com.kalca.voidfulenhancements.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class BlockHit extends Module {

    private final SliderSetting chanceSetting = new SliderSetting("Chance", 80, 0, 100, 1);

    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean pendingReblock;
    private int reblockIn;

    public BlockHit() {
        super("BlockHit", Category.COMBAT);
        settings.add(chanceSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!isEnabled()) return;
        if (event.button != 0 || !event.buttonstate) return;
        if (mc.currentScreen != null || mc.thePlayer == null || mc.theWorld == null) return;

        EntityPlayer player = mc.thePlayer;
        if (player.isBlocking() && player.getHeldItem() != null && rollChance()) {
            unblock(player);
            pendingReblock = true;
            reblockIn = 3;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        EntityPlayer player = mc.thePlayer;
        if (pendingReblock && mc.playerController != null) {
            reblockIn--;
            boolean useDown = mc.gameSettings.keyBindUseItem.isKeyDown();
            boolean attackDown = mc.gameSettings.keyBindAttack.isKeyDown();
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
        pendingReblock = false;
    }

    @Override
    public void onDisable() {
        pendingReblock = false;
    }
}