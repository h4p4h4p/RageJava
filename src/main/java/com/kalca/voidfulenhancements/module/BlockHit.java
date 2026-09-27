package com.kalca.voidfulenhancements.module;

import com.kalca.voidfulenhancements.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class BlockHit extends Module {

    private final SliderSetting chanceSetting = new SliderSetting("Chance", 80, 0, 100, 1);

    private final Minecraft mc = Minecraft.getMinecraft();

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
        if (!player.isBlocking() || player.getHeldItem() == null) return;
        if (!rollChance()) return;

        if (mc.playerController == null || mc.getNetHandler() == null) return;
        if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) return;

        Entity target = mc.objectMouseOver.entityHit;
        mc.playerController.attackEntity(player, target);
    }

    private boolean rollChance() {
        return Math.random() * 100.0D < chanceSetting.getValue();
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}
}