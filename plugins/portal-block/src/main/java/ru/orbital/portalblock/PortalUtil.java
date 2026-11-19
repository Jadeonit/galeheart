package ru.orbital.portalblock;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Portal block detection and safe-spot search.
 */
public final class PortalUtil {

    private static final double HALF_WIDTH = 0.31D;
    private static final double HEIGHT = 1.79D;

    private PortalUtil() {
    }

    /**
     * Material of a portal block the player's bounding box overlaps, or null.
     */
    public static Material portalMaterialAt(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        double minX = location.getX() - HALF_WIDTH;
        double maxX = location.getX() + HALF_WIDTH;
        double minY = location.getY() + 0.001D;
        double maxY = location.getY() + HEIGHT;
        double minZ = location.getZ() - HALF_WIDTH;
        double maxZ = location.getZ() + HALF_WIDTH;

        for (int x = floor(minX); x <= floor(maxX); x++) {
            for (int y = floor(minY); y <= floor(maxY); y++) {
                for (int z = floor(minZ); z <= floor(maxZ); z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type == Material.NETHER_PORTAL || type == Material.END_PORTAL) {
                        return type;
                    }
                }
            }
        }
        return null;
    }

    public static boolean intersectsPortal(Location location) {
        return portalMaterialAt(location) != null;
    }

    public static boolean isPortal(Material material) {
        return material == Material.NETHER_PORTAL || material == Material.END_PORTAL;
    }

    /**
     * Finds the nearest standable location outside any portal.
     *
     * @return a safe location, or {@code null} if none was found nearby
     */
    public static Location findSafeLocation(Location origin) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }

        Location best = null;
        double bestDistance = Double.MAX_VALUE;

        int originX = origin.getBlockX();
        int originY = origin.getBlockY();
        int originZ = origin.getBlockZ();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Block feet = world.getBlockAt(originX + dx, originY + dy, originZ + dz);
                    if (isPortal(feet.getType()) || !feet.isPassable()) {
                        continue;
                    }

                    Block head = feet.getRelative(BlockFace.UP);
                    if (isPortal(head.getType()) || !head.isPassable()) {
                        continue;
                    }

                    Block ground = feet.getRelative(BlockFace.DOWN);
                    if (!ground.getType().isSolid()) {
                        continue;
                    }

                    Location candidate = new Location(world,
                            feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D,
                            origin.getYaw(), origin.getPitch());

                    if (intersectsPortal(candidate)) {
                        continue;
                    }

                    double distance = candidate.distanceSquared(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
