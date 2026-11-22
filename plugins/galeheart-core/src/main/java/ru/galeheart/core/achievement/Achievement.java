package ru.galeheart.core.achievement;

/** One achievement definition, loaded from config. */
public record Achievement(String id, String display, String description, String icon,
                          int shards, Trigger trigger, int amount) {

    public enum Trigger {
        KILLS,
        DEATHS,
        BLOCKS,
        DIAMONDS,
        MOBS,
        MINUTES,
        HEARTS,
        BOUNTY,
        PASS_LEVEL,
        SHARDS_EARNED,
        SHARDS_SPENT,
        QUESTS
    }

    public static Trigger parse(String raw) {
        if (raw == null) {
            return Trigger.KILLS;
        }
        try {
            return Trigger.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Trigger.KILLS;
        }
    }
}
