package dev.nightbeam.donutessentials.storage;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The one thread that talks to the database. Changes are made in memory first and saved here in the order they were
 * made, so a later save never lands before an earlier one, and a read queued after a save sees it. Shutting down
 * waits for every queued save.
 */
public final class DataWriter {

    @FunctionalInterface
    public interface SqlTask {
        void run(Connection connection) throws Exception;
    }

    @FunctionalInterface
    public interface SqlCall<T> {
        T call(Connection connection) throws Exception;
    }

    private final Database database;
    private final Logger logger;
    private final ExecutorService executor;

    public DataWriter(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "DonutEssentials-Database");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Database database() {
        return database;
    }

    /** Queues a save. Failures are logged; the in-memory state stays as it is. */
    public void write(String what, SqlTask task) {
        Runnable job = () -> {
            try (Connection connection = database.connection()) {
                task.run(connection);
            } catch (Throwable error) {
                logger.log(Level.SEVERE, "Could not save " + what, error);
            }
        };
        if (executor.isShutdown()) {
            job.run();
        } else {
            executor.execute(job);
        }
    }

    /** Runs a query on the database thread, after every save queued before it. */
    public <T> CompletableFuture<T> query(SqlCall<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable job = () -> {
            try (Connection connection = database.connection()) {
                future.complete(call.call(connection));
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        };
        if (executor.isShutdown()) {
            job.run();
        } else {
            executor.execute(job);
        }
        return future;
    }

    /** Waits for every queued save (up to 30 seconds), then stops the thread. */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warning("Some data was still being saved after 30 seconds.");
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
