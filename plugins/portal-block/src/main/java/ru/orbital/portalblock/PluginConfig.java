package ru.orbital.portalblock;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/** Portal gates: per-dimension permission and message. */
public record PluginConfig(
        boolean enabled,
        String message,
        long messageCooldownMillis,
        boolean blockMovement,
        boolean pushOut,
        Gate nether,
        Gate end
) {

    public record Gate(boolean enabled, String permission, String message) {

        public boolean allows(String permission) {
            return permission == null || permission.isBlank();
        }
    }

    public static PluginConfig load(FileConfiguration config) {
        long cooldownSeconds = Math.max(0L, config.getLong("message-cooldown-seconds", 5L));
        String fallback = config.getString("message", "");

        return new PluginConfig(
                config.getBoolean("enabled", true),
                fallback,
                cooldownSeconds * 1000L,
                config.getBoolean("block-movement", true),
                config.getBoolean("push-out", true),
                gate(config, "nether", fallback),
                gate(config, "end", fallback)
        );
    }

    private static Gate gate(FileConfiguration config, String key, String fallbackMessage) {
        String path = "portals." + key + ".";
        String message = config.getString(path + "message", fallbackMessage);
        return new Gate(
                config.getBoolean(path + "enabled", true),
                config.getString(path + "permission", ""),
                message == null ? "" : message
        );
    }

    /** Returns the gate that applies to the portal block, or null. */
    public Gate gateFor(Material material) {
        if (material == Material.NETHER_PORTAL) {
            return nether;
        }
        if (material == Material.END_PORTAL) {
            return end;
        }
        return null;
    }
}
