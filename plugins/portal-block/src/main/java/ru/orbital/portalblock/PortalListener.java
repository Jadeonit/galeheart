package ru.orbital.portalblock;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class PortalListener implements Listener {

    private final PortalBlockPlugin plugin;

    public PortalListener(PortalBlockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Stops the player at the portal edge, before the client shows the
     * purple portal overlay.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.config().enabled() || !plugin.config().blockMovement()) {
            return;
        }
        if (plugin.canBypass(player)) {
            return;
        }

        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || PortalUtil.intersectsPortal(from)) {
            return;
        }

        Material portal = PortalUtil.portalMaterialAt(to);
        if (portal == null) {
            return;
        }
        PluginConfig.Gate gate = plugin.config().gateFor(portal);
        if (gate == null || !gate.enabled() || plugin.allowed(player, gate)) {
            return;
        }

        event.setTo(event.getFrom());
        plugin.notifyBlocked(player, gate);
    }

    /**
     * Fallback: cancels the actual teleport (plugin teleports, players
     * already inside a portal on login, end portal entry, etc.).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!plugin.config().enabled()) {
            return;
        }
        if (plugin.canBypass(event.getPlayer())) {
            return;
        }

        TeleportCause cause = event.getCause();
        Material portal;
        if (cause == TeleportCause.NETHER_PORTAL) {
            portal = Material.NETHER_PORTAL;
        } else if (cause == TeleportCause.END_PORTAL) {
            portal = Material.END_PORTAL;
        } else {
            return;
        }

        PluginConfig.Gate gate = plugin.config().gateFor(portal);
        if (gate == null || !gate.enabled() || plugin.allowed(event.getPlayer(), gate)) {
            return;
        }

        event.setCancelled(true);
        plugin.notifyBlocked(event.getPlayer(), gate);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.forget(event.getPlayer());
    }
}
