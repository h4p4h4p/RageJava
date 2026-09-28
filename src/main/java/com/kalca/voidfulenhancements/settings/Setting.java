package com.kalca.voidfulenhancements.settings;

public abstract class Setting {

    private final String name;
    private String defaultConfig = "";

    protected Setting(String name) {
        this.name = name;
    }

    /** Call at the end of the subclass constructor so right-click reset has something to restore. */
    protected void captureDefault() {
        defaultConfig = toConfigString();
    }

    public boolean isDefault() {
        return defaultConfig.equals(toConfigString());
    }

    public String getName() {
        return name;
    }

    public abstract String toConfigString();

    public abstract void fromConfigString(String value);
}
