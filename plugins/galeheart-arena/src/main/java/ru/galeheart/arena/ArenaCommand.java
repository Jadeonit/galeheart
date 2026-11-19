package ru.galeheart.arena;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.galeheart.arena.util.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /pvp, /safepvp, /arena. */
public final class ArenaCommand implements CommandExecutor, TabCompleter {

    private final ArenaPlugin plugin;

    public ArenaCommand(ArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (name.equals("arena")) {
            return admin(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<red>Players only."));
            return true;
        }
        if (!player.hasPermission("galeheart.arena.use")) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<red>No permission."));
            return true;
        }

        DuelManager.Mode mode = name.equals("safepvp") ? DuelManager.Mode.SAFE : DuelManager.Mode.STAKE;

        if (args.length == 0) {
            stats(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("accept")) {
            plugin.duels().accept(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("deny")) {
            plugin.duels().deny(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            plugin.duels().forfeit(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("stats")) {
            stats(player);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<red>Player not found."));
            return true;
        }
        plugin.duels().request(player, target, mode);
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("galeheart.arena.admin")) {
            sender.sendMessage(Msg.parse(plugin.prefix() + "<red>No permission."));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(Msg.parse(plugin.prefix() + "<green>Arena config reloaded. Restart for world/spawn changes."));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("stats") && args.length > 1) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Msg.parse(plugin.prefix() + "<red>Player not found."));
                return true;
            }
            int[] value = plugin.duels().stats(target);
            sender.sendMessage(Msg.parse(plugin.prefix() + "<white>" + target.getName() + "<gray>: <green>"
                    + value[0] + "W <red>" + value[1] + "L"));
            return true;
        }
        sender.sendMessage(Msg.parse(plugin.prefix() + "<gray>/arena reload | /arena stats <player>"));
        return true;
    }

    private void stats(Player player) {
        int[] value = plugin.duels().stats(player);
        player.sendMessage(Msg.parse(plugin.prefix() + "<gray>Duels: <green>" + value[0] + "W <red>"
                + value[1] + "L"));
        player.sendMessage(Msg.parse("<dark_gray>/pvp <player> — stake duel · /safepvp <player> — practice"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("accept", "deny", "leave", "stats"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            options.add(player.getName());
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
