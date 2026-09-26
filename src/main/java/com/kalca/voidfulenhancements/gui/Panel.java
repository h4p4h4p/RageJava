package com.kalca.voidfulenhancements.gui;

import com.kalca.voidfulenhancements.module.Category;
import com.kalca.voidfulenhancements.module.Module;
import com.kalca.voidfulenhancements.util.RenderUtil;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class Panel {

    private static final float HEADER_HEIGHT = 16f;
    private static final float WIDTH = 122f;

    private final Category category;
    private final ClickGUI gui;
    private final List<ModuleButton> buttons = new ArrayList<>();

    private float x;
    private float y;
    private boolean collapsed;

    private boolean draggingHeader;
    private float dragOffsetX;
    private float dragOffsetY;

    public Panel(Category category, ClickGUI gui) {
        this.category = category;
        this.gui = gui;
        for (Module module : gui.getClient().moduleManager.getModulesInCategory(category)) {
            buttons.add(new ModuleButton(module, this));
        }
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Category getCategory() {
        return category;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        float w = WIDTH;
        for (ModuleButton button : buttons) {
            float bw = RenderUtil.getTextWidth(button.getModule().getName());
            float bindW = button.getBindWidget() == null ? 0 : RenderUtil.getTextWidth(button.getBindWidget().keyName());
            bw += bindW + 36;
            for (Widget widget : button.getWidgets()) {
                bw = Math.max(bw, widget.getPreferredWidth() + 8);
            }
            if (button.isExpanded() && button.hasSidePreview()) {
                bw += ModuleButton.PREVIEW_WIDTH + 16;
            }
            w = Math.max(w, bw);
        }
        return w;
    }

    public float getHeight() {
        float h = HEADER_HEIGHT;
        if (!collapsed) {
            for (ModuleButton button : buttons) h += button.getHeight();
        }
        return h;
    }

    public boolean isInHeader(float mx, float my) {
        return mx >= x && mx <= x + getWidth() && my >= y && my <= y + HEADER_HEIGHT;
    }

    public boolean contains(float mx, float my) {
        return mx >= x && mx <= x + getWidth() && my >= y && my <= y + getHeight();
    }

    public void startHeaderDrag(float mx, float my) {
        draggingHeader = true;
        dragOffsetX = mx - x;
        dragOffsetY = my - y;
    }

    public void updateHeaderDrag(float mx, float my) {
        if (!draggingHeader) return;
        if (!Mouse.isButtonDown(0)) {
            draggingHeader = false;
            return;
        }
        x = mx - dragOffsetX;
        y = my - dragOffsetY;
    }

    public void stopHeaderDrag() {
        draggingHeader = false;
    }

    public boolean isDragging() {
        return draggingHeader;
    }

    public void toggleCollapsed() {
        collapsed = !collapsed;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public BindWidget getCapturingBind() {
        for (ModuleButton button : buttons) {
            BindWidget bindWidget = button.getBindWidget();
            if (bindWidget != null && bindWidget.isCapturing()) return bindWidget;
        }
        return null;
    }

    public void draw(float mx, float my) {
        updateHeaderDrag(mx, my);

        float height = getHeight();
        float width = getWidth();
        RenderUtil.drawRect(x - 1, y - 1, width + 2, 1, Theme.PANEL_BORDER);
        RenderUtil.drawRect(x - 1, y + height, width + 2, 1, Theme.PANEL_BORDER);
        RenderUtil.drawRect(x - 1, y - 1, 1, height + 2, Theme.PANEL_BORDER);
        RenderUtil.drawRect(x + width, y - 1, 1, height + 2, Theme.PANEL_BORDER);
        RenderUtil.drawRect(x, y, width, height, Theme.PANEL_BG);
        RenderUtil.drawRect(x, y, width, HEADER_HEIGHT, Theme.HEADER_BG);

        RenderUtil.drawString(category.getName().toUpperCase(), x + 7, y + (HEADER_HEIGHT - RenderUtil.getTextHeight()) / 2f, Theme.TEXT);
        RenderUtil.drawString(collapsed ? "+" : "-", x + width - 12, y + (HEADER_HEIGHT - RenderUtil.getTextHeight()) / 2f, Theme.TEXT_GRAY);

        RenderUtil.drawRect(x + 6, y + HEADER_HEIGHT - 1, width - 12, 1, 0xAA00CFFF);

        if (collapsed) return;

        float by = y + HEADER_HEIGHT;
        for (ModuleButton button : buttons) {
            button.setPosition(x, by, width);
            button.draw(mx, my);
            by += button.getHeight();
        }
    }

    public boolean onClick(float mx, float my, int button) {
        if (my <= y + HEADER_HEIGHT) return false;

        float by = y + HEADER_HEIGHT;
        for (ModuleButton moduleButton : buttons) {
            float bh = moduleButton.getHeight();
            if (my >= by && my <= by + bh) {
                return moduleButton.onClick(mx, my, button);
            }
            by += bh;
        }
        return false;
    }

    public ClickGUI getGui() {
        return gui;
    }
}