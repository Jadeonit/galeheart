package ru.galeheart.core.onboarding;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.util.Msg;

import java.util.Map;

/** Starter kit for first-time players. */
public final class StarterKit {

    private final GaleHeartCore plugin;

    public StarterKit(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("starter-kit.enabled", true);
    }

    public void give(Player player) {
        if (!enabled()) {
            return;
        }
        int given = 0;
        for (String raw : plugin.getConfig().getStringList("starter-kit.items")) {
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
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(material, amount));
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            given++;
        }
        if (given > 0) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<gray>Starter kit: <white>"
                    + plugin.getConfig().getStringList("starter-kit.items").size() + " items"));
        }
    }
}
