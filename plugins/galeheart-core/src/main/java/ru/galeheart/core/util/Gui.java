package ru.galeheart.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small helpers shared by the menus. */
public final class Gui {

    private Gui() {
    }

    public static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.parse(name));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Msg.parse(line));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack glowing(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack filler(Material material) {
        return icon(material, " ", List.of());
    }

    public static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    /** 20 character progress bar. */
    public static String bar(int value, int max, String filled, String empty) {
        int length = 20;
        int done = max <= 0 ? 0 : Math.max(0, Math.min(length, value * length / max));
        return "<dark_gray>[" + filled + "|".repeat(done) + "<dark_gray>" + "|".repeat(length - done) + "]";
    }
}
