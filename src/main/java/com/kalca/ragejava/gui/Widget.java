package com.kalca.ragejava.gui;

import com.kalca.ragejava.util.RenderUtil;

public abstract class Widget {

    protected static final int MARKER = 0xFF33D6FF;

    protected float x;
    protected float y;
    protected float width;
    protected float height = 14;

    public void setPosition(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float getHeight() {
        return height;
    }

    public boolean contains(float mx, float my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    /** Small accent square after the label showing the value differs from the shipped default. */
    protected void drawModifiedMarker(boolean modified, String name) {
        if (!modified) return;
        float mx = x + 8 + RenderUtil.getTextWidth(name);
        RenderUtil.drawRect(mx, y + 6, 3, 3, MARKER);
    }

    public abstract void draw(float mx, float my);

    public boolean onClick(float mx, float my, int button) {
        return false;
    }

    public void onDrag(float mx, float my) {
    }

    public float getPreferredWidth() {
        return 100;
    }
}