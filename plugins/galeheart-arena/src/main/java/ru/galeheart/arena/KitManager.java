package ru.galeheart.arena;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

/** Gives the practice kit for /safepvp. */
public final class KitManager {

    private final ArenaPlugin plugin;

    public KitManager(ArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void give(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();

        List<String> armor = plugin.getConfig().getStringList("kits.safe.armor");
        ItemStack[] pieces = new ItemStack[4];
        for (int i = 0; i < Math.min(4, armor.size()); i++) {
            Material material = Material.matchMaterial(armor.get(i));
            if (material != null && material.isItem()) {
                pieces[i] = new ItemStack(material);
            }
        }
        // Bukkit order: boots, leggings, chestplate, helmet
        ItemStack[] ordered = {pieces[3], pieces[2], pieces[1], pieces[0]};
        inventory.setArmorContents(ordered);

        for (String raw : plugin.getConfig().getStringList("kits.safe.items")) {
            String[] parts = raw.split(":");
            Material material = Material.matchMaterial(parts[0].trim());
            if (material == null || !material.isItem()) {
                continue;
            }
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
            inventory.addItem(new ItemStack(material, amount));
        }
    }
}
