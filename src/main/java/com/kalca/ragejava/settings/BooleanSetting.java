package com.kalca.ragejava.settings;

public class BooleanSetting extends Setting {

    private boolean value;

    public BooleanSetting(String name, boolean value) {
        super(name);
        this.value = value;
        captureDefault();
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    public void toggle() {
        value = !value;
    }

    @Override
    public String toConfigString() {
        return Boolean.toString(value);
    }

    @Override
    public void fromConfigString(String s) {
        value = Boolean.parseBoolean(s);
    }
}