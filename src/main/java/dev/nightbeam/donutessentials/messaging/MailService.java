package dev.nightbeam.donutessentials.messaging;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.storage.DataWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Offline mail in the {@code mail} table. Bodies are plain text, shown as typed. */
final class MailService {

    /** One letter. */
    record Mail(long id, String senderName, String body, long sent, boolean read) {
    }

    private final DataWriter writer;
    private final String table;

    MailService(DonutEssentialsPlugin plugin) {
        this.writer = plugin.writer();
        this.table = writer.database().table("mail");
    }

    /** How many letters the player has: all of them, or only unread ones. */
    CompletableFuture<Integer> count(UUID recipient, boolean unreadOnly) {
        return writer.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE recipient = ?"
                    + (unreadOnly ? " AND is_read = 0" : ""))) {
                statement.setString(1, recipient.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    void send(UUID recipient, UUID sender, String senderName, String body) {
        long now = System.currentTimeMillis();
        writer.write("mail", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table
                    + " (recipient, sender, sender_name, body, sent, is_read) VALUES (?, ?, ?, ?, ?, 0)")) {
                statement.setString(1, recipient.toString());
                statement.setString(2, sender == null ? null : sender.toString());
                statement.setString(3, senderName);
                statement.setString(4, body);
                statement.setLong(5, now);
                statement.executeUpdate();
            }
        });
    }

    /** Every letter, newest first; they are marked read once listed. */
    CompletableFuture<List<Mail>> readAll(UUID recipient) {
        return writer.query(connection -> {
            List<Mail> mails = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, sender_name, body, sent, is_read FROM " + table
                    + " WHERE recipient = ? ORDER BY sent DESC, id DESC")) {
                statement.setString(1, recipient.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        mails.add(new Mail(result.getLong(1), result.getString(2), result.getString(3), result.getLong(4),
                                result.getInt(5) != 0));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table + " SET is_read = 1 WHERE recipient = ?")) {
                statement.setString(1, recipient.toString());
                statement.executeUpdate();
            }
            return mails;
        });
    }

    void clear(UUID recipient) {
        writer.write("mail", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE recipient = ?")) {
                statement.setString(1, recipient.toString());
                statement.executeUpdate();
            }
        });
    }
}
