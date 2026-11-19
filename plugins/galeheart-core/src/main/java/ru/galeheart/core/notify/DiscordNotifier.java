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
    private final String secret;

    public DiscordNotifier(GaleHeartCore plugin) {
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        this.endpoint = plugin.getConfig().getString("discord.endpoint", "http://127.0.0.1:8099/event");
        this.secret = plugin.getConfig().getString("discord.secret", "");
    }

    public void send(String title, String description, int color) {
        if (!enabled) {
            return;
        }
        String json = "{\"title\":\"" + escape(title) + "\",\"description\":\"" + escape(description)
                + "\",\"color\":" + color + "}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("X-Secret", secret == null ? "" : secret)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(error -> null);
        } catch (IllegalArgumentException ignored) {
            // malformed endpoint in config
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
