package ru.anomaly.weather.anomaly;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import ru.anomaly.weather.AnomalousWeatherPlugin;
import ru.anomaly.weather.notify.DiscordNotifier;
import ru.anomaly.weather.protection.ProtectionManager;
import ru.anomaly.weather.util.Messages;
import ru.anomaly.weather.util.Msg;
import ru.anomaly.weather.util.TimeUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Ядро плагина: выбирает аномалию, тикает её эффекты и показывает её игрокам.
 * Ванильная погода по умолчанию не изменяется — аномалия работает поверх неё.
 */
public final class AnomalyManager {

    private static final long TICK_PERIOD_SECONDS = 1L;

    private final AnomalousWeatherPlugin plugin;
    private final ProtectionManager protection;
    private final Messages messages;
    private final DiscordNotifier notifier;
    private final Random random = new Random();

    private final Map<AnomalyType, WeatherSettings> settings = new EnumMap<>(AnomalyType.class);
    private final Map<World, SavedWeather> savedWeather = new HashMap<>();
    private final Set<UUID> fakeWeatherPlayers = new HashSet<>();
    private final Set<UUID> bossBarViewers = new HashSet<>();
    private final Set<UUID> pendingBossBarViewers = new HashSet<>();
    private final Set<String> worlds = new HashSet<>();

    private BukkitTask task;
    private ActiveAnomaly active;
    private BossBar bossBar;
    private PendingAnomaly pending;
    private BossBar pendingBossBar;
    private final Set<Integer> firedWarnings = new HashSet<>();

    private boolean requireVanillaRain;
    private boolean overrideVanillaWeather;
    private boolean visualFakeWeather;
    private boolean announceStart;
    private boolean announceEnd;
    private boolean announceTitle;
    private boolean bossbarEnabled;
    private boolean warnWithoutProtection;
    private boolean ignoreCreative;
    private boolean ignoreSpectator;
    private String immunityPermission = "";
    private int checkInterval;
    private int anomalyChance;
    private int minPlayers;
    private int minCooldown;
    private int maxCooldown;
    private int effectRadius;
    private int warningInterval;
    private boolean warnEnabled;
    private int warnLeadSeconds;
    private int[] warnThresholds = new int[0];
    private boolean warnBossbar;
    private boolean warnSoundEnabled;
    private String warnSoundKey;
    private float warnSoundVolume;
    private float warnSoundPitch;

    private long nextCheckAt = Long.MAX_VALUE;

    public AnomalyManager(AnomalousWeatherPlugin plugin) {
        this.plugin = plugin;
        this.protection = plugin.protectionManager();
        this.messages = plugin.messages();
        this.notifier = new DiscordNotifier(plugin);
    }

    // ------------------------------------------------------------------
    // Загрузка конфига и запуск планировщика
    // ------------------------------------------------------------------

    public void load() {
        if (active != null) {
            endAnomaly(false);
        }
        cancelPending();
        FileConfiguration config = plugin.getConfig();

        worlds.clear();
        for (String world : config.getStringList("settings.worlds")) {
            worlds.add(world.toLowerCase(Locale.ROOT));
        }
        if (worlds.isEmpty()) {
            worlds.add("*");
        }

        requireVanillaRain = config.getBoolean("settings.require-vanilla-rain", false);
        overrideVanillaWeather = config.getBoolean("settings.override-vanilla-weather", false);
        visualFakeWeather = config.getBoolean("settings.visual-fake-weather", true);
        checkInterval = Math.max(5, config.getInt("settings.check-interval-seconds", 90));
        anomalyChance = Math.max(0, Math.min(100, config.getInt("settings.anomaly-chance-percent", 35)));
        minPlayers = Math.max(0, config.getInt("settings.min-players", 1));
        minCooldown = Math.max(1, config.getInt("settings.cooldown.min-seconds", 180));
        maxCooldown = Math.max(minCooldown, config.getInt("settings.cooldown.max-seconds", 480));
        int globalMinDuration = Math.max(30, config.getInt("settings.duration.min-seconds", 120));
        int globalMaxDuration = Math.max(globalMinDuration, config.getInt("settings.duration.max-seconds", 240));
        effectRadius = Math.max(4, config.getInt("settings.effect-radius", 12));
        ignoreCreative = config.getBoolean("settings.ignore-creative", true);
        ignoreSpectator = config.getBoolean("settings.ignore-spectator", true);
        immunityPermission = config.getString("settings.immunity-permission", "");
        announceStart = config.getBoolean("settings.announce.start-message", true);
        announceEnd = config.getBoolean("settings.announce.end-message", true);
        announceTitle = config.getBoolean("settings.announce.title", true);
        bossbarEnabled = config.getBoolean("settings.announce.bossbar", true);
        warnWithoutProtection = config.getBoolean("settings.announce.actionbar-warning", true)
                && config.getBoolean("protection.warn-without-protection", true);
        warningInterval = Math.max(1, config.getInt("settings.warning-interval-seconds", 10));

        warnEnabled = config.getBoolean("settings.warning.enabled", true);
        warnLeadSeconds = Math.max(0, config.getInt("settings.warning.lead-seconds", 3600));
        warnBossbar = config.getBoolean("settings.warning.bossbar", true);
        warnSoundEnabled = config.getBoolean("settings.warning.sound.enabled", true);
        warnSoundKey = config.getString("settings.warning.sound.key", "minecraft:block.note_block.bell");
        warnSoundVolume = (float) config.getDouble("settings.warning.sound.volume", 0.8);
        warnSoundPitch = (float) config.getDouble("settings.warning.sound.pitch", 1.1);
        List<Integer> thresholds = new ArrayList<>();
        for (int value : config.getIntegerList("settings.warning.announce-at-seconds")) {
            if (value > 0 && value <= warnLeadSeconds) {
                thresholds.add(value);
            }
        }
        thresholds.sort(Collections.reverseOrder());
        warnThresholds = thresholds.stream().mapToInt(Integer::intValue).toArray();

        settings.clear();
        for (AnomalyType type : AnomalyType.values()) {
            WeatherSettings weatherSettings = WeatherSettings.from(plugin, type, globalMinDuration, globalMaxDuration);
            if (weatherSettings != null) {
                settings.put(type, weatherSettings);
            }
        }

        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L * TICK_PERIOD_SECONDS);
        }
        nextCheckAt = System.currentTimeMillis() + checkInterval * 1000L;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        active = null;
        hideBossBar();
        pending = null;
        firedWarnings.clear();
        removePendingBossBar();
        restoreVanillaWeather();
        resetFakeWeather();
    }

    // ------------------------------------------------------------------
    // Основной тик
    // ------------------------------------------------------------------

    private void tick() {
        if (active == null) {
            if (pending != null) {
                tickPending();
                return;
            }
            if (System.currentTimeMillis() >= nextCheckAt) {
                nextCheckAt = System.currentTimeMillis() + checkInterval * 1000L;
                tryStartRandom();
            }
            return;
        }

        active.tick();

        if (requireVanillaRain && !overrideVanillaWeather && !isVanillaRainActive()) {
            endAnomaly(true);
            return;
        }
        if (active.finished()) {
            endAnomaly(true);
            return;
        }

        updateBossBar();
        tickPlayers();
        if (active.every(active.settings().damageInterval)) {
            damageMobs(active.settings());
        }
    }

    private void tryStartRandom() {
        if (active != null) {
            return;
        }
        // Пустой сервер не должен сжигать аномалию и кулдаун. 0 — проверка выключена.
        if (minPlayers > 0 && Bukkit.getOnlinePlayers().size() < minPlayers) {
            return;
        }
        if (requireVanillaRain && !overrideVanillaWeather && !isVanillaRainActive()) {
            return;
        }
        if (random.nextDouble() * 100.0 >= anomalyChance) {
            return;
        }
        WeatherSettings picked = pickWeighted();
        if (picked != null) {
            scheduleAnomaly(picked);
        }
    }

    /**
     * Ставит аномалию в очередь: сначала идёт обратный отсчёт в BossBar
     * и анонсы, через lead-seconds она начнётся сама.
     */
    private void scheduleAnomaly(WeatherSettings weatherSettings) {
        if (!warnEnabled || warnLeadSeconds <= 0) {
            startAnomaly(weatherSettings.type, 0);
            return;
        }
        long now = System.currentTimeMillis();
        pending = new PendingAnomaly(weatherSettings, now + warnLeadSeconds * 1000L, warnLeadSeconds);
        firedWarnings.clear();
        if (warnBossbar) {
            createPendingBossBar();
        }
        plugin.getLogger().info("Scheduled " + weatherSettings.type.name()
                + " in " + TimeUtil.format(warnLeadSeconds));
        notifier.send("Weather forecast",
                "In " + Math.max(1, warnLeadSeconds / 60) + " min: " + weatherSettings.displayName,
                0x3498DB);
        warnIfDue(pending.remainingSeconds());
    }

    /** Тик обратного отсчёта: обновляет BossBar, рассылает анонсы, стартует аномалию. */
    private void tickPending() {
        PendingAnomaly current = pending;
        if (current == null) {
            return;
        }
        int remaining = current.remainingSeconds();
        if (remaining > 0) {
            updatePendingBossBar(remaining);
            warnIfDue(remaining);
            return;
        }

        WeatherSettings weatherSettings = current.settings();
        pending = null;
        removePendingBossBar();
        firedWarnings.clear();

        if (minPlayers > 0 && Bukkit.getOnlinePlayers().size() < minPlayers) {
            plugin.getLogger().info("Anomaly " + weatherSettings.type.name() + " cancelled: no players online.");
            nextCheckAt = System.currentTimeMillis() + checkInterval * 1000L;
            return;
        }
        if (requireVanillaRain && !overrideVanillaWeather && !isVanillaRainActive()) {
            plugin.getLogger().info("Anomaly " + weatherSettings.type.name() + " cancelled: no vanilla rain.");
            nextCheckAt = System.currentTimeMillis() + checkInterval * 1000L;
            return;
        }
        startAnomaly(weatherSettings.type, 0);
    }

    /** Пишет в чат и играет звук на каждой отметке из announce-at-seconds. */
    private void warnIfDue(int remaining) {
        if (pending == null || warnThresholds.length == 0) {
            return;
        }
        for (int threshold : warnThresholds) {
            if (remaining > threshold || !firedWarnings.add(threshold)) {
                continue;
            }
            broadcast(messages.get("anomaly-warning",
                    "weather", pending.settings().displayName,
                    "time", TimeUtil.format(remaining)));
            if (warnSoundEnabled) {
                for (Player player : eligiblePlayers()) {
                    playSound(player, warnSoundKey, warnSoundVolume, warnSoundPitch);
                }
            }
        }
    }

    private void createPendingBossBar() {
        if (pending == null) {
            return;
        }
        pendingBossBar = BossBar.bossBar(pendingBossBarName(pending.remainingSeconds()), 1.0f,
                pending.settings().bossbarColor, BossBar.Overlay.PROGRESS);
        pendingBossBarViewers.clear();
        for (Player player : eligiblePlayers()) {
            pendingBossBarViewers.add(player.getUniqueId());
            pendingBossBar.addViewer(player);
        }
    }

    private void updatePendingBossBar(int remaining) {
        if (pendingBossBar == null) {
            return;
        }
        pendingBossBar.name(pendingBossBarName(remaining));
        float progress = warnLeadSeconds <= 0 ? 0.0f : (float) remaining / (float) warnLeadSeconds;
        pendingBossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (eligible(player)) {
                if (pendingBossBarViewers.add(id)) {
                    pendingBossBar.addViewer(player);
                }
            } else if (pendingBossBarViewers.remove(id)) {
                pendingBossBar.removeViewer(player);
            }
        }
    }

    private void removePendingBossBar() {
        if (pendingBossBar == null) {
            pendingBossBarViewers.clear();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            pendingBossBar.removeViewer(player);
        }
        pendingBossBarViewers.clear();
        pendingBossBar = null;
    }

    private Component pendingBossBarName(int remaining) {
        if (pending == null) {
            return Component.empty();
        }
        return Msg.parse(pending.settings().displayName)
                .append(Msg.parse(" <dark_gray>| <gray>in <white>" + TimeUtil.format(remaining)));
    }

    /** Отменяет запланированную аномалию и убирает её отсчёт. */
    public void cancelPending() {
        if (pending == null && pendingBossBar == null) {
            return;
        }
        if (pending != null) {
            plugin.getLogger().info("Scheduled anomaly " + pending.type().name() + " cancelled.");
        }
        pending = null;
        firedWarnings.clear();
        removePendingBossBar();
    }

    public PendingAnomaly pending() {
        return pending;
    }

    private WeatherSettings pickWeighted() {
        double total = totalWeight();
        if (total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        WeatherSettings last = null;
        for (WeatherSettings candidate : settings.values()) {
            if (!candidate.enabled || candidate.chance <= 0) {
                continue;
            }
            last = candidate;
            roll -= candidate.chance;
            if (roll <= 0) {
                return candidate;
            }
        }
        return last;
    }

    // ------------------------------------------------------------------
    // Запуск и остановка аномалии
    // ------------------------------------------------------------------

    /** Запускает аномалию. Если seconds <= 0, длительность берётся случайно из настроек. */
    public boolean startAnomaly(AnomalyType type, int seconds) {
        WeatherSettings weatherSettings = settings.get(type);
        if (weatherSettings == null) {
            return false;
        }
        if (active != null) {
            endAnomaly(false);
        }
        cancelPending();
        int duration = seconds > 0 ? seconds : randomBetween(weatherSettings.minDuration, weatherSettings.maxDuration);
        active = new ActiveAnomaly(type, weatherSettings, duration);

        applyVanillaWeather();
        showBossBar();
        syncViewers();

        if (announceStart) {
            broadcast(messages.get("anomaly-start", "weather", weatherSettings.displayName, "time", TimeUtil.format(duration)));
        }
        if (announceTitle) {
            showStartTitle(weatherSettings);
        }

        plugin.getLogger().info("Anomaly " + type.name() + " started (" + duration + "s)");
        notifier.send("Anomaly started", weatherSettings.displayName, 0x9B59B6);
        return true;
    }

    public void stopAnomaly() {
        if (active != null) {
            endAnomaly(true);
            return;
        }
        cancelPending();
    }

    private void endAnomaly(boolean announce) {
        ActiveAnomaly finished = active;
        active = null;

        if (finished != null) {
            if (announce && announceEnd) {
                broadcast(messages.get("anomaly-end", "weather", finished.settings().displayName));
            }
            plugin.getLogger().info("Anomaly " + finished.type().name() + " ended.");
            notifier.send("Anomaly ended", finished.settings().displayName, 0x95A5A6);
        }

        hideBossBar();
        restoreVanillaWeather();
        resetFakeWeather();
        nextCheckAt = System.currentTimeMillis() + randomBetween(minCooldown, maxCooldown) * 1000L;
    }

    // ------------------------------------------------------------------
    // Эффекты по игрокам
    // ------------------------------------------------------------------

    private void tickPlayers() {
        ActiveAnomaly current = active;
        if (current == null) {
            return;
        }
        WeatherSettings weatherSettings = current.settings();
        boolean damageTick = weatherSettings.damageAmount > 0 && current.every(weatherSettings.damageInterval);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!eligible(player)) {
                continue;
            }
            boolean exposed = !weatherSettings.requireSky || isExposed(player.getLocation());
            if (exposed) {
                spawnParticles(player, weatherSettings);
            }
            playAmbient(player);

            if (!exposed || (weatherSettings.waterProtects && player.isInWater())) {
                continue;
            }

            double reduction = protection.reduction(player, weatherSettings.type);
            boolean effectsBlocked = protection.blocksEffects(player, weatherSettings.type);

            if (damageTick) {
                double damage = weatherSettings.damageAmount * (1.0 - reduction);
                if (damage > 0) {
                    player.damage(damage);
                }
            }

            if (!effectsBlocked) {
                for (PotionSpec spec : weatherSettings.effects) {
                    player.addPotionEffect(new PotionEffect(spec.type(), spec.durationTicks(), spec.amplifier(), true, true, true));
                }
            }

            if (weatherSettings.armorDamage > 0 && reduction < 1.0) {
                int amount = Math.max(1, (int) Math.round(weatherSettings.armorDamage * (1.0 - reduction)));
                damageArmor(player, amount);
            }

            switch (weatherSettings.type) {
                case FIRE_RAIN -> {
                    if (weatherSettings.fireTicks > 0 && reduction < 1.0) {
                        player.setFireTicks(Math.max(player.getFireTicks(), weatherSettings.fireTicks));
                    }
                }
                case BLIZZARD -> {
                    if (weatherSettings.freezeTicks > 0 && reduction < 1.0) {
                        player.setFreezeTicks(Math.min(200, player.getFreezeTicks() + weatherSettings.freezeTicks));
                    }
                }
                case THUNDERSTORM -> tickLightning(player, weatherSettings, reduction);
                case VOID_STORM -> tickTeleport(player, weatherSettings, reduction);
                default -> {
                }
            }

            if (warnWithoutProtection && reduction < 1.0 && current.every(warningInterval)) {
                player.sendActionBar(messages.get("actionbar-warning",
                        "weather", weatherSettings.displayName,
                        "protection", protection.itemName(weatherSettings.type)));
            }
        }
    }

    private void tickLightning(Player player, WeatherSettings weatherSettings, double reduction) {
        if (!weatherSettings.lightningEnabled || reduction >= 1.0) {
            return;
        }
        if (!active.every(weatherSettings.lightningInterval)) {
            return;
        }
        if (random.nextDouble() * 100.0 >= weatherSettings.lightningChance) {
            return;
        }
        Location location = player.getLocation();
        if (weatherSettings.lightningReal) {
            location.getWorld().strikeLightning(location);
        } else {
            location.getWorld().strikeLightningEffect(location);
        }
        if (weatherSettings.lightningDamage > 0) {
            player.damage(weatherSettings.lightningDamage * (1.0 - reduction));
        }
        if (weatherSettings.lightningFireTicks > 0) {
            player.setFireTicks(Math.max(player.getFireTicks(), weatherSettings.lightningFireTicks));
        }
    }

    private void tickTeleport(Player player, WeatherSettings weatherSettings, double reduction) {
        if (!weatherSettings.teleportEnabled || reduction >= 1.0) {
            return;
        }
        if (!active.every(weatherSettings.teleportInterval)) {
            return;
        }
        if (random.nextDouble() * 100.0 >= weatherSettings.teleportChance) {
            return;
        }
        Location target = randomTeleportLocation(player.getLocation(), weatherSettings.teleportRadius);
        if (target == null) {
            return;
        }
        World world = player.getWorld();
        world.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
        player.teleport(target);
        world.spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
        playSound(player, weatherSettings.teleportSound, 0.9f, 0.8f);
        if (weatherSettings.teleportDamage > 0) {
            player.damage(weatherSettings.teleportDamage * (1.0 - reduction));
        }
    }

    private Location randomTeleportLocation(Location base, int radius) {
        World world = base.getWorld();
        if (world == null) {
            return null;
        }
        for (int attempt = 0; attempt < 14; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if (Math.abs(dx) + Math.abs(dz) < 5) {
                continue;
            }
            int x = base.getBlockX() + dx;
            int z = base.getBlockZ() + dz;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (y <= world.getMinHeight()) {
                continue;
            }
            // Не телепортировать в лаву, огонь, кактус, порошковый снег и прочую гадость.
            if (isHazard(world.getBlockAt(x, y, z).getType())
                    || isHazard(world.getBlockAt(x, y - 1, z).getType())) {
                continue;
            }
            Location target = new Location(world, x + 0.5, y + 1.0, z + 0.5, base.getYaw(), base.getPitch());
            if (target.getBlock().isPassable() && target.clone().add(0, 1, 0).getBlock().isPassable()) {
                return target;
            }
        }
        return null;
    }

    private void damageMobs(WeatherSettings weatherSettings) {
        if (!weatherSettings.affectMobs || weatherSettings.damageAmount <= 0) {
            return;
        }
        Set<UUID> alreadyHit = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!eligible(player)) {
                continue;
            }
            for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), effectRadius, effectRadius, effectRadius)) {
                if (!(entity instanceof LivingEntity living) || entity instanceof Player) {
                    continue;
                }
                if (!alreadyHit.add(entity.getUniqueId())) {
                    continue;
                }
                if (weatherSettings.requireSky && !isExposed(living.getLocation())) {
                    continue;
                }
                living.damage(weatherSettings.damageAmount);
                if (weatherSettings.fireTicks > 0) {
                    living.setFireTicks(Math.max(living.getFireTicks(), weatherSettings.fireTicks));
                }
            }
        }
    }

    private void damageArmor(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] armor = inventory.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack stack = armor[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            short maxDurability = stack.getType().getMaxDurability();
            if (maxDurability <= 0) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof Damageable damageable)) {
                continue;
            }
            // Незебоевые вещи и защитные предметы других аномалий не ломаем.
            if (damageable.isUnbreakable()) {
                continue;
            }
            int newDamage = damageable.getDamage() + amount;
            if (newDamage >= maxDurability) {
                armor[i] = new ItemStack(Material.AIR);
                playSound(player, "minecraft:entity.item.break", 0.8f, 1.0f);
            } else {
                damageable.setDamage(newDamage);
                stack.setItemMeta(damageable);
            }
            changed = true;
        }
        if (changed) {
            inventory.setArmorContents(armor);
        }
    }

    // ------------------------------------------------------------------
    // Визуал: частицы, звуки, BossBar, тайтл, "личная" погода
    // ------------------------------------------------------------------

    private void spawnParticles(Player player, WeatherSettings weatherSettings) {
        ParticleSpec spec = weatherSettings.particle;
        if (spec == null) {
            return;
        }
        Location location = player.getLocation().add(0, 1.2, 0);
        World world = player.getWorld();
        double radius = spec.radius();
        if (spec.needsData()) {
            world.spawnParticle(spec.particle(), location, spec.count(), radius, 2.2, radius, 0.0,
                    new Particle.DustOptions(spec.bukkitColor(), spec.size()));
        } else {
            world.spawnParticle(spec.particle(), location, spec.count(), radius, 2.2, radius, 0.02);
        }
    }

    private void playAmbient(Player player) {
        WeatherSettings weatherSettings = active == null ? null : active.settings();
        if (weatherSettings == null || weatherSettings.sound == null) {
            return;
        }
        SoundSpec spec = weatherSettings.sound;
        if (!active.every(spec.intervalSeconds())) {
            return;
        }
        playSound(player, spec.key(), spec.volume(), spec.pitch());
    }

    private void playSound(Player player, String soundKey, float volume, float pitch) {
        if (soundKey == null || soundKey.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(soundKey), Sound.Source.AMBIENT, volume, pitch));
        } catch (Throwable ignored) {
            // некорректный ключ звука в конфиге — просто пропускаем
        }
    }

    private void showStartTitle(WeatherSettings weatherSettings) {
        Title title = Title.title(
                Msg.parse(weatherSettings.title),
                Msg.parse(weatherSettings.subtitle),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))
        );
        for (Player player : eligiblePlayers()) {
            player.showTitle(title);
        }
    }

    private void showBossBar() {
        if (!bossbarEnabled || active == null) {
            return;
        }
        bossBar = BossBar.bossBar(bossBarName(), 1.0f, active.settings().bossbarColor, BossBar.Overlay.PROGRESS);
        syncViewers();
    }

    private Component bossBarName() {
        if (active == null) {
            return Component.empty();
        }
        return Msg.parse(active.settings().displayName)
                .append(Msg.parse(" <dark_gray>| <gray>осталось <white>" + TimeUtil.format(active.remaining())));
    }

    private void updateBossBar() {
        if (bossBar == null || active == null) {
            return;
        }
        bossBar.name(bossBarName());
        bossBar.progress(Math.max(0.0f, (float) active.remaining() / (float) active.duration()));
        syncViewers();
    }

    private void hideBossBar() {
        if (bossBar == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.removeViewer(player);
        }
        bossBarViewers.clear();
        bossBar = null;
    }

    /** Синхронизирует BossBar и "личную" погоду у всех игроков. */
    private void syncViewers() {
        if (active == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayer(player);
        }
    }

    public void syncPlayer(Player player) {
        UUID id = player.getUniqueId();
        if (active == null) {
            if (bossBar != null && bossBarViewers.remove(id)) {
                bossBar.removeViewer(player);
            }
            if (fakeWeatherPlayers.remove(id)) {
                player.resetPlayerWeather();
            }
            if (pendingBossBar != null) {
                if (eligible(player)) {
                    if (pendingBossBarViewers.add(id)) {
                        pendingBossBar.addViewer(player);
                    }
                } else if (pendingBossBarViewers.remove(id)) {
                    pendingBossBar.removeViewer(player);
                }
            }
            return;
        }

        boolean ok = eligible(player);
        if (bossBar != null) {
            if (ok) {
                if (bossBarViewers.add(id)) {
                    bossBar.addViewer(player);
                }
            } else if (bossBarViewers.remove(id)) {
                bossBar.removeViewer(player);
            }
        }

        WeatherType fake = active.settings().fakeWeather;
        if (ok && visualFakeWeather && fake != null) {
            if (fakeWeatherPlayers.add(id)) {
                player.setPlayerWeather(fake);
            }
        } else if (fakeWeatherPlayers.remove(id)) {
            player.resetPlayerWeather();
        }
    }

    public void handleJoin(Player player) {
        syncPlayer(player);
        if (active == null) {
            return;
        }
        if (eligible(player)) {
            player.sendActionBar(messages.get("anomaly-status",
                    "weather", active.settings().displayName,
                    "time", TimeUtil.format(active.remaining())));
        }
    }

    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        fakeWeatherPlayers.remove(id);
        if (bossBar != null && bossBarViewers.remove(id)) {
            bossBar.removeViewer(player);
        }
        if (pendingBossBar != null && pendingBossBarViewers.remove(id)) {
            pendingBossBar.removeViewer(player);
        }
    }

    private void resetFakeWeather() {
        if (fakeWeatherPlayers.isEmpty()) {
            return;
        }
        for (UUID id : fakeWeatherPlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.resetPlayerWeather();
            }
        }
        fakeWeatherPlayers.clear();
    }

    // ------------------------------------------------------------------
    // Ванильная погода (не трогаем, если override-vanilla-weather: false)
    // ------------------------------------------------------------------

    private void applyVanillaWeather() {
        if (!overrideVanillaWeather || active == null) {
            return;
        }
        savedWeather.clear();
        boolean thunder = active.settings().vanillaThunder;
        for (World world : Bukkit.getWorlds()) {
            if (!worldEnabled(world)) {
                continue;
            }
            savedWeather.put(world, new SavedWeather(
                    world.hasStorm(),
                    world.isThundering(),
                    world.getWeatherDuration(),
                    world.getThunderDuration()
            ));
            world.setStorm(true);
            world.setThundering(thunder);
            world.setWeatherDuration(active.duration() * 20 + 200);
            world.setThunderDuration(active.duration() * 20 + 200);
        }
    }

    private void restoreVanillaWeather() {
        if (savedWeather.isEmpty()) {
            return;
        }
        for (Map.Entry<World, SavedWeather> entry : savedWeather.entrySet()) {
            World world = entry.getKey();
            SavedWeather saved = entry.getValue();
            world.setStorm(saved.storm());
            world.setThundering(saved.thundering());
            world.setWeatherDuration(saved.weatherDuration());
            world.setThunderDuration(saved.thunderDuration());
        }
        savedWeather.clear();
    }

    private boolean isVanillaRainActive() {
        for (World world : Bukkit.getWorlds()) {
            if (worldEnabled(world) && world.hasStorm()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    public boolean eligible(Player player) {
        if (player.isDead() || !player.isOnline()) {
            return false;
        }
        if (!worldEnabled(player.getWorld())) {
            return false;
        }
        GameMode gameMode = player.getGameMode();
        if (ignoreSpectator && gameMode == GameMode.SPECTATOR) {
            return false;
        }
        if (ignoreCreative && gameMode == GameMode.CREATIVE) {
            return false;
        }
        if (!immunityPermission.isBlank() && player.hasPermission(immunityPermission)) {
            return false;
        }
        if (requireVanillaRain && !overrideVanillaWeather) {
            return player.getWorld().hasStorm();
        }
        return true;
    }

    private List<Player> eligiblePlayers() {
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (eligible(player)) {
                result.add(player);
            }
        }
        return result;
    }

    private void broadcast(Component component) {
        for (Player player : eligiblePlayers()) {
            player.sendMessage(component);
        }
    }

    public boolean worldEnabled(World world) {
        if (world == null) {
            return false;
        }
        return worlds.contains("*") || worlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    /** Находится ли точка под открытым небом (нет блоков над головой). */
    public static boolean isExposed(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        // Листва не считается укрытием: под кроной дерева аномалия достаёт.
        int highest = world.getHighestBlockYAt(location, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return highest <= location.getBlockY();
    }

    /** Блок, в который нельзя телепортировать игрока. */
    private static boolean isHazard(Material material) {
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, SWEET_BERRY_BUSH,
                 POWDER_SNOW, CAMPFIRE, SOUL_CAMPFIRE, WITHER_ROSE,
                 NETHER_PORTAL, END_PORTAL -> true;
            default -> false;
        };
    }

    private int randomBetween(int min, int max) {
        if (max <= min) {
            return Math.max(5, min);
        }
        return min + random.nextInt(max - min + 1);
    }

    public double totalWeight() {
        double total = 0.0;
        for (WeatherSettings weatherSettings : settings.values()) {
            if (weatherSettings.enabled && weatherSettings.chance > 0) {
                total += weatherSettings.chance;
            }
        }
        return total;
    }

    /** Итоговый шанс аномалии (в %) среди всех включённых аномалий. */
    public double effectiveChance(AnomalyType type) {
        WeatherSettings weatherSettings = settings.get(type);
        if (weatherSettings == null || !weatherSettings.enabled) {
            return 0.0;
        }
        double total = totalWeight();
        return total <= 0 ? 0.0 : weatherSettings.chance / total * 100.0;
    }

    public Collection<WeatherSettings> settings() {
        return settings.values();
    }

    public WeatherSettings settingsOf(AnomalyType type) {
        return settings.get(type);
    }

    public ActiveAnomaly active() {
        return active;
    }

    public long secondsUntilNextCheck() {
        return Math.max(0, (nextCheckAt - System.currentTimeMillis()) / 1000L);
    }

    private record SavedWeather(boolean storm, boolean thundering, int weatherDuration, int thunderDuration) {
    }

    /** Запланированная аномалия с обратным отсчётом до старта. */
    public static final class PendingAnomaly {

        private final WeatherSettings settings;
        private final long startAtMillis;
        private final int leadSeconds;

        PendingAnomaly(WeatherSettings settings, long startAtMillis, int leadSeconds) {
            this.settings = settings;
            this.startAtMillis = startAtMillis;
            this.leadSeconds = leadSeconds;
        }

        public WeatherSettings settings() {
            return settings;
        }

        public AnomalyType type() {
            return settings.type;
        }

        public int leadSeconds() {
            return leadSeconds;
        }

        /** Сколько секунд осталось до старта (округление вверх, не меньше нуля). */
        public int remainingSeconds() {
            long remaining = startAtMillis - System.currentTimeMillis();
            return (int) Math.max(0, (remaining + 999L) / 1000L);
        }
    }
}
