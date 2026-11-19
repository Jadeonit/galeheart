package ru.galeheart.arena;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Consensual duels for GaleHeart SMP. */
public final class ArenaPlugin extends JavaPlugin {

    private DuelManager duels;
    private String prefix = "<gradient:#4FA9C9:#C8324B>[GaleHeart]</gradient> ";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        prefix = getConfig().getString("prefix", prefix);

        duels = new DuelManager(this);

        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);

        ArenaCommand executor = new ArenaCommand(this);
        for (String name : new String[]{"pvp", "safepvp", "arena"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(executor);
                getCommand(name).setTabCompleter(executor);
            }
        }

        Bukkit.getScheduler().runTaskTimer(this, duels::tick, 20L, 20L);

        getLogger().info("GaleHeartArena enabled.");
    }

    public DuelManager duels() {
        return duels;
    }

    public String prefix() {
        return prefix;
    }
}
