package com.kalca.voidfulenhancements.settings;

public class ModeSetting extends Setting {

    private final String[] options;
    private int index;

    public ModeSetting(String name, String[] options, int defaultIndex) {
        super(name);
        this.options = options;
        this.index = Math.max(0, Math.min(options.length - 1, defaultIndex));
        captureDefault();
    }

    public String[] getOptions() {
        return options;
    }

    public int getIndex() {
        return index;
    }

    public String getValue() {
        return options[index];
    }

    public void setIndex(int index) {
        this.index = Math.max(0, Math.min(options.length - 1, index));
    }

    @Override
    public String toConfigString() {
        return Integer.toString(index);
    }

    @Override
    public void fromConfigString(String s) {
        try {
            setIndex(Integer.parseInt(s));
        } catch (NumberFormatException ignored) {
        }
    }
}