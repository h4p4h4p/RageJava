package com.kalca.voidfulenhancements.gui;

import com.kalca.voidfulenhancements.VoidfulEnhancements;
import com.kalca.voidfulenhancements.settings.ModeSetting;
import com.kalca.voidfulenhancements.util.RenderUtil;

public class ModeWidget extends Widget {

    private final ModeSetting setting;

    public ModeWidget(ModeSetting setting) {
        this.setting = setting;
    }

    @Override
    public float getPreferredWidth() {
        return Math.max(120, RenderUtil.getTextWidth(setting.getName()) + 6 + RenderUtil.getTextWidth(setting.getValue()) + 16);
    }

    @Override
    public void draw(float mx, float my) {
        boolean hovered = contains(mx, my);
        RenderUtil.drawString(setting.getName(), x + 6, y + 4, Theme.TEXT_GRAY);
        drawModifiedMarker(!setting.isDefault(), setting.getName());

        String value = setting.getValue();
        float vw = RenderUtil.getTextWidth(value);
        RenderUtil.drawString(value, x + width - 12 - vw, y + 4, hovered ? 0xFF8AE9FF : Theme.ACCENT);
    }

    @Override
    public boolean onClick(float mx, float my, int button) {
        if (button == 1) {
            setting.resetToDefault();
        } else if (button == 0) {
            setting.setIndex((setting.getIndex() + 1) % setting.getOptions().length);
        } else if (button == 2) {
            int len = setting.getOptions().length;
            setting.setIndex((setting.getIndex() + len - 1) % len);
        } else {
            return false;
        }
        VoidfulEnhancements.scheduleSave();
        return true;
    }
}