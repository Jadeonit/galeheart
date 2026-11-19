package ru.galeheart.core.season;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.util.Msg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Season state, finale and reset. */
public final class SeasonManager {

    private final GaleHeartCore plugin;
    private final Map<UUID, Double> originalBorders = new HashMap<>();

    private boolean finale;
    private BukkitTask restoreTask;

    public SeasonManager(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    public boolean finale() {
        return finale;
    }

    public void startFinale() {
        if (finale) {
            return;
        }
        finale = true;

        for (Player player : Bukkit.getOnlinePlayers()) {
            AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null) {
                attribute.setBaseValue(2.0);
                player.setHealth(2.0);
            }
            player.sendMessage(Msg.parse(plugin.prefix() + "<red><bold>THE FINALE BEGINS<red> — everyone is down to one heart."));
        }

        double size = plugin.getConfig().getDouble("season.border-size", 500.0);
        int seconds = plugin.getConfig().getInt("season.border-shrink-seconds", 600);
        for (World world : Bukkit.getWorlds()) {
            originalBorders.putIfAbsent(world.getUID(), world.getWorldBorder().getSize());
            world.getWorldBorder().changeSize(size, seconds * 20L);
        }

        long duration = plugin.getConfig().getLong("season.one-heart-seconds", 1800L);
        restoreTask = Bukkit.getScheduler().runTaskLater(plugin, this::stopFinale, duration * 20L);
        Bukkit.broadcast(Msg.parse(plugin.prefix() + "<gray>One heart for <white>" + (duration / 60) + " min<gray>. Good luck."));
        plugin.notifier().send("Season finale", "Everyone is down to one heart for " + (duration / 60)
                + " minutes. Border is closing to " + (int) size + " blocks.", 0xE74C3C);
    }

    public void stopFinale() {
        if (!finale) {
            return;
        }
        finale = false;
        if (restoreTask != null) {
            restoreTask.cancel();
            restoreTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.hearts().apply(player);
            player.sendMessage(Msg.parse(plugin.prefix() + "<gray>The finale is over. Your hearts are back."));
        }
        double defaultSize = plugin.getConfig().getDouble("season.default-border", 60000.0);
        for (World world : Bukkit.getWorlds()) {
            world.getWorldBorder().changeSize(defaultSize, 1200L);
        }
        originalBorders.clear();
        plugin.notifier().send("Finale over", "Hearts are restored. Season continues.", 0x2ECC71);
    }

    public void resetSeason() {
        stopFinale();
        plugin.store().resetAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.hearts().apply(player);
            plugin.quests().grant(player);
            player.sendMessage(Msg.parse(plugin.prefix() + "<green>A new season has started. Hearts reset to "
                    + plugin.getConfig().getInt("hearts.start", 3) + "."));
        }
        Bukkit.broadcast(Msg.parse(plugin.prefix() + "<gradient:#4FA9C9:#C8324B><bold>SEASON RESET<gradient>"));
        plugin.notifier().send("Season reset", "Hearts and quests wiped. A new season begins.", 0x3498DB);
    }
}
