package com.kalca.ragejava.gui;

import com.kalca.ragejava.RageJava;
import com.kalca.ragejava.settings.ModeSetting;
import com.kalca.ragejava.util.RenderUtil;

/**
 * Mode picker as a dropdown. The open list is drawn from {@link #drawOpenPopup} rather than from
 * {@link #draw}, because a widget's own draw happens while the panel is still laying out the rows
 * below it - the list would be painted over by the next setting. ClickGUI runs the popup pass once
 * every panel has drawn, which puts the list on top of everything.
 */
public class ModeWidget extends Widget {

    private static final float ROW = 12f;
    private static final float CARET_W = 4f;
    private static final float CARET_H = 3f;
    private static final float FIELD_PAD = 4f;

    private static ModeWidget open;

    private final ModeSetting setting;

    public ModeWidget(ModeSetting setting) {
        this.setting = setting;
    }

    /** Only one list open at a time, across every panel. */
    public static void closeOpen() {
        open = null;
    }

    public static boolean isOpen() {
        return open != null;
    }

    /**
     * Consumes clicks while a list is open: picks the option under the cursor, or just dismisses.
     * Returning true either way stops the click reaching the module row underneath and toggling it.
     */
    public static boolean handleOpenPopupClick(float mx, float my, int button) {
        if (open == null) return false;
        return open.listClick(mx, my, button);
    }

    /** Drawn after all panels so the list sits above the other settings. */
    public static void drawOpenPopup(float mx, float my, float bottomLimit) {
        if (open == null) return;
        open.drawList(mx, my, bottomLimit);
    }

    @Override
    public void draw(float mx, float my) {
        boolean hovered = contains(mx, my) || (open == this && insideList(mx, my));
        RenderUtil.drawString(setting.getName(), x + 6, y + 4, hovered ? Theme.TEXT : Theme.TEXT_GRAY);
        drawModifiedMarker(!setting.isDefault(), setting.getName());

        float fieldX = x + width - 6 - fieldW();
        RenderUtil.drawRect(fieldX, y + 1, fieldW(), height - 2, open == this ? 0x40219609 : 0x26000000);

        String value = setting.getDisplayValue();
        int valueColor = setting.isUnset() ? Theme.TEXT_GRAY : (hovered ? Theme.ACCENT : Theme.TEXT);
        RenderUtil.drawString(value, fieldX + FIELD_PAD, y + 4, valueColor);

        float caretX = fieldX + fieldW() - FIELD_PAD - CARET_W;
        float caretY = y + height / 2f - CARET_H / 2f;
        int caretColor = hovered ? Theme.ACCENT : Theme.TEXT_GRAY;
        if (open == this) {
            RenderUtil.drawRect(caretX, caretY + 2, CARET_W, 1, caretColor);
        } else {
            RenderUtil.drawRect(caretX, caretY, CARET_W, 1, caretColor);
            RenderUtil.drawRect(caretX + 1, caretY + 1, CARET_W - 2, 1, caretColor);
        }
    }

    private float fieldW() {
        return Math.max(28f, RenderUtil.getTextWidth(setting.getDisplayValue()) + 10 + CARET_W + FIELD_PAD);
    }

    private float listX() {
        return x + width - 6 - listW();
    }

    private float listW() {
        float widest = RenderUtil.getTextWidth(ModeSetting.UNSET_LABEL);
        for (String option : setting.getOptions()) {
            widest = Math.max(widest, RenderUtil.getTextWidth(option));
        }
        return Math.max(fieldW(), widest + 12);
    }

    private float listH() {
        // One row per option. The unset row is not offered - picking a mode is the point of the list.
        return setting.getOptions().length * ROW;
    }

    private boolean insideList(float mx, float my) {
        return mx >= listX() && mx <= listX() + listW() && my >= listY() && my <= listY() + listH();
    }

    /** Opens upward when the list would run off the bottom of the screen. */
    private float listY() {
        float below = y + height;
        return below + listH() <= bottomLimit ? below : y - listH();
    }

    private float bottomLimit = Float.MAX_VALUE;

    private void drawList(float mx, float my, float bottomLimit) {
        this.bottomLimit = bottomLimit;

        float lx = listX();
        float ly = listY();
        String[] options = setting.getOptions();

        RenderUtil.drawRect(lx - 1, ly - 1, listW() + 2, listH() + 2, 0xFF07070A);
        RenderUtil.drawRect(lx, ly, listW(), listH(), 0xFF1B1D22);

        for (int i = 0; i < options.length; i++) {
            float rowY = ly + i * ROW;
            boolean selected = setting.getIndex() == i;
            boolean hovered = mx >= lx && mx <= lx + listW() && my >= rowY && my <= rowY + ROW;
            if (hovered) RenderUtil.drawRect(lx + 1, rowY + 1, listW() - 2, ROW - 2, Theme.HOVER);
            int color = selected ? Theme.ACCENT : (hovered ? Theme.TEXT : Theme.TEXT_GRAY);
            RenderUtil.drawString(options[i], lx + 5, rowY + 3, color);
            if (selected) {
                RenderUtil.drawRect(lx + listW() - 4, rowY + ROW / 2f - 1, 2, 2, Theme.ACCENT);
            }
        }
    }

    private boolean listClick(float mx, float my, int button) {
        if (insideList(mx, my)) {
            int i = (int) ((my - listY()) / ROW);
            if (i >= 0 && i < setting.getOptions().length) {
                setting.setIndex(i);
                RageJava.scheduleSave();
            }
            open = null;
            return true;
        }
        open = null;
        return true;
    }

    @Override
    public boolean onClick(float mx, float my, int button) {
        if (button != 0 && button != 1) return false;
        if (open == this) {
            open = null;
            return true;
        }
        open = this;
        return true;
    }

    @Override
    public float getPreferredWidth() {
        float marker = setting.isDefault() ? 0 : 7;
        float widest = RenderUtil.getTextWidth(ModeSetting.UNSET_LABEL);
        for (String option : setting.getOptions()) {
            widest = Math.max(widest, RenderUtil.getTextWidth(option));
        }
        // Wide enough for the label, the widest option once opened, and the caret.
        return Math.max(120, RenderUtil.getTextWidth(setting.getName()) + marker + 14 + widest + 10 + CARET_W);
    }
}
