package com.kalca.voidfulenhancements.gui;

import com.kalca.voidfulenhancements.VoidfulEnhancements;
import com.kalca.voidfulenhancements.settings.BooleanSetting;
import com.kalca.voidfulenhancements.util.RenderUtil;

public class BooleanWidget extends Widget {

    private final BooleanSetting setting;

    public BooleanWidget(BooleanSetting setting) {
        this.setting = setting;
    }

    @Override
    public void draw(float mx, float my) {
        RenderUtil.drawString(setting.getName(), x + 6, y + 4, Theme.TEXT_GRAY);
        drawModifiedMarker(!setting.isDefault(), setting.getName());

        float sw = 18;
        float sh = 7;
        float sx = x + width - sw - 6;
        float sy = y + height / 2f - sh / 2f;

        boolean enabled = setting.getValue();
        RenderUtil.drawRoundedRect(sx, sy, sw, sh, 2.5f, enabled ? Theme.ACCENT : 0xFF333333);

        float dot = 5;
        float dx = sx + (enabled ? sw - dot - 1 : 1);
        RenderUtil.drawRoundedRect(dx, sy + 1, dot, sh - 2, 2f, Theme.KNOB);
    }

    @Override
    public float getPreferredWidth() {
        return Math.max(110, RenderUtil.getTextWidth(setting.getName()) + 6 + 18 + 6 + 6);
    }

    @Override
    public boolean onClick(float mx, float my, int button) {
        if (button == 1) {
            setting.resetToDefault();
        } else if (button == 0) {
            setting.toggle();
        } else {
            return false;
        }
        VoidfulEnhancements.scheduleSave();
        return true;
    }
}