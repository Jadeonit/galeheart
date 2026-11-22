package ru.galeheart.core.pass;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.util.Gui;
import ru.galeheart.core.util.Msg;

import java.util.ArrayList;
import java.util.List;

/** Main season pass menu: progress, shards and shortcuts. */
public final class PassGui implements InventoryHolder, Listener {

    private static final int SIZE = 45;
    private static final int HEAD = 4;
    private static final int LOCKER = 20;
    private static final int ACHIEVEMENTS = 22;
    private static final int REWARDS = 24;
    private static final int CLOSE = 40;

    private final GaleHeartCore plugin;

    public PassGui(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public void open(Player player) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        PassManager pass = plugin.pass();
        int level = pass.level(player);
        int into = pass.xpIntoLevel(data.passXp);
        int need = level >= pass.maxLevel() ? 0 : pass.costForLevel(level);

        Inventory inventory = Bukkit.createInventory(this, SIZE,
                Msg.parse("<gradient:#4FA9C9:#C8324B>Season Pass"));

        for (int slot = 0; slot < SIZE; slot++) {
            boolean border = slot < 9 || slot > 35 || slot % 9 == 0 || slot % 9 == 8;
            if (!border) {
                continue;
            }
            Material glass = (slot + level) % 4 == 0
                    ? Material.CYAN_STAINED_GLASS_PANE
                    : Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            inventory.setItem(slot, Gui.filler(glass));
        }

        List<String> headLore = new ArrayList<>();
        headLore.add("<gray>Level <white>" + level + "<gray>/" + pass.maxLevel());
        if (need > 0) {
            headLore.add(Gui.bar(into, need, "<aqua>", "<gray>"));
            headLore.add("<gray>XP <aqua>" + into + "<gray>/<white>" + need);
        } else {
            headLore.add("<green>Max level reached");
        }
        headLore.add("");
        headLore.add("<gray>Shards <aqua>" + data.shards);
        headLore.add("<gray>Achievements <white>" + plugin.achievements().unlockedCount(player)
                + "<gray>/" + plugin.achievements().all().size());
        String title = pass.equipped(player, Cosmetic.Type.TITLE);
        Cosmetic titleCosmetic = pass.cosmetic(title);
        headLore.add("<gray>Title " + (titleCosmetic == null ? "<dark_gray>none" : titleCosmetic.display()));
        inventory.setItem(HEAD, head(player, level, headLore));

        inventory.setItem(LOCKER, Gui.icon(Material.CHEST, "<aqua><bold>Locker", List.of(
                "<gray>Titles, trails and join effects",
                "<gray>Owned: <white>" + data.owned.size() + "<gray>/" + pass.cosmetics().size(),
                "",
                "<yellow>Click to open")));

        inventory.setItem(ACHIEVEMENTS, Gui.icon(Material.EXPERIENCE_BOTTLE, "<gold><bold>Achievements", List.of(
                "<gray>Unlocked: <white>" + plugin.achievements().unlockedCount(player)
                        + "<gray>/" + plugin.achievements().all().size(),
                "<gray>Every one pays shards",
                "",
                "<yellow>Click to open")));

        List<String> rewardLore = new ArrayList<>();
        rewardLore.add("<gray>Next unlocks:");
        int shown = 0;
        for (int next = level + 1; next <= pass.maxLevel() && shown < 4; next++) {
            Cosmetic reward = levelReward(next);
            if (reward == null) {
                continue;
            }
            rewardLore.add("<dark_gray>Lv " + next + " <gray>— " + reward.display()
                    + " <dark_gray>(" + reward.type().label().toLowerCase() + ")");
            shown++;
        }
        if (shown == 0) {
            rewardLore.add("<dark_gray>Everything unlocked");
        }
        inventory.setItem(REWARDS, Gui.icon(Material.BOOK, "<light_purple><bold>Level rewards", rewardLore));

        inventory.setItem(CLOSE, Gui.icon(Material.BARRIER, "<red>Close", List.of("<gray>Or press E")));

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.5f);
    }

    private ItemStack head(Player player, int level, List<String> lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        if (stack.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            meta.displayName(Msg.parse("<white><bold>" + player.getName()));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Msg.parse(line));
            }
            meta.lore(lines);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Cosmetic levelReward(int level) {
        var section = plugin.getConfig().getConfigurationSection("pass.level-rewards");
        if (section == null) {
            return null;
        }
        for (String key : section.getKeys(false)) {
            try {
                if (Integer.parseInt(key.trim()) == level) {
                    return plugin.pass().cosmetic(section.getString(key, ""));
                }
            } catch (NumberFormatException ignored) {
                // malformed level key
            }
        }
        return null;
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
        int slot = event.getRawSlot();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
        switch (slot) {
            case CLOSE -> player.closeInventory();
            case LOCKER -> plugin.lockerGui().open(player);
            case ACHIEVEMENTS -> plugin.achievementsGui().open(player);
            case REWARDS -> sendRewards(player);
            default -> {
                // filler, nothing to do
            }
        }
    }

    private void sendRewards(Player player) {
        int level = plugin.pass().level(player);
        player.sendMessage(Msg.parse("<gradient:#4FA9C9:#C8324B><bold>Next pass rewards"));
        int shown = 0;
        for (int next = level + 1; next <= plugin.pass().maxLevel() && shown < 8; next++) {
            Cosmetic reward = levelReward(next);
            if (reward == null) {
                continue;
            }
            player.sendMessage(Msg.parse("<gray>Lv <white>" + next + " <dark_gray>— " + reward.display()
                    + " <dark_gray>(" + reward.type().label() + ")"));
            shown++;
        }
        if (shown == 0) {
            player.sendMessage(Msg.parse("<gray>Everything is unlocked. Go touch grass."));
        }
    }
}
