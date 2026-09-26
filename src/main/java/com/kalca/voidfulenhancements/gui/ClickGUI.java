package com.kalca.voidfulenhancements.gui;

import com.kalca.voidfulenhancements.VoidfulEnhancements;
import com.kalca.voidfulenhancements.module.Category;
import com.kalca.voidfulenhancements.module.Interface;
import com.kalca.voidfulenhancements.module.Module;
import com.kalca.voidfulenhancements.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClickGUI extends GuiScreen {

    private static final Logger LOGGER = LogManager.getLogger("VoidfulEnhancements");
    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<Panel> panels = new ArrayList<>();

    public ClickGUI() {
        for (Category category : Category.values()) {
            panels.add(new Panel(category, this));
        }
    }

    public VoidfulEnhancements getClient() {
        return VoidfulEnhancements.INSTANCE;
    }

    @Override
    public void initGui() {
        float x = 6;
        for (Panel panel : panels) {
            panel.setPosition(x, 6);
            LOGGER.info("Panel {} at ({}, {}) size {}x{}", panel.getCategory().getName(), panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight());
            x += panel.getWidth() + 6;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        ScaledResolution sr = new ScaledResolution(mc);
        RenderUtil.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), getDimColor());

        reflow();
        for (Panel panel : panels) {
            panel.draw(mouseX, mouseY);
        }
    }

    private final List<Float> dragOffsets = new ArrayList<>();

    private void reflow() {
        float nx = 6;
        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            float natural = nx;
            while (dragOffsets.size() <= i) dragOffsets.add(0f);
            if (!panel.isDragging()) {
                dragOffsets.set(i, panel.getX() - natural);
                panel.setPosition(natural + dragOffsets.get(i), panel.getY());
            }
            nx = natural + panel.getWidth() + 6f;
        }
    }

    private int getDimColor() {
        int alpha = 153;
        if (VoidfulEnhancements.INSTANCE != null) {
            Module iface = VoidfulEnhancements.INSTANCE.moduleManager.getModule("Interface");
            if (iface instanceof Interface) {
                alpha = ((Interface) iface).getBgDimAlpha();
            }
        }
        return (alpha << 24) | 0x000000;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (Panel panel : panels) {
            if (panel.isInHeader(mouseX, mouseY)) {
                if (mouseButton == 0) {
                    if (mouseX >= panel.getX() + panel.getWidth() - 12 && mouseX <= panel.getX() + panel.getWidth()) {
                        panel.toggleCollapsed();
                    } else {
                        panel.startHeaderDrag(mouseX, mouseY);
                    }
                }
                return;
            }
        }

        boolean consumed = false;
        for (Panel panel : panels) {
            if (!panel.isCollapsed() && panel.contains(mouseX, mouseY)) {
                consumed = panel.onClick(mouseX, mouseY, mouseButton);
                break;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for (Panel panel : panels) {
            panel.stopHeaderDrag();
        }
        getClient().saveConfig();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        BindWidget capturing = null;
        for (Panel panel : panels) {
            capturing = panel.getCapturingBind();
            if (capturing != null) break;
        }

        if (capturing != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                capturing.cancelCapture();
            } else if (keyCode == Keyboard.KEY_DELETE) {
                capturing.setBind(-1);
            } else {
                capturing.setBind(keyCode);
            }
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            return;
        }

        if (VoidfulEnhancements.INSTANCE != null) {
            if (VoidfulEnhancements.INSTANCE.clickGuiKey.getKeyCode() == keyCode) {
                close();
                return;
            }
            for (Module module : getClient().moduleManager.getModules()) {
                if (module.getKey() == keyCode && module.getKey() != -1) {
                    module.toggle();
                    return;
                }
            }
        }

        super.keyTyped(typedChar, keyCode);
    }

    public void close() {
        mc.displayGuiScreen(null);
        getClient().saveConfig();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}