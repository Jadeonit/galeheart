package ru.galeheart.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.util.Msg;

/** All gameplay event wiring. */
public final class CoreListener implements Listener {

    private final GaleHeartCore plugin;

    public CoreListener(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.store().get(player.getUniqueId());
        data.name = player.getName();

        if (data.firstJoin == 0L) {
            data.firstJoin = System.currentTimeMillis();
            plugin.combat().grantNewPlayerProtection(player);
            player.sendMessage(Msg.parse(plugin.prefix() + "<green>Welcome. You have <white>"
                    + plugin.getConfig().getInt("protection.new-player-hours", 24)
                    + "h<green> of protection — nobody can touch you, and you cannot attack players."));
        }

        plugin.hearts().apply(player);
        plugin.quests().grant(player);
        plugin.store().write(data);

        long left = plugin.combat().protectionLeftMillis(player);
        if (left > 0) {
            player.sendActionBar(Msg.parse("<gray>Protection: <white>" + (left / 3_600_000L) + "h left"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.store().save(plugin.store().get(player.getUniqueId()));
        plugin.combat().clear(player);
        plugin.quests().forget(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        plugin.hearts().onDeath(victim, victim.getKiller());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.hearts().apply(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (plugin.combat().isProtected(victim) || plugin.combat().isProtected(attacker)) {
            event.setCancelled(true);
            attacker.sendActionBar(Msg.parse("<red>Protected players cannot fight."));
            return;
        }
        plugin.combat().tag(victim);
        plugin.combat().tag(attacker);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!plugin.hearts().item().isHeart(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        plugin.hearts().consume(event.getPlayer(), event.getItem());
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        plugin.quests().onKill(killer, event.getEntityType());
    }
}
