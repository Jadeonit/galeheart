package ru.galeheart.core.notify;

import ru.galeheart.core.GaleHeartCore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Fire-and-forget event poster for the Discord bot. */
public final class DiscordNotifier {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final boolean enabled;
    private final String endpoint;
    private final String webhook;
    private final String secret;
    private final String name;
    private final String avatar;

    public DiscordNotifier(GaleHeartCore plugin) {
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        this.endpoint = plugin.getConfig().getString("discord.endpoint", "http://127.0.0.1:8099/event");
        this.webhook = plugin.getConfig().getString("discord.webhook", "");
        this.secret = plugin.getConfig().getString("discord.secret", "");
        this.name = plugin.getConfig().getString("discord.name", "GaleHeart");
        this.avatar = plugin.getConfig().getString("discord.avatar", "");
    }

    public void send(String title, String description, int color) {
        if (!enabled) {
            return;
        }
        boolean direct = webhook != null && !webhook.isBlank();
        String url = direct ? webhook : endpoint;
        String json;
        if (direct) {
            StringBuilder payload = new StringBuilder("{\"username\":\"").append(escape(name)).append('"');
            if (avatar != null && !avatar.isBlank()) {
                payload.append(",\"avatar_url\":\"").append(escape(avatar)).append('"');
            }
            payload.append(",\"embeds\":[{\"title\":\"").append(escape(title))
                    .append("\",\"description\":\"").append(escape(description))
                    .append("\",\"color\":").append(color).append("}]}");
            json = payload.toString();
        } else {
            json = "{\"title\":\"" + escape(title) + "\",\"description\":\"" + escape(description)
                    + "\",\"color\":" + color + "}";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
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
