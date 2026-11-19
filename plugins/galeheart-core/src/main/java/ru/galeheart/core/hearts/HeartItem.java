package ru.galeheart.core.hearts;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.util.Msg;

import java.util.ArrayList;
import java.util.List;

/** The tradeable heart item. */
public final class HeartItem {

    private final GaleHeartCore plugin;
    private final NamespacedKey key;

    public HeartItem(GaleHeartCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "heart");
    }

    public ItemStack create() {
        String rawMaterial = plugin.getConfig().getString("hearts.item.material", "NETHER_STAR");
        Material material = Material.matchMaterial(rawMaterial == null ? "NETHER_STAR" : rawMaterial);
        if (material == null || !material.isItem()) {
            material = Material.NETHER_STAR;
        }
        int max = plugin.getConfig().getInt("hearts.max", 10);

        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Msg.parse(plugin.getConfig().getString("hearts.item.name", "<red><bold>Heart")));
            List<String> lore = new ArrayList<>();
            for (String line : plugin.getConfig().getStringList("hearts.item.lore")) {
                lore.add(line.replace("{max}", String.valueOf(max)));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(Msg::parse).toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        return stack;
    }

    public boolean isHeart(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte value = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == 1;
    }
}
