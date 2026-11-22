package ru.galeheart.core.achievement;

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
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.util.Gui;
import ru.galeheart.core.util.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Achievement board: progress, rewards, one page of twenty-eight. */
public final class AchievementGui implements InventoryHolder, Listener {

    private static final int SIZE = 54;
    private static final int INFO = 4;
    private static final int CLOSE = 8;
    private static final int PREV = 45;
    private static final int PAGE = 49;
    private static final int NEXT = 53;
    private static final int PER_PAGE = 28;

    private final GaleHeartCore plugin;
    private final Map<UUID, Integer> pages = new HashMap<>();

    public AchievementGui(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public void open(Player player) {
        open(player, pages.getOrDefault(player.getUniqueId(), 0));
    }

    public void open(Player player, int page) {
        AchievementManager manager = plugin.achievements();
        List<Achievement> all = manager.all();
        int pageCount = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(pageCount - 1, page));
        pages.put(player.getUniqueId(), current);

        Inventory inventory = Bukkit.createInventory(this, SIZE,
                Msg.parse("<gradient:#4FA9C9:#C8324B>Achievements"));

        for (int slot = 0; slot < SIZE; slot++) {
            boolean border = slot < 9 || slot > 44 || slot % 9 == 0 || slot % 9 == 8;
            if (border && slot != PREV && slot != NEXT && slot != PAGE) {
                inventory.setItem(slot, Gui.filler(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        int unlocked = manager.unlockedCount(player);
        List<String> infoLore = new ArrayList<>();
        infoLore.add("<gray>Unlocked <white>" + unlocked + "<gray>/" + all.size());
        infoLore.add(Gui.bar(unlocked, all.size(), "<gold>", "<gray>"));
        infoLore.add("");
        infoLore.add("<gray>Each one pays shards");
        inventory.setItem(INFO, Gui.icon(Material.EXPERIENCE_BOTTLE, "<gold><bold>Achievements", infoLore));
        inventory.setItem(CLOSE, Gui.icon(Material.BARRIER, "<red>Back", List.of("<gray>To the pass menu")));

        int start = current * PER_PAGE;
        int slot = 9;
        for (int index = start; index < all.size() && index < start + PER_PAGE; index++) {
            while (slot < 45 && (slot % 9 == 0 || slot % 9 == 8)) {
                slot++;
            }
            if (slot >= 45) {
                break;
            }
            Achievement achievement = all.get(index);
            inventory.setItem(slot, render(player, achievement));
            slot++;
        }

        if (pageCount > 1) {
            inventory.setItem(PREV, Gui.icon(Material.ARROW, "<gray>Previous page", List.of()));
            inventory.setItem(NEXT, Gui.icon(Material.ARROW, "<gray>Next page", List.of()));
            inventory.setItem(PAGE, Gui.icon(Material.PAPER,
                    "<white>Page " + (current + 1) + "<gray>/" + pageCount, List.of()));
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.2f);
    }

    private ItemStack render(Player player, Achievement achievement) {
        boolean done = plugin.achievements().unlocked(player, achievement.id());
        int progress = plugin.achievements().progress(player, achievement.trigger());
        int amount = achievement.amount();
        List<String> lore = new ArrayList<>();
        if (!achievement.description().isBlank()) {
            lore.add("<gray>" + achievement.description());
            lore.add("");
        }
        if (done) {
            lore.add("<green>Unlocked");
            lore.add("<gray>Reward: <aqua>" + achievement.shards() + " shards");
        } else {
            lore.add("<gray>Progress <white>" + Math.min(progress, amount) + "<gray>/<white>" + amount);
            lore.add(Gui.bar(Math.min(progress, amount), amount, "<aqua>", "<gray>"));
            lore.add("<gray>Reward: <aqua>" + achievement.shards() + " shards");
        }
        ItemStack stack = Gui.icon(Gui.material(achievement.icon(), Material.PAPER),
                (done ? "<green>" : "<gray>") + achievement.display(), lore);
        return done ? Gui.glowing(stack) : stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AchievementGui)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
        int page = pages.getOrDefault(player.getUniqueId(), 0);
        if (slot == CLOSE) {
            plugin.passGui().open(player);
        } else if (slot == PREV) {
            open(player, page - 1);
        } else if (slot == NEXT) {
            open(player, page + 1);
        }
    }
}
