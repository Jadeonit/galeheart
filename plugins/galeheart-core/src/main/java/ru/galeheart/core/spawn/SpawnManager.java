package ru.galeheart.core.spawn;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import ru.galeheart.core.GaleHeartCore;

import java.util.Random;

/**
 * First-join scattering and the starter platform.
 * Nothing here is protected: players may break or improve it.
 */
public final class SpawnManager {

    private final GaleHeartCore plugin;
    private final Random random = new Random();

    public SpawnManager(GaleHeartCore plugin) {
        this.plugin = plugin;
        prepareWorld();
    }

    private void prepareWorld() {
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.SPAWN_RADIUS, plugin.getConfig().getInt("spawn.respawn-radius", 30));
        }
        if (plugin.getConfig().getBoolean("spawn.platform", true)) {
            buildPlatform(Bukkit.getWorlds().get(0));
        }
    }

    /** Small breakable platform at the world spawn, built once. */
    private void buildPlatform(World world) {
        Location spawn = world.getSpawnLocation();
        int x0 = spawn.getBlockX();
        int z0 = spawn.getBlockZ();
        int y0 = world.getHighestBlockYAt(x0, z0, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (!world.getBlockAt(x0, y0 + 1, z0).getType().isAir()) {
            return; // something is already here
        }
        int radius = Math.max(3, plugin.getConfig().getInt("spawn.platform-radius", 5));

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x0 + x, y0, z0 + z).setType(Material.STONE_BRICKS);
                if (random.nextInt(6) == 0) {
                    world.getBlockAt(x0 + x, y0 - 1, z0 + z).setType(Material.COBBLESTONE);
                }
            }
        }
        world.getBlockAt(x0 - radius, y0 + 1, z0 - radius).setType(Material.LANTERN);
        world.getBlockAt(x0 + radius, y0 + 1, z0 - radius).setType(Material.LANTERN);
        world.getBlockAt(x0 - radius, y0 + 1, z0 + radius).setType(Material.LANTERN);
        world.getBlockAt(x0 + radius, y0 + 1, z0 + radius).setType(Material.LANTERN);

        plugin.getLogger().info("Built the spawn platform at " + x0 + " " + y0 + " " + z0 + ".");
    }

    /** Teleports a first-time player to a random safe spot near the spawn. */
    public void scatter(Player player) {
        int radius = plugin.getConfig().getInt("spawn.first-join-radius", 250);
        if (radius <= 0) {
            return;
        }
        Location target = findSafeSpot(player.getWorld(), radius);
        if (target == null) {
            return;
        }
        player.teleport(target);
        player.sendMessage(ru.galeheart.core.util.Msg.parse(plugin.prefix()
                + "<gray>You wake up somewhere out in the wild. Good luck."));
    }

    private Location findSafeSpot(World world, int radius) {
        Location center = world.getSpawnLocation();
        int min = Math.max(48, radius / 2);

        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = min + random.nextDouble() * Math.max(1, radius - min);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                world.getChunkAt(x >> 4, z >> 4).load();
            }

            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = world.getBlockAt(x, y, z);
            if (ground.isLiquid() || !ground.getType().isSolid()) {
                continue;
            }
            Location target = new Location(world, x + 0.5, y + 1.0, z + 0.5, playerYaw(), 0);
            if (!target.getBlock().isPassable() || !target.clone().add(0, 1, 0).getBlock().isPassable()) {
                continue;
            }
            return target;
        }
        return null;
    }

    private float playerYaw() {
        return random.nextFloat() * 360.0f - 180.0f;
    }
}
