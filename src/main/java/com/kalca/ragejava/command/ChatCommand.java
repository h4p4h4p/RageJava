package com.kalca.ragejava.command;

public abstract class ChatCommand {

    private final String name;
    private final String[] aliases;

    protected ChatCommand(String name, String... aliases) {
        this.name = name;
        this.aliases = aliases.length == 0 ? new String[]{name} : aliases;
    }

    public String getName() {
        return name;
    }

    public String[] getAliases() {
        return aliases;
    }

    public abstract String getUsage();

    public abstract void execute(String[] args);
}