package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Cancels the directional camera kick you get when you take a hit.
 *
 * <p>Vanilla reads this in {@code EntityRenderer.hurtCameraEffect}, which builds its tilt from
 * {@code hurtTime / maxHurtTime} and {@code attackedAtYaw}. Nothing there is reachable without a
 * coremod, but both fields are plain public ints on {@code EntityLivingBase}, so the ratio is
 * something we can influence instead.
 *
 * <p>Inflating {@code maxHurtTime} is what keeps this honest. The tilt peaks at 14 degrees, scaled by
 * {@code sin(f^4 * PI)} where {@code f = (hurtTime - partialTicks) / maxHurtTime}. With vanilla's 10
 * that lands mid-swing; pushing the divisor to a million drops {@code f} to ~1e-5, and {@code f^4}
 * underflows the sine to nothing. So the camera stays level while {@code hurtTime} keeps counting
 * down normally, which is what leaves the red damage flash intact - the module is named for the
 * camera, and some players still want to see that flash.
 *
 * <p>With {@code No Flash} on, we instead zero {@code hurtTime} outright, which is the blunt version
 * that suppresses the flash too.
 */
public class NoHurtCam extends Module {

    /**
     * Large enough that {@code f^4} vanishes under float precision, small enough to stay well clear
     * of any int overflow if something ever multiplies against it.
     */
    private static final int SUPPRESSED_MAX_HURT_TIME = 1_000_000;

    /** What vanilla assigns in every place it sets {@code hurtTime}, and so what we restore. */
    private static final int VANILLA_MAX_HURT_TIME = 10;

    private final BooleanSetting noFlashSetting = new BooleanSetting("No Flash", false);

    private final Minecraft mc = Minecraft.getMinecraft();

    public NoHurtCam() {
        super("NoHurtCam", Category.RENDER);
        settings.add(noFlashSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        // END, not START: damage is applied while the world ticks, so this has to run after it or
        // we clear last tick's value and spend this frame with the tilt back.
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled()) return;

        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;

        if (noFlashSetting.getValue()) {
            player.hurtTime = 0;
            if (player.maxHurtTime == SUPPRESSED_MAX_HURT_TIME) {
                player.maxHurtTime = VANILLA_MAX_HURT_TIME;
            }
            return;
        }

        // Vanilla reassigns maxHurtTime = 10 on every hit, so re-assert ours whenever it moved.
        if (player.maxHurtTime != SUPPRESSED_MAX_HURT_TIME) {
            player.maxHurtTime = SUPPRESSED_MAX_HURT_TIME;
        }
    }

    @Override
    public void onEnable() {
        apply();
    }

    @Override
    public void onDisable() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;
        if (player.maxHurtTime == SUPPRESSED_MAX_HURT_TIME) {
            player.maxHurtTime = VANILLA_MAX_HURT_TIME;
        }
    }

    private void apply() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;
        if (noFlashSetting.getValue()) {
            player.hurtTime = 0;
        } else if (player.maxHurtTime != SUPPRESSED_MAX_HURT_TIME) {
            player.maxHurtTime = SUPPRESSED_MAX_HURT_TIME;
        }
    }
}