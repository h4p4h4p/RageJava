package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class Jetpack extends Module {

    private final SliderSetting speedSetting = new SliderSetting("Speed", 0.5, 0.1, 2.0, 0.05);
    private final Minecraft mc = Minecraft.getMinecraft();

    public Jetpack() {
        super("Jetpack", Category.MOVEMENT);
        settings.add(speedSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.thePlayer == null) return;

        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            mc.thePlayer.motionY = speedSetting.getValue();
        }
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}