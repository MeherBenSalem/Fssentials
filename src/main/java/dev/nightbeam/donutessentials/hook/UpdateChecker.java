package dev.nightbeam.donutessentials.hook;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;

/** Asks Modrinth for the newest release once at start-up; admins are told on join. */
public final class UpdateChecker {

    public static final String PROJECT = "1YpSYvSs";
    public static final String URL = "https://modrinth.com/plugin/donutessentials";
    private static final Pattern VERSION = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    private final DonutEssentialsPlugin plugin;
    private volatile String latest;

    public UpdateChecker(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public void check() {
        if (!plugin.getConfig().getBoolean("update-check.enabled", true)) {
            return;
        }
        plugin.scheduler().runAsync(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create("https://api.modrinth.com/v2/project/" + PROJECT
                        + "/version?version_type=release").toURL().openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "NightBeam/DonutEssentials/" + plugin.version());
                if (connection.getResponseCode() != 200) {
                    return;
                }
                String body;
                try (InputStream in = connection.getInputStream()) {
                    body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                Matcher matcher = VERSION.matcher(body);
                if (matcher.find() && newer(matcher.group(1), plugin.version())) {
                    latest = matcher.group(1);
                    plugin.getLogger().info("Donut Essentials " + latest + " is available: " + URL);
                }
            } catch (Exception ignored) {
                // Offline servers simply skip the check.
            }
        });
    }

    public void notify(Player player) {
        String version = latest;
        if (version != null && player.hasPermission("donutessentials.admin")) {
            plugin.messages().send(player, "admin.update", "latest", version, "version", plugin.version(), "url", URL);
        }
    }

    /** Whether {@code candidate} is a higher x.y.z than {@code current}. */
    public static boolean newer(String candidate, String current) {
        int[] a = parts(candidate);
        int[] b = parts(current);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }

    private static int[] parts(String version) {
        String clean = version == null ? "0" : version.replaceAll("[^0-9.].*$", "");
        String[] split = clean.split("\\.");
        int[] out = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                out[i] = Integer.parseInt(split[i]);
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }
}
