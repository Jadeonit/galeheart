package ru.galeheart.arena;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import ru.galeheart.arena.util.Msg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Challenge lifecycle: request, accept, fight, end. */
public final class DuelManager {

    public enum Mode {SAFE, STAKE}

    public static final class Duel {
        public final UUID a;
        public final UUID b;
        public final Mode mode;
        public final Location returnA;
        public final Location returnB;
        public final long startedAt;
        public long lastDamage;
        public boolean ended;

        Duel(UUID a, UUID b, Mode mode, Location returnA, Location returnB, long startedAt) {
            this.a = a;
            this.b = b;
            this.mode = mode;
            this.returnA = returnA;
            this.returnB = returnB;
            this.startedAt = startedAt;
            this.lastDamage = startedAt;
        }

        public UUID other(UUID id) {
            return id.equals(a) ? b : a;
        }
    }

    private record Request(UUID from, Mode mode, long expiresAt) {
    }

    private final ArenaPlugin plugin;
    private final SnapshotStore snapshots;
    private final KitManager kits;

    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, Duel> active = new HashMap<>();
    private final Map<UUID, Long> combat = new HashMap<>();
    private final Map<UUID, Location> pendingReturn = new HashMap<>();
    private final Map<UUID, Long> disconnected = new HashMap<>();
    private final Map<UUID, int[]> stats = new HashMap<>();

    private World world;
    private Location spawnA;
    private Location spawnB;

    public DuelManager(ArenaPlugin plugin) {
        this.plugin = plugin;
        this.snapshots = new SnapshotStore(plugin);
        this.kits = new KitManager(plugin);
        loadWorld();
        loadStats();
    }

    // ---------------------------------------------------------------- world

    private void loadWorld() {
        String name = plugin.getConfig().getString("world", "arena");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name)
                    .generator(new VoidGenerator())
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .createWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("Could not create arena world '" + name + "'.");
            return;
        }
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setSpawnLocation(0, 64, 0);
        buildPlatform();

        spawnA = parseSpawn(plugin.getConfig().getString("spawns.a", "-8.5,64,0.5,90"), world);
        spawnB = parseSpawn(plugin.getConfig().getString("spawns.b", "8.5,64,0.5,-90"), world);
    }

    private void buildPlatform() {
        if (!world.getBlockAt(0, 63, 0).getType().isAir()) {
            return;
        }
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                world.getBlockAt(x, 63, z).setType(Material.SMOOTH_STONE);
                world.getBlockAt(x, 62, z).setType(Material.STONE);
            }
        }
        for (int x = -20; x <= 20; x++) {
            world.getBlockAt(x, 64, -20).setType(Material.GLASS);
            world.getBlockAt(x, 64, 20).setType(Material.GLASS);
        }
        for (int z = -20; z <= 20; z++) {
            world.getBlockAt(-20, 64, z).setType(Material.GLASS);
            world.getBlockAt(20, 64, z).setType(Material.GLASS);
        }
        plugin.getLogger().info("Built the arena platform.");
    }

    private static Location parseSpawn(String raw, World world) {
        String[] parts = raw.split(",");
        double x = parts.length > 0 ? Double.parseDouble(parts[0].trim()) : 0;
        double y = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 64;
        double z = parts.length > 2 ? Double.parseDouble(parts[2].trim()) : 0;
        float yaw = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 0;
        return new Location(world, x, y, z, yaw, 0);
    }

    // ---------------------------------------------------------------- requests

    public boolean request(Player from, Player to, Mode mode) {
        if (!ready(from) || !ready(to)) {
            return false;
        }
        if (from.equals(to)) {
            from.sendMessage(Msg.parse(plugin.prefix() + "<red>You cannot duel yourself."));
            return false;
        }
        if (mode == Mode.STAKE && plugin.getConfig().getBoolean("block-same-ip", true) && sameIp(from, to)) {
            from.sendMessage(Msg.parse(plugin.prefix() + "<red>Accounts on the same IP cannot stake-duel."));
            return false;
        }

        long seconds = plugin.getConfig().getLong("request-seconds", 30);
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), mode, System.currentTimeMillis() + seconds * 1000L));

        String label = mode == Mode.SAFE ? "safe duel" : "stake duel";
        from.sendMessage(Msg.parse(plugin.prefix() + "<gray>Challenge sent: <white>" + to.getName() + " <gray>(" + label + ")"));
        to.sendMessage(Msg.parse(plugin.prefix() + "<white>" + from.getName() + " <gray>challenges you to a <white>" + label
                + "<gray>. Type <white>/" + (mode == Mode.SAFE ? "safepvp" : "pvp") + " accept<gray> in " + seconds + "s."));
        return true;
    }

    public boolean accept(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(Msg.parse(plugin.prefix() + "<red>No pending challenge."));
            return false;
        }
        Player from = Bukkit.getPlayer(request.from());
        if (from == null) {
            target.sendMessage(Msg.parse(plugin.prefix() + "<red>That player is offline."));
            return false;
        }
        start(from, target, request.mode());
        return true;
    }

    public boolean deny(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null) {
            return false;
        }
        Player from = Bukkit.getPlayer(request.from());
        target.sendMessage(Msg.parse(plugin.prefix() + "<gray>Challenge declined."));
        if (from != null) {
            from.sendMessage(Msg.parse(plugin.prefix() + "<white>" + target.getName() + " <gray>declined."));
        }
        return true;
    }

    private boolean ready(Player player) {
        if (isInDuel(player)) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<red>You are already in a duel."));
            return false;
        }
        if (isTagged(player)) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<red>You are in combat. Wait a few seconds."));
            return false;
        }
        if (world == null) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<red>Arena is not configured."));
            return false;
        }
        return true;
    }

    private static boolean sameIp(Player a, Player b) {
        if (a.getAddress() == null || b.getAddress() == null) {
            return false;
        }
        return a.getAddress().getAddress().equals(b.getAddress().getAddress());
    }

    // ---------------------------------------------------------------- fight

    private void start(Player a, Player b, Mode mode) {
        if (world == null) {
            return;
        }
        Location returnA = a.getLocation().clone();
        Location returnB = b.getLocation().clone();

        if (mode == Mode.SAFE) {
            snapshots.save(a);
            snapshots.save(b);
            kits.give(a);
            kits.give(b);
        }

        heal(a);
        heal(b);
        a.teleport(spawnA);
        b.teleport(spawnB);

        Duel duel = new Duel(a.getUniqueId(), b.getUniqueId(), mode, returnA, returnB, System.currentTimeMillis());
        active.put(a.getUniqueId(), duel);
        active.put(b.getUniqueId(), duel);

        String label = mode == Mode.SAFE ? "<aqua>SAFE DUEL" : "<red>STAKE DUEL";
        a.sendMessage(Msg.parse(plugin.prefix() + label + "<gray> — fight!"));
        b.sendMessage(Msg.parse(plugin.prefix() + label + "<gray> — fight!"));
    }

    private void heal(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            player.setHealth(attribute.getValue());
        }
        player.setFoodLevel(20);
        player.setFireTicks(0);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
    }

    /** Called from PlayerDeathEvent. Moves drops to the winner in stake mode. */
    public void handleDeath(Player victim, List<ItemStack> drops) {
        Duel duel = active.get(victim.getUniqueId());
        if (duel == null) {
            return;
        }
        Player other = Bukkit.getPlayer(duel.other(victim.getUniqueId()));
        if (other != null && duel.mode == Mode.STAKE) {
            for (ItemStack drop : new ArrayList<>(drops)) {
                Map<Integer, ItemStack> leftover = other.getInventory().addItem(drop);
                for (ItemStack rest : leftover.values()) {
                    other.getWorld().dropItemNaturally(other.getLocation(), rest);
                }
            }
        }
        drops.clear();
        end(duel, other == null ? null : other.getUniqueId(), "death");
    }

    public void end(Duel duel, UUID winner, String reason) {
        if (duel.ended) {
            return;
        }
        duel.ended = true;
        active.remove(duel.a);
        active.remove(duel.b);

        Player a = Bukkit.getPlayer(duel.a);
        Player b = Bukkit.getPlayer(duel.b);

        if (duel.mode == Mode.SAFE) {
            if (a != null) {
                snapshots.restore(a);
            }
            if (b != null) {
                snapshots.restore(b);
            }
        }

        if (winner != null) {
            UUID loser = winner.equals(duel.a) ? duel.b : duel.a;
            addStat(winner, true);
            addStat(loser, false);
        }

        if (a != null) {
            sendHome(a, duel.returnA);
        }
        if (b != null) {
            sendHome(b, duel.returnB);
        }

        String summary = switch (reason) {
            case "afk" -> "Both players were idle. Duel cancelled.";
            case "disconnect" -> "Duel ended: opponent disconnected.";
            default -> winner == null ? "Duel ended." : "Duel ended.";
        };
        if (a != null) {
            a.sendMessage(Msg.parse(plugin.prefix() + "<gray>" + summary));
        }
        if (b != null) {
            b.sendMessage(Msg.parse(plugin.prefix() + "<gray>" + summary));
        }
    }

    public void forfeit(Player player) {
        Duel duel = active.get(player.getUniqueId());
        if (duel == null) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<red>You are not in a duel."));
            return;
        }
        end(duel, duel.other(player.getUniqueId()), "forfeit");
    }

    private void sendHome(Player player, Location location) {
        if (player.isDead() || !player.isOnline()) {
            pendingReturn.put(player.getUniqueId(), location);
            return;
        }
        player.teleport(location);
    }

    // ---------------------------------------------------------------- tick

    public void tick() {
        long now = System.currentTimeMillis();
        requests.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());

        long afkMs = plugin.getConfig().getLong("afk-seconds", 180) * 1000L;
        long dcMs = plugin.getConfig().getLong("disconnect-seconds", 30) * 1000L;

        for (Duel duel : new LinkedHashSet<>(active.values())) {
            if (duel.ended) {
                continue;
            }
            if (now - duel.lastDamage > afkMs) {
                end(duel, null, "afk");
                continue;
            }
            Long dcA = disconnected.get(duel.a);
            if (dcA != null && now - dcA > dcMs) {
                end(duel, duel.b, "disconnect");
                continue;
            }
            Long dcB = disconnected.get(duel.b);
            if (dcB != null && now - dcB > dcMs) {
                end(duel, duel.a, "disconnect");
            }
        }
    }

    public void tag(Player player) {
        combat.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean isTagged(Player player) {
        Long since = combat.get(player.getUniqueId());
        if (since == null) {
            return false;
        }
        long window = plugin.getConfig().getLong("combat-tag-seconds", 15) * 1000L;
        return System.currentTimeMillis() - since < window;
    }

    public boolean isInDuel(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    public Duel duelOf(Player player) {
        return active.get(player.getUniqueId());
    }

    public void touch(Duel duel) {
        if (duel != null) {
            duel.lastDamage = System.currentTimeMillis();
        }
    }

    // ---------------------------------------------------------------- join / quit

    public void onQuit(Player player) {
        if (isInDuel(player)) {
            disconnected.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    public void onJoin(Player player) {
        disconnected.remove(player.getUniqueId());

        if (snapshots.has(player.getUniqueId())) {
            snapshots.restore(player);
        }

        Duel duel = active.get(player.getUniqueId());
        if (duel != null && world != null) {
            boolean first = duel.a.equals(player.getUniqueId());
            player.teleport(first ? spawnA : spawnB);
        }

        Location back = pendingReturn.remove(player.getUniqueId());
        if (back != null) {
            Bukkit.getScheduler().runTask(plugin, () -> player.teleport(back));
        }
    }

    public Location pendingReturn(Player player) {
        return pendingReturn.get(player.getUniqueId());
    }

    public void clearPendingReturn(Player player) {
        pendingReturn.remove(player.getUniqueId());
    }

    public World world() {
        return world;
    }

    // ---------------------------------------------------------------- stats

    private File statsFile() {
        return new File(plugin.getDataFolder(), "stats.yml");
    }

    private void loadStats() {
        File file = statsFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                stats.put(id, new int[]{config.getInt(key + ".wins"), config.getInt(key + ".losses")});
            } catch (IllegalArgumentException ignored) {
                // skip malformed uuid
            }
        }
    }

    private void addStat(UUID id, boolean win) {
        int[] value = stats.computeIfAbsent(id, key -> new int[2]);
        if (win) {
            value[0]++;
        } else {
            value[1]++;
        }
        saveStats();
    }

    private void saveStats() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, int[]> entry : stats.entrySet()) {
            config.set(entry.getKey() + ".wins", entry.getValue()[0]);
            config.set(entry.getKey() + ".losses", entry.getValue()[1]);
        }
        try {
            config.save(statsFile());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save stats.yml: " + exception.getMessage());
        }
    }

    public int[] stats(Player player) {
        return stats.getOrDefault(player.getUniqueId(), new int[2]);
    }
}
