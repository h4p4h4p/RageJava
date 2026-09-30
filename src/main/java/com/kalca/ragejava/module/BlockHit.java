package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class BlockHit extends Module {

    private final SliderSetting chanceSetting = new SliderSetting("Chance", 80, 0, 100, 1);
    private final SliderSetting delaySetting = new SliderSetting("Delay", 200, 0, 500, 10);

    private static final int IDLE = 0;
    private static final int WAIT_HIT = 1;
    private static final int WAIT_REBLOCK = 2;

    private final Minecraft mc = Minecraft.getMinecraft();
    private int state = IDLE;
    private long hitAt;
    private long reblockAt;
    private Entity target;

    public BlockHit() {
        super("BlockHit", Category.COMBAT);
        settings.add(chanceSetting);
        settings.add(delaySetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!isEnabled()) return;
        if (event.button != 0 || !event.buttonstate) return;
        if (mc.currentScreen != null || mc.thePlayer == null || mc.theWorld == null) return;
        if (state != IDLE) return;

        EntityPlayer player = mc.thePlayer;
        if (!player.isBlocking() || player.getHeldItem() == null) return;
        if (!rollChance()) return;
        if (mc.playerController == null || mc.getNetHandler() == null) return;
        if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) return;

        target = mc.objectMouseOver.entityHit;
        event.setCanceled(true);
        unblock(player);

        long now = System.currentTimeMillis();
        hitAt = now + 40L + (long) (Math.random() * 60.0D);
        reblockAt = hitAt + randomizedDelay();
        state = WAIT_HIT;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || state == IDLE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        EntityPlayer player = mc.thePlayer;
        if (!mc.gameSettings.keyBindUseItem.isKeyDown()) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();

        if (state == WAIT_HIT) {
            if (now >= hitAt) {
                if (player.isUsingItem()) {
                    reset();
                    return;
                }
                player.swingItem();
                if (target != null && mc.playerController != null) {
                    mc.playerController.attackEntity(player, target);
                }
                state = WAIT_REBLOCK;
            }
            return;
        }

        if (state == WAIT_REBLOCK) {
            if (now >= reblockAt && !player.isUsingItem() && player.getHeldItem() != null) {
                if (mc.playerController != null) {
                    mc.playerController.sendUseItem(player, mc.theWorld, player.getHeldItem());
                }
                reset();
            }
        }
    }

    private long randomizedDelay() {
        double base = delaySetting.getValue();
        double jitter = base * 0.25D * (Math.random() * 2.0D - 1.0D);
        return (long) Math.max(0, base + jitter);
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

    private void reset() {
        state = IDLE;
        target = null;
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