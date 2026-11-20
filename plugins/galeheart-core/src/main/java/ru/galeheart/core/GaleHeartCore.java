package ru.galeheart.core;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.galeheart.core.combat.CombatManager;
import ru.galeheart.core.bounty.BountyManager;
import ru.galeheart.core.chat.ChatListener;
import ru.galeheart.core.chat.ChatManager;
import ru.galeheart.core.command.GaleCommand;
import ru.galeheart.core.data.DataStore;
import ru.galeheart.core.hearts.HeartManager;
import ru.galeheart.core.notify.DiscordNotifier;
import ru.galeheart.core.onboarding.StarterKit;
import ru.galeheart.core.pass.PassManager;
import ru.galeheart.core.quest.QuestManager;
import ru.galeheart.core.season.SeasonManager;
import ru.galeheart.core.spawn.SpawnManager;

/** Hearts, cooldowns, cooling, seasons and quests for GaleHeart SMP. */
public final class GaleHeartCore extends JavaPlugin {

    private DataStore store;
    private HeartManager hearts;
    private CombatManager combat;
    private SeasonManager season;
    private QuestManager quests;
    private DiscordNotifier notifier;
    private SpawnManager spawn;
    private BountyManager bounty;
    private PassManager pass;
    private ChatManager chat;
    private StarterKit kit;

    private String prefix = "<gradient:#4FA9C9:#C8324B>[GaleHeart]</gradient> ";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        prefix = getConfig().getString("prefix", prefix);

        store = new DataStore(this);
        store.load();

        hearts = new HeartManager(this);
        combat = new CombatManager(this);
        notifier = new DiscordNotifier(this);
        season = new SeasonManager(this);
        quests = new QuestManager(this);
        spawn = new SpawnManager(this);
        bounty = new BountyManager(this);
        pass = new PassManager(this);
        chat = new ChatManager(this);
        kit = new StarterKit(this);

        getServer().getPluginManager().registerEvents(new CoreListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        GaleCommand executor = new GaleCommand(this);
        for (String name : new String[]{"hearts", "top", "season", "quest", "pass", "mute", "unmute"}) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(executor);
            }
        }

        Bukkit.getScheduler().runTaskTimer(this, hearts::coolingTick, 20L * 300, 20L * 600);
        Bukkit.getScheduler().runTaskTimer(this, quests::tickAll, 20L * 5, 20L * 5);
        Bukkit.getScheduler().runTaskTimer(this, bounty::tick, 20L * 10, 20L * 10);
        Bukkit.getScheduler().runTaskTimer(this, pass::tickPlaytime, 20L * 60, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(this, store::saveAll, 20L * 300, 20L * 300);

        getLogger().info("GaleHeartCore enabled: " + quests.quests().size() + " quests, max "
                + hearts.max() + " hearts.");
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.saveAll();
        }
    }

    public DataStore store() {
        return store;
    }

    public HeartManager hearts() {
        return hearts;
    }

    public CombatManager combat() {
        return combat;
    }

    public SeasonManager season() {
        return season;
    }

    public QuestManager quests() {
        return quests;
    }

    public DiscordNotifier notifier() {
        return notifier;
    }

    public SpawnManager spawn() {
        return spawn;
    }

    public BountyManager bounty() {
        return bounty;
    }

    public PassManager pass() {
        return pass;
    }

    public ChatManager chat() {
        return chat;
    }

    public StarterKit kit() {
        return kit;
    }

    public String prefix() {
        return prefix;
    }
}
