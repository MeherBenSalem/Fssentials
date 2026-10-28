package dev.nightbeam.donutessentials.hook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Posts staff events (punishments, reports) to a Discord channel webhook as embeds. Requests run on one background
 * thread in order, and Discord's rate limit (HTTP 429) is respected. No bot is needed.
 */
public final class DiscordWebhook {

    /** One embed field. */
    public record Field(String name, String value, boolean inline) {
    }

    private static final Pattern RETRY_AFTER = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9.]+)");

    private final Logger logger;
    private final HttpClient client;
    private final ExecutorService sender;
    private volatile String url = "";
    private volatile String username = "Donut Essentials";
    private volatile long lastWarning;

    public DiscordWebhook(Logger logger) {
        this.logger = logger;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.sender = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "DonutEssentials-Discord");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void configure(String url, String username) {
        this.url = url == null ? "" : url.trim();
        this.username = username == null || username.isBlank() ? "Donut Essentials" : username;
    }

    public boolean enabled() {
        return url.startsWith("https://");
    }

    /** Queues an embed. {@code thumbnailUuid} shows that player's head (null for none). */
    public void post(String title, String description, int color, List<Field> fields, String thumbnailUuid) {
        if (!enabled()) {
            return;
        }
        String target = url;
        String body = json(title, description, color, fields, thumbnailUuid);
        sender.execute(() -> send(target, body, 0));
    }

    String json(String title, String description, int color, List<Field> fields, String thumbnailUuid) {
        StringBuilder out = new StringBuilder("{\"username\":").append(quote(username)).append(",\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{");
        out.append("\"title\":").append(quote(title));
        if (description != null && !description.isBlank()) {
            out.append(",\"description\":").append(quote(description));
        }
        out.append(",\"color\":").append(color);
        out.append(",\"timestamp\":").append(quote(Instant.now().toString()));
        if (thumbnailUuid != null) {
            out.append(",\"thumbnail\":{\"url\":").append(quote("https://mc-heads.net/avatar/" + thumbnailUuid + "/64")).append('}');
        }
        if (fields != null && !fields.isEmpty()) {
            out.append(",\"fields\":[");
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                out.append(i > 0 ? "," : "").append("{\"name\":").append(quote(field.name())).append(",\"value\":")
                        .append(quote(field.value() == null || field.value().isBlank() ? "-" : field.value()))
                        .append(",\"inline\":").append(field.inline()).append('}');
            }
            out.append(']');
        }
        out.append(",\"footer\":{\"text\":\"Donut Essentials\"}}]}");
        return out.toString();
    }

    private void send(String target, String body, int attempt) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json").header("User-Agent", "NightBeam-DonutEssentials")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 && attempt < 3) {
                Matcher matcher = RETRY_AFTER.matcher(response.body());
                double seconds = matcher.find() ? Double.parseDouble(matcher.group(1)) : 2;
                TimeUnit.MILLISECONDS.sleep((long) (Math.min(30, seconds) * 1000) + 100);
                send(target, body, attempt + 1);
            } else if (response.statusCode() >= 300) {
                warn("Discord webhook answered HTTP " + response.statusCode());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            warn("Discord webhook failed: " + error.getMessage());
        }
    }

    private void warn(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarning > 60_000) {
            lastWarning = now;
            logger.warning(message);
        }
    }

    public void shutdown() {
        sender.shutdown();
        try {
            sender.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        String value = text == null ? "" : text;
        if (value.length() > 1024) {
            value = value.substring(0, 1021) + "...";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
