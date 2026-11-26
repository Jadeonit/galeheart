package ru.galeheart.core.news;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import ru.galeheart.core.GaleHeartCore;
import ru.galeheart.core.util.Msg;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pulls the announcement channel from Discord so players without Discord can read
 * the news in game. Cached, because Discord rate limits are real.
 */
public final class NewsManager {

    private final GaleHeartCore plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private List<NewsItem> cache = List.of();
    private long cachedAt;

    public NewsManager(GaleHeartCore plugin) {
        this.plugin = plugin;
    }

    public record NewsItem(String id, String title, String body, long timestamp, String url) {
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("news.enabled", true);
    }

    public int pageSize() {
        return Math.max(1, plugin.getConfig().getInt("news.page-size", 4));
    }

    /** Latest announcements, newest first. Cached for a couple of minutes. */
    public List<NewsItem> items() {
        if (!enabled()) {
            return List.of();
        }
        long ttl = Math.max(10, plugin.getConfig().getInt("news.cache-seconds", 120)) * 1000L;
        if (!cache.isEmpty() && System.currentTimeMillis() - cachedAt < ttl) {
            return cache;
        }
        String channel = plugin.getConfig().getString("discord.channel-id", "");
        String token = plugin.getConfig().getString("discord.bot-token", "");
        if (channel == null || channel.isBlank() || token == null || token.isBlank()) {
            return cache;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://discord.com/api/v10/channels/" + channel + "/messages?limit=20"))
                    .timeout(Duration.ofSeconds(6))
                    .header("Authorization", "Bot " + token)
                    .header("User-Agent", "GaleHeart/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return cache;
            }
            List<NewsItem> parsed = parse(response.body(), channel);
            if (!parsed.isEmpty()) {
                cache = parsed;
                cachedAt = System.currentTimeMillis();
            }
            return cache.isEmpty() ? parsed : cache;
        } catch (Exception error) {
            return cache;
        }
    }

    private List<NewsItem> parse(String json, String channel) {
        List<NewsItem> list = new ArrayList<>();
        String guild = plugin.getConfig().getString("discord.guild-id", "");
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject message = element.getAsJsonObject();
                String id = message.has("id") ? message.get("id").getAsString() : "";
                String content = message.has("content") && !message.get("content").isJsonNull()
                        ? message.get("content").getAsString() : "";
                String title = "";
                String body = "";

                if (message.has("embeds") && message.get("embeds").isJsonArray()
                        && message.getAsJsonArray("embeds").size() > 0) {
                    JsonObject embed = message.getAsJsonArray("embeds").get(0).getAsJsonObject();
                    title = embed.has("title") && !embed.get("title").isJsonNull()
                            ? embed.get("title").getAsString() : "";
                    body = embed.has("description") && !embed.get("description").isJsonNull()
                            ? embed.get("description").getAsString() : "";
                }
                if (content.isBlank() && body.isBlank()) {
                    continue;
                }
                if (title.isBlank()) {
                    String[] lines = content.split("\\n", 2);
                    title = lines[0];
                    body = lines.length > 1 ? lines[1] : "";
                }
                long stamp = 0L;
                if (message.has("timestamp") && !message.get("timestamp").isJsonNull()) {
                    try {
                        stamp = Instant.parse(message.get("timestamp").getAsString()).toEpochMilli();
                    } catch (RuntimeException ignored) {
                        stamp = System.currentTimeMillis();
                    }
                }
                String url = guild.isBlank() ? ""
                        : "https://discord.com/channels/" + guild + "/" + channel + "/" + id;
                list.add(new NewsItem(id, clean(title), clean(body), stamp, url));
            }
        } catch (RuntimeException ignored) {
            // malformed payload, keep the old cache
        }
        return list;
    }

    /** Discord markdown is noise in a Minecraft chat line. */
    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("**", "").replace("__", "").replace("~~", "")
                .replace("`", "").replace("\\n", " ");
        text = text.replaceAll("<@[!&]?\\d+>", "");
        text = text.replace("@everyone", "").replace("@here", "");
        text = text.replaceAll("\\[(.*?)]\\((.*?)\\)", "$1");
        text = text.replaceAll("<(https?://[^>]+)>", "$1");
        return text.replaceAll("\\s{2,}", " ").trim();
    }

    public void show(Player player, int page) {
        List<NewsItem> items = items();
        if (items.isEmpty()) {
            player.sendMessage(Msg.parse(plugin.prefix() + "<gray>No news yet. Check back later."));
            return;
        }
        int size = pageSize();
        int pages = (items.size() + size - 1) / size;
        int current = Math.max(1, Math.min(pages, page));
        player.sendMessage(Msg.parse("<gradient:#4FA9C9:#C8324B><bold>GaleHeart news</bold></gradient>"
                + " <dark_gray>page " + current + "/" + pages));
        int from = (current - 1) * size;
        int to = Math.min(items.size(), from + size);
        for (int index = from; index < to; index++) {
            NewsItem item = items.get(index);
            String title = item.title().length() > 70 ? item.title().substring(0, 67) + "..." : item.title();
            String hover = item.body().isBlank() ? title
                    : (item.body().length() > 150 ? item.body().substring(0, 147) + "..." : item.body());
            String click = item.url().isBlank() ? "" : "<click:open_url:'" + item.url() + "'>";
            String close = item.url().isBlank() ? "" : "</click>";
            player.sendMessage(Msg.parse("<gray>• " + click + "<white>" + title + close
                    + " <dark_gray>· " + ago(item.timestamp())));
            if (!item.body().isBlank()) {
                String preview = item.body().length() > 90 ? item.body().substring(0, 87) + "..." : item.body();
                player.sendMessage(Msg.parse("  <dark_gray>" + preview));
            }
        }
        if (pages > 1) {
            player.sendMessage(Msg.parse("<dark_gray>Use <white>/news " + (current < pages ? current + 1 : 1)
                    + " <dark_gray>for more"));
        }
    }

    private static String ago(long timestamp) {
        if (timestamp <= 0) {
            return "recently";
        }
        long diff = Math.max(0, System.currentTimeMillis() - timestamp) / 1000L;
        if (diff < 60) {
            return "just now";
        }
        if (diff < 3600) {
            return (diff / 60) + "m ago";
        }
        if (diff < 86_400) {
            return (diff / 3600) + "h ago";
        }
        return (diff / 86_400) + "d ago";
    }
}
