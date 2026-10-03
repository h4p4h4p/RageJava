package com.kalca.ragejava.gui;

import com.kalca.ragejava.RageJava;
import com.kalca.ragejava.settings.StringSetting;
import com.kalca.ragejava.util.RenderUtil;
import net.minecraft.client.Minecraft;

public class StringWidget extends Widget implements TextInput {

    private final StringSetting setting;
    private final Minecraft mc = Minecraft.getMinecraft();
    private String buffer = "";
    private boolean capturing = false;
    private int cursorPos = 0;
    private int blinkTimer = 0;
    private int maxLength = 64;

    public StringWidget(StringSetting setting) {
        this.setting = setting;
        this.buffer = setting.getValue() != null ? setting.getValue() : "";
        this.cursorPos = buffer.length();
    }

    @Override
    public void draw(float mx, float my) {
        RenderUtil.drawString(setting.getName(), x + 6, y + 4, Theme.TEXT_GRAY);
        drawModifiedMarker(!setting.isDefault(), setting.getName());

        float fieldX = x + width - 100;
        float fieldY = y + 2;
        float fieldW = 94;
        float fieldH = height - 4;

        RenderUtil.drawRoundedRect(fieldX, fieldY, fieldW, fieldH, 2f, 0xFF1A1A1A);
        RenderUtil.drawRoundedRect(fieldX, fieldY, fieldW, fieldH, 2f, capturing ? Theme.ACCENT : 0xFF333333);

        String display = buffer;
        float textX = fieldX + 4;
        float textY = fieldY + fieldH / 2f - RenderUtil.getTextHeight() / 2f;
        RenderUtil.drawString(display, textX, textY, Theme.TEXT);

        if (capturing) {
            blinkTimer++;
            if (blinkTimer % 40 < 20) {
                String beforeCursor = buffer.substring(0, Math.min(cursorPos, buffer.length()));
                float cursorX = textX + RenderUtil.getTextWidth(beforeCursor);
                RenderUtil.drawRect(cursorX, textY, 1, RenderUtil.getTextHeight(), Theme.TEXT);
            }
        }
    }

    @Override
    public float getPreferredWidth() {
        return Math.max(110, RenderUtil.getTextWidth(setting.getName()) + 6 + 100 + 6);
    }

    @Override
    public boolean onClick(float mx, float my, int button) {
        float fieldX = x + width - 100;
        float fieldY = y + 2;
        float fieldW = 94;
        float fieldH = height - 4;

        boolean hit = mx >= fieldX && mx <= fieldX + fieldW && my >= fieldY && my <= fieldY + fieldH;
        if (hit && button == 0) {
            capturing = true;
            mc.displayGuiScreen(null);
            return true;
        }
        if (!hit && capturing) {
            commitTextInput();
        }
        return false;
    }

    @Override
    public boolean isTextCapturing() {
        return capturing;
    }

    @Override
    public boolean hitsTextField(float mx, float my) {
        float fieldX = x + width - 100;
        float fieldY = y + 2;
        float fieldW = 94;
        float fieldH = height - 4;
        return mx >= fieldX && mx <= fieldX + fieldW && my >= fieldY && my <= fieldY + fieldH;
    }

    @Override
    public boolean onTextKey(char typedChar, int keyCode) {
        if (!capturing) return false;

        if (keyCode == 14) { // backspace
            if (cursorPos > 0) {
                buffer = buffer.substring(0, cursorPos - 1) + buffer.substring(cursorPos);
                cursorPos--;
            }
            return true;
        }
        if (keyCode == 211) { // delete
            if (cursorPos < buffer.length()) {
                buffer = buffer.substring(0, cursorPos) + buffer.substring(cursorPos + 1);
            }
            return true;
        }
        if (keyCode == 203) { // left arrow
            cursorPos = Math.max(0, cursorPos - 1);
            return true;
        }
        if (keyCode == 205) { // right arrow
            cursorPos = Math.min(buffer.length(), cursorPos + 1);
            return true;
        }
        if (keyCode == 28 || keyCode == 156) { // enter
            commitTextInput();
            return true;
        }
        if (keyCode == 1) { // escape
            cancelTextInput();
            return true;
        }

        if (typedChar >= ' ' && typedChar <= '~' && buffer.length() < maxLength) {
            buffer = buffer.substring(0, cursorPos) + typedChar + buffer.substring(cursorPos);
            cursorPos++;
            return true;
        }
        return false;
    }

    @Override
    public void commitTextInput() {
        if (capturing) {
            setting.setValue(buffer);
            RageJava.scheduleSave();
            capturing = false;
        }
    }

    @Override
    public void cancelTextInput() {
        buffer = setting.getValue() != null ? setting.getValue() : "";
        cursorPos = buffer.length();
        capturing = false;
    }
}