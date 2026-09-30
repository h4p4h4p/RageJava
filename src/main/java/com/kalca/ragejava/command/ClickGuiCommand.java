package com.kalca.ragejava.command;

import com.kalca.ragejava.RageJava;
import com.kalca.ragejava.gui.ClickGUI;
import net.minecraft.client.Minecraft;

public class ClickGuiCommand extends ChatCommand {

    public ClickGuiCommand() {
        super("clickgui", "gui", "config");
    }

    @Override
    public String getUsage() {
        return "%clickgui";
    }

    @Override
    public void execute(String[] args) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.currentScreen instanceof ClickGUI) return;
            if (RageJava.INSTANCE == null || RageJava.INSTANCE.clickGui == null) return;
            mc.displayGuiScreen(RageJava.INSTANCE.clickGui);
        });
    }
}