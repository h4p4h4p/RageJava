package com.kalca.ragejava.util;

import net.minecraft.entity.player.EntityPlayer;

public class PlayerUtil {

    public static boolean isMoving(EntityPlayer player) {
        return player.moveForward != 0 || player.moveStrafing != 0;
    }

    public static void strafe(EntityPlayer player, double speed) {
        boolean forward = player.moveForward > 0;
        boolean backward = player.moveForward < 0;
        boolean right = player.moveStrafing > 0;
        boolean left = player.moveStrafing < 0;

        float yaw = player.rotationYaw;

        if (forward) {
            if (left) yaw += 45;
            else if (right) yaw -= 45;
        } else if (backward) {
            yaw += 180;
            if (left) yaw -= 45;
            else if (right) yaw += 45;
        } else {
            if (left) yaw += 90;
            else if (right) yaw -= 90;
        }

        if (forward || backward || left || right) {
            double rad = Math.toRadians(yaw);
            player.motionX = -Math.sin(rad) * speed;
            player.motionZ = Math.cos(rad) * speed;
        }
    }
}