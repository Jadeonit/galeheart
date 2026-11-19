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
