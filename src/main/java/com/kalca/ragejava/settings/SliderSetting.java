package com.kalca.ragejava.settings;

public class SliderSetting extends Setting {

    private final double min;
    private final double max;
    private final double step;
    private double value;

    public SliderSetting(String name, double value, double min, double max, double step) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        setValue(value);
        captureDefault();
    }

    public double getValue() {
        return value;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public void setValue(double value) {
        this.value = Math.max(min, Math.min(max, value));
    }

    @Override
    public String toConfigString() {
        return Double.toString(value);
    }

    @Override
    public void fromConfigString(String s) {
        try {
            setValue(Double.parseDouble(s));
        } catch (NumberFormatException ignored) {
        }
    }
}