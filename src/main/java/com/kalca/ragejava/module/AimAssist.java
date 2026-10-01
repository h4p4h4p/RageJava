package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Nudges the view toward the living entity nearest the crosshair.
 *
 * Rotates at RenderWorldLastEvent - the end of the frame, after the world tick and
 * after the mouse-look delta has already been applied for this frame. So the mouse
 * still nudges the aim on the next frame instead of overwriting it, and the new
 * rotation rides out with the next outgoing C03 look packet (sent every tick from
 * EntityPlayerSP.onUpdate), which is how the server learns where you're looking.
 *
 * Every frame it picks the entity with the smallest angular offset from the
 * crosshair that sits inside the FOV cone on both axes, then eases the current
 * yaw/pitch toward it by (smooth% * sensitivity) rather than snapping.
 */
public class AimAssist extends Module {

    private final SliderSetting xSmoothSetting = new SliderSetting("X Smooth", 30, 0, 100, 1);
    private final SliderSetting ySmoothSetting = new SliderSetting("Y Smooth", 30, 0, 100, 1);
    private final SliderSetting sensitivitySetting = new SliderSetting("Sensitivity", 1.0, 0.1, 3.0, 0.05);
    private final SliderSetting fovSetting = new SliderSetting("FOV", 45, 1, 180, 1);

    private final Minecraft mc = Minecraft.getMinecraft();

    public AimAssist() {
        super("AimAssist", Category.COMBAT);
        settings.add(xSmoothSetting);
        settings.add(ySmoothSetting);
        settings.add(sensitivitySetting);
        settings.add(fovSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;

        float curYaw = mc.thePlayer.rotationYaw;
        float curPitch = mc.thePlayer.rotationPitch;
        float fov = (float) fovSetting.getValue();

        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double pz = mc.thePlayer.posZ;

        float targetYaw = 0.0F;
        float targetPitch = 0.0F;
        double bestScore = Double.MAX_VALUE;
        boolean found = false;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == mc.thePlayer || entity.isDead || !(entity instanceof EntityLivingBase)) continue;

            double dx = entity.posX - px;
            double dy = (entity.posY + entity.getEyeHeight()) - py;
            double dz = entity.posZ - pz;
            double hor = Math.sqrt(dx * dx + dz * dz);
            if (hor < 0.01D && Math.abs(dy) < 0.01D) continue;

            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, hor));

            float dYaw = Math.abs(wrapDegrees(yaw - curYaw));
            float dPitch = Math.abs(pitch - curPitch);
            if (dYaw > fov || dPitch > fov) continue;

            double score = dYaw + dPitch;
            if (score < bestScore) {
                bestScore = score;
                targetYaw = yaw;
                targetPitch = pitch;
                found = true;
            }
        }

        if (!found) return;

        double sensitivity = sensitivitySetting.getValue();
        float yawFactor = (float) (xSmoothSetting.getValue() / 100.0 * sensitivity);
        float pitchFactor = (float) (ySmoothSetting.getValue() / 100.0 * sensitivity);

        float newYaw = curYaw + wrapDegrees(targetYaw - curYaw) * yawFactor;
        float newPitch = MathHelper.clamp_float(curPitch + (targetPitch - curPitch) * pitchFactor, -90.0F, 90.0F);

        mc.thePlayer.rotationYaw = newYaw;
        mc.thePlayer.rotationPitch = newPitch;
    }

    /** Normalises an angle to [-180, 180) so the aim takes the short way around. */
    private float wrapDegrees(float degrees) {
        float d = degrees % 360.0F;
        if (d >= 180.0F) d -= 360.0F;
        if (d < -180.0F) d += 360.0F;
        return d;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}