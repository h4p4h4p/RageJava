package com.kalca.ragejava.module;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Cancels the camera kick you get when you take a hit.
 *
 * <p>Vanilla builds it in {@code EntityRenderer.hurtCameraEffect}, which is private and has no Forge
 * hook, but it is driven entirely by the public {@code hurtTime} counter and guarded by
 * {@code if (f < 0.0F) return;} where {@code f = hurtTime - partialTicks}. Holding {@code hurtTime}
 * at zero therefore short-circuits that guard before any rotation is emitted, which is a hard
 * guarantee rather than something depending on how a tilt is scaled.
 *
 * <p>{@code hurtCameraEffect} is only reachable from {@code setupCameraTransform}, which runs at the
 * very top of {@code renderWorldPass} - ahead of {@code RenderWorldEvent.Pre} - so there is no
 * render-time hook to catch this and the counter has to be cleared on the tick instead.
 *
 * <p>Side effect, and the reason the module does not try to be cleverer: {@code RendererLivingEntity}
 * gates the red damage flash on {@code hurtTime > 0}, so zeroing the counter removes that too. An
 * earlier version inflated {@code maxHurtTime} instead, to shrink the tilt while leaving the flash
 * intact - but that only suppresses the effect if the divisor survives to the render, and it did
 * not reliably. Blunt and verifiable beats subtle and wrong.
 */
public class NoHurtCam extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    public NoHurtCam() {
        super("NoHurtCam", Category.RENDER);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        // END, not START: damage is applied while the world ticks, so this has to run after it or we
        // clear last tick's value and the frame still renders with the tilt.
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled()) return;
        clearHurtTime();
    }

    @Override
    public void onEnable() {
        clearHurtTime();
    }

    @Override
    public void onDisable() {
        // Nothing to undo: hurtTime is re-armed by vanilla on the next hit, and nothing was patched.
    }

    /**
     * Targets the render view entity rather than {@code thePlayer} so this also holds while riding or
     * spectating, since that is the entity the camera effect actually reads.
     */
    private void clearHurtTime() {
        Entity view = mc.getRenderViewEntity();
        if (view instanceof EntityLivingBase) {
            ((EntityLivingBase) view).hurtTime = 0;
        }
    }
}