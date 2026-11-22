package ru.galeheart.core.pass;

/**
 * One entry of the cosmetic catalogue. Same record is used by the season pass
 * menu, the locker and the level rewards.
 */
public record Cosmetic(Type type, String id, String display, int cost, int unlockLevel,
                       String prefix, String particle, String sound, String icon) {

    public enum Type {
        TITLE("Title", "NAME_TAG"),
        TRAIL("Trail", "BLAZE_POWDER"),
        JOIN("Join effect", "FIREWORK_ROCKET");

        private final String label;
        private final String defaultIcon;

        Type(String label, String defaultIcon) {
            this.label = label;
            this.defaultIcon = defaultIcon;
        }

        public String label() {
            return label;
        }

        public String defaultIcon() {
            return defaultIcon;
        }

        public static Type parse(String raw) {
            if (raw == null) {
                return TITLE;
            }
            try {
                return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return TITLE;
            }
        }
    }
}
