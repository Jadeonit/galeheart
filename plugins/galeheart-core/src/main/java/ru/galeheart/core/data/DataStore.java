package ru.galeheart.core.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.galeheart.core.GaleHeartCore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Flat YAML storage: one section per player. */
public final class DataStore {

    private final GaleHeartCore plugin;
    private final File file;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    private YamlConfiguration data = new YamlConfiguration();

    public DataStore(GaleHeartCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (file.exists()) {
            data = YamlConfiguration.loadConfiguration(file);
        }
        cache.clear();
        for (String key : data.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                cache.put(id, read(id));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping malformed player id in data.yml: " + key);
            }
        }
    }

    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, this::read);
    }

    private PlayerData read(UUID id) {
        PlayerData player = new PlayerData(id);
        player.hearts = plugin.getConfig().getInt("hearts.start", 3);

        ConfigurationSection section = data.getConfigurationSection(id.toString());
        if (section == null) {
            return player;
        }
        player.name = section.getString("name", "");
        player.hearts = section.getInt("hearts", player.hearts);
        player.firstJoin = section.getLong("first-join", 0L);
        player.lastDeath = section.getLong("last-death", 0L);
        player.lastKill = section.getLong("last-kill", 0L);
        player.protectionUntil = section.getLong("protection-until", 0L);
        String target = section.getString("bounty-target", "");
        if (!target.isBlank()) {
            try {
                player.targetId = UUID.fromString(target);
            } catch (IllegalArgumentException ignored) {
                player.targetId = null;
            }
        }
        player.targetName = section.getString("bounty-target-name", "");
        player.passXp = section.getInt("pass-xp", 0);
        player.shards = section.getInt("shards", 0);
        player.dailyShards = section.getInt("daily-shards", 0);
        String day = section.getString("pass-day", "");
        if (!day.isBlank()) {
            try {
                player.passDay = java.time.LocalDate.parse(day);
            } catch (RuntimeException ignored) {
                // keep today
            }
        }
        ConfigurationSection daily = section.getConfigurationSection("daily-xp");
        if (daily != null) {
            for (String key : daily.getKeys(false)) {
                player.dailyXp.put(key, daily.getInt(key));
            }
        }
        for (String item : section.getStringList("owned")) {
            if (!item.isBlank()) {
                player.owned.add(item);
            }
        }
        player.trail = section.getString("trail", "");
        player.title = section.getString("title", "");
        player.joinEffect = section.getString("join-effect", "");
        player.kills = section.getInt("kills", 0);
        player.deaths = section.getInt("deaths", 0);
        player.blocksMined = section.getInt("blocks-mined", 0);
        player.diamondsMined = section.getInt("diamonds-mined", 0);
        player.mobsKilled = section.getInt("mobs-killed", 0);
        player.playMinutes = section.getInt("play-minutes", 0);
        player.bountyDone = section.getInt("bounty-done", 0);
        player.lifetimeShards = section.getInt("lifetime-shards", 0);
        player.shardsSpent = section.getInt("shards-spent", 0);
        for (String achievement : section.getStringList("achievements")) {
            if (!achievement.isBlank()) {
                player.achievements.add(achievement);
            }
        }

        ConfigurationSection quests = section.getConfigurationSection("quests");
        if (quests != null) {
            for (String key : quests.getKeys(false)) {
                String value = quests.getString(key);
                if (value == null) {
                    continue;
                }
                player.questsDone.add(key);
            }
        }
        ConfigurationSection progress = section.getConfigurationSection("progress");
        if (progress != null) {
            for (String key : progress.getKeys(false)) {
                player.questProgress.put(key, progress.getInt(key));
            }
        }
        return player;
    }

    /** Persists one player into the in-memory config (no disk write). */
    public void write(PlayerData player) {
        String path = player.id.toString();
        data.set(path + ".name", player.name);
        data.set(path + ".hearts", player.hearts);
        data.set(path + ".first-join", player.firstJoin);
        data.set(path + ".last-death", player.lastDeath);
        data.set(path + ".last-kill", player.lastKill);
        data.set(path + ".protection-until", player.protectionUntil);
        data.set(path + ".bounty-target", player.targetId == null ? null : player.targetId.toString());
        data.set(path + ".bounty-target-name", player.targetName);
        data.set(path + ".pass-xp", player.passXp);
        data.set(path + ".shards", player.shards);
        data.set(path + ".daily-shards", player.dailyShards);
        data.set(path + ".pass-day", player.passDay.toString());
        data.set(path + ".daily-xp", null);
        for (Map.Entry<String, Integer> entry : player.dailyXp.entrySet()) {
            data.set(path + ".daily-xp." + entry.getKey(), entry.getValue());
        }
        data.set(path + ".owned", new java.util.ArrayList<>(player.owned));
        data.set(path + ".trail", player.trail);
        data.set(path + ".title", player.title);
        data.set(path + ".join-effect", player.joinEffect);
        data.set(path + ".kills", player.kills);
        data.set(path + ".deaths", player.deaths);
        data.set(path + ".blocks-mined", player.blocksMined);
        data.set(path + ".diamonds-mined", player.diamondsMined);
        data.set(path + ".mobs-killed", player.mobsKilled);
        data.set(path + ".play-minutes", player.playMinutes);
        data.set(path + ".bounty-done", player.bountyDone);
        data.set(path + ".lifetime-shards", player.lifetimeShards);
        data.set(path + ".shards-spent", player.shardsSpent);
        data.set(path + ".achievements", new java.util.ArrayList<>(player.achievements));
        data.set(path + ".progress", null);
        for (Map.Entry<String, Integer> entry : player.questProgress.entrySet()) {
            data.set(path + ".progress." + entry.getKey(), entry.getValue());
        }
        data.set(path + ".quests", null);
        for (String quest : player.questsDone) {
            data.set(path + ".quests." + quest, "done");
        }
    }

    public void save(PlayerData player) {
        write(player);
        flush();
    }

    public void saveAll() {
        for (PlayerData player : cache.values()) {
            write(player);
        }
        flush();
    }

    private void flush() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save data.yml: " + exception.getMessage());
        }
    }

    /** Season reset: wipe every player's hearts and progress. */
    public void resetAll() {
        data = new YamlConfiguration();
        for (PlayerData player : cache.values()) {
            player.hearts = plugin.getConfig().getInt("hearts.start", 3);
            player.lastDeath = 0L;
            player.lastKill = 0L;
            player.protectionUntil = 0L;
            player.questProgress.clear();
            player.questsDone.clear();
            write(player);
        }
        flush();
    }

    public List<PlayerData> top() {
        List<PlayerData> all = new ArrayList<>(cache.values());
        all.sort(Comparator.comparingInt((PlayerData p) -> p.hearts).reversed()
                .thenComparing(p -> p.name == null ? "" : p.name));
        return all;
    }

    public Map<UUID, PlayerData> cache() {
        return cache;
    }
}
