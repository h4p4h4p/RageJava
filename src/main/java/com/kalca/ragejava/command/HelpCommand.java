package com.kalca.ragejava.command;

public class HelpCommand extends ChatCommand {

    private final CommandManager manager;

    public HelpCommand(CommandManager manager) {
        super("help", "?", "helpme");
        this.manager = manager;
    }

    @Override
    public String getUsage() {
        return "%help";
    }

    @Override
    public void execute(String[] args) {
        CommandManager.sendMessage("\u00a7eCommands:");
        for (ChatCommand command : manager.getCommands()) {
            if (command == this) continue;
            CommandManager.sendMessage("\u00a7a" + command.getUsage());
        }
    }
}