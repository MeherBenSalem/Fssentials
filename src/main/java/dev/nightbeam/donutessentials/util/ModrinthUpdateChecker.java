package dev.nightbeam.donutessentials.util;

import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class ModrinthUpdateChecker {
    private static final String PROJECT_ID = "1YpSYvSs";
    private static final String MODRINTH_URL = "https://modrinth.com/plugin/donutessentials";
    private static final String VERSIONS_ENDPOINT =
            "https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version?version_type=release&limit=20";

    private ModrinthUpdateChecker() {
    }

    public static void checkAsync(Plugin plugin, FoliaScheduler scheduler, boolean enabled) {
        if (!enabled) {
            return;
        }
        var currentVersion = plugin.getDescription().getVersion();
        scheduler.runAsync(() -> {
            try {
                var latestVersion = fetchLatestVersion(plugin);
                if (latestVersion == null) {
                    return;
                }
                var comparison = compareVersions(latestVersion, currentVersion);
                scheduler.runGlobal(() -> {
                    if (comparison > 0) {
                        plugin.getLogger().warning(
                                "[DonutEssentials] A new version is available: " + latestVersion
                                        + " (running " + currentVersion + "). Download: " + MODRINTH_URL);
                        for (var player : plugin.getServer().getOnlinePlayers()) {
                            if (player.isOp()) {
                                player.sendMessage("DonutEssentials update available: " + latestVersion + " — " + MODRINTH_URL);
                            }
                        }
                    } else {
                        plugin.getLogger().info("[DonutEssentials] Running the latest version (" + currentVersion + ").");
                    }
                });
            } catch (Exception error) {
                plugin.getLogger().log(Level.FINE, "[DonutEssentials] Could not check Modrinth for updates.", error);
            }
        });
    }

    private static String fetchLatestVersion(Plugin plugin) throws Exception {
        var connection = (HttpURLConnection) URI.create(VERSIONS_ENDPOINT).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setRequestProperty("User-Agent",
                "DonutEssentials/" + plugin.getDescription().getVersion() + " (" + MODRINTH_URL + ")");
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            return null;
        }
        var body = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return pickNewestVersionNumber(body.toString());
    }

    static String pickNewestVersionNumber(String json) {
        var entries = parseVersionEntries(json);
        if (entries.isEmpty()) {
            return null;
        }
        entries.sort((a, b) -> b.datePublished.compareTo(a.datePublished));
        return entries.get(0).versionNumber;
    }

    static List<VersionEntry> parseVersionEntries(String json) {
        var entries = new ArrayList<VersionEntry>();
        if (json == null || json.isBlank()) {
            return entries;
        }
        var number = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");
        var date = Pattern.compile("\"date_published\"\\s*:\\s*\"([^\"]+)\"");
        var numMatcher = number.matcher(json);
        while (numMatcher.find()) {
            var object = enclosingObject(json, numMatcher.start());
            if (object == null) {
                continue;
            }
            var versionInObject = number.matcher(object);
            var dateInObject = date.matcher(object);
            if (versionInObject.find() && dateInObject.find()) {
                entries.add(new VersionEntry(versionInObject.group(1), dateInObject.group(1)));
            }
        }
        return entries;
    }

    static String enclosingObject(String json, int index) {
        int depth = 0;
        int start = -1;
        for (int i = index; i >= 0; i--) {
            char c = json.charAt(i);
            if (c == '}') {
                depth++;
            } else if (c == '{') {
                if (depth == 0) {
                    start = i;
                    break;
                }
                depth--;
            }
        }
        if (start < 0) {
            return null;
        }
        depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    static int compareVersions(String a, String b) {
        var left = normalizeVersion(a).split("\\.");
        var right = normalizeVersion(b).split("\\.");
        var length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int leftPart = i < left.length ? parseVersionPart(left[i]) : 0;
            int rightPart = i < right.length ? parseVersionPart(right[i]) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    static String normalizeVersion(String version) {
        if (version == null) {
            return "0";
        }
        var trimmed = version.trim();
        var plus = trimmed.indexOf('+');
        if (plus >= 0) {
            trimmed = trimmed.substring(0, plus);
        }
        var hyphen = trimmed.indexOf('-');
        if (hyphen > 0) {
            trimmed = trimmed.substring(0, hyphen);
        }
        return trimmed;
    }

    private static int parseVersionPart(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        return Integer.parseInt(part.substring(0, end));
    }

    static final class VersionEntry {
        final String versionNumber;
        final String datePublished;

        VersionEntry(String versionNumber, String datePublished) {
            this.versionNumber = versionNumber;
            this.datePublished = datePublished == null ? "" : datePublished;
        }
    }
}
