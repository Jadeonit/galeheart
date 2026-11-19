package ru.galeheart.core.quest;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.data.PlayerData;
import ru.galeheart.core.util.Msg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Config driven quest chains. Completing a quest grants a permission node,
 * which other plugins (PortalBlock) use to unlock dimensions.
 */
public final class QuestManager {

    public record Step(String type, String arg, int amount) {
    }

    public record Quest(String id, String display, String permission, List<Step> steps) {
    }

    private final GaleHeartCore plugin;
    private final Map<String, Quest> quests = new LinkedHashMap<>();
    private final Set<UUID> grantedThisSession = new HashSet<>();

    public QuestManager(GaleHeartCore plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        quests.clear();
        var section = plugin.getConfig().getConfigurationSection("quests");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            String display = section.getString(id + ".display", id);
            String permission = section.getString(id + ".permission", "galeheart." + id);
            List<Step> steps = new ArrayList<>();
            for (String raw : section.getStringList(id + ".steps")) {
                String[] parts = raw.split(":");
                if (parts.length != 3) {
                    plugin.getLogger().warning("Bad quest step: " + raw);
                    continue;
                }
                try {
                    steps.add(new Step(parts[0].trim().toUpperCase(), parts[1].trim().toUpperCase(), Integer.parseInt(parts[2].trim())));
                } catch (NumberFormatException exception) {
                    plugin.getLogger().warning("Bad quest amount: " + raw);
                }
            }
            if (!steps.isEmpty()) {
                quests.put(id, new Quest(id, display, permission, List.copyOf(steps)));
            }
        }
        plugin.getLogger().info("Loaded " + quests.size() + " quests.");
    }

    public Map<String, Quest> quests() {
        return quests;
    }

    private String key(Quest quest, int step) {
        return quest.id() + ":" + step;
    }

    public int currentStep(PlayerData data, Quest quest) {
        for (int i = 0; i < quest.steps().size(); i++) {
            if (data.questProgress.getOrDefault(key(quest, i), 0) < quest.steps().get(i).amount()) {
                return i;
            }
        }
        return quest.steps().size();
    }

    public int progress(PlayerData data, Quest quest, int step) {
        return data.questProgress.getOrDefault(key(quest, step), 0);
    }

    public void onKill(Player player, EntityType type) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        boolean dirty = false;

        for (Quest quest : quests.values()) {
            if (data.questsDone.contains(quest.id())) {
                continue;
            }
            int index = currentStep(data, quest);
            if (index >= quest.steps().size()) {
                continue;
            }
            Step step = quest.steps().get(index);
            if (!"KILL_MOB".equals(step.type()) || !step.arg().equals(type.name())) {
                continue;
            }
            int value = progress(data, quest, index) + 1;
            data.questProgress.put(key(quest, index), value);
            dirty = true;
            if (value >= step.amount()) {
                player.sendMessage(Msg.parse(plugin.prefix() + "<green>Step complete: <white>" + step.arg().toLowerCase()
                        + " <gray>(" + step.amount() + ")"));
            }
        }
        if (dirty) {
            plugin.store().write(data);
            checkCompletion(player, data);
        }
    }

    /** Called every few seconds: counts collection steps. */
    public void tickAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.store().get(player.getUniqueId());
            boolean dirty = false;

            for (Quest quest : quests.values()) {
                if (data.questsDone.contains(quest.id())) {
                    continue;
                }
                int index = currentStep(data, quest);
                if (index >= quest.steps().size()) {
                    continue;
                }
                Step step = quest.steps().get(index);
                if (!"COLLECT_ITEM".equals(step.type())) {
                    continue;
                }
                Material material = Material.matchMaterial(step.arg());
                if (material == null) {
                    continue;
                }
                int have = count(player, material);
                if (have >= step.amount()) {
                    remove(player, material, step.amount());
                    data.questProgress.put(key(quest, index), step.amount());
                    player.sendMessage(Msg.parse(plugin.prefix() + "<green>Step complete: <white>"
                            + step.amount() + "x " + pretty(material)));
                    dirty = true;
                }
            }
            if (dirty) {
                plugin.store().write(data);
                checkCompletion(player, data);
            }
        }
    }

    private void checkCompletion(Player player, PlayerData data) {
        for (Quest quest : quests.values()) {
            if (data.questsDone.contains(quest.id())) {
                continue;
            }
            if (currentStep(data, quest) < quest.steps().size()) {
                continue;
            }
            data.questsDone.add(quest.id());
            plugin.store().write(data);
            grant(player);
            player.sendMessage(Msg.parse(plugin.prefix() + "<gradient:#4FA9C9:#C8324B><bold>QUEST COMPLETE: "
                    + quest.display() + "<gradient>"));
            Bukkit.broadcast(Msg.parse(plugin.prefix() + "<gray>" + player.getName() + " completed <white>"
                    + quest.display() + "<gray>."));
            plugin.notifier().send("Quest complete", player.getName() + " finished " + quest.id()
                    + ". The gate is open for them.", 0xF1C40F);
        }
    }

    public void grant(Player player) {
        PlayerData data = plugin.store().get(player.getUniqueId());
        for (Quest quest : quests.values()) {
            if (data.questsDone.contains(quest.id())) {
                player.addAttachment(plugin, quest.permission(), true);
            }
        }
        grantedThisSession.add(player.getUniqueId());
    }

    public void forget(Player player) {
        grantedThisSession.remove(player.getUniqueId());
    }

    private int count(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void remove(Player player, Material material, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (stack.getAmount() - take <= 0) {
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}
