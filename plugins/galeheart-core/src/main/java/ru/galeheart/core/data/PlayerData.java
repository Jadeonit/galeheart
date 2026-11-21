package ru.galeheart.core.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything we persist per player. */
public final class PlayerData {

    public final UUID id;
    public String name = "";

    public int hearts;
    public long firstJoin;
    public long lastDeath;
    public long lastKill;
    public long protectionUntil;

    // Season pass
    public int passXp;
    public int shards;
    public int dailyShards;
    public java.time.LocalDate passDay = java.time.LocalDate.now();
    public final Map<String, Integer> dailyXp = new HashMap<>();
    public final Map<UUID, Long> killCooldowns = new HashMap<>();

    // Cosmetic shop
    public final Set<String> owned = new HashSet<>();
    public String trail = "";

    // Bounty contract
    public UUID targetId;
    public String targetName = "";

    /** "questId:stepIndex" -> progress. */
    public final Map<String, Integer> questProgress = new HashMap<>();
    public final Set<String> questsDone = new HashSet<>();

    public PlayerData(UUID id) {
        this.id = id;
    }

    public boolean protectedNow() {
        return System.currentTimeMillis() < protectionUntil;
    }
}
