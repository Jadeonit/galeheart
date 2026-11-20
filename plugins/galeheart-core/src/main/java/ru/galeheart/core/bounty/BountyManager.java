package ru.galeheart.core.bounty;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.combat.CombatManager;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.util.Msg;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Weekly bounty: the leader gets hunted for a fixed window.
 * Everyone receives a compass pointing at the target.
 */
public final class BountyManager {

    private final GaleHeartCore plugin;
    private final NamespacedKey compassKey;

    private UUID targetId;
    private long endsAt;
    private LocalDate lastRun;
    private BossBar targetBar;
    private final Set<UUID> compassGiven = new HashSet<>();

    public BountyManager(GaleHeartCore plugin) {
        this.plugin = plugin;
        this.compassKey = new NamespacedKey(plugin, "bounty_compass");
    }

    public boolean active() {
        return targetId != null;
    }

    public void tick() {
        if (!plugin.getConfig().getBoolean("bounty.enabled", true)) {
            return;
        }
        if (targetId != null) {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) {
                finish(false, "The bounty target left the server.");
                return;
            }
            updateBar(target);
            updateCompasses(Bukkit.getPlayer(targetId));
            if (System.currentTimeMillis() >= endsAt) {
                finish(true, null);
            }
            return;
        }
        if (shouldStartNow()) {
            start();
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
        DayOfWeek day = today.getDayOfWeek();
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

    private static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw.trim());
        } catch (RuntimeException exception) {
            return LocalTime.of(19, 0);
        }
    }

    /** Picks the leader and starts the hunt. Returns false when nobody is eligible. */
    public boolean start() {
        if (targetId != null) {
            return false;
        }
        int minHearts = plugin.getConfig().getInt("bounty.min-hearts", 8);
        boolean skipStaff = plugin.getConfig().getBoolean("bounty.skip-staff", true);

        List<Player> candidates = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.store().get(player.getUniqueId());
            if (data.hearts < minHearts) {
                continue;
            }
            if (plugin.combat().isProtected(player)) {
                continue;
            }
            if (skipStaff && player.hasPermission("galeheart.admin")) {
                continue;
            }
            if (player.getWorld().getName().equals(plugin.getConfig().getString("bounty.arena-world", "arena"))) {
                continue;
            }
            candidates.add(player);
        }
        if (candidates.isEmpty()) {
            return false;
        }
        candidates.sort(Comparator.comparingInt((Player p) -> plugin.hearts().hearts(p)).reversed());
        Player target = candidates.get(0);

        targetId = target.getUniqueId();
        lastRun = LocalDate.now();
        long minutes = plugin.getConfig().getLong("bounty.duration-minutes", 20);
        endsAt = System.currentTimeMillis() + minutes * 60_000L;

        targetBar = BossBar.bossBar(
                Msg.parse("<red><bold>BOUNTY<red> — survive for " + minutes + " min"),
                1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        targetBar.addViewer(target);

        Bukkit.broadcast(Msg.parse(plugin.prefix() + "<red><bold>BOUNTY<red> on <white>" + target.getName()
                + "<red>! <gray>" + minutes + " minutes. Your compass points at them."));
        plugin.notifier().send("Bounty",
                target.getName() + " has a bounty: " + minutes + " minutes, " + plugin.hearts().hearts(target)
                        + " hearts. Compasses point at them.", 0xE74C3C);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(target)) {
                continue;
            }
            setCompass(player, target.getLocation());
        }
        return true;
    }

    private void setCompass(Player player, Location location) {
        if (!compassGiven.contains(player.getUniqueId())) {
            boolean has = false;
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType() == Material.COMPASS && isMarked(stack)) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                player.getInventory().addItem(markedCompass());
            }
            compassGiven.add(player.getUniqueId());
        }
        player.setCompassTarget(location);
    }

    private ItemStack markedCompass() {
        ItemStack stack = new ItemStack(Material.COMPASS);
        stack.editMeta(meta -> {
            meta.displayName(Msg.parse("<red>Bounty Compass"));
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        });
        return stack;
    }

    private boolean isMarked(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE);
    }

    private void updateCompasses(Player target) {
        if (target == null) {
            return;
        }
        for (UUID id : compassGiven) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.setCompassTarget(target.getLocation());
            }
        }
    }

    private void updateBar(Player target) {
        if (targetBar == null) {
            return;
        }
        long left = Math.max(0, endsAt - System.currentTimeMillis());
        long minutes = left / 60_000L;
        long seconds = (left % 60_000L) / 1000L;
        targetBar.name(Msg.parse("<red><bold>BOUNTY<red> — " + minutes + ":" + String.format("%02d", seconds) + " left"));
        targetBar.progress((float) left / getDurationMillis());
    }

    private long getDurationMillis() {
        return Math.max(1, plugin.getConfig().getLong("bounty.duration-minutes", 20) * 60_000L);
    }

    /** Called from PlayerDeathEvent when the victim is the bounty target. */
    public boolean handleDeath(Player victim, Player killer) {
        if (targetId == null || !targetId.equals(victim.getUniqueId())) {
            return false;
        }
        if (killer != null) {
            int reward = plugin.getConfig().getInt("bounty.reward-hearts", 1);
            plugin.hearts().add(killer, reward);
            Bukkit.broadcast(Msg.parse(plugin.prefix() + "<gold>" + killer.getName() + " <gray>claimed the bounty on <white>"
                    + victim.getName() + "<gray>. <red>+" + reward + " heart"));
            plugin.notifier().send("Bounty claimed", killer.getName() + " killed " + victim.getName()
                    + " and took the bounty.", 0xF1C40F);
        }
        finish(false, null);
        return true;
    }

    public void onQuit(Player player) {
        if (targetId != null && targetId.equals(player.getUniqueId())) {
            finish(false, "The bounty target left the server.");
        }
    }

    /** Ends the hunt. rewardSurvivor is used on timeout. */
    public void finish(boolean rewardSurvivor, String reason) {
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);

        if (rewardSurvivor && target != null) {
            int reward = plugin.getConfig().getInt("bounty.survivor-hearts", 1);
            plugin.hearts().add(target, reward);
            Bukkit.broadcast(Msg.parse(plugin.prefix() + "<green>" + target.getName()
                    + " survived the bounty. <red>+" + reward + " heart"));
            plugin.notifier().send("Bounty survived", target.getName() + " survived the hunt.", 0x2ECC71);
        } else if (reason != null) {
            Bukkit.broadcast(Msg.parse(plugin.prefix() + "<gray>" + reason));
        }

        if (targetBar != null) {
            if (target != null) {
                targetBar.removeViewer(target);
            }
            targetBar = null;
        }
        for (UUID id : compassGiven) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack != null && stack.getType() == Material.COMPASS && isMarked(stack)) {
                    player.getInventory().setItem(slot, null);
                }
            }
            player.setCompassTarget(player.getWorld().getSpawnLocation());
        }
        compassGiven.clear();
        targetId = null;
        endsAt = 0L;
    }

    public void cancel() {
        finish(false, "Bounty cancelled.");
    }

    public String targetName() {
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        return target == null ? null : target.getName();
    }
}
