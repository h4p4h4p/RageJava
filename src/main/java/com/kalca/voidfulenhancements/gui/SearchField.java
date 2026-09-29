package com.kalca.voidfulenhancements.gui;

import com.kalca.voidfulenhancements.util.RenderUtil;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

/**
 * Floating filter for the module list. Typing hides every setting that does not match, and any
 * module left without a matching setting disappears too, so modules with a lot of settings stay
 * navigable without scrolling past every row.
 */
public class SearchField {

    private static final String PLACEHOLDER = "Search settings...";
    private static final int MAX_QUERY = 24;
    private static final float WIDTH = 150f;
    private static final float HEIGHT = 14f;

    private final StringBuilder query = new StringBuilder();
    private float x;
    private float y;
    private boolean focused;

    public void layout(int screenHeight) {
        x = 4;
        y = screenHeight - HEIGHT - 4;
    }

    public String query() {
        return query.toString();
    }

    private String lowerQuery() {
        return query.toString().toLowerCase(Locale.ROOT);
    }

    public boolean isFiltering() {
        return query.length() > 0;
    }

    public boolean isFocused() {
        return focused;
    }

    public void clear() {
        query.setLength(0);
    }

    public void unfocus() {
        focused = false;
    }

    public boolean contains(float mx, float my) {
        return mx >= x && mx <= x + WIDTH && my >= y && my <= y + HEIGHT;
    }

    public boolean matches(String settingName) {
        if (!isFiltering()) return true;
        return settingName.toLowerCase(Locale.ROOT).contains(lowerQuery());
    }

    public void draw(float mx, float my) {
        boolean hovered = contains(mx, my);
        int border = focused ? 0xFF33D6FF : Theme.PANEL_BORDER;
        RenderUtil.drawRect(x, y, WIDTH, HEIGHT, focused ? 0xF01A2126 : 0xE0141414);
        RenderUtil.drawRect(x, y, WIDTH, 1, border);
        RenderUtil.drawRect(x, y + HEIGHT - 1, WIDTH, 1, border);
        RenderUtil.drawRect(x, y, 1, HEIGHT, border);
        RenderUtil.drawRect(x + WIDTH - 1, y, 1, HEIGHT, border);

        String text = isFiltering() ? query.toString() : PLACEHOLDER;
        int color = isFiltering() ? Theme.TEXT : (hovered ? Theme.TEXT_DIM : Theme.TEXT_GRAY);

        float tx = x + 6;
        if (isFiltering() && (System.currentTimeMillis() / 450L) % 2 == 0) {
            tx += RenderUtil.getTextWidth(text);
            RenderUtil.drawRect(tx + 1, y + 3, 1, RenderUtil.getTextHeight(), 0xFF33D6FF);
        }
        RenderUtil.drawString(text, tx, y + 4, color);
    }

    public boolean onClick(float mx, float my, int button) {
        if (button != 0 || !contains(mx, my)) return false;
        focused = true;
        return true;
    }

    /** Returns true when the key was consumed by the field. */
    public boolean onKey(char typedChar, int keyCode) {
        if (!focused) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (isFiltering()) clear();
            else focused = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (query.length() > 0) query.setLength(query.length() - 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            focused = false;
            return true;
        }
        if (typedChar >= ' ' && query.length() < MAX_QUERY) {
            query.append(typedChar);
        }
        return true;
    }
}
