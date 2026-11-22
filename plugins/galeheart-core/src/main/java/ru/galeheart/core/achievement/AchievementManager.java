package ru.galeheart.core.achievement;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.util.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tracks lifetime counters and unlocks achievements with shard rewards. */
public final class AchievementManager {

    private final GaleHeartCore plugin;

    public AchievementManager(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("achievements.enabled", true);
    }

    public List<Achievement> all() {
        List<Achievement> list = new ArrayList<>();
        for (Object entry : plugin.getConfig().getMapList("achievements.list")) {
            if (!(entry instanceof Map<?, ?> raw)) {
                continue;
            }
            Object id = raw.get("id");
            if (id == null) {
                continue;
            }
            Object display = raw.get("display");
            Object description = raw.get("description");
            Object icon = raw.get("icon");
            Object shards = raw.get("shards");
            Object amount = raw.get("amount");
            list.add(new Achievement(
                    String.valueOf(id),
                    display == null ? String.valueOf(id) : String.valueOf(display),
                    description == null ? "" : String.valueOf(description),
                    icon == null ? "PAPER" : String.valueOf(icon),
                    shards instanceof Number number ? number.intValue() : 25,
                    Achievement.parse(raw.get("trigger") == null ? null : String.valueOf(raw.get("trigger"))),
                    amount instanceof Number number ? Math.max(1, number.intValue()) : 1));
        }
        return list;
    }

    public Achievement find(String id) {
        for (Achievement achievement : all()) {
            if (achievement.id().equalsIgnoreCase(id)) {
                return achievement;
            }
        }
        return null;
    }

    /** Current progress for one player and one trigger. */
    public int progress(Player player, Achievement.Trigger trigger) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        return switch (trigger) {
            case KILLS -> data.kills;
            case DEATHS -> data.deaths;
            case BLOCKS -> data.blocksMined;
            case DIAMONDS -> data.diamondsMined;
            case MOBS -> data.mobsKilled;
            case MINUTES -> data.playMinutes;
            case HEARTS -> plugin.hearts().hearts(player);
            case BOUNTY -> data.bountyDone;
            case PASS_LEVEL -> plugin.pass().level(player);
            case SHARDS_EARNED -> data.lifetimeShards;
            case SHARDS_SPENT -> data.shardsSpent;
            case QUESTS -> data.questsDone.size();
        };
    }

    public boolean unlocked(Player player, String id) {
        return plugin.store().get(player.getUniqueId()).achievements.contains(id);
    }

    /** Re-checks every achievement for this player. Cheap enough at a few dozen. */
    public void check(Player player) {
        if (!enabled()) {
            return;
        }
        PlayerData data = plugin.store().get(player.getUniqueId());
        boolean changed = false;
        for (Achievement achievement : all()) {
            if (data.achievements.contains(achievement.id())) {
                continue;
            }
            if (progress(player, achievement.trigger()) < achievement.amount()) {
                continue;
            }
            data.achievements.add(achievement.id());
            data.shards += achievement.shards();
            data.lifetimeShards += achievement.shards();
            changed = true;
            player.sendMessage(Msg.parse(plugin.prefix() + "<gradient:#4FA9C9:#C8324B><bold>ACHIEVEMENT<reset> "
                    + achievement.display() + " <gray>— +" + achievement.shards() + " shards"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
        if (changed) {
            plugin.store().write(data);
        }
    }

    /** Bumps a counter and re-checks. */
    public void increment(Player player, Counter counter, int amount) {
        if (!enabled() || amount <= 0 || player == null) {
            return;
        }
        PlayerData data = plugin.store().get(player.getUniqueId());
        switch (counter) {
            case KILL -> data.kills += amount;
            case DEATH -> data.deaths += amount;
            case BLOCK -> data.blocksMined += amount;
            case DIAMOND -> data.diamondsMined += amount;
            case MOB -> data.mobsKilled += amount;
            case BOUNTY -> data.bountyDone += amount;
        }
        plugin.store().write(data);
        check(player);
    }

    public enum Counter {
        KILL, DEATH, BLOCK, DIAMOND, MOB, BOUNTY
    }

    public int unlockedCount(Player player) {
        int count = 0;
        for (Achievement achievement : all()) {
            if (unlocked(player, achievement.id())) {
                count++;
            }
        }
        return count;
    }
}
