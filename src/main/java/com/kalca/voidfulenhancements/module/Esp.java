package com.kalca.voidfulenhancements.module;

import com.kalca.voidfulenhancements.settings.BooleanSetting;
import com.kalca.voidfulenhancements.settings.ColorSetting;
import com.kalca.voidfulenhancements.settings.ModeSetting;
import com.kalca.voidfulenhancements.settings.Setting;
import com.kalca.voidfulenhancements.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class Esp extends Module {

    public static final String MODE_2D = "2D";
    public static final String MODE_3D = "3D";

    private static final double NEAR_CAMERA_MARGIN = 1.2D;

    private final ModeSetting modeSetting = new ModeSetting("Mode", new String[]{MODE_2D, MODE_3D}, 0);
    private final BooleanSetting cornersSetting = new BooleanSetting("Corners", false);
    private final BooleanSetting namesSetting = new BooleanSetting("Names", true);
    private final ColorSetting colorSetting = new ColorSetting("Color", 0xFF1B395C);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<float[]> rects = new ArrayList<>();
    private final List<NameEntry> names = new ArrayList<>();

    private final float[] mv = new float[16];
    private final float[] pr = new float[16];
    private IntBuffer viewport;
    private FloatBuffer mvBuf;
    private FloatBuffer prBuf;
    private int vw;
    private int vh;
    private double scale;

    public Esp() {
        super("ESP", Category.RENDER);
        settings.add(modeSetting);
        settings.add(cornersSetting);
        settings.add(namesSetting);
        settings.add(colorSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean isSettingVisible(Setting setting) {
        if (setting == cornersSetting) return MODE_2D.equals(mode());
        return true;
    }

    private String mode() {
        return modeSetting.getValue();
    }

    public String getPreviewMode() {
        return mode();
    }

    public int getPreviewColor() {
        return colorSetting.getValue();
    }

    public boolean getPreviewCorners() {
        return cornersSetting.getValue();
    }

    public boolean getPreviewNames() {
        return namesSetting.getValue();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        rects.clear();
        names.clear();
        if (!captureMatrices()) return;
        if (MODE_3D.equals(mode())) {
            render3D(event.partialTicks);
        } else {
            collect2D();
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (!isEnabled()) return;
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (MODE_2D.equals(mode())) {
            for (float[] r : rects) {
                float x = r[0];
                float y = r[1];
                float w = r[2];
                float h = r[3];
                int color = colorSetting.getValue();
                float len = Math.max(4f, Math.min(h / 6f, 10f));
                if (cornersSetting.getValue()) {
                    RenderUtil.drawRect(x, y, len, 1, color);
                    RenderUtil.drawRect(x, y, 1, len, color);
                    RenderUtil.drawRect(x + w - len, y, len, 1, color);
                    RenderUtil.drawRect(x + w - 1, y, 1, len, color);
                    RenderUtil.drawRect(x, y + h - 1, len, 1, color);
                    RenderUtil.drawRect(x, y + h - len, 1, len, color);
                    RenderUtil.drawRect(x + w - len, y + h - 1, len, 1, color);
                    RenderUtil.drawRect(x + w - 1, y + h - len, 1, len, color);
                } else {
                    RenderUtil.drawRect(x, y, w, 1, color);
                    RenderUtil.drawRect(x, y + h - 1, w, 1, color);
                    RenderUtil.drawRect(x, y, 1, h, color);
                    RenderUtil.drawRect(x + w - 1, y, 1, h, color);
                }
            }
        }
        if (namesSetting.getValue()) {
            for (NameEntry entry : names) {
                RenderUtil.drawString(entry.text, entry.x - RenderUtil.getTextWidth(entry.text) / 2f, entry.y - 9f, 0xFFFFFFFF);
            }
        }
    }

    private boolean captureMatrices() {
        if (mvBuf == null) {
            viewport = BufferUtils.createIntBuffer(16);
            mvBuf = BufferUtils.createFloatBuffer(16);
            prBuf = BufferUtils.createFloatBuffer(16);
        }
        mvBuf.clear();
        prBuf.clear();
        viewport.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mvBuf);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, prBuf);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        mvBuf.get(mv);
        prBuf.get(pr);
        vw = viewport.get(2);
        vh = viewport.get(3);
        if (vw <= 0 || vh <= 0) return false;

        scale = vh / (double) new ScaledResolution(mc).getScaledHeight();
        return scale > 0;
    }

    private void addName(Entity entity, AxisAlignedBB bb, double camX, double camY, double camZ) {
        if (!namesSetting.getValue()) return;
        double midX = (bb.minX + bb.maxX) / 2.0D;
        double midZ = (bb.minZ + bb.maxZ) / 2.0D;
        double[] head = toScreen(midX - camX, bb.maxY - camY, midZ - camZ, mv, pr, vw, vh);
        if (head == null) return;
        names.add(new NameEntry(entity.getName(), (float) (head[0] / scale), (float) ((vh - head[1]) / scale)));
    }

    private void render3D(float partialTicks) {
        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;

        int color = colorSetting.getValue();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = 255;

        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        for (Entity entity : mc.theWorld.playerEntities) {
            if (entity == mc.thePlayer) continue;
            double posX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
            double posY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
            double posZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
            AxisAlignedBB bb = entity.getEntityBoundingBox();

            if (RenderUtil.pointNearBox(camX, camY, camZ, bb, NEAR_CAMERA_MARGIN)) continue;

            addName(entity, bb, camX, camY, camZ);

            double pad = 0.1D;
            AxisAlignedBB box = AxisAlignedBB.fromBounds(
                    bb.minX + posX - entity.posX - camX - pad,
                    bb.minY + posY - entity.posY - camY - pad,
                    bb.minZ + posZ - entity.posZ - camZ - pad,
                    bb.maxX + posX - entity.posX - camX + pad,
                    bb.maxY + posY - entity.posY - camY + pad,
                    bb.maxZ + posZ - entity.posZ - camZ + pad);
            RenderUtil.drawOutlinedBox(tessellator, box, r, g, b, a);
        }
        GlStateManager.enableTexture2D();
    }

    private void collect2D() {
        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;

        for (Entity entity : mc.theWorld.playerEntities) {
            if (entity == mc.thePlayer) continue;
            AxisAlignedBB bb = entity.getEntityBoundingBox();
            double midX = (bb.minX + bb.maxX) / 2.0D;
            double midZ = (bb.minZ + bb.maxZ) / 2.0D;

            double[] feet = toScreen(midX - camX, bb.minY - camY, midZ - camZ, mv, pr, vw, vh);
            double[] head = toScreen(midX - camX, bb.maxY - camY, midZ - camZ, mv, pr, vw, vh);
            if (feet == null || head == null) continue;
            addName(entity, bb, camX, camY, camZ);

            double topDownHead = vh - head[1];
            double topDownFeet = vh - feet[1];
            double h = Math.abs(topDownFeet - topDownHead);
            if (h < 2.0D) continue;
            double w = h * 0.5D;
            float x = (float) ((feet[0] - w / 2.0D) / scale);
            float y = (float) (topDownHead / scale);
            rects.add(new float[]{x, y, (float) (w / scale), (float) (h / scale)});
        }
    }

    private double[] toScreen(double x, double y, double z, float[] mv, float[] pr, int vw, int vh) {
        double inX = mv[0] * x + mv[4] * y + mv[8] * z + mv[12];
        double inY = mv[1] * x + mv[5] * y + mv[9] * z + mv[13];
        double inZ = mv[2] * x + mv[6] * y + mv[10] * z + mv[14];
        double inW = mv[3] * x + mv[7] * y + mv[11] * z + mv[15];

        double clipX = pr[0] * inX + pr[4] * inY + pr[8] * inZ + pr[12] * inW;
        double clipY = pr[1] * inX + pr[5] * inY + pr[9] * inZ + pr[13] * inW;
        double clipZ = pr[2] * inX + pr[6] * inY + pr[10] * inZ + pr[14] * inW;
        double clipW = pr[3] * inX + pr[7] * inY + pr[11] * inZ + pr[15] * inW;

        if (clipW <= 0.0D) return null;
        double ndcX = clipX / clipW;
        double ndcY = clipY / clipW;
        double ndcZ = clipZ / clipW;
        if (ndcX < -1.5D || ndcX > 1.5D) return null;
        if (ndcY < -1.5D || ndcY > 1.5D) return null;
        if (ndcZ < -1.0D || ndcZ > 1.0D) return null;

        return new double[]{(ndcX + 1.0D) * 0.5D * vw, (ndcY + 1.0D) * 0.5D * vh};
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        rects.clear();
        names.clear();
    }

    private static class NameEntry {
        final String text;
        final float x;
        final float y;

        NameEntry(String text, float x, float y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
}