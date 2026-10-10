package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.BooleanSetting;
import com.kalca.ragejava.settings.ModeSetting;
import com.kalca.ragejava.settings.Setting;
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

    public static final String MODE_STANDARD = "Standard";
    public static final String MODE_DISADVANTAGE = "Disadvantage";

    private final ModeSetting modeSetting = new ModeSetting("Mode", new String[]{MODE_STANDARD, MODE_DISADVANTAGE}, 0);
    private final SliderSetting multiplierSetting = new SliderSetting("Multiplier", 1.5, 1.0, 5.0, 0.05);
    private final BooleanSetting espSetting = new BooleanSetting("Show Hitboxes", false);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<UUID, double[]> originalDims = new HashMap<>(); // [width, height]

    public Hitboxes() {
        super("Hitboxes", Category.COMBAT);
        settings.add(modeSetting);
        settings.add(multiplierSetting);
        settings.add(espSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean isSettingVisible(Setting setting) {
        return true;
    }

    private String mode() {
        return modeSetting.getValue();
    }

    private boolean standard() {
        return MODE_STANDARD.equals(mode());
    }

    private boolean disadvantage() {
        return MODE_DISADVANTAGE.equals(mode());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        double mult = multiplierSetting.getValue();

        if (standard()) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity == mc.thePlayer) continue;
                if (!(entity instanceof EntityPlayer)) continue;
                if (entity.isDead) continue;

                UUID uuid = entity.getUniqueID();
                double[] dims = originalDims.get(uuid);

                if (dims == null) {
                    dims = new double[]{entity.width, entity.height};
                    originalDims.put(uuid, dims);
                }

                double origWidth = dims[0];
                double origHeight = dims[1];

                double halfX = (origWidth * 0.5) * mult;
                double halfY = (origHeight * 0.5) * mult;
                double halfZ = (origWidth * 0.5) * mult;

                double cx = entity.posX;
                double cy = entity.posY;
                double cz = entity.posZ;

                AxisAlignedBB expanded = new AxisAlignedBB(
                        cx - halfX, cy - halfY, cz - halfZ,
                        cx + halfX, cy + halfY, cz + halfZ);

                entity.setEntityBoundingBox(expanded);
            }
        } else if (disadvantage()) {
            EntityPlayer player = mc.thePlayer;
            UUID uuid = player.getUniqueID();
            double[] dims = originalDims.get(uuid);

            if (dims == null) {
                dims = new double[]{player.width, player.height};
                originalDims.put(uuid, dims);
            }

            double origWidth = dims[0];
            double origHeight = dims[1];

            double halfX = (origWidth * 0.5) * mult;
            double halfY = (origHeight * 0.5) * mult;
            double halfZ = (origWidth * 0.5) * mult;

            double cx = player.posX;
            double cy = player.posY;
            double cz = player.posZ;

            AxisAlignedBB expanded = new AxisAlignedBB(
                    cx - halfX, cy - halfY, cz - halfZ,
                    cx + halfX, cy + halfY, cz + halfZ);

            player.setEntityBoundingBox(expanded);
        }
    }

    @Override
    public void onEnable() {
        originalDims.clear();
    }

    @Override
    public void onDisable() {
        if (mc.theWorld == null) return;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
                UUID uuid = entity.getUniqueID();
                double[] dims = originalDims.get(uuid);
                if (dims != null) {
                    double w = dims[0] * 0.5;
                    double h = dims[1];
                    double yOffset = 0;
                    if (entity instanceof EntityLivingBase) {
                        yOffset = ((EntityLivingBase) entity).getEyeHeight() - h;
                    }
                    entity.setEntityBoundingBox(new AxisAlignedBB(
                            entity.posX - w, entity.posY + yOffset, entity.posZ - w,
                            entity.posX + w, entity.posY + yOffset + h, entity.posZ + w));
                } else {
                    double w = entity.width * 0.5;
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
        if (mc.thePlayer != null) {
            UUID uuid = mc.thePlayer.getUniqueID();
            double[] dims = originalDims.get(uuid);
            if (dims != null) {
                double w = dims[0] * 0.5;
                double h = dims[1];
                double yOffset = 0;
                if (mc.thePlayer instanceof EntityLivingBase) {
                    yOffset = ((EntityLivingBase) mc.thePlayer).getEyeHeight() - h;
                }
                mc.thePlayer.setEntityBoundingBox(new AxisAlignedBB(
                        mc.thePlayer.posX - w, mc.thePlayer.posY + yOffset, mc.thePlayer.posZ - w,
                        mc.thePlayer.posX + w, mc.thePlayer.posY + yOffset + h, mc.thePlayer.posZ + w));
            }
        }
        originalDims.clear();
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

        if (standard()) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity == mc.thePlayer) continue;
                if (!(entity instanceof EntityPlayer)) continue;
                if (entity.isDead) continue;

                UUID uuid = entity.getUniqueID();
                double[] dims = originalDims.get(uuid);
                if (dims == null) continue;

                double mult = multiplierSetting.getValue();
                double origWidth = dims[0];
                double origHeight = dims[1];

                double halfX = (origWidth * 0.5) * mult;
                double halfY = (origHeight * 0.5) * mult;
                double halfZ = (origWidth * 0.5) * mult;

                double cx = entity.posX;
                double cy = entity.posY;
                double cz = entity.posZ;

                AxisAlignedBB expanded = new AxisAlignedBB(
                        cx - halfX, cy - halfY, cz - halfZ,
                        cx + halfX, cy + halfY, cz + halfZ);

                AxisAlignedBB box = AxisAlignedBB.fromBounds(
                        expanded.minX - camX, expanded.minY - camY, expanded.minZ - camZ,
                        expanded.maxX - camX, expanded.maxY - camY, expanded.maxZ - camZ);

                RenderUtil.drawOutlinedBox(tessellator, box, 255, 255, 255, a);
            }
        } else if (disadvantage()) {
            EntityPlayer player = mc.thePlayer;
            UUID uuid = player.getUniqueID();
            double[] dims = originalDims.get(uuid);
            if (dims != null) {
                double mult = multiplierSetting.getValue();
                double origWidth = dims[0];
                double origHeight = dims[1];

                double halfX = (origWidth * 0.5) * mult;
                double halfY = (origHeight * 0.5) * mult;
                double halfZ = (origWidth * 0.5) * mult;

                double cx = player.posX;
                double cy = player.posY;
                double cz = player.posZ;

                AxisAlignedBB expanded = new AxisAlignedBB(
                        cx - halfX, cy - halfY, cz - halfZ,
                        cx + halfX, cy + halfY, cz + halfZ);

                AxisAlignedBB box = AxisAlignedBB.fromBounds(
                        expanded.minX - camX, expanded.minY - camY, expanded.minZ - camZ,
                        expanded.maxX - camX, expanded.maxY - camY, expanded.maxZ - camZ);

                RenderUtil.drawOutlinedBox(tessellator, box, 255, 255, 255, a);
            }
        }

        GlStateManager.enableTexture2D();
    }
}