package ru.galeheart.core.pass;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

/** Season pass: XP, shards, level rewards and the cosmetic catalogue. */
public final class PassManager {

    private final GaleHeartCore plugin;

    public PassManager(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("pass.enabled", true);
    }

    public int maxLevel() {
        return Math.max(1, plugin.getConfig().getInt("pass.levels", 100));
    }

    private int xpBase() {
        return Math.max(1, plugin.getConfig().getInt("pass.xp-base", 90));
    }

    private int xpStep() {
        return Math.max(0, plugin.getConfig().getInt("pass.xp-step", 6));
    }

    public int costForLevel(int level) {
        return xpBase() + xpStep() * (level - 1);
    }

    public int totalXpForLevel(int level) {
        int total = 0;
        for (int index = 1; index < level; index++) {
            total += costForLevel(index);
        }
        return total;
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
            data.dailyShards = 0;
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
        int cap = Math.max(0, plugin.getConfig().getInt("pass.shards.daily-cap", 60));
        int add = Math.min(delta / per, Math.max(0, cap - data.dailyShards));
        if (add > 0) {
            data.dailyShards += add;
            data.shards += add;
            data.lifetimeShards += add;
            plugin.store().write(data);
            plugin.achievements().check(player);
        }
    }

    private void onLevelUp(Player player, PlayerData data, int from, int to) {
        int perLevel = Math.max(0, plugin.getConfig().getInt("pass.shards.per-level", 4));
        for (int level = from + 1; level <= to; level++) {
            data.shards += perLevel;
            data.lifetimeShards += perLevel;
            player.sendMessage(Msg.parse(plugin.prefix() + "<gold>Pass level <white>" + level
                    + "<gold>! <gray>+" + perLevel + " shards"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            grantLevelReward(player, level);
            runRewards(player, level);
        }
        plugin.store().write(data);
        plugin.achievements().check(player);
    }

    private void grantLevelReward(Player player, int level) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("pass.level-rewards");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            int rewardLevel;
            try {
                rewardLevel = Integer.parseInt(key.trim());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (rewardLevel != level) {
                continue;
            }
            Cosmetic cosmetic = cosmetic(section.getString(key, ""));
            if (cosmetic == null) {
                continue;
            }
            PlayerData data = plugin.store().get(player.getUniqueId());
            if (data.owned.contains(cosmetic.id())) {
                continue;
            }
            data.owned.add(cosmetic.id());
            plugin.store().write(data);
            player.sendMessage(Msg.parse(plugin.prefix() + "<green>Unlocked <white>" + cosmetic.display()
                    + " <dark_gray>(" + cosmetic.type().label() + ") <gray>— see <white>/locker"));
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.2f);
        }
    }

    private void runRewards(Player player, int level) {
        List<String> commands = plugin.getConfig().getStringList("pass.rewards." + level);
        for (String raw : commands) {
            String command = raw.replace("%player%", player.getName());
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
    }

    /** Called once a minute: XP for playtime and the playtime achievement counter. */
    public void tickPlaytime() {
        if (!enabled()) {
            return;
        }
        int perHour = Math.max(0, plugin.getConfig().getInt("pass.playtime.xp-per-hour", 90));
        int perMinute = Math.max(0, perHour / 60);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.store().get(player.getUniqueId());
            data.playMinutes += 1;
            if (perMinute > 0) {
                addXp(player, "playtime", perMinute);
            }
            plugin.achievements().check(player);
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

    // ------------------------------------------------------------ catalogue

    public List<Cosmetic> cosmetics() {
        List<Cosmetic> list = new ArrayList<>();
        for (Object entry : plugin.getConfig().getMapList("pass.cosmetics")) {
            if (!(entry instanceof Map<?, ?> raw)) {
                continue;
            }
            Object id = raw.get("id");
            if (id == null) {
                continue;
            }
            Object display = raw.get("display");
            Object cost = raw.get("cost");
            Object unlock = raw.get("unlock-level");
            Cosmetic.Type type = Cosmetic.Type.parse(raw.get("type") == null ? null : String.valueOf(raw.get("type")));
            list.add(new Cosmetic(
                    type,
                    String.valueOf(id),
                    display == null ? String.valueOf(id) : String.valueOf(display),
                    cost instanceof Number number ? number.intValue() : 0,
                    unlock instanceof Number level ? level.intValue() : 1,
                    raw.get("prefix") == null ? "" : String.valueOf(raw.get("prefix")),
                    raw.get("particle") == null ? "" : String.valueOf(raw.get("particle")),
                    raw.get("sound") == null ? "" : String.valueOf(raw.get("sound")),
                    raw.get("icon") == null ? type.defaultIcon() : String.valueOf(raw.get("icon"))));
        }
        return list;
    }

    public List<Cosmetic> cosmetics(Cosmetic.Type type) {
        List<Cosmetic> list = new ArrayList<>();
        for (Cosmetic cosmetic : cosmetics()) {
            if (cosmetic.type() == type) {
                list.add(cosmetic);
            }
        }
        return list;
    }

    public Cosmetic cosmetic(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (Cosmetic cosmetic : cosmetics()) {
            if (cosmetic.id().equalsIgnoreCase(id)) {
                return cosmetic;
            }
        }
        return null;
    }

    public boolean owns(Player player, String id) {
        return plugin.store().get(player.getUniqueId()).owned.contains(id);
    }

    /** Buys a cosmetic with shards. Returns false and explains when it fails. */
    public boolean buy(Player player, String id) {
        Cosmetic cosmetic = cosmetic(id);
        if (cosmetic == null) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<gray>Unknown cosmetic. <white>/locker"));
            return false;
        }
        PlayerData data = plugin.store().get(player.getUniqueId());
        if (data.owned.contains(id)) {
            equip(player, id);
            return false;
        }
        int level = levelOf(data.passXp);
        if (level < cosmetic.unlockLevel()) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<red>Locked. <gray>Needs pass level <white>"
                    + cosmetic.unlockLevel() + "<gray>."));
            return false;
        }
        if (data.shards < cosmetic.cost()) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<red>Not enough shards. <gray>Need <white>"
                    + cosmetic.cost() + "<gray>, you have <white>" + data.shards + "<gray>."));
            return false;
        }
        data.shards -= cosmetic.cost();
        data.shardsSpent += cosmetic.cost();
        data.owned.add(id);
        plugin.store().write(data);
        player.sendMessage(Msg.parse(plugin.prefix() + "<green>Unlocked <white>" + cosmetic.display()
                + "<green>. <gray>-" + cosmetic.cost() + " shards"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        equip(player, id);
        plugin.achievements().check(player);
        return true;
    }

    /** Equips an owned cosmetic, replacing whatever was equipped in that slot. */
    public void equip(Player player, String id) {
        Cosmetic cosmetic = cosmetic(id);
        if (cosmetic == null) {
            return;
        }
        PlayerData data = plugin.store().get(player.getUniqueId());
        if (!data.owned.contains(id)) {
            return;
        }
        switch (cosmetic.type()) {
            case TITLE -> {
                data.title = id;
                applyPrefix(player, cosmetic.prefix());
            }
            case TRAIL -> data.trail = id;
            case JOIN -> data.joinEffect = id;
        }
        plugin.store().write(data);
        player.sendMessage(Msg.parse(plugin.prefix() + "<green>Equipped <white>" + cosmetic.display()));
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 1.3f);
    }

    /** Removes whatever is equipped in a category. */
    public void unequip(Player player, Cosmetic.Type type) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        switch (type) {
            case TITLE -> {
                data.title = "";
                removePrefix(player);
            }
            case TRAIL -> data.trail = "";
            case JOIN -> data.joinEffect = "";
        }
        plugin.store().write(data);
        player.sendMessage(Msg.parse(plugin.prefix() + "<gray>Removed " + type.label().toLowerCase(Locale.ROOT) + "."));
    }

    public String equipped(Player player, Cosmetic.Type type) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        return switch (type) {
            case TITLE -> data.title == null ? "" : data.title;
            case TRAIL -> data.trail == null ? "" : data.trail;
            case JOIN -> data.joinEffect == null ? "" : data.joinEffect;
        };
    }

    private void applyPrefix(Player player, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        String command = "lp user " + player.getName() + " meta setprefix 100 \"" + prefix + "\"";
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    private void removePrefix(Player player) {
        String command = "lp user " + player.getName() + " meta removeprefix 100";
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    /** Fires the equipped join effect once. */
    public void playJoinEffect(Player player) {
        Cosmetic cosmetic = cosmetic(plugin.store().get(player.getUniqueId()).joinEffect);
        if (cosmetic == null || cosmetic.particle().isBlank()) {
            return;
        }
        Particle particle = particle(cosmetic.particle());
        if (particle == null) {
            return;
        }
        player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1.0, 0), 40, 0.6, 0.9, 0.6, 0.05);
        Sound sound = sound(cosmetic.sound());
        if (sound != null) {
            player.getWorld().playSound(player.getLocation(), sound, 0.7f, 1.2f);
        }
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
            Cosmetic cosmetic = cosmetic(data.trail);
            if (cosmetic == null || cosmetic.particle().isBlank()) {
                continue;
            }
            Particle particle = particle(cosmetic.particle());
            if (particle == null) {
                continue;
            }
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 0.15, 0), 3, 0.25, 0.05, 0.25);
        }
    }

    public static Particle particle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    public static Sound sound(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
