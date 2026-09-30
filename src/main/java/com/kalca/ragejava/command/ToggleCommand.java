package com.kalca.ragejava.command;

import com.kalca.ragejava.RageJava;
import com.kalca.ragejava.module.Module;

public class ToggleCommand extends ChatCommand {

    public ToggleCommand() {
        super("toggle", "t");
    }

    @Override
    public String getUsage() {
        return "%toggle <module>";
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            CommandManager.sendMessage("\u00a7cUsage: " + getUsage());
            return;
        }
        if (RageJava.INSTANCE == null) return;
        Module module = RageJava.INSTANCE.moduleManager.getModule(args[0]);
        if (module == null) {
            CommandManager.sendMessage("\u00a7cModule '\u00a7f" + args[0] + "\u00a7c' not found. Type \u00a7f%help");
            return;
        }
        module.toggle();
        CommandManager.sendMessage("\u00a7a" + module.getName() + "\u00a77 toggled \u00a7a" + (module.isEnabled() ? "on" : "off"));
    }
}