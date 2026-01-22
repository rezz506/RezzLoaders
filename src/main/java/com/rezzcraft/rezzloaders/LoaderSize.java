package com.rezzcraft.rezzloaders;

public enum LoaderSize {
    ONE_BY_ONE(1, 0),
    FIVE_BY_FIVE(5, 2);

    public final int size;
    public final int radius;

    LoaderSize(int size, int radius) {
        this.size = size;
        this.radius = radius;
    }

    public static LoaderSize fromString(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase();
        // Accept both user-friendly labels ("1x1"/"5x5") and enum-style names
        // ("ONE_BY_ONE"/"FIVE_BY_FIVE") to remain compatible with older saved data.
        return switch (s) {
            case "1x1", "1", "one", "one_by_one" -> ONE_BY_ONE;
            case "5x5", "5", "five", "five_by_five" -> FIVE_BY_FIVE;
            default -> null;
        };
    }
}
