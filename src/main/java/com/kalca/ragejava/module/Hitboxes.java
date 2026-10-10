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

    private final SliderSetting expansionSetting = new SliderSetting("Expansion", 0.3, 0.0, 2.0, 0.05);
    private final SliderSetting heightSetting = new SliderSetting("Height", 0.0, 0.0, 2.0, 0.05);
    private final BooleanSetting espSetting = new BooleanSetting("Show Hitboxes", false);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<UUID, AxisAlignedBB> originalBoxes = new HashMap<>();

    public Hitboxes() {
        super("Hitboxes", Category.COMBAT);
        settings.add(expansionSetting);
        settings.add(heightSetting);
        settings.add(espSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        double expand = expansionSetting.getValue();
        double height = heightSetting.getValue();

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

            double minX = original.minX - expand;
            double minY = original.minY - (height > 0 ? height / 2.0 : 0);
            double minZ = original.minZ - expand;
            double maxX = original.maxX + expand;
            double maxY = original.maxY + (height > 0 ? height / 2.0 : 0);
            double maxZ = original.maxZ + expand;

            entity.setEntityBoundingBox(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    @Override
    public void onEnable() {
        originalBoxes.clear();
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
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        if (!espSetting.getValue()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;

        double expand = expansionSetting.getValue();
        double height = heightSetting.getValue();

        int r = 255, g = 255, b = 255, a = 180;

        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == mc.thePlayer) continue;
            if (!(entity instanceof EntityPlayer)) continue;
            if (entity.isDead) continue;

            UUID uuid = entity.getUniqueID();
            AxisAlignedBB original = originalBoxes.get(uuid);
            if (original == null) continue;

            double minX = original.minX - expand;
            double minY = original.minY - (height > 0 ? height / 2.0 : 0);
            double minZ = original.minZ - expand;
            double maxX = original.maxX + expand;
            double maxY = original.maxY + (height > 0 ? height / 2.0 : 0);
            double maxZ = original.maxZ + expand;

            AxisAlignedBB box = AxisAlignedBB.fromBounds(
                    minX - camX, minY - camY, minZ - camZ,
                    maxX - camX, maxY - camY, maxZ - camZ);

            RenderUtil.drawOutlinedBox(tessellator, box, r, g, b, a);
        }

        GlStateManager.enableTexture2D();
    }
}