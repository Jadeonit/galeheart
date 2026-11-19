package ru.orbital.portalblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PortalBlockPlugin extends JavaPlugin {

    public static final String BYPASS_PERMISSION = "portalblock.bypass";

    private final Map<UUID, Long> lastMessageAt = new HashMap<>();

    private PluginConfig config;
    private BukkitTask guardTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = PluginConfig.load(getConfig());

        getServer().getPluginManager().registerEvents(new PortalListener(this), this);

        PortalBlockCommand command = new PortalBlockCommand(this);
        if (getCommand("portalblock") != null) {
            getCommand("portalblock").setExecutor(command);
        }

        guardTask = Bukkit.getScheduler().runTaskTimer(this, this::checkPlayersInsidePortals, 20L, 20L);

        getLogger().info("Enabled. Nether gate: " + describe(config.nether()) + ", end gate: " + describe(config.end()));
    }

    @Override
    public void onDisable() {
        if (guardTask != null) {
            guardTask.cancel();
        }
        lastMessageAt.clear();
    }

    private static String describe(PluginConfig.Gate gate) {
        if (!gate.enabled()) {
            return "open";
        }
        return gate.permission().isBlank() ? "open (no permission set)" : gate.permission();
    }

    PluginConfig config() {
        return config;
    }

    void reloadPlugin() {
        reloadConfig();
        config = PluginConfig.load(getConfig());
        lastMessageAt.clear();
    }

    boolean canBypass(Player player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    /** A gate with a blank permission is open to everyone. */
    boolean allowed(Player player, PluginConfig.Gate gate) {
        if (gate.permission() == null || gate.permission().isBlank()) {
            return true;
        }
        return player.hasPermission(gate.permission());
    }

    void notifyBlocked(Player player, PluginConfig.Gate gate) {
        String message = gate.message();
        if (message == null || message.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previous = lastMessageAt.get(player.getUniqueId());
        if (previous != null && now - previous < config.messageCooldownMillis()) {
            return;
        }
        lastMessageAt.put(player.getUniqueId(), now);

        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        player.sendMessage(component);
    }

    void forget(Player player) {
        lastMessageAt.remove(player.getUniqueId());
    }

    private void checkPlayersInsidePortals() {
        if (!config.enabled() || !config.pushOut()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canBypass(player)) {
                continue;
            }
            Location location = player.getLocation();
            Material portal = PortalUtil.portalMaterialAt(location);
            if (portal == null) {
                continue;
            }
            PluginConfig.Gate gate = config.gateFor(portal);
            if (gate == null || !gate.enabled() || allowed(player, gate)) {
                continue;
            }

            Location safe = PortalUtil.findSafeLocation(location);
            if (safe != null) {
                player.teleport(safe);
                notifyBlocked(player, gate);
            }
        }
    }
}
