package com.kalca.ragejava.gui;

import com.kalca.ragejava.RageJava;
import com.kalca.ragejava.settings.ColorSetting;
import com.kalca.ragejava.util.RenderUtil;
import org.lwjgl.input.Keyboard;

public class ColorWidget extends Widget implements TextInput {

    private static final int MAX_CHARS = 6;
    private static final float SWATCH = 7f;
    private static final float GAP = 4f;

    private final ColorSetting setting;

    private boolean capturing;
    private final StringBuilder buffer = new StringBuilder();

    public ColorWidget(ColorSetting setting) {
        this.setting = setting;
    }

    @Override
    public void draw(float mx, float my) {
        boolean hovered = contains(mx, my);
        RenderUtil.drawString(setting.getName(), x + 6, y + 4, hovered ? Theme.TEXT : Theme.TEXT_GRAY);
        drawModifiedMarker(!setting.isDefault(), setting.getName());

        int shown = currentColor();
        String hex = text();

        float textW = RenderUtil.getTextWidth(hex);
        float textX = x + width - 6 - textW;
        float swatchX = textX - GAP - SWATCH;
        float swatchY = y + height / 2f - SWATCH / 2f;

        if (capturing) {
            RenderUtil.drawRect(textX - 3, y + 1, textW + 6, height - 2, 0x40219609);
        }

        RenderUtil.drawRect(swatchX, swatchY, SWATCH, SWATCH, shown);

        drawLegible(hex, textX, y + 4, shown);

        if (capturing && (System.currentTimeMillis() / 450L) % 2 == 0) {
            RenderUtil.drawRect(textX + textW + 1, y + 3, 1, RenderUtil.getTextHeight(), MARKER);
        }
    }

    /**
     * The text is drawn in the colour it represents, as asked. Near-black and near-white are
     * unreadable against the panel, so only those extremes get a contrasting outline - mid
     * range colours render exactly as entered with nothing behind them.
     */
    private void drawLegible(String hex, float tx, float ty, int color) {
        float lum = luminance(color);
        if (lum > 0.12F && lum < 0.78F) {
            RenderUtil.drawString(hex, tx, ty, color);
            return;
        }
        int outline = lum < 0.5F ? 0xFFFFFFFF : 0xFF101014;
        RenderUtil.drawString(hex, tx - 1, ty, outline);
        RenderUtil.drawString(hex, tx + 1, ty, outline);
        RenderUtil.drawString(hex, tx, ty - 1, outline);
        RenderUtil.drawString(hex, tx, ty + 1, outline);
        RenderUtil.drawString(hex, tx, ty, color);
    }

    private static float luminance(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        return 0.2126F * r + 0.7152F * g + 0.0722F * b;
    }

    /** While editing, preview the partial code so the text and swatch follow what you type. */
    private int currentColor() {
        if (!capturing) return setting.getValue();
        return parse(buffer.toString(), setting.getValue());
    }

    private String text() {
        if (!capturing) return String.format("%06X", setting.getValue() & 0xFFFFFF);
        if (buffer.length() == 0) return "......";
        StringBuilder shown = new StringBuilder(buffer.toString().toUpperCase());
        while (shown.length() < MAX_CHARS) shown.append('.');
        return shown.toString();
    }

    private static int parse(String s, int fallback) {
        if (s == null || s.isEmpty()) return fallback;
        StringBuilder hex = new StringBuilder(s);
        while (hex.length() < MAX_CHARS) hex.append('0');
        try {
            return 0xFF000000 | (Integer.parseInt(hex.substring(0, MAX_CHARS), 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public float getPreferredWidth() {
        float marker = setting.isDefault() ? 0 : 7;
        return Math.max(120, RenderUtil.getTextWidth(setting.getName()) + marker + 10
                + SWATCH + GAP + RenderUtil.getTextWidth("FFFFFF") + 12);
    }

    private float textX() {
        return x + width - 6 - RenderUtil.getTextWidth(text());
    }

    private float swatchX() {
        return textX() - GAP - SWATCH;
    }

    @Override
    public boolean onClick(float mx, float my, int button) {
        if (button != 0 && button != 1) return false;
        if (hitsTextField(mx, my)) {
            capturing = true;
            buffer.setLength(0);
            return true;
        }
        float sx = swatchX();
        float sy = y + height / 2f - SWATCH / 2f;
        if (mx < sx - 2 || mx > sx + SWATCH + 2 || my < sy - 2 || my > sy + SWATCH + 2) return false;
        setting.cycle(button == 0);
        RageJava.scheduleSave();
        return true;
    }

    @Override
    public boolean isTextCapturing() {
        return capturing;
    }

    @Override
    public boolean hitsTextField(float mx, float my) {
        float tx = textX();
        return mx >= tx - 3 && mx <= x + width - 3 && my >= y && my <= y + height;
    }

    @Override
    public boolean onTextKey(char typedChar, int keyCode) {
        if (!capturing) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelTextInput();
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            commitTextInput();
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (buffer.length() > 0) buffer.setLength(buffer.length() - 1);
            return true;
        }
        if (isHexDigit(typedChar) && buffer.length() < MAX_CHARS) {
            buffer.append(Character.toUpperCase(typedChar));
        }
        return true;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    @Override
    public void commitTextInput() {
        if (!capturing) return;
        capturing = false;
        if (buffer.length() > 0) {
            setting.setValue(parse(buffer.toString(), setting.getValue()));
        }
        buffer.setLength(0);
        RageJava.scheduleSave();
    }

    @Override
    public void cancelTextInput() {
        capturing = false;
        buffer.setLength(0);
    }
}
