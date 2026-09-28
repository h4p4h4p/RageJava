package com.kalca.voidfulenhancements.settings;

public class ColorSetting extends Setting {

    private int value;

    public ColorSetting(String name, int value) {
        super(name);
        this.value = value;
        captureDefault();
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public void cycle(boolean forward) {
        int[] palette = Palette.DEFAULT;
        for (int i = 0; i < palette.length; i++) {
            if ((palette[i] & 0xFFFFFF) == (value & 0xFFFFFF)) {
                int next = forward
                        ? (i + 1) % palette.length
                        : (i + palette.length - 1) % palette.length;
                value = 0xFF000000 | palette[next];
                return;
            }
        }
        value = 0xFF000000 | palette[forward ? 0 : palette.length - 1];
    }

    @Override
    public String toConfigString() {
        return String.format("%06X", value & 0xFFFFFF);
    }

    @Override
    public void fromConfigString(String s) {
        try {
            value = 0xFF000000 | (Integer.parseInt(s, 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
        }
    }

    public static final class Palette {
        public static final int[] DEFAULT = new int[]{
                0x1B395C, 0xFF4D4D, 0xFFB84D, 0x4DFF4D, 0x4DC9FF,
                0x4D5AFF, 0xFF4DFF, 0xFFFFFF, 0x8A8A8A, 0x000000
        };
    }
}