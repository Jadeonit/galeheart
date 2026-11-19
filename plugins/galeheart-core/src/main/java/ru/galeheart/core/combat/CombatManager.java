package ru.galeheart.core.combat;

import org.bukkit.entity.Player;
import ru.galeheart.core.GaleHeartCore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** New-player protection and combat tagging. */
public final class CombatManager {

    public static final String BYPASS = "galeheart.bypass.protection";

    private final GaleHeartCore plugin;
    private final Map<UUID, Long> tagged = new HashMap<>();

    public CombatManager(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    public boolean isProtected(Player player) {
        if (player.hasPermission(BYPASS)) {
            return false;
        }
        return plugin.store().get(player.getUniqueId()).protectedNow();
    }

    public long protectionLeftMillis(Player player) {
        long until = plugin.store().get(player.getUniqueId()).protectionUntil;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    /** Grants protection starting now (used on first join). */
    public void grantNewPlayerProtection(Player player) {
        long hours = plugin.getConfig().getLong("protection.new-player-hours", 24L);
        plugin.store().get(player.getUniqueId()).protectionUntil = System.currentTimeMillis() + hours * 3_600_000L;
    }

    public void tag(Player player) {
        tagged.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean isTagged(Player player) {
        Long since = tagged.get(player.getUniqueId());
        if (since == null) {
            return false;
        }
        long window = plugin.getConfig().getLong("protection.combat-tag-seconds", 15L) * 1000L;
        return System.currentTimeMillis() - since < window;
    }

    public void clear(Player player) {
        tagged.remove(player.getUniqueId());
    }

    public void clearAll() {
        tagged.clear();
    }
}
