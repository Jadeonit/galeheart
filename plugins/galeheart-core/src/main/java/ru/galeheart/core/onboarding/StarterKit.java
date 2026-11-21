package ru.galeheart.core.onboarding;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.util.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Starter kit for first-time players. */
public final class StarterKit {

    private static final List<String> PAGES = List.of(
            "<dark_red><bold>GaleHeart\n<reset><dark_gray>Season 1: Acid Sky\n\n"
                    + "<black>You start with <bold>10 hearts<reset><black>.\n\n"
                    + "Die, and you drop one.\nKill, and you take one.\n\n"
                    + "20 is the ceiling.",

            "<dark_red><bold>No teleports\n<reset><black>No /home.\nNo /tpa.\nNo /spawn.\n\n"
                    + "Beds set your respawn.\nEverything else is walking.\n\n"
                    + "Bases stay hidden because getting there is hard.",

            "<dark_red><bold>Weather\n<reset><black>Every anomaly warns you one hour ahead.\n\n"
                    + "Acid rain, fire rain, blizzard, toxic fog.\n\n"
                    + "Each type has a craftable protection item. Keep one in your base.",

            "<dark_red><bold>Commands\n<reset><black>/hearts  - your hearts\n"
                    + "/bounty  - your contract\n"
                    + "/pass  - season pass menu\n"
                    + "/quest  - Nether and End chains\n"
                    + "/top  - leaderboard\n\n"
                    + "Good luck. Trust nobody."
    );

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
        if (plugin.getConfig().getBoolean("starter-kit.book", true)) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(book());
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            given++;
        }
        if (given > 0) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<gray>Starter kit: <white>" + given + " items"));
        }
    }

    /** A guide book so a new player does not have to alt-tab to Discord. */
    private ItemStack book() {
        ItemStack stack = new ItemStack(Material.WRITTEN_BOOK);
        if (!(stack.getItemMeta() instanceof BookMeta meta)) {
            return stack;
        }
        meta.title(Component.text("GaleHeart: first hour"));
        meta.author(Component.text("GaleHeart"));
        List<Component> pages = new ArrayList<>();
        for (String page : PAGES) {
            pages.add(Msg.parse(page));
        }
        meta.pages(pages);
        stack.setItemMeta(meta);
        return stack;
    }
}
