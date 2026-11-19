package ru.galeheart.arena;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.galeheart.arena.util.Msg;

/** Duel wiring: deaths, damage, joins, respawns. */
public final class ArenaListener implements Listener {

    private final ArenaPlugin plugin;

    public ArenaListener(ArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!plugin.duels().isInDuel(victim)) {
            return;
        }
        event.setDroppedExp(0);
        plugin.duels().handleDeath(victim, event.getDrops());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }
        plugin.duels().tag(victim);
        plugin.duels().tag(attacker);

        DuelManager.Duel duel = plugin.duels().duelOf(victim);
        if (duel != null && plugin.duels().duelOf(attacker) == duel) {
            plugin.duels().touch(duel);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.duels().onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.duels().onQuit(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        var back = plugin.duels().pendingReturn(event.getPlayer());
        if (back != null) {
            event.setRespawnLocation(back);
            plugin.duels().clearPendingReturn(event.getPlayer());
            event.getPlayer().sendMessage(Msg.parse(plugin.prefix() + "<gray>Returned from the arena."));
        }
    }
}
