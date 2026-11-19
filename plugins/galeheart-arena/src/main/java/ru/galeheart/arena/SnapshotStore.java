package ru.galeheart.arena;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Crash-safe inventory snapshots for practice duels.
 * Written to disk before the inventory is cleared, restored on duel end.
 */
public final class SnapshotStore {

    private final ArenaPlugin plugin;
    private final File directory;

    public SnapshotStore(ArenaPlugin plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "snapshots");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create snapshots directory.");
        }
    }

    private File file(UUID id) {
        return new File(directory, id + ".yml");
    }

    public void save(Player player) {
        YamlConfiguration config = new YamlConfiguration();
        PlayerInventory inventory = player.getInventory();

        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                config.set("contents." + i, contents[i]);
            }
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) {
                config.set("armor." + i, armor[i]);
            }
        }
        if (inventory.getItemInOffHand() != null) {
            config.set("offhand", inventory.getItemInOffHand());
        }
        try {
            config.save(file(player.getUniqueId()));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save snapshot for " + player.getName() + ": " + exception.getMessage());
        }
    }

    public boolean has(UUID id) {
        return file(id).exists();
    }

    /** Restores the inventory and deletes the snapshot. Safe to call when none exists. */
    public void restore(Player player) {
        File file = file(player.getUniqueId());
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();

        if (config.isConfigurationSection("contents")) {
            for (String key : config.getConfigurationSection("contents").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = config.getItemStack("contents." + key);
                    if (item != null && slot >= 0 && slot < inventory.getSize() + 5) {
                        inventory.setItem(slot, item);
                    }
                } catch (NumberFormatException ignored) {
                    // skip malformed slot
                }
            }
        }
        if (config.isConfigurationSection("armor")) {
            ItemStack[] armor = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                armor[i] = config.getItemStack("armor." + i);
            }
            inventory.setArmorContents(armor);
        }
        ItemStack offhand = config.getItemStack("offhand");
        if (offhand != null) {
            inventory.setItemInOffHand(offhand);
        }
        if (!file.delete()) {
            plugin.getLogger().warning("Could not delete snapshot " + file.getName());
        }
    }
}
