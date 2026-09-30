package com.kalca.ragejava.command;

import com.kalca.ragejava.RageJava;
import com.kalca.ragejava.gui.Theme;

public class ColorCommand extends ChatCommand {

    public ColorCommand() {
        super("color", "colour");
    }

    @Override
    public String getUsage() {
        return "%color <hex | rainbow>";
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            CommandManager.sendMessage("\u00a7cUsage: " + getUsage());
            return;
        }

        String input = args[0].toLowerCase();
        if (input.equals("rainbow")) {
            CommandManager.rainbow = true;
            CommandManager.sendMessage("\u00a7aRainbow mode enabled");
            RageJava.scheduleSave();
            return;
        }

        CommandManager.rainbow = false;
        String hex = args[0];
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) {
            CommandManager.sendMessage("\u00a7cInvalid hex color: " + args[0] + "\u00a7c (use RRGGBB)");
            return;
        }
        try {
            int rgb = Integer.parseInt(hex, 16);
            Theme.ACCENT = 0xFF000000 | rgb;
            CommandManager.sendMessage("\u00a7aAccent color set to #" + hex.toUpperCase());
            RageJava.scheduleSave();
        } catch (NumberFormatException e) {
            CommandManager.sendMessage("\u00a7cInvalid hex color: " + args[0] + "\u00a7c (use RRGGBB)");
        }
    }
}