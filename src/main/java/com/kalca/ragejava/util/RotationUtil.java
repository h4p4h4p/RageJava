package com.kalca.ragejava.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class RotationUtil {

    public static float[] getRotations(Vec3 target) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return new float[]{0, 0};

        double diffX = target.xCoord - mc.thePlayer.posX;
        double diffY = target.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double diffZ = target.zCoord - mc.thePlayer.posZ;

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        yaw = MathHelper.wrapAngleTo180_float(yaw);
        pitch = MathHelper.clamp_float(pitch, -90.0f, 90.0f);

        return new float[]{yaw, pitch};
    }

    public static float[] getRotations(double x, double y, double z) {
        return getRotations(new Vec3(x, y, z));
    }

    public static float getYawChange(float targetYaw) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return 0;
        float yaw = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
        return yaw;
    }

    public static float getPitchChange(float targetPitch) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return 0;
        return targetPitch - mc.thePlayer.rotationPitch;
    }
}