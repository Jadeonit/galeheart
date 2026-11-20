package ru.galeheart.core.chat;

import org.bukkit.entity.Player;
import ru.galeheart.core.GaleHeartCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Lightweight chat guard: mutes, spam, caps, blocked words. */
public final class ChatManager {

    public enum Verdict {ALLOW, BLOCK, LOWERCASE}

    public record Result(Verdict verdict, String reason) {
        public static final Result OK = new Result(Verdict.ALLOW, "");
    }

    private final GaleHeartCore plugin;
    private final Map<UUID, Long> mutedUntil = new HashMap<>();
    private final Map<UUID, List<Long>> recent = new HashMap<>();

    public ChatManager(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("chat.enabled", true);
    }

    public boolean muted(Player player) {
        Long until = mutedUntil.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            mutedUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public long muteMinutesLeft(Player player) {
        Long until = mutedUntil.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        return Math.max(0, (until - System.currentTimeMillis()) / 60_000L);
    }

    public void mute(Player player, int minutes) {
        mutedUntil.put(player.getUniqueId(), System.currentTimeMillis() + minutes * 60_000L);
    }

    public void unmute(Player player) {
        mutedUntil.remove(player.getUniqueId());
    }

    public Result check(Player player, String text) {
        if (!enabled()) {
            return Result.OK;
        }
        if (muted(player)) {
            return new Result(Verdict.BLOCK, "<red>You are muted for " + muteMinutesLeft(player) + " min.");
        }
        int maxLength = plugin.getConfig().getInt("chat.max-length", 200);
        if (text.length() > maxLength) {
            return new Result(Verdict.BLOCK, "<red>Message too long.");
        }

        String lower = text.toLowerCase(Locale.ROOT);
        for (String blocked : plugin.getConfig().getStringList("chat.blocked-substrings")) {
            if (!blocked.isBlank() && lower.contains(blocked.toLowerCase(Locale.ROOT))) {
                return new Result(Verdict.BLOCK, "<red>That is not allowed in chat.");
            }
        }

        if (isSpam(player)) {
            return new Result(Verdict.BLOCK, "<red>Slow down.");
        }

        if (text.length() >= 8 && capsPercent(text) >= plugin.getConfig().getInt("chat.caps-percent", 70)) {
            return new Result(Verdict.LOWERCASE, "");
        }
        return Result.OK;
    }

    private boolean isSpam(Player player) {
        int window = plugin.getConfig().getInt("chat.spam-window-seconds", 8);
        int max = plugin.getConfig().getInt("chat.spam-max-messages", 4);
        long now = System.currentTimeMillis();
        List<Long> stamps = recent.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        stamps.removeIf(stamp -> now - stamp > window * 1000L);
        stamps.add(now);
        return stamps.size() > max;
    }

    private static int capsPercent(String text) {
        int letters = 0;
        int caps = 0;
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    caps++;
                }
            }
        }
        return letters == 0 ? 0 : caps * 100 / letters;
    }

    public void forget(Player player) {
        recent.remove(player.getUniqueId());
    }
}
