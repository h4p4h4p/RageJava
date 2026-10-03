package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows hitting entities at positions they occupied in the past.
 *
 * <p>Stores a short history of entity positions each tick. When you attack an entity, this temporarily
 * moves that entity back to a previous position (based on the configured delay) before the attack is
 * sent, then immediately restores it. This affects client-side hit detection and makes the server
 * receive the attack against the historical position/hitbox.</p>
 */
public class Backtrack extends Module {

    private final SliderSetting ticksSetting = new SliderSetting("Ticks", 3, 1, 10, 1);
    private final SliderSetting rangeSetting = new SliderSetting("Range", 6, 1, 20, 0.5);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<Integer, Deque<PositionData>> history = new HashMap<>();

    private boolean isAttacking;
    private Entity attackedEntity;
    private double origPosX, origPosY, origPosZ;
    private double origPrevX, origPrevY, origPrevZ;
    private double origLastX, origLastY, origLastZ;

    public Backtrack() {
        super("Backtrack", Category.COMBAT);
        settings.add(ticksSetting);
        settings.add(rangeSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled()) return;
        if (mc.theWorld == null || mc.thePlayer == null) {
            history.clear();
            return;
        }

        int ticks = (int) Math.round(ticksSetting.getValue());
        double range = rangeSetting.getValue();
        double rangeSq = range * range;

        // Clean up old history
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Deque<PositionData>> entry : history.entrySet()) {
            Deque<PositionData> deque = entry.getValue();
            while (!deque.isEmpty() && mc.thePlayer.ticksExisted - deque.peekFirst().tick > ticks + 2) {
                deque.pollFirst();
            }
            if (deque.isEmpty()) {
                toRemove.add(entry.getKey());
            }
        }
        for (int id : toRemove) {
            history.remove(id);
        }

        // Record current positions
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            if (entity == mc.thePlayer) continue;
            if (entity.isDead) continue;
            if (entity.getDistanceSqToEntity(mc.thePlayer) > rangeSq) continue;

            Deque<PositionData> deque = history.get(entity.getEntityId());
            if (deque == null) {
                deque = new ArrayDeque<>();
                history.put(entity.getEntityId(), deque);
            }
            // Only record if position changed significantly to avoid spam
            if (deque.isEmpty()) {
                deque.addLast(new PositionData(mc.thePlayer.ticksExisted, entity.posX, entity.posY, entity.posZ,
                        entity.prevPosX, entity.prevPosY, entity.prevPosZ,
                        entity.lastTickPosX, entity.lastTickPosY, entity.lastTickPosZ));
            } else {
                PositionData last = deque.peekLast();
                double moved = (entity.posX - last.x) * (entity.posX - last.x) +
                        (entity.posY - last.y) * (entity.posY - last.y) +
                        (entity.posZ - last.z) * (entity.posZ - last.z);
                if (moved > 0.0001) {
                    deque.addLast(new PositionData(mc.thePlayer.ticksExisted, entity.posX, entity.posY, entity.posZ,
                            entity.prevPosX, entity.prevPosY, entity.prevPosZ,
                            entity.lastTickPosX, entity.lastTickPosY, entity.lastTickPosZ));
                }
            }
            while (deque.size() > ticks + 5) {
                deque.pollFirst();
            }
        }
    }

    @Override
    public void onEnable() {
        history.clear();
        isAttacking = false;
        attackedEntity = null;
    }

    @Override
    public void onDisable() {
        history.clear();
        restore();
        isAttacking = false;
        attackedEntity = null;
    }

    public boolean handleAttack() {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return false;
        if (isAttacking) return false;

        MovingObjectPosition over = mc.objectMouseOver;
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || over.entityHit == null) {
            return false;
        }
        if (!(over.entityHit instanceof EntityLivingBase)) return false;

        Entity entity = over.entityHit;
        Deque<PositionData> deque = history.get(entity.getEntityId());
        if (deque == null || deque.size() < 2) return false;

        int ticksBack = (int) Math.round(ticksSetting.getValue());
        PositionData targetData = null;
        for (PositionData data : deque) {
            if (mc.thePlayer.ticksExisted - data.tick >= ticksBack) {
                targetData = data;
                break;
            }
        }
        if (targetData == null) {
            return false;
        }

        isAttacking = true;
        attackedEntity = entity;

        origPosX = entity.posX;
        origPosY = entity.posY;
        origPosZ = entity.posZ;
        origPrevX = entity.prevPosX;
        origPrevY = entity.prevPosY;
        origPrevZ = entity.prevPosZ;
        origLastX = entity.lastTickPosX;
        origLastY = entity.lastTickPosY;
        origLastZ = entity.lastTickPosZ;

        entity.posX = targetData.x;
        entity.posY = targetData.y;
        entity.posZ = targetData.z;
        entity.prevPosX = targetData.prevX;
        entity.prevPosY = targetData.prevY;
        entity.prevPosZ = targetData.prevZ;
        entity.lastTickPosX = targetData.lastX;
        entity.lastTickPosY = targetData.lastY;
        entity.lastTickPosZ = targetData.lastZ;
        entity.setPosition(targetData.x, targetData.y, targetData.z);

        return false;
    }

    public void afterAttack() {
        restore();
    }

    private void restore() {
        if (!isAttacking || attackedEntity == null) {
            isAttacking = false;
            attackedEntity = null;
            return;
        }

        Entity entity = attackedEntity;
        if (!entity.isDead && entity.worldObj == mc.theWorld) {
            entity.posX = origPosX;
            entity.posY = origPosY;
            entity.posZ = origPosZ;
            entity.prevPosX = origPrevX;
            entity.prevPosY = origPrevY;
            entity.prevPosZ = origPrevZ;
            entity.lastTickPosX = origLastX;
            entity.lastTickPosY = origLastY;
            entity.lastTickPosZ = origLastZ;
            entity.setPosition(origPosX, origPosY, origPosZ);
        }

        isAttacking = false;
        attackedEntity = null;
    }

    private static class PositionData {
        final int tick;
        final double x, y, z;
        final double prevX, prevY, prevZ;
        final double lastX, lastY, lastZ;

        PositionData(int tick, double x, double y, double z,
                     double prevX, double prevY, double prevZ,
                     double lastX, double lastY, double lastZ) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.prevX = prevX;
            this.prevY = prevY;
            this.prevZ = prevZ;
            this.lastX = lastX;
            this.lastY = lastY;
            this.lastZ = lastZ;
        }
    }
}