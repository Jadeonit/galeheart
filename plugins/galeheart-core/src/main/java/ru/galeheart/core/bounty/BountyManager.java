package ru.galeheart.core.bounty;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.util.Msg;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Personal bounty contracts. Every player has one random target; killing it
 * pays hearts and rerolls the contract. A scheduled window doubles the payout.
 */
public final class BountyManager {

    private final GaleHeartCore plugin;
    private final Random random = new Random();
    private final Set<DayOfWeek> chosenDays = new HashSet<>();

    private int chosenWeek = -1;
    private LocalDate warnedDay;
    private LocalDate lastRun;

    private boolean active;
    private long endsAt;
    private BossBar bar;

    public BountyManager(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    public boolean active() {
        return active;
    }

    // ------------------------------------------------------------ contracts

    /** Assigns a target if the player has none, or theirs is offline. */
    public void ensureTarget(Player player) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        if (data.targetId != null) {
            if (!data.targetId.equals(player.getUniqueId()) && Bukkit.getPlayer(data.targetId) != null) {
                return;
            }
        }
        assign(player);
    }

    private void assign(Player player) {
        List<Player> pool = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                pool.add(online);
            }
        }
        PlayerData data = plugin.store().get(player.getUniqueId());
        if (pool.isEmpty()) {
            data.targetId = null;
            data.targetName = "";
            plugin.store().write(data);
            return;
        }
        Player target = pool.get(random.nextInt(pool.size()));
        data.targetId = target.getUniqueId();
        data.targetName = target.getName();
        plugin.store().write(data);
    }

    public String targetName(Player player) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        if (data.targetId == null) {
            assign(player);
            data = plugin.store().get(player.getUniqueId());
        }
        if (data.targetId == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(data.targetId);
        if (online != null) {
            return online.getName();
        }
        return data.targetName == null || data.targetName.isBlank() ? null : data.targetName;
    }

    /** Called from PlayerDeathEvent. Pays out when the killer's contract is the victim. */
    public void handleDeath(Player victim, Player killer) {
        if (killer == null || killer.equals(victim)) {
            return;
        }
        PlayerData data = plugin.store().get(killer.getUniqueId());
        if (data.targetId == null || !data.targetId.equals(victim.getUniqueId())) {
            return;
        }
        int base = Math.max(0, plugin.getConfig().getInt("bounty.reward-hearts", 1));
        int multiplier = active ? Math.max(1, plugin.getConfig().getInt("bounty.window-multiplier", 2)) : 1;
        int reward = base * multiplier;
        if (reward > 0) {
            plugin.hearts().add(killer, reward);
        }
        Bukkit.broadcast(Msg.parse(plugin.prefix() + "<gold>" + killer.getName() + "<gray> completed the contract on <white>"
                + victim.getName() + "<gray>. <red>+" + reward + " heart"));
        assign(killer);
        String next = targetName(killer);
        if (next != null) {
            killer.sendMessage(Msg.parse(plugin.prefix() + "<gray>New contract: <white>" + next));
        }
    }

    /** Rerolls contracts pointing at a player who just left. */
    public void onQuit(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) {
                continue;
            }
            PlayerData data = plugin.store().get(online.getUniqueId());
            if (player.getUniqueId().equals(data.targetId)) {
                assign(online);
                String next = targetName(online);
                if (next != null) {
                    online.sendMessage(Msg.parse(plugin.prefix() + "<gray>Contract rerolled: <white>" + next));
                }
            }
        }
    }

    // ------------------------------------------------------------ window

    public void tick() {
        if (!plugin.getConfig().getBoolean("bounty.enabled", true)) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            ensureTarget(online);
        }
        if (active) {
            if (System.currentTimeMillis() >= endsAt) {
                finish();
                return;
            }
            updateBar();
            return;
        }
        if (shouldStartNow()) {
            start();
            return;
        }
        if (shouldWarnNow()) {
            Bukkit.broadcast(Msg.parse(plugin.prefix()
                    + "<red><bold>BOUNTY<red> in 10 minutes. <gray>Contracts pay double."));
            plugin.notifier().send("Bounty soon", "Contracts pay double in 10 minutes.", 0xE67E22);
        }
    }

    private boolean shouldStartNow() {
        LocalDate today = LocalDate.now();
        if (lastRun != null && lastRun.equals(today)) {
            return false;
        }
        LocalTime time = parseTime(plugin.getConfig().getString("bounty.time", "19:00"));
        if (LocalTime.now().isBefore(time)) {
            return false;
        }
        int window = Math.max(1, plugin.getConfig().getInt("bounty.window-minutes", 90));
        if (LocalTime.now().isAfter(time.plusMinutes(window))) {
            lastRun = today;
            return false;
        }
        return isBountyDay(today);
    }

    private boolean shouldWarnNow() {
        LocalDate today = LocalDate.now();
        if (warnedDay != null && warnedDay.equals(today)) {
            return false;
        }
        LocalTime time = parseTime(plugin.getConfig().getString("bounty.time", "19:00"));
        LocalTime now = LocalTime.now();
        if (now.isBefore(time.minusMinutes(10)) || now.isAfter(time)) {
            return false;
        }
        if (!isBountyDay(today)) {
            return false;
        }
        warnedDay = today;
        return true;
    }

    private static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw.trim());
        } catch (RuntimeException exception) {
            return LocalTime.of(19, 0);
        }
    }

    private boolean isBountyDay(LocalDate today) {
        DayOfWeek day = today.getDayOfWeek();
        if (plugin.getConfig().getBoolean("bounty.random-days", true)) {
            int week = today.get(WeekFields.ISO.weekOfWeekBasedYear());
            if (week != chosenWeek) {
                chosenWeek = week;
                chosenDays.clear();
                List<DayOfWeek> pool = new ArrayList<>(List.of(DayOfWeek.values()));
                int perWeek = Math.max(1, Math.min(7, plugin.getConfig().getInt("bounty.days-per-week", 3)));
                for (int i = 0; i < perWeek && !pool.isEmpty(); i++) {
                    chosenDays.add(pool.remove(random.nextInt(pool.size())));
                }
                plugin.getLogger().info("Bounty days this week: " + chosenDays);
            }
            return chosenDays.contains(day);
        }
        List<String> days = plugin.getConfig().getStringList("bounty.days");
        if (days.isEmpty()) {
            return true;
        }
        for (String raw : days) {
            try {
                if (DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT)) == day) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // skip malformed day
            }
        }
        return false;
    }

    public boolean start() {
        if (active) {
            return false;
        }
        long minutes = plugin.getConfig().getLong("bounty.duration-minutes", 20);
        endsAt = System.currentTimeMillis() + minutes * 60_000L;
        active = true;
        lastRun = LocalDate.now();
        int multiplier = Math.max(1, plugin.getConfig().getInt("bounty.window-multiplier", 2));

        bar = BossBar.bossBar(Msg.parse("<red><bold>BOUNTY<red> — contracts pay x" + multiplier), 1.0f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bar.addViewer(player);
        }

        Bukkit.broadcast(Msg.parse(plugin.prefix() + "<red><bold>BOUNTY WINDOW<red> for <white>" + minutes
                + " min<red>. <gray>Contracts pay <white>x" + multiplier + "<gray>."));
        plugin.notifier().send("Bounty window",
                "Contracts pay x" + multiplier + " for " + minutes + " minutes.", 0xE74C3C);
        return true;
    }

    private void updateBar() {
        if (bar == null) {
            return;
        }
        long left = Math.max(0, endsAt - System.currentTimeMillis());
        long minutes = left / 60_000L;
        long seconds = (left % 60_000L) / 1000L;
        bar.name(Msg.parse("<red><bold>BOUNTY<red> — " + minutes + ":" + String.format("%02d", seconds) + " left"));
        long total = Math.max(1, plugin.getConfig().getLong("bounty.duration-minutes", 20) * 60_000L);
        bar.progress((float) left / total);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bar.addViewer(player);
        }
    }

    private void removeBar() {
        if (bar == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            bar.removeViewer(player);
        }
        bar = null;
    }

    public void finish() {
        if (!active) {
            removeBar();
            return;
        }
        active = false;
        removeBar();
        Bukkit.broadcast(Msg.parse(plugin.prefix() + "<gray>The bounty window is closed. Contracts pay normally again."));
        plugin.notifier().send("Bounty closed", "The bounty window is over.", 0x95A5A6);
    }

    public void cancel() {
        active = false;
        removeBar();
    }

    public String status() {
        if (!active) {
            return "window closed";
        }
        long left = Math.max(0, endsAt - System.currentTimeMillis()) / 1000L;
        return "window open, " + (left / 60) + "m " + (left % 60) + "s left";
    }

    public UUID targetId(Player player) {
        return plugin.store().get(player.getUniqueId()).targetId;
    }
}
