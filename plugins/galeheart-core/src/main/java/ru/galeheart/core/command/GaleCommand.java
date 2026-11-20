package ru.galeheart.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.quest.QuestManager;
import ru.galeheart.core.util.Msg;

import java.util.List;

/** /hearts, /top, /season, /quest. */
public final class GaleCommand implements CommandExecutor {

    private final GaleHeartCore plugin;

    public GaleCommand(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        return switch (name) {
            case "hearts" -> hearts(sender, args);
            case "top" -> top(sender);
            case "season" -> season(sender, args);
            case "quest" -> quest(sender);
            default -> false;
        };
    }

    private boolean hearts(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
            if (!sender.hasPermission("galeheart.admin")) {
                sender.sendMessage(Msg.parse(plugin.prefix() + "<red>No permission."));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Msg.parse(plugin.prefix() + "<red>Player not found."));
                return true;
            }
            int amount = args.length >= 3 ? parse(args[2], 1) : 1;
            plugin.hearts().add(target, amount);
            sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Hearts of " + target.getName() + ": "
                    + plugin.hearts().hearts(target)));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>Use /hearts add <player> <n>"));
            return true;
        }
        int hearts = plugin.hearts().hearts(player);
        sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>Hearts: <red>" + hearts + "<gray>/"
                + plugin.hearts().max()));
        return true;
    }

    private boolean top(CommandSender sender) {
        List<PlayerData> top = plugin.hearts().top(10);
        sender.sendMessage(Msg.parse("<gradient:#4FA9C9:#C8324B><bold>Hearts leaderboard"));
        int place = 1;
        for (PlayerData data : top) {
            String name = data.name == null || data.name.isBlank() ? data.id.toString().substring(0, 8) : data.name;
            sender.sendMessage(Msg.parse("<gray>" + place + ". <white>" + name + " <dark_gray>— <red>"
                    + data.hearts + " ♥"));
            place++;
        }
        return true;
    }

    private boolean season(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>Finale: "
                    + (plugin.season().finale() ? "<red>active" : "<green>not active")));
            return true;
        }
        if (!sender.hasPermission("galeheart.admin")) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<red>No permission."));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "finale" -> {
                plugin.season().startFinale();
                sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Finale started."));
            }
            case "stop" -> {
                plugin.season().stopFinale();
                sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Finale stopped."));
            }
            case "reset" -> {
                plugin.season().resetSeason();
                sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Season reset."));
            }
            case "announce" -> {
                if (args.length < 2) {
                    sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>/season announce <text>"));
                    break;
                }
                String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                plugin.notifier().send("Announcement", text, 0x4FA9C9);
                sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Sent to Discord."));
            }
            case "bounty" -> {
                String action = args.length > 1 ? args[1].toLowerCase() : "status";
                switch (action) {
                    case "start" -> sender.sendMessage(Msg.parse(plugin.prefix()
                            + (plugin.bounty().start() ? "<green>Bounty started." : "<red>Nobody eligible.")));
                    case "stop" -> {
                        plugin.bounty().cancel();
                        sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Bounty stopped."));
                    }
                    default -> sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>Bounty: "
                            + (plugin.bounty().active() ? "<red>" + plugin.bounty().targetName() : "<green>not active")));
                }
            }
            default -> sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>/season finale|stop|reset|announce <text>|info"));
        }
        return true;
    }

    private boolean quest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        PlayerData data = plugin.store().get(player.getUniqueId());
        sender.sendMessage(Msg.parse("<gradient:#4FA9C9:#C8324B><bold>Quests"));
        for (QuestManager.Quest quest : plugin.quests().quests().values()) {
            if (data.questsDone.contains(quest.id())) {
                sender.sendMessage(Msg.parse("<gray>• " + quest.display() + " <green>done"));
                continue;
            }
            int index = plugin.quests().currentStep(data, quest);
            if (index >= quest.steps().size()) {
                continue;
            }
            QuestManager.Step step = quest.steps().get(index);
            int have = plugin.quests().progress(data, quest, index);
            sender.sendMessage(Msg.parse("<gray>• " + quest.display() + " <dark_gray>— <white>"
                    + step.type().toLowerCase().replace('_', ' ') + " <gray>" + have + "/" + step.amount()));
        }
        return true;
    }

    private static int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
