package com.kalca.ragejava.settings;

public class ModeSetting extends Setting {

    /** Nothing picked yet. The widget shows {@link #UNSET_LABEL} and getValue() returns null. */
    public static final int UNSET = -1;
    public static final String UNSET_LABEL = "...";

    private final String[] options;
    private int index;

    public ModeSetting(String name, String[] options, int defaultIndex) {
        super(name);
        this.options = options;
        this.index = normalise(defaultIndex, options.length);
        captureDefault();
    }

    public String[] getOptions() {
        return options;
    }

    public int getIndex() {
        return index;
    }

    public boolean isUnset() {
        return index < 0;
    }

    /** The selected mode, or null when nothing has been picked. */
    public String getValue() {
        return isUnset() ? null : options[index];
    }

    /** What the widget puts on screen, so callers never have to special-case the unset state. */
    public String getDisplayValue() {
        return isUnset() ? UNSET_LABEL : options[index];
    }

    public void setIndex(int index) {
        this.index = normalise(index, options.length);
    }

    private static int normalise(int index, int length) {
        if (index < 0) return UNSET;
        return Math.min(index, length - 1);
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
