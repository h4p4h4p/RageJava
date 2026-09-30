package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

public class LeftClicker extends Module {

    private final SliderSetting delaySetting = new SliderSetting("Delay", 100, 0, 1000, 10);
    private final SliderSetting cpsSetting = new SliderSetting("CPS", 12, 1, 60, 1);
    private final SliderSetting randomSetting = new SliderSetting("Randomization", 0, 0, 100, 5);

    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean wasDown;
    private long pressTime;
    private int lastAttackTick = -1;

    public LeftClicker() {
        super("LeftClicker", Category.COMBAT);
        settings.add(delaySetting);
        settings.add(cpsSetting);
        settings.add(randomSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            reset();
            return;
        }

        boolean down = Mouse.isButtonDown(0);
        long now = System.currentTimeMillis();

        if (down && !wasDown) {
            pressTime = now;
            lastAttackTick = -1;
        }
        wasDown = down;

        if (!down) {
            reset();
            return;
        }

        if (mc.thePlayer.isUsingItem()) {
            lastAttackTick = mc.thePlayer.ticksExisted;
            return;
        }

        long delay = (long) delaySetting.getValue();
        if (now - pressTime < delay) return;

        if (mc.thePlayer.ticksExisted == lastAttackTick) return;

        double chance = Math.min(1.0, (cpsSetting.getValue() / 20.0) * jitterFactor());
        if (Math.random() < chance) {
            click();
            lastAttackTick = mc.thePlayer.ticksExisted;
        }
    }

    private double jitterFactor() {
        return 1.0 + (Math.random() * 2.0 - 1.0) * (randomSetting.getValue() / 100.0);
    }

    private void click() {
        MovingObjectPosition over = mc.objectMouseOver;
        if (over != null && over.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && over.entityHit != null) {
            mc.thePlayer.swingItem();
            mc.playerController.attackEntity(mc.thePlayer, over.entityHit);
        } else {
            mc.thePlayer.swingItem();
            if (over != null && over.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                mc.playerController.onPlayerDamageBlock(over.getBlockPos(), over.sideHit);
            }
        }
    }

    private void reset() {
        wasDown = false;
        pressTime = 0;
        lastAttackTick = -1;
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }
}