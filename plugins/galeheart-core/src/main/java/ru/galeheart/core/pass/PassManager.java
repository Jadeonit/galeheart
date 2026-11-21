package ru.galeheart.core.pass;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.util.Msg;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Season pass: XP from play and events, cosmetic shards, level rewards. */
public final class PassManager {

    private final GaleHeartCore plugin;

    public PassManager(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("pass.enabled", true);
    }

    public int maxLevel() {
        return Math.max(1, plugin.getConfig().getInt("pass.levels", 30));
    }

    private int xpBase() {
        return Math.max(1, plugin.getConfig().getInt("pass.xp-base", 500));
    }

    private int xpStep() {
        return Math.max(0, plugin.getConfig().getInt("pass.xp-step", 20));
    }

    public int costForLevel(int level) {
        return xpBase() + xpStep() * (level - 1);
    }

    public int levelOf(int totalXp) {
        int level = 1;
        int remaining = Math.max(0, totalXp);
        while (level < maxLevel()) {
            int need = costForLevel(level);
            if (remaining < need) {
                break;
            }
            remaining -= need;
            level++;
        }
        return level;
    }

    public int xpIntoLevel(int totalXp) {
        int level = 1;
        int remaining = Math.max(0, totalXp);
        while (level < maxLevel()) {
            int need = costForLevel(level);
            if (remaining < need) {
                break;
            }
            remaining -= need;
            level++;
        }
        return remaining;
    }

    private void rollDay(PlayerData data) {
        LocalDate today = LocalDate.now();
        if (!today.equals(data.passDay)) {
            data.passDay = today;
            data.dailyXp.clear();
        }
    }

    private int capFor(String source) {
        ConfigurationSection caps = plugin.getConfig().getConfigurationSection("pass.daily-caps");
        if (caps == null) {
            return Integer.MAX_VALUE;
        }
        int cap = caps.getInt(source, -1);
        return cap <= 0 ? Integer.MAX_VALUE : cap;
    }

    /** Adds XP from a capped source. Returns the amount actually applied. */
    public int addXp(Player player, String source, int amount) {
        if (!enabled() || amount <= 0) {
            return 0;
        }
        PlayerData data = plugin.store().get(player.getUniqueId());
        rollDay(data);

        int used = data.dailyXp.getOrDefault(source, 0);
        int allowed = Math.min(amount, Math.max(0, capFor(source) - used));
        if (allowed <= 0) {
            return 0;
        }
        data.dailyXp.put(source, used + allowed);

        int before = levelOf(data.passXp);
        data.passXp += allowed;
        int after = levelOf(data.passXp);
        plugin.store().write(data);

        if (after > before) {
            onLevelUp(player, data, before, after);
        }
        return allowed;
    }

    /** Vanilla experience: 1 XP toward the pass, plus shards at a fixed rate. */
    public void addVanillaXp(Player player, int delta) {
        if (!enabled() || delta <= 0) {
            return;
        }
        addXp(player, "vanilla-xp", delta);

        PlayerData data = plugin.store().get(player.getUniqueId());
        int per = Math.max(1, plugin.getConfig().getInt("pass.shards.per-vanilla-xp", 20));
        int cap = Math.max(0, plugin.getConfig().getInt("pass.shards.daily-cap", 40));
        int earned = data.dailyShards;
        int add = Math.min(delta / per, Math.max(0, cap - earned));
        if (add > 0) {
            data.dailyShards += add;
            data.shards += add;
            plugin.store().write(data);
        }
    }

    private void onLevelUp(Player player, PlayerData data, int from, int to) {
        int perLevel = Math.max(0, plugin.getConfig().getInt("pass.shards.per-level", 20));
        for (int level = from + 1; level <= to; level++) {
            data.shards += perLevel;
            player.sendMessage(Msg.parse(plugin.prefix() + "<gold>Pass level <white>" + level
                    + "<gold>! <gray>+" + perLevel + " shards"));
            runRewards(player, level);
        }
        plugin.store().write(data);
    }

    private void runRewards(Player player, int level) {
        List<String> commands = plugin.getConfig().getStringList("pass.rewards." + level);
        for (String raw : commands) {
            String command = raw.replace("%player%", player.getName());
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
    }

    /** Called once a minute: 60 XP per hour of play. */
    public void tickPlaytime() {
        if (!enabled()) {
            return;
        }
        int perHour = Math.max(0, plugin.getConfig().getInt("pass.playtime.xp-per-hour", 60));
        int perMinute = Math.max(0, perHour / 60);
        if (perMinute <= 0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            addXp(player, "playtime", perMinute);
        }
    }

    /** Kills count once per victim per cooldown window. */
    public void onKill(Player killer, Player victim) {
        if (!enabled() || killer.equals(victim)) {
            return;
        }
        PlayerData data = plugin.store().get(killer.getUniqueId());
        long cooldown = plugin.getConfig().getLong("pass.kills.victim-cooldown-hours", 1) * 3_600_000L;
        long now = System.currentTimeMillis();
        Long previous = data.killCooldowns.get(victim.getUniqueId());
        if (previous != null && now - previous < cooldown) {
            return;
        }
        data.killCooldowns.put(victim.getUniqueId(), now);
        int xp = Math.max(0, plugin.getConfig().getInt("pass.kills.xp", 40));
        addXp(killer, "kills", xp);
    }

    public int level(Player player) {
        return levelOf(plugin.store().get(player.getUniqueId()).passXp);
    }

    public int shards(Player player) {
        return plugin.store().get(player.getUniqueId()).shards;
    }

    public int xp(Player player) {
        return plugin.store().get(player.getUniqueId()).passXp;
    }

    public UUID id(Player player) {
        return player.getUniqueId();
    }

    // ------------------------------------------------------------ cosmetics

    public record ShopItem(String id, String display, int cost, String prefix, String particle) {
    }

    public List<ShopItem> shopItems() {
        List<ShopItem> items = new ArrayList<>();
        for (Object entry : plugin.getConfig().getMapList("pass.shop")) {
            if (!(entry instanceof Map<?, ?> raw)) {
                continue;
            }
            Object id = raw.get("id");
            if (id == null) {
                continue;
            }
            Object cost = raw.get("cost");
            Object display = raw.get("display");
            items.add(new ShopItem(
                    String.valueOf(id),
                    display == null ? String.valueOf(id) : String.valueOf(display),
                    cost instanceof Number number ? number.intValue() : 100,
                    raw.get("prefix") == null ? "" : String.valueOf(raw.get("prefix")),
                    raw.get("particle") == null ? "" : String.valueOf(raw.get("particle"))));
        }
        return items;
    }

    public ShopItem shopItem(String id) {
        for (ShopItem item : shopItems()) {
            if (item.id().equalsIgnoreCase(id)) {
                return item;
            }
        }
        return null;
    }

    /** Spends shards on a cosmetic. Returns false and explains why when it fails. */
    public boolean buy(Player player, String id) {
        ShopItem item = shopItem(id);
        if (item == null) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<gray>Unknown item. <white>/pass shop"));
            return false;
        }
        PlayerData data = plugin.store().get(player.getUniqueId());
        if (data.owned.contains(item.id())) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<gray>You already own <white>" + item.display() + "<gray>."));
            return false;
        }
        if (data.shards < item.cost()) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<red>Not enough shards. <gray>Need <white>"
                    + item.cost() + "<gray>, you have <white>" + data.shards + "<gray>."));
            return false;
        }
        data.shards -= item.cost();
        data.owned.add(item.id());
        if (!item.prefix().isBlank()) {
            applyPrefix(player, item.prefix());
        }
        if (!item.particle().isBlank()) {
            data.trail = item.id();
        }
        plugin.store().write(data);
        player.sendMessage(Msg.parse(plugin.prefix() + "<green>Unlocked <white>" + item.display()
                + "<green>. <gray>-" + item.cost() + " shards"));
        return true;
    }

    private void applyPrefix(Player player, String prefix) {
        String command = "lp user " + player.getName() + " meta setprefix 100 \"" + prefix + "\"";
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    /** Turns the particle trail off without losing the purchase. */
    public void clearTrail(Player player) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        data.trail = "";
        plugin.store().write(data);
    }

    public String trail(Player player) {
        return plugin.store().get(player.getUniqueId()).trail;
    }

    /** Called twice a second: renders the equipped trail. */
    public void trailTick() {
        if (!enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.store().get(player.getUniqueId());
            if (data.trail == null || data.trail.isBlank() || !data.owned.contains(data.trail)) {
                continue;
            }
            ShopItem item = shopItem(data.trail);
            if (item == null || item.particle().isBlank()) {
                continue;
            }
            Particle particle;
            try {
                particle = Particle.valueOf(item.particle().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknownParticle) {
                continue;
            }
            player.getWorld().spawnParticle(particle,
                    player.getLocation().add(0, 0.15, 0), 3, 0.25, 0.05, 0.25);
        }
    }
}
