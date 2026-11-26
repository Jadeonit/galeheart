package ru.galeheart.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.pass.PassManager;
import ru.galeheart.core.quest.QuestManager;
import ru.galeheart.core.util.Msg;

import java.util.List;
import java.util.Map;

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
            case "pass" -> pass(sender, args);
            case "grant" -> grant(sender, args);
            case "bounty" -> bounty(sender);
            case "locker" -> locker(sender);
            case "achievements" -> achievements(sender);
            case "cosmetic" -> cosmetic(sender, args);
            case "news" -> news(sender, args);
            default -> false;
        };
    }

    private boolean hearts(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("withdraw")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>/hearts withdraw <amount>"));
                return true;
            }
            int amount = args.length >= 2 ? parse(args[1], 1) : 1;
            int current = plugin.hearts().hearts(player);
            if (amount <= 0 || current - amount < plugin.hearts().min()) {
                player.sendMessage(Msg.parse(plugin.prefix() + "<red>Not enough hearts to withdraw."));
                return true;
            }
            plugin.hearts().set(player, current - amount);
            for (int i = 0; i < amount; i++) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(plugin.hearts().item().create());
                for (ItemStack rest : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
            player.sendMessage(Msg.parse(plugin.prefix() + "<gray>Withdrew <red>" + amount + " <gray>heart(s)."));
            return true;
        }

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
        sender.sendMessage(Msg.parse("<dark_gray>/hearts withdraw <amount> — turn hearts into items"));
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
                            + (plugin.bounty().active() ? "<red>" + plugin.bounty().status() : "<green>not active")));
                }
            }
            default -> sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>/season finale|stop|reset|announce <text>|info"));
        }
        return true;
    }

    private boolean bounty(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>Use /bounty in game."));
            return true;
        }
        String name = plugin.bounty().targetName(player);
        if (name == null) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>No contract yet. Need another player online."));
        } else {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>Your contract: <white>" + name
                    + " <dark_gray>· <gray>kill them for <red>+1 heart"));
        }
        return true;
    }

    private boolean grant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("galeheart.admin")) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<red>No permission."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>/grant <player> <rank>"));
            return true;
        }
        String playerName = args[0];
        String rank = args[1].toLowerCase();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " parent add " + rank);
        sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Granted <white>" + rank + " <green>to <white>" + playerName));
        plugin.notifier().send("Donation", playerName + " received the " + rank + " rank.", 0xF1C40F);
        return true;
    }

    private boolean pass(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("add")) {
            if (!sender.hasPermission("galeheart.admin")) {
                sender.sendMessage(Msg.parse(plugin.prefix() + "<red>No permission."));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Msg.parse(plugin.prefix() + "<red>Player not found."));
                return true;
            }
            plugin.pass().addXp(target, "admin", parse(args[2], 0));
            sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Pass XP: " + plugin.pass().xp(target)));
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("shards")) {
            if (!sender.hasPermission("galeheart.admin")) {
                sender.sendMessage(Msg.parse(plugin.prefix() + "<red>No permission."));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Msg.parse(plugin.prefix() + "<red>Player not found."));
                return true;
            }
            PlayerData data = plugin.store().get(target.getUniqueId());
            data.shards = Math.max(0, data.shards + parse(args[2], 0));
            plugin.store().write(data);
            sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Shards of " + target.getName() + ": "
                    + data.shards));
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("buy")) {
            if (!(sender instanceof Player buyer)) {
                sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>/pass buy <id>"));
                return true;
            }
            plugin.pass().buy(buyer, args[1]);
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("trail") && args[1].equalsIgnoreCase("off")) {
            if (!(sender instanceof Player owner)) {
                sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>/pass trail off"));
                return true;
            }
            plugin.pass().unequip(owner, ru.galeheart.core.pass.Cosmetic.Type.TRAIL);
            owner.sendMessage(Msg.parse(plugin.prefix() + "<gray>Trail off. Gear up in <white>/locker<gray>."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.parse(plugin.prefix()
                    + "<gray>/pass add <player> <xp> <dark_gray>· /pass shards <player> <n>"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            int level = plugin.pass().level(player);
            int into = plugin.pass().xpIntoLevel(plugin.pass().xp(player));
            int need = level >= plugin.pass().maxLevel() ? 0 : plugin.pass().costForLevel(level);
            sender.sendMessage(Msg.parse("<gradient:#4FA9C9:#C8324B><bold>Season Pass"));
            sender.sendMessage(Msg.parse("<gray>Level <white>" + level + "<gray>/" + plugin.pass().maxLevel()
                    + (need > 0 ? " <dark_gray>(" + into + "/" + need + " xp)" : " <green>max")));
            sender.sendMessage(Msg.parse("<gray>Shards: <white>" + plugin.pass().shards(player)));
            return true;
        }
        plugin.passGui().open(player);
        return true;
    }

    private boolean news(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>Use /news in game."));
            return true;
        }
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        plugin.news().show(player, page);
        return true;
    }

    private boolean locker(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>Use /locker in game."));
            return true;
        }
        plugin.lockerGui().open(player);
        return true;
    }

    private boolean achievements(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>Use /achievements in game."));
            return true;
        }
        plugin.achievementsGui().open(player);
        return true;
    }

    private boolean cosmetic(CommandSender sender, String[] args) {
        if (!sender.hasPermission("galeheart.admin")) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<red>No permission."));
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>/cosmetic give <player> <id>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<red>Player not found."));
            return true;
        }
        if (plugin.pass().give(target, args[2])) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Gave <white>" + args[2] + "<green> to "
                    + target.getName()));
        } else {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<red>Unknown id, or already owned."));
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

    private boolean mute(CommandSender sender, String[] args, boolean mute) {
        if (!sender.hasPermission("galeheart.admin")) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<red>No permission."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>/" + (mute ? "mute" : "unmute") + " <player> [minutes]"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<red>Player not found."));
            return true;
        }
        if (mute) {
            int minutes = args.length > 1 ? parse(args[1], 10) : 10;
            plugin.chat().mute(target, minutes);
            sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Muted " + target.getName() + " for " + minutes + " min."));
            target.sendMessage(Msg.parse(plugin.prefix() + "<red>You are muted for " + minutes + " min."));
        } else {
            plugin.chat().unmute(target);
            sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Unmuted " + target.getName() + "."));
            target.sendMessage(Msg.parse(plugin.prefix() + "<green>You are unmuted."));
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
