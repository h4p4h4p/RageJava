package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.BooleanSetting;
import com.kalca.ragejava.settings.ModeSetting;
import com.kalca.ragejava.settings.Setting;
import com.kalca.ragejava.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

public class LeftClicker extends Module {

    public static final String MODE_NORMAL = "Normal";
    public static final String MODE_TRIGGER = "Trigger";
    public static final String MODE_NCP = "NCP";

    private final ModeSetting modeSetting = new ModeSetting("Mode", new String[]{MODE_NORMAL, MODE_TRIGGER, MODE_NCP}, 0);
    private final SliderSetting delaySetting = new SliderSetting("Delay", 100, 0, 1000, 10);
    private final SliderSetting cpsSetting = new SliderSetting("CPS", 12, 1, 24, 1);
    private final SliderSetting randomSetting = new SliderSetting("Randomization", 0, 0, 100, 5);
    private final BooleanSetting doubleClickSetting = new BooleanSetting("Double Click", false);
    private final SliderSetting holdDurationSetting = new SliderSetting("Hold Duration", 50, 10, 200, 10);
    private final BooleanSetting tickSpreaderSetting = new BooleanSetting("Tick Spreader", false);

    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean wasDown;
    private long pressTime;
    private int lastAttackTick = -1;
    private boolean doubleClicked;

    public LeftClicker() {
        super("LeftClicker", Category.COMBAT);
        settings.add(modeSetting);
        settings.add(delaySetting);
        settings.add(cpsSetting);
        settings.add(randomSetting);
        settings.add(doubleClickSetting);
        settings.add(holdDurationSetting);
        settings.add(tickSpreaderSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean isSettingVisible(Setting setting) {
        boolean ncp = MODE_NCP.equals(modeSetting.getValue());
        if (setting == doubleClickSetting) return ncp;
        if (setting == holdDurationSetting) return ncp && doubleClickSetting.getValue();
        if (setting == tickSpreaderSetting) return ncp;
        return true;
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
            doubleClicked = false;
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

        boolean ncp = MODE_NCP.equals(modeSetting.getValue());
        double cps = ncp ? Math.min(cpsSetting.getValue(), 18) : cpsSetting.getValue();
        double chance = Math.min(1.0, (cps / 20.0) * jitterFactor());

        if (ncp && tickSpreaderSetting.getValue()) {
            if (mc.thePlayer.ticksExisted % 2 != 0) return;
        }

        if (Math.random() < chance) {
            click();
            lastAttackTick = mc.thePlayer.ticksExisted;

            if (ncp && doubleClickSetting.getValue() && !doubleClicked) {
                doubleClicked = true;
                long holdMs = (long) holdDurationSetting.getValue();
                if (now + holdMs > pressTime) {
                    click();
                }
            }
        }
    }

    private double jitterFactor() {
        return 1.0 + (Math.random() * 2.0 - 1.0) * (randomSetting.getValue() / 100.0);
    }

    private boolean triggerMode() {
        return MODE_TRIGGER.equals(modeSetting.getValue());
    }

    private void click() {
        if (triggerMode()) {
            clickEntityOnly();
            return;
        }
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

    private void clickEntityOnly() {
        MovingObjectPosition over = mc.objectMouseOver;
        if (over == null) return;
        if (over.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;
        Entity target = over.entityHit;
        if (target == null) return;
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, target);
    }

    private void reset() {
        wasDown = false;
        pressTime = 0;
        lastAttackTick = -1;
        doubleClicked = false;
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