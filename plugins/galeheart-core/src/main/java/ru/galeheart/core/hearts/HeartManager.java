package ru.galeheart.core.hearts;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.combat.CombatManager;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.util.Msg;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Hearts as health + a tradeable item. */
public final class HeartManager {

    private final GaleHeartCore plugin;
    private final HeartItem item;
    private final Set<UUID> pendingPenalty = new HashSet<>();

    public HeartManager(GaleHeartCore plugin) {
        this.plugin = plugin;
        this.item = new HeartItem(plugin);
    }

    public HeartItem item() {
        return item;
    }

    public int hearts(Player player) {
        return plugin.store().get(player.getUniqueId()).hearts;
    }

    public int min() {
        return Math.max(1, plugin.getConfig().getInt("hearts.min", 1));
    }

    public int max() {
        return Math.max(min(), plugin.getConfig().getInt("hearts.max", 10));
    }

    public void add(Player player, int delta) {
        set(player, hearts(player) + delta);
    }

    public void set(Player player, int value) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        int clamped = Math.max(min(), Math.min(max(), value));
        data.hearts = clamped;
        plugin.store().write(data);
        apply(player);
        plugin.achievements().check(player);
    }

    public void apply(Player player) {
        int hearts = Math.max(min(), Math.min(max(), hearts(player)));
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double maxHealth = hearts * 2.0;
        attribute.setBaseValue(maxHealth);
        if (player.getHealth() > maxHealth) {
            player.setHealth(Math.max(1.0, maxHealth));
        }
    }

    /** Drops one heart item where the player died. Called from PlayerDeathEvent. */
    public void dropHeart(org.bukkit.Location location) {
        if (location.getWorld() == null) {
            return;
        }
        location.getWorld().dropItemNaturally(location, item.create());
    }

    public void onDeath(Player victim, Player killer) {
        PlayerData data = plugin.store().get(victim.getUniqueId());
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("protection.death-cooldown-seconds", 180L) * 1000L;

        boolean bypass = victim.hasPermission(CombatManager.BYPASS);
        boolean canLose = (bypass || !data.protectedNow()) && now - data.lastDeath >= cooldownMs;
        if (canLose && data.hearts > min()) {
            data.hearts -= 1;
            data.lastDeath = now;
            dropHeart(victim.getLocation());
            victim.sendMessage(Msg.parse(plugin.prefix() + "<red>You lost a heart. <gray>(" + data.hearts + " left)"));
        } else if (canLose) {
            // Already at the floor: no heart to take, so the death costs time instead.
            pendingPenalty.add(victim.getUniqueId());
            victim.sendMessage(Msg.parse(plugin.prefix() + "<dark_red>You died on your last heart. <gray>You will respawn weakened."));
        }

        if (killer != null && !killer.equals(victim)) {
            PlayerData killerData = plugin.store().get(killer.getUniqueId());
            killerData.lastKill = now;
            plugin.store().write(killerData);
        }
        plugin.store().save(data);
    }

    /** Consumes a heart item and adds one heart. */
    public boolean consume(Player player, ItemStack stack) {
        if (!item.isHeart(stack)) {
            return false;
        }
        if (hearts(player) >= max()) {
            player.sendActionBar(Msg.parse("<red>You are at the heart cap (" + max() + ")"));
            return false;
        }
        int remaining = stack.getAmount() - 1;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(remaining);
        }
        add(player, 1);
        player.sendActionBar(Msg.parse("<red>+1 heart <gray>(" + hearts(player) + "/" + max() + ")"));
        return true;
    }

    /** Hearts above the threshold decay without PvP kills. Runs every 10 minutes. */
    public void coolingTick() {
        if (!plugin.getConfig().getBoolean("cooling.enabled", true)) {
            return;
        }
        int above = plugin.getConfig().getInt("cooling.above", 8);
        long windowMs = plugin.getConfig().getLong("cooling.hours-without-kill", 48L) * 3_600_000L;
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.store().get(player.getUniqueId());
            if (data.protectedNow() || data.hearts <= above) {
                continue;
            }
            if (now - data.lastKill < windowMs) {
                continue;
            }
            data.hearts -= 1;
            plugin.store().write(data);
            apply(player);
            player.sendMessage(Msg.parse(plugin.prefix()
                    + "<gray>Your heart cools: <red>-1 <gray>(" + data.hearts + "/" + max() + ")"));
        }
    }

    public List<PlayerData> top(int limit) {
        List<PlayerData> all = plugin.store().top();
        return all.subList(0, Math.min(limit, all.size()));
    }

    /** Applies the last-heart penalty after respawn, if one is pending. */
    public void applyPenalty(Player player) {
        if (!pendingPenalty.remove(player.getUniqueId())) {
            return;
        }
        if (!plugin.getConfig().getBoolean("hearts.min-penalty.enabled", true)) {
            return;
        }
        int seconds = Math.max(10, plugin.getConfig().getInt("hearts.min-penalty.seconds", 600));
        for (String raw : plugin.getConfig().getStringList("hearts.min-penalty.effects")) {
            String[] parts = raw.split(":");
            PotionEffectType type = PotionEffectType.getByName(parts[0].trim().toUpperCase());
            if (type == null) {
                continue;
            }
            int amplifier = parts.length > 1 ? Math.max(0, Integer.parseInt(parts[1].trim())) : 0;
            player.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, true, true, true));
        }
        player.sendMessage(Msg.parse(plugin.prefix() + "<dark_red>Weakened for <white>"
                + (seconds / 60) + " min<dark_red>. Do not die on your last heart."));
    }
}
