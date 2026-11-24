package me.dogs.dragoncannon;

import java.util.Locale;

public enum CannonMode {
    NORMAL("normal", "Rings"),
    STAB("stab", "Stab Shot");

    private final String configKey;
    private final String label;

    CannonMode(String configKey, String label) {
        this.configKey = configKey;
        this.label = label;
    }

    public String configKey() {
        return configKey;
    }

    public String label() {
        return label;
    }

    public static CannonMode fromString(String input) {
        if (input == null) {
            return null;
        }
        switch (input.toLowerCase(Locale.ROOT)) {
            case "normal":
            case "ring":
            case "rings":
                return NORMAL;
            case "stab":
            case "stabshot":
            case "stab_shot":
                return STAB;
            default:
                return null;
        }
    }
}
