package ru.galeheart.core.pass;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.util.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chest menu for the season pass: progress, shards and the cosmetic shop. */
public final class PassGui implements InventoryHolder, Listener {

    private static final int SIZE = 45;
    private static final int INFO_SLOT = 4;
    private static final int SHOP_FIRST_SLOT = 19;
    private static final int SHOP_LAST_SLOT = 25;
    private static final int TRAIL_OFF_SLOT = 31;
    private static final int CLOSE_SLOT = 40;

    private final GaleHeartCore plugin;
    private final Map<UUID, Map<Integer, String>> actions = new HashMap<>();

    public PassGui(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    /** Builds and opens the menu for one player. */
    public void open(Player player) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(this, SIZE, Msg.parse("<dark_gray>Season Pass"));
        Map<Integer, String> mapping = new HashMap<>();

        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        PassManager pass = plugin.pass();
        int level = pass.level(player);
        int into = pass.xpIntoLevel(data.passXp);
        int need = level >= pass.maxLevel() ? 0 : pass.costForLevel(level);
        List<String> infoLore = new ArrayList<>();
        infoLore.add("<gray>Level <white>" + level + "<gray>/" + pass.maxLevel());
        if (need > 0) {
            infoLore.add("<gray>XP <white>" + into + "<gray>/<white>" + need);
            infoLore.add(progressBar(into, need));
        } else {
            infoLore.add("<green>Max level reached");
        }
        infoLore.add("");
        infoLore.add("<gray>Shards: <aqua>" + data.shards);
        infoLore.add("<dark_gray>Earned by playing, killing and levelling");
        inventory.setItem(INFO_SLOT, icon(Material.NETHER_STAR, "<gradient:#4FA9C9:#C8324B>Season Pass", infoLore));

        List<PassManager.ShopItem> items = pass.shopItems();
        for (int index = 0; index < items.size(); index++) {
            int slot = SHOP_FIRST_SLOT + index;
            if (slot > SHOP_LAST_SLOT) {
                break;
            }
            PassManager.ShopItem item = items.get(index);
            boolean owned = data.owned.contains(item.id());
            boolean activeTrail = item.id().equals(data.trail);
            List<String> lore = new ArrayList<>();
            lore.add(item.display());
            lore.add("");
            if (owned) {
                lore.add(activeTrail ? "<green>Equipped" : "<green>Owned");
                if (!item.particle().isBlank() && !activeTrail) {
                    lore.add("<gray>Click to equip");
                }
            } else {
                lore.add("<gray>Cost: <gold>" + item.cost() + " shards");
                lore.add("<gray>You have: <white>" + data.shards);
                lore.add("");
                lore.add("<yellow>Click to buy");
            }
            Material material = material(item.icon(), owned);
            ItemStack stack = icon(material, item.display(), lore);
            if (owned) {
                glow(stack);
            }
            inventory.setItem(slot, stack);
            mapping.put(slot, item.id());
        }

        List<String> trailLore = new ArrayList<>();
        trailLore.add("<gray>Current: " + (data.trail.isBlank() ? "<dark_gray>none"
                : "<white>" + itemName(items, data.trail)));
        trailLore.add("");
        trailLore.add("<yellow>Click to turn the trail off");
        inventory.setItem(TRAIL_OFF_SLOT, icon(Material.BARRIER, "<red>Trail off", trailLore));
        mapping.put(TRAIL_OFF_SLOT, "__trail_off");

        inventory.setItem(CLOSE_SLOT, icon(Material.OAK_DOOR, "<gray>Close", List.of()));
        mapping.put(CLOSE_SLOT, "__close");

        actions.put(player.getUniqueId(), mapping);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.4f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PassGui)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Map<Integer, String> mapping = actions.get(player.getUniqueId());
        if (mapping == null) {
            return;
        }
        String action = mapping.get(event.getRawSlot());
        if (action == null) {
            return;
        }
        switch (action) {
            case "__close" -> player.closeInventory();
            case "__trail_off" -> {
                plugin.pass().clearTrail(player);
                player.sendMessage(Msg.parse(plugin.prefix() + "<gray>Trail off."));
                refresh(player);
            }
            default -> {
                PassManager.ShopItem item = plugin.pass().shopItem(action);
                if (item == null) {
                    return;
                }
                if (item.particle().isBlank()) {
                    plugin.pass().buy(player, action);
                } else if (plugin.store().get(player.getUniqueId()).owned.contains(action)) {
                    plugin.pass().equipTrail(player, action);
                    player.sendMessage(Msg.parse(plugin.prefix() + "<green>Equipped <white>" + item.display()));
                } else {
                    plugin.pass().buy(player, action);
                }
                refresh(player);
            }
        }
    }

    /** Rebuilds the menu one tick later so the client sees the new state. */
    public void refresh(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                open(player);
            }
        });
    }

    private static String itemName(List<PassManager.ShopItem> items, String id) {
        for (PassManager.ShopItem item : items) {
            if (item.id().equals(id)) {
                return item.display();
            }
        }
        return id;
    }

    private static String progressBar(int value, int max) {
        int length = 20;
        int filled = max <= 0 ? 0 : Math.max(0, Math.min(length, value * length / max));
        StringBuilder bar = new StringBuilder("<dark_gray>[<aqua>");
        for (int i = 0; i < length; i++) {
            if (i == filled) {
                bar.append("<dark_gray>");
            }
            bar.append('|');
        }
        bar.append("<dark_gray>]");
        return bar.toString();
    }

    private static Material material(String raw, boolean owned) {
        Material material = Material.matchMaterial(raw == null ? "" : raw.toUpperCase(java.util.Locale.ROOT));
        if (material == null) {
            material = Material.PAPER;
        }
        return material;
    }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.parse(name));
            List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Msg.parse(line));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void glow(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
    }
}
