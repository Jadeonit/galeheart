package ru.galeheart.core.notify;

import ru.galeheart.core.GaleHeartCore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Posts events to Discord.
 *
 * <p>Three transports, in order of preference:
 * <ol>
 *   <li>bot token + channel id - the message is owned by the bot, so it can be
 *       published into an announcement channel without Manage Messages;</li>
 *   <li>a channel webhook - simple, but the bot cannot publish its messages;</li>
 *   <li>the legacy local relay endpoint from the first version.</li>
 * </ol>
 */
public final class DiscordNotifier {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"(\\d+)\"");

    private final boolean enabled;
    private final String botToken;
    private final String channelId;
    private final String webhook;
    private final String endpoint;
    private final String secret;
    private final String name;
    private final String avatar;
    private final boolean crosspost;

    public DiscordNotifier(GaleHeartCore plugin) {
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        this.botToken = plugin.getConfig().getString("discord.bot-token", "");
        this.channelId = plugin.getConfig().getString("discord.channel-id", "");
        this.webhook = plugin.getConfig().getString("discord.webhook", "");
        this.endpoint = plugin.getConfig().getString("discord.endpoint", "http://127.0.0.1:8099/event");
        this.secret = plugin.getConfig().getString("discord.secret", "");
        this.name = plugin.getConfig().getString("discord.name", "GaleHeart");
        this.avatar = plugin.getConfig().getString("discord.avatar", "");
        this.crosspost = plugin.getConfig().getBoolean("discord.crosspost", true);
    }

    public void send(String title, String description, int color) {
        if (!enabled) {
            return;
        }
        String safeTitle = clamp(strip(title), 250);
        String safeBody = clamp(strip(description), 3_900);
        if (has(botToken) && has(channelId)) {
            postAsBot(safeTitle, safeBody, color);
        }
        if (has(webhook)) {
            postAsWebhook(safeTitle, safeBody, color);
        }
        if (!has(botToken) && !has(webhook)) {
            postToRelay(safeTitle, safeBody, color);
        }
    }

    private void postAsBot(String title, String body, int color) {
        String json = "{\"embeds\":[{\"title\":\"" + escape(title) + "\",\"description\":\"" + escape(body)
                + "\",\"color\":" + color + "}]}";
        HttpRequest request = HttpRequest.newBuilder(messageUrl())
                .timeout(Duration.ofSeconds(6))
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200 && crosspost) {
                        Matcher matcher = ID.matcher(response.body());
                        if (matcher.find()) {
                            publish(matcher.group(1));
                        }
                    }
                })
                .exceptionally(error -> null);
    }

    /** Publishes a bot-owned message so announcement followers get a push. */
    private void publish(String messageId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "https://discord.com/api/v10/channels/" + channelId + "/messages/" + messageId + "/crosspost"))
                .timeout(Duration.ofSeconds(6))
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(error -> null);
    }

    private void postAsWebhook(String title, String body, int color) {
        StringBuilder payload = new StringBuilder("{\"username\":\"").append(escape(name)).append('"');
        if (has(avatar)) {
            payload.append(",\"avatar_url\":\"").append(escape(avatar)).append('"');
        }
        payload.append(",\"embeds\":[{\"title\":\"").append(escape(title))
                .append("\",\"description\":\"").append(escape(body))
                .append("\",\"color\":").append(color).append("}]}");
        send(payload.toString(), webhook);
    }

    private void postToRelay(String title, String body, int color) {
        String json = "{\"title\":\"" + escape(title) + "\",\"description\":\"" + escape(body)
                + "\",\"color\":" + color + "}";
        send(json, endpoint);
    }

    private void send(String json, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Secret", secret == null ? "" : secret)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(error -> null);
        } catch (IllegalArgumentException ignored) {
            // malformed url in config
        }
    }

    private URI messageUrl() {
        return URI.create("https://discord.com/api/v10/channels/" + channelId + "/messages");
    }

    private static boolean has(String value) {
        return value != null && !value.isBlank();
    }

    private static String clamp(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    /** MiniMessage tags are meaningless in Discord. */
    private static String strip(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("<[^>]+>", "").replaceAll("&[0-9a-fk-or]", "");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
