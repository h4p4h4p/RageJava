package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.BooleanSetting;
import com.kalca.ragejava.settings.ColorSetting;
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

public class Hitboxes extends Module {

    private final SliderSetting expansionSetting = new SliderSetting("Expansion", 0.3, 0.0, 2.0, 0.05);
    private final SliderSetting heightSetting = new SliderSetting("Height", 0.0, 0.0, 2.0, 0.05);
    private final BooleanSetting espSetting = new BooleanSetting("Show Hitboxes", false);
    private final ColorSetting espColorSetting = new ColorSetting("ESP Color", 0xFF1B395C);

    private final Minecraft mc = Minecraft.getMinecraft();

    public Hitboxes() {
        super("Hitboxes", Category.COMBAT);
        settings.add(expansionSetting);
        settings.add(heightSetting);
        settings.add(espSetting);
        settings.add(espColorSetting);
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

            AxisAlignedBB bb = entity.getEntityBoundingBox();
            double minX = bb.minX - expand;
            double minY = bb.minY - (height > 0 ? height / 2.0 : 0);
            double minZ = bb.minZ - expand;
            double maxX = bb.maxX + expand;
            double maxY = bb.maxY + (height > 0 ? height / 2.0 : 0);
            double maxZ = bb.maxZ + expand;

            entity.setEntityBoundingBox(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        if (mc.theWorld == null) return;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
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

        int color = espColorSetting.getValue();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = 180;

        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == mc.thePlayer) continue;
            if (!(entity instanceof EntityPlayer)) continue;
            if (entity.isDead) continue;

            AxisAlignedBB bb = entity.getEntityBoundingBox();
            double minX = bb.minX - expand;
            double minY = bb.minY - (height > 0 ? height / 2.0 : 0);
            double minZ = bb.minZ - expand;
            double maxX = bb.maxX + expand;
            double maxY = bb.maxY + (height > 0 ? height / 2.0 : 0);
            double maxZ = bb.maxZ + expand;

            AxisAlignedBB box = AxisAlignedBB.fromBounds(
                    minX - camX, minY - camY, minZ - camZ,
                    maxX - camX, maxY - camY, maxZ - camZ);

            RenderUtil.drawOutlinedBox(tessellator, box, r, g, b, a);
        }

        GlStateManager.enableTexture2D();
    }
}