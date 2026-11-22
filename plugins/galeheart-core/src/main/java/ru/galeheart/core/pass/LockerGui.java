package ru.galeheart.core.pass;

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
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.util.Gui;
import ru.galeheart.core.util.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cosmetic locker: titles, trails and join effects, one tab each. */
public final class LockerGui implements InventoryHolder, Listener {

    private static final int SIZE = 54;
    private static final int TAB_TITLES = 0;
    private static final int TAB_TRAILS = 1;
    private static final int TAB_JOIN = 2;
    private static final int INFO = 4;
    private static final int CLOSE = 8;
    private static final int PREV = 45;
    private static final int PAGE = 49;
    private static final int NEXT = 53;
    private static final int PER_PAGE = 28;

    private final GaleHeartCore plugin;
    private final Map<UUID, State> states = new HashMap<>();

    public LockerGui(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    private static final class State {
        Cosmetic.Type type = Cosmetic.Type.TITLE;
        int page;
        final Map<Integer, String> slots = new HashMap<>();
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public void open(Player player) {
        open(player, Cosmetic.Type.TITLE, 0);
    }

    public void open(Player player, Cosmetic.Type type, int page) {
        State state = states.computeIfAbsent(player.getUniqueId(), key -> new State());
        state.type = type;
        state.slots.clear();

        PlayerData data = plugin.store().get(player.getUniqueId());
        PassManager pass = plugin.pass();
        List<Cosmetic> items = pass.cosmetics(type);
        int pages = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
        state.page = Math.max(0, Math.min(pages - 1, page));

        Inventory inventory = Bukkit.createInventory(this, SIZE,
                Msg.parse("<gradient:#4FA9C9:#C8324B>Locker"));

        for (int slot = 0; slot < SIZE; slot++) {
            boolean border = slot < 9 || slot > 44 || slot % 9 == 0 || slot % 9 == 8;
            if (border && slot != PREV && slot != NEXT && slot != PAGE) {
                inventory.setItem(slot, Gui.filler(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        inventory.setItem(TAB_TITLES, tab(Material.NAME_TAG, "<aqua>Titles", type == Cosmetic.Type.TITLE,
                pass.cosmetics(Cosmetic.Type.TITLE).size(), data, Cosmetic.Type.TITLE));
        inventory.setItem(TAB_TRAILS, tab(Material.BLAZE_POWDER, "<gold>Trails", type == Cosmetic.Type.TRAIL,
                pass.cosmetics(Cosmetic.Type.TRAIL).size(), data, Cosmetic.Type.TRAIL));
        inventory.setItem(TAB_JOIN, tab(Material.FIREWORK_ROCKET, "<light_purple>Join effects",
                type == Cosmetic.Type.JOIN, pass.cosmetics(Cosmetic.Type.JOIN).size(), data, Cosmetic.Type.JOIN));

        List<String> infoLore = new ArrayList<>();
        infoLore.add("<gray>Shards <aqua>" + data.shards);
        infoLore.add("<gray>Pass level <white>" + pass.level(player) + "<gray>/" + pass.maxLevel());
        infoLore.add("");
        infoLore.add("<gray>Click owned item to equip");
        infoLore.add("<gray>Click equipped item to remove");
        inventory.setItem(INFO, Gui.icon(Material.BOOK, "<white><bold>Locker", infoLore));
        inventory.setItem(CLOSE, Gui.icon(Material.BARRIER, "<red>Back", List.of("<gray>To the pass menu")));

        int start = state.page * PER_PAGE;
        int slot = 9;
        for (int index = start; index < items.size() && index < start + PER_PAGE; index++) {
            while (slot < 45 && (slot % 9 == 0 || slot % 9 == 8)) {
                slot++;
            }
            if (slot >= 45) {
                break;
            }
            Cosmetic cosmetic = items.get(index);
            inventory.setItem(slot, render(player, data, cosmetic));
            state.slots.put(slot, cosmetic.id());
            slot++;
        }

        boolean ownTitles = !pass.cosmetics(Cosmetic.Type.TITLE).isEmpty();
        if (pages > 1) {
            inventory.setItem(PREV, Gui.icon(Material.ARROW, "<gray>Previous page", List.of()));
            inventory.setItem(NEXT, Gui.icon(Material.ARROW, "<gray>Next page", List.of()));
            inventory.setItem(PAGE, Gui.icon(Material.PAPER, "<white>Page " + (state.page + 1) + "<gray>/" + pages,
                    List.of("<gray>Owned: <white>" + data.owned.size() + "<gray>/" + pass.cosmetics().size())));
        } else if (ownTitles) {
            inventory.setItem(PAGE, Gui.icon(Material.PAPER, "<white>Owned " + data.owned.size()
                    + "<gray>/" + pass.cosmetics().size(), List.of()));
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.3f);
    }

    private ItemStack tab(Material material, String name, boolean active, int count, PlayerData data, Cosmetic.Type type) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Items: <white>" + count);
        lore.add("<gray>Owned: <white>" + data.owned.stream()
                .filter(id -> {
                    Cosmetic cosmetic = plugin.pass().cosmetic(id);
                    return cosmetic != null && cosmetic.type() == type;
                }).count());
        if (active) {
            lore.add("");
            lore.add("<green>Open");
        }
        ItemStack stack = Gui.icon(material, name, lore);
        return active ? Gui.glowing(stack) : stack;
    }

    private ItemStack render(Player player, PlayerData data, Cosmetic cosmetic) {
        boolean owned = data.owned.contains(cosmetic.id());
        boolean equipped = cosmetic.id().equals(plugin.pass().equipped(player, cosmetic.type()));
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + cosmetic.type().label());
        lore.add("");
        if (!cosmetic.prefix().isBlank()) {
            lore.add("<gray>Prefix: <white>" + cosmetic.prefix());
        }
        if (!cosmetic.particle().isBlank()) {
            lore.add("<gray>Particle: <white>" + cosmetic.particle().toLowerCase().replace('_', ' '));
        }
        lore.add("");
        if (owned) {
            lore.add(equipped ? "<green>Equipped" : "<green>Owned");
            lore.add(equipped ? "<yellow>Click to remove" : "<yellow>Click to equip");
        } else {
            lore.add("<gray>Cost: <gold>" + cosmetic.cost() + " shards");
            lore.add("<gray>Unlock: pass level <white>" + cosmetic.unlockLevel());
            lore.add("");
            lore.add(plugin.pass().level(player) >= cosmetic.unlockLevel()
                    ? "<yellow>Click to buy"
                    : "<red>Locked");
        }
        ItemStack stack = Gui.icon(Gui.material(cosmetic.icon(), Material.PAPER), cosmetic.display(), lore);
        return equipped ? Gui.glowing(stack) : stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LockerGui)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        State state = states.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        int slot = event.getRawSlot();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);

        if (slot == CLOSE) {
            plugin.passGui().open(player);
            return;
        }
        if (slot == TAB_TITLES) {
            open(player, Cosmetic.Type.TITLE, 0);
            return;
        }
        if (slot == TAB_TRAILS) {
            open(player, Cosmetic.Type.TRAIL, 0);
            return;
        }
        if (slot == TAB_JOIN) {
            open(player, Cosmetic.Type.JOIN, 0);
            return;
        }
        if (slot == PREV) {
            open(player, state.type, state.page - 1);
            return;
        }
        if (slot == NEXT) {
            open(player, state.type, state.page + 1);
            return;
        }

        String id = state.slots.get(slot);
        if (id == null) {
            return;
        }
        PlayerData data = plugin.store().get(player.getUniqueId());
        if (data.owned.contains(id)) {
            if (id.equals(plugin.pass().equipped(player, state.type))) {
                plugin.pass().unequip(player, state.type);
            } else {
                plugin.pass().equip(player, id);
            }
        } else {
            plugin.pass().buy(player, id);
        }
        open(player, state.type, state.page);
    }
}
