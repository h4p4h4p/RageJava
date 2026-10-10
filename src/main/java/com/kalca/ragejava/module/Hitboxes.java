package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.BooleanSetting;
import com.kalca.ragejava.settings.SliderSetting;
import com.kalca.ragejava.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Hitboxes extends Module {

    private final SliderSetting multiplierSetting = new SliderSetting("Multiplier", 1.5, 1.0, 5.0, 0.05);
    private final BooleanSetting espSetting = new BooleanSetting("Show Hitboxes", false);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<UUID, AxisAlignedBB> originalBoxes = new HashMap<>();
    private final Map<UUID, AxisAlignedBB> expandedBoxes = new HashMap<>();

    public Hitboxes() {
        super("Hitboxes", Category.COMBAT);
        settings.add(multiplierSetting);
        settings.add(espSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        double mult = multiplierSetting.getValue();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == mc.thePlayer) continue;
            if (!(entity instanceof EntityPlayer)) continue;
            if (entity.isDead) continue;

            UUID uuid = entity.getUniqueID();
            AxisAlignedBB original = originalBoxes.get(uuid);

            if (original == null) {
                original = entity.getEntityBoundingBox();
                originalBoxes.put(uuid, original);
            }

            double cx = (original.minX + original.maxX) * 0.5;
            double cy = (original.minY + original.maxY) * 0.5;
            double cz = (original.minZ + original.maxZ) * 0.5;

            double halfX = (original.maxX - original.minX) * 0.5 * mult;
            double halfY = (original.maxY - original.minY) * 0.5 * mult;
            double halfZ = (original.maxZ - original.minZ) * 0.5 * mult;

            AxisAlignedBB expanded = new AxisAlignedBB(
                    cx - halfX, cy - halfY, cz - halfZ,
                    cx + halfX, cy + halfY, cz + halfZ);

            entity.setEntityBoundingBox(expanded);
            expandedBoxes.put(uuid, expanded);
        }
    }

    @Override
    public void onEnable() {
        originalBoxes.clear();
        expandedBoxes.clear();
    }

    @Override
    public void onDisable() {
        if (mc.theWorld == null) return;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
                UUID uuid = entity.getUniqueID();
                AxisAlignedBB original = originalBoxes.get(uuid);
                if (original != null) {
                    entity.setEntityBoundingBox(original);
                } else {
                    double w = entity.width / 2.0;
                    double h = entity.height;
                    double yOffset = 0;
                    if (entity instanceof EntityLivingBase) {
                        yOffset = ((EntityLivingBase) entity).getEyeHeight() - h;
                    }
                    entity.setEntityBoundingBox(new AxisAlignedBB(
                            entity.posX - w, entity.posY + yOffset, entity.posZ - w,
                            entity.posX + w, entity.posY + yOffset + h, entity.posZ + w));
                }
            }
        }
        originalBoxes.clear();
        expandedBoxes.clear();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        if (!espSetting.getValue()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;

        int r = 255, g = 255, b = 255, a = 180;

        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == mc.thePlayer) continue;
            if (!(entity instanceof EntityPlayer)) continue;
            if (entity.isDead) continue;

            UUID uuid = entity.getUniqueID();
            AxisAlignedBB expanded = expandedBoxes.get(uuid);
            if (expanded == null) continue;

            AxisAlignedBB box = AxisAlignedBB.fromBounds(
                    expanded.minX - camX, expanded.minY - camY, expanded.minZ - camZ,
                    expanded.maxX - camX, expanded.maxY - camY, expanded.maxZ - camZ);

            RenderUtil.drawOutlinedBox(tessellator, box, r, g, b, a);
        }

        GlStateManager.enableTexture2D();
    }
}