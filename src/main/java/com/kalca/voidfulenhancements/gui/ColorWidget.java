package com.kalca.voidfulenhancements.gui;

import com.kalca.voidfulenhancements.VoidfulEnhancements;
import com.kalca.voidfulenhancements.settings.ColorSetting;
import com.kalca.voidfulenhancements.util.RenderUtil;

public class ColorWidget extends Widget {

    private final ColorSetting setting;

    public ColorWidget(ColorSetting setting) {
        this.setting = setting;
    }

    @Override
    public void draw(float mx, float my) {
        RenderUtil.drawString(setting.getName(), x + 6, y + 4, Theme.TEXT_GRAY);
        drawModifiedMarker(!setting.isDefault(), setting.getName());

        float sw = 26;
        float sh = 8;
        float sx = x + width - sw - 6;
        float sy = y + height / 2f - sh / 2f;

        RenderUtil.drawRoundedRect(sx, sy, sw, sh, 2.5f, setting.getValue());
        String hex = String.format("%06X", setting.getValue() & 0xFFFFFF);
        RenderUtil.drawString(hex, sx - RenderUtil.getTextWidth(hex) - 4, y + 4, setting.getValue());
    }

    @Override
    public float getPreferredWidth() {
        return Math.max(140, RenderUtil.getTextWidth(setting.getName()) + 6 + 26 + 6 + 6);
    }

    @Override
    public boolean onClick(float mx, float my, int button) {
        if (button != 0 && button != 1) return false;
        setting.cycle(button == 0);
        VoidfulEnhancements.scheduleSave();
        return true;
    }
}