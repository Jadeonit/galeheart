package ru.anomaly.weather.protection;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import ru.anomaly.weather.AnomalousWeatherPlugin;
import ru.anomaly.weather.anomaly.AnomalyType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Хранит предметы защиты и проверяет, надет ли нужный предмет.
 */
public final class ProtectionManager {

    private final AnomalousWeatherPlugin plugin;
    private final NamespacedKey protectionKey;
    private final Map<AnomalyType, ProtectionItem> items = new EnumMap<>(AnomalyType.class);
    private final List<NamespacedKey> registeredRecipes = new ArrayList<>();
    /** Предметы защиты, снятые с дропа при смерти и ожидающие возврата. */
    private final Map<UUID, List<ItemStack>> keptOnDeath = new HashMap<>();
    private boolean recipesEnabled;
    private boolean unlockRecipesOnJoin;

    public ProtectionManager(AnomalousWeatherPlugin plugin) {
        this.plugin = plugin;
        this.protectionKey = new NamespacedKey(plugin, "anomaly_protection");
    }

    /** Перечитывает предметы защиты и заново регистрирует рецепты. */
    public void load() {
        items.clear();
        unregisterRecipes();

        for (AnomalyType type : AnomalyType.values()) {
            ConfigurationSection section = plugin.getConfig()
                    .getConfigurationSection("protection.items." + type.name());
            if (section == null) {
                continue;
            }
            ProtectionItem item = ProtectionItem.from(type, section);
            if (item == null) {
                plugin.getLogger().warning("Invalid protection item for anomaly " + type.name());
                continue;
            }
            items.put(type, item);
        }

        recipesEnabled = plugin.getConfig().getBoolean("protection.recipes.enabled", true);
        unlockRecipesOnJoin = plugin.getConfig().getBoolean("protection.recipes.unlock-on-join", true);
        if (recipesEnabled) {
            registerRecipes();
            if (unlockRecipesOnJoin) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    unlockRecipes(player);
                }
            }
        }
    }

    public ProtectionItem item(AnomalyType type) {
        return items.get(type);
    }

    /** Название предмета защиты для сообщений. */
    public String itemName(AnomalyType type) {
        ProtectionItem item = items.get(type);
        return item == null ? "<gray>not configured" : item.name();
    }

    /** Создаёт предмет защиты (для команды /anomaly give). */
    public ItemStack create(AnomalyType type) {
        ProtectionItem item = items.get(type);
        return item == null ? null : item.buildItem(protectionKey);
    }

    /** Доля поглощаемого урона: 0.0 — защиты нет, 1.0 — полная защита. */
    public double reduction(Player player, AnomalyType type) {
        ProtectionItem item = items.get(type);
        if (item == null) {
            return 0.0;
        }
        return wears(player, item) ? item.damageReduction() : 0.0;
    }

    /** Блокирует ли защита негативные эффекты (яд, слепота и т.д.). */
    public boolean blocksEffects(Player player, AnomalyType type) {
        ProtectionItem item = items.get(type);
        return item != null && item.blocksEffects() && wears(player, item);
    }

    /** Находит предмет защиты по ItemStack (или null, если это не защита). */
    public ProtectionItem find(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        for (ProtectionItem item : items.values()) {
            if (item.matches(stack, protectionKey)) {
                return item;
            }
        }
        return null;
    }

    /** Нельзя ли терять этот предмет (выброс, рамки, портал Края, смерть). */
    public boolean preventsDrop(ItemStack stack) {
        ProtectionItem item = find(stack);
        return item != null && item.preventDrop();
    }

    /**
     * Убирает защитные предметы из дропа при смерти и запоминает их,
     * чтобы вернуть владельцу после возрождения.
     */
    public void keepOnDeath(PlayerDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        List<ItemStack> kept = null;
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (!preventsDrop(stack)) {
                continue;
            }
            if (kept == null) {
                kept = new ArrayList<>();
            }
            kept.add(stack.clone());
            iterator.remove();
        }
        if (kept != null) {
            keptOnDeath.computeIfAbsent(event.getEntity().getUniqueId(), id -> new ArrayList<>()).addAll(kept);
        }
    }

    /** Возвращает предметы, сохранённые при смерти. Вызывать после возрождения. */
    public void restoreAfterDeath(Player player) {
        List<ItemStack> kept = keptOnDeath.remove(player.getUniqueId());
        if (kept == null || kept.isEmpty()) {
            return;
        }
        for (ItemStack stack : kept) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        player.sendActionBar(plugin.messages().get("item-kept-on-death"));
    }

    /** Ключи зарегистрированных рецептов. */
    public List<NamespacedKey> recipeKeys() {
        return List.copyOf(registeredRecipes);
    }

    /** Открывает рецепты защиты в книге рецептов игрока. */
    public void unlockRecipes(Player player) {
        if (!unlockRecipesOnJoin) {
            return;
        }
        for (NamespacedKey key : registeredRecipes) {
            try {
                player.discoverRecipe(key);
            } catch (Throwable ignored) {
                // рецепт мог не зарегистрироваться — пропускаем
            }
        }
    }

    public boolean wears(Player player, ProtectionItem item) {
        if (item.slot() == ProtectionSlot.ANY) {
            for (ItemStack candidate : player.getInventory().getArmorContents()) {
                if (item.matches(candidate, protectionKey)) {
                    return true;
                }
            }
            return item.matches(player.getInventory().getItemInOffHand(), protectionKey)
                    || item.matches(player.getInventory().getItemInMainHand(), protectionKey);
        }
        return item.matches(itemInSlot(player, item.slot()), protectionKey);
    }

    private ItemStack itemInSlot(Player player, ProtectionSlot slot) {
        return switch (slot) {
            case HEAD -> player.getInventory().getHelmet();
            case CHEST -> player.getInventory().getChestplate();
            case LEGS -> player.getInventory().getLeggings();
            case FEET -> player.getInventory().getBoots();
            case OFF_HAND -> player.getInventory().getItemInOffHand();
            case MAIN_HAND -> player.getInventory().getItemInMainHand();
            case ANY -> null;
        };
    }

    private void registerRecipes() {
        for (Map.Entry<AnomalyType, ProtectionItem> entry : items.entrySet()) {
            AnomalyType type = entry.getKey();
            ProtectionItem item = entry.getValue();
            RecipeDefs.RecipeDef definition = RecipeDefs.get(type);
            if (definition == null) {
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, "protection_" + type.name().toLowerCase(Locale.ROOT));
            ShapedRecipe recipe = new ShapedRecipe(key, item.buildItem(protectionKey));
            recipe.shape(definition.shape());
            definition.ingredients().forEach((symbol, material) -> recipe.setIngredient(symbol, material));
            try {
                Bukkit.addRecipe(recipe);
                registeredRecipes.add(key);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Could not register recipe " + key + ": " + throwable.getMessage());
            }
        }
    }

    public void unregisterRecipes() {
        for (NamespacedKey key : registeredRecipes) {
            try {
                Bukkit.removeRecipe(key);
            } catch (Throwable ignored) {
                // рецепта уже нет — не страшно
            }
        }
        registeredRecipes.clear();
    }
}
