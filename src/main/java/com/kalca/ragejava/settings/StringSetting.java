package com.kalca.ragejava.settings;

public class StringSetting extends Setting {

    private String value;

    public StringSetting(String name, String value) {
        super(name);
        this.value = value;
        captureDefault();
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toConfigString() {
        return value == null ? "" : value;
    }

    @Override
    public void fromConfigString(String s) {
        value = s;
    }
}