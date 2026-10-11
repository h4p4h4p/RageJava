package com.kalca.ragejava.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

public class RenderUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean pointNearBox(double px, double py, double pz, AxisAlignedBB box, double margin) {
        double dx = Math.max(Math.max(box.minX - px, 0.0D), Math.max(px - box.maxX, 0.0D));
        double dy = Math.max(Math.max(box.minY - py, 0.0D), Math.max(py - box.maxY, 0.0D));
        double dz = Math.max(Math.max(box.minZ - pz, 0.0D), Math.max(pz - box.maxZ, 0.0D));
        return dx * dx + dy * dy + dz * dz < margin * margin;
    }

    public static void drawOutlinedBox(Tessellator tessellator, AxisAlignedBB box, int r, int g, int b, int a) {
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(3, DefaultVertexFormats.POSITION_COLOR);
        vertex(wr, box.minX, box.minY, box.minZ, r, g, b, a);
        vertex(wr, box.maxX, box.minY, box.minZ, r, g, b, a);
        vertex(wr, box.maxX, box.minY, box.maxZ, r, g, b, a);
        vertex(wr, box.minX, box.minY, box.maxZ, r, g, b, a);
        tessellator.draw();
        wr.begin(3, DefaultVertexFormats.POSITION_COLOR);
        vertex(wr, box.minX, box.maxY, box.minZ, r, g, b, a);
        vertex(wr, box.maxX, box.maxY, box.minZ, r, g, b, a);
        vertex(wr, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        vertex(wr, box.minX, box.maxY, box.maxZ, r, g, b, a);
        tessellator.draw();
        wr.begin(1, DefaultVertexFormats.POSITION_COLOR);
        vertex(wr, box.minX, box.minY, box.minZ, r, g, b, a);
        vertex(wr, box.minX, box.maxY, box.minZ, r, g, b, a);
        vertex(wr, box.maxX, box.minY, box.minZ, r, g, b, a);
        vertex(wr, box.maxX, box.maxY, box.minZ, r, g, b, a);
        vertex(wr, box.maxX, box.minY, box.maxZ, r, g, b, a);
        vertex(wr, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        vertex(wr, box.minX, box.minY, box.maxZ, r, g, b, a);
        vertex(wr, box.minX, box.maxY, box.maxZ, r, g, b, a);
        tessellator.draw();
    }

    public static void drawFilledBox(Tessellator tessellator, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int r, int g, int b, int a) {
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        
        // Bottom (minY) - CCW from outside (viewed from below)
        vertex(wr, minX, minY, maxZ, r, g, b, a);
        vertex(wr, maxX, minY, maxZ, r, g, b, a);
        vertex(wr, maxX, minY, minZ, r, g, b, a);
        vertex(wr, minX, minY, minZ, r, g, b, a);
        
        // Top (maxY) - CCW from outside (above)
        vertex(wr, minX, maxY, minZ, r, g, b, a);
        vertex(wr, maxX, maxY, minZ, r, g, b, a);
        vertex(wr, maxX, maxY, maxZ, r, g, b, a);
        vertex(wr, minX, maxY, maxZ, r, g, b, a);
        
        // Front (maxZ) - CCW from outside (front)
        vertex(wr, minX, minY, maxZ, r, g, b, a);
        vertex(wr, maxX, minY, maxZ, r, g, b, a);
        vertex(wr, maxX, maxY, maxZ, r, g, b, a);
        vertex(wr, minX, maxY, maxZ, r, g, b, a);
        
        // Back (minZ) - CCW from outside (back)
        vertex(wr, maxX, minY, minZ, r, g, b, a);
        vertex(wr, minX, minY, minZ, r, g, b, a);
        vertex(wr, minX, maxY, minZ, r, g, b, a);
        vertex(wr, maxX, maxY, minZ, r, g, b, a);
        
        // Left (minX) - CCW from outside (left)
        vertex(wr, minX, minY, minZ, r, g, b, a);
        vertex(wr, minX, minY, maxZ, r, g, b, a);
        vertex(wr, minX, maxY, maxZ, r, g, b, a);
        vertex(wr, minX, maxY, minZ, r, g, b, a);
        
        // Right (maxX) - CCW from outside (right)
        vertex(wr, maxX, minY, maxZ, r, g, b, a);
        vertex(wr, maxX, minY, minZ, r, g, b, a);
        vertex(wr, maxX, maxY, minZ, r, g, b, a);
        vertex(wr, maxX, maxY, maxZ, r, g, b, a);
        
        tessellator.draw();
    }

    private static void vertex(WorldRenderer wr, double x, double y, double z, int r, int g, int b, int a) {
        wr.pos(x, y, z).color(r, g, b, a).endVertex();
    }

    public static void drawRect(float x, float y, float w, float h, int color) {
        if (w == 0 || h == 0) return;
        Gui.drawRect((int) x, (int) y, (int) (x + w), (int) (y + h), color);
    }

    public static void drawBatchedRects(float[] quads, int count, int color) {
        if (count == 0) return;
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < count; i++) {
            int o = i << 2;
            int x1 = (int) quads[o];
            int y1 = (int) quads[o + 1];
            int x2 = (int) (quads[o] + quads[o + 2]);
            int y2 = (int) (quads[o + 1] + quads[o + 3]);
            if (x1 == x2 || y1 == y2) continue;
            wr.pos(x1, y1, 0.0D).color(r, g, b, a).endVertex();
            wr.pos(x1, y2, 0.0D).color(r, g, b, a).endVertex();
            wr.pos(x2, y2, 0.0D).color(r, g, b, a).endVertex();
            wr.pos(x2, y1, 0.0D).color(r, g, b, a).endVertex();
        }
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /**
     * Filled ring of the given thickness, built from quads rather than a GL line loop so the
     * stroke width is honoured on drivers that clamp glLineWidth to 1px.
     */
    public static void drawCircleOutline(float cx, float cy, float radius, float thickness, int color, int segments) {
        if (radius <= 0.0F || segments < 3) return;
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        float half = Math.max(thickness, 0.5F) * 0.5F;
        float inner = Math.max(radius - half, 0.0F);
        float outer = radius + half;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        double step = Math.PI * 2.0 / segments;
        for (int i = 0; i < segments; i++) {
            double a0 = i * step;
            double a1 = (i + 1) * step;
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            wr.pos((float) (cx + c0 * inner), (float) (cy + s0 * inner), 0.0D).color(r, g, b, a).endVertex();
            wr.pos((float) (cx + c1 * inner), (float) (cy + s1 * inner), 0.0D).color(r, g, b, a).endVertex();
            wr.pos((float) (cx + c1 * outer), (float) (cy + s1 * outer), 0.0D).color(r, g, b, a).endVertex();
            wr.pos((float) (cx + c0 * outer), (float) (cy + s0 * outer), 0.0D).color(r, g, b, a).endVertex();
        }
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void drawRoundedRect(float x, float y, float w, float h, float r, int color) {
        drawRect(x, y, w, h, color);
    }

    public static void drawString(String text, float x, float y, int color) {
        FontRenderer fr = mc.fontRendererObj;
        fr.drawStringWithShadow(text, (int) x, (int) y, color);
    }

    public static int getTextWidth(String text) {
        return mc.fontRendererObj.getStringWidth(text);
    }

    public static int getTextHeight() {
        return mc.fontRendererObj.FONT_HEIGHT;
    }
}