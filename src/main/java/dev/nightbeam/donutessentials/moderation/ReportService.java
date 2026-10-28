package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Player reports: open and claimed ones in memory, all of them in {@code reports}. */
final class ReportService {

    static final String OPEN = "OPEN";
    static final String CLAIMED = "CLAIMED";
    static final String CLOSED = "CLOSED";

    /** One report. Status changes make a new copy. */
    record Report(long id, UUID reporter, String reporterName, UUID target, String targetName, String reason, long created,
                  String status, String handler, String location) {

        Report with(String newStatus, String newHandler) {
            return new Report(id, reporter, reporterName, target, targetName, reason, created, newStatus, newHandler, location);
        }
    }

    private final DonutEssentialsPlugin plugin;
    private final Map<Long, Report> open = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();

    ReportService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    private String table() {
        return plugin.writer().database().table("reports");
    }

    /** Loads open and claimed reports (start-up). */
    void load() throws Exception {
        List<Report> loaded = plugin.writer().query(connection -> {
            List<Report> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, reporter, reporter_name, target, target_name, reason,"
                    + " created, status, handler, location FROM " + table() + " WHERE status <> ?")) {
                statement.setString(1, CLOSED);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        out.add(new Report(result.getLong(1), PunishmentStore.parseUuid(result.getString(2)), result.getString(3),
                                PunishmentStore.parseUuid(result.getString(4)), result.getString(5), result.getString(6), result.getLong(7),
                                result.getString(8), result.getString(9), result.getString(10)));
                    }
                }
            }
            return out;
        }).get(30, TimeUnit.SECONDS);
        open.clear();
        for (Report report : loaded) {
            open.put(report.id(), report);
        }
    }

    /** Seconds the reporter still has to wait, 0 when they may report. */
    long cooldownLeft(UUID reporter, int cooldownSeconds) {
        Long last = lastReport.get(reporter);
        if (last == null) {
            return 0;
        }
        return Math.max(0, (last + cooldownSeconds * 1000L - System.currentTimeMillis() + 999) / 1000);
    }

    /** Saves a new report and returns it with its ID. */
    CompletableFuture<Report> create(UUID reporter, String reporterName, UUID target, String targetName, String reason, String location) {
        long now = System.currentTimeMillis();
        lastReport.put(reporter, now);
        return plugin.writer().query(connection -> {
            long id;
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table()
                    + " (reporter, reporter_name, target, target_name, reason, created, status, handler, location)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, reporter.toString());
                statement.setString(2, reporterName);
                statement.setString(3, target == null ? null : target.toString());
                statement.setString(4, targetName);
                statement.setString(5, reason);
                statement.setLong(6, now);
                statement.setString(7, OPEN);
                statement.setString(8, null);
                statement.setString(9, location);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    id = keys.next() ? keys.getLong(1) : -1;
                }
            }
            Report report = new Report(id, reporter, reporterName, target, targetName, reason, now, OPEN, null, location);
            if (id > 0) {
                open.put(id, report);
            }
            return report;
        });
    }

    Report get(long id) {
        return open.get(id);
    }

    /** Open and claimed reports, newest first. */
    List<Report> list() {
        List<Report> list = new ArrayList<>(open.values());
        list.sort(Comparator.comparingLong(Report::created).reversed());
        return list;
    }

    /** Claims (or closes) a report. Returns the changed report, or null when it is not open. */
    Report change(long id, String status, String handler) {
        Report current = open.get(id);
        if (current == null) {
            return null;
        }
        Report next = current.with(status, handler);
        if (CLOSED.equals(status)) {
            open.remove(id);
        } else {
            open.put(id, next);
        }
        plugin.writer().write("report " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table() + " SET status = ?, handler = ? WHERE id = ?")) {
                statement.setString(1, status);
                statement.setString(2, handler);
                statement.setLong(3, id);
                statement.executeUpdate();
            }
        });
        return next;
    }
}
