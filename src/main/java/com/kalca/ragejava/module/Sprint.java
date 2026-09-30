package com.kalca.ragejava.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Always-sprint without the double-tap or control key, and overrides the
 * client-side checks that normally break sprint (EntityPlayerSP.onLivingUpdate
 * sets sprint false when moveForward drops, the player hits a wall, runs low
 * on food or starts eating). Re-asserting on PlayerTickEvent.Phase.END puts us
 * after that decision, so sprint stays up for as long as forward is held.
 *
 * Gated on forward input rather than unconditional: the server only grants the
 * speed boost while actually travelling forward, and holding a stale sprint
 * flag while idle or backpedalling buys nothing but packet churn and an
 * obvious modifier on the movement. Sneaking still force-sneaks on the server:
 * it silently ignores START_SPRINTING while the sneak flag is set.
 */
public class Sprint extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled()) return;

        EntityPlayerSP player = mc.thePlayer;
        if (player == null || event.player != player) return;
        if (player.movementInput.moveForward > 0) {
            player.setSprinting(true);
        }
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            mc.thePlayer.setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
    }
}