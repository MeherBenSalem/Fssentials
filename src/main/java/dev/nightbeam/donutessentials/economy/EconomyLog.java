package dev.nightbeam.donutessentials.economy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A daily economy log in {@code plugins/DonutEssentials/logs/economy-YYYY-MM-DD.log}: one line per balance change,
 * written on a background thread so the server never waits for the disk.
 */
final class EconomyLog {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final File folder;
    private final Logger logger;
    private final ExecutorService writer;
    private volatile boolean enabled = true;
    private volatile long lastWarning;

    EconomyLog(File dataFolder, Logger logger) {
        this.folder = new File(dataFolder, "logs");
        this.logger = logger;
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "DonutEssentials-EconomyLog");
            thread.setDaemon(true);
            return thread;
        });
    }

    void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Queues one line: {@code time | action | player | change | before -> after | reason}. */
    void log(String action, String player, double before, double after, String reason) {
        if (!enabled || writer.isShutdown()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String line = line(now, action, player, before, after, reason);
        writer.execute(() -> {
            try {
                folder.mkdirs();
                File file = new File(folder, "economy-" + DAY.format(now) + ".log");
                Files.writeString(file.toPath(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException error) {
                long time = System.currentTimeMillis();
                if (time - lastWarning > 60_000) {
                    lastWarning = time;
                    logger.warning("Could not write the economy log: " + error.getMessage());
                }
            }
        });
    }

    static String line(LocalDateTime time, String action, String player, double before, double after, String reason) {
        double change = after - before;
        return String.format(Locale.ROOT, "%s | %s | %s | %s%.2f | %.2f -> %.2f | %s", TIME.format(time), action, player,
                change >= 0 ? "+" : "", change, before, after, reason == null ? "" : reason.replace('\n', ' '));
    }

    void shutdown() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
