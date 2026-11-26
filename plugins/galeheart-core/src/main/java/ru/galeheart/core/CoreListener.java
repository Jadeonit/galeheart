package ru.galeheart.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.galeheart.core.achievement.AchievementManager;
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
            plugin.store().save(data);
            player.sendMessage(Msg.parse(plugin.prefix() + "<green>Welcome. You have <white>"
                    + plugin.getConfig().getInt("protection.new-player-hours", 24)
                    + "h<green> of protection — nobody can touch you, and you cannot attack players."));
            Bukkit.getScheduler().runTask(plugin, () -> plugin.spawn().scatter(player));
            plugin.kit().give(player);
        }

        plugin.hearts().apply(player);
        plugin.quests().grant(player);
        plugin.bounty().ensureTarget(player);
        plugin.store().write(data);
        plugin.pass().playJoinEffect(player);
        plugin.achievements().check(player);

        long left = plugin.combat().protectionLeftMillis(player);
        if (left > 0) {
            player.sendActionBar(Msg.parse("<gray>Protection: <white>" + (left / 3_600_000L) + "h left"));
        }

        if (data.firstJoin != 0L && System.currentTimeMillis() - data.firstJoin > 60_000L
                && plugin.getConfig().getBoolean("chat.join-hint", true)) {
            String hint = plugin.getConfig().getString("chat.join-hint-text",
                    "<gray>Lifesteal + hostile weather. <white>/quest<gray> Nether and End, "
                            + "<white>/bounty<gray> targets, <white>/pass<gray> season, <white>/discord<gray> community.");
            player.sendMessage(Msg.parse(plugin.prefix() + hint));
        }

        if (plugin.getConfig().getBoolean("chat.alone-hint", true) && Bukkit.getOnlinePlayers().size() == 1) {
            String alone = plugin.getConfig().getString("chat.alone-hint-text",
                    "<gray>You are the only one online right now. This chat is bridged to Discord — "
                            + "someone there can answer you.");
            player.sendMessage(Msg.parse(plugin.prefix() + alone));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.store().save(plugin.store().get(player.getUniqueId()));
        plugin.combat().clear(player);
        plugin.quests().forget(player);
        plugin.bounty().onQuit(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (plugin.getConfig().getStringList("hearts.ignored-worlds").stream()
                .anyMatch(world -> world.equalsIgnoreCase(victim.getWorld().getName()))) {
            // duels and other side worlds do not move hearts or pay contracts
            return;
        }
        plugin.hearts().onDeath(victim, victim.getKiller());
        plugin.bounty().handleDeath(victim, victim.getKiller());
        plugin.achievements().increment(victim, AchievementManager.Counter.DEATH, 1);
        if (victim.getKiller() != null && !victim.getKiller().equals(victim)) {
            plugin.pass().onKill(victim.getKiller(), victim);
            plugin.achievements().increment(victim.getKiller(), AchievementManager.Counter.KILL, 1);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.hearts().apply(event.getPlayer());
            plugin.hearts().applyPenalty(event.getPlayer());
        });
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
        plugin.achievements().increment(killer, AchievementManager.Counter.MOB, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        plugin.achievements().increment(player, AchievementManager.Counter.BLOCK, 1);
        Material type = event.getBlock().getType();
        if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE) {
            plugin.achievements().increment(player, AchievementManager.Counter.DIAMOND, 1);
        }
    }

    @EventHandler
    public void onExp(PlayerExpChangeEvent event) {
        plugin.pass().addVanillaXp(event.getPlayer(), event.getAmount());
    }
}
