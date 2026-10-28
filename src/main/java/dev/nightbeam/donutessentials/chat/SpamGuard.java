package dev.nightbeam.donutessentials.chat;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Chat pacing per player: a minimum delay, slow mode, and no saying the same thing twice in a short while. */
public final class SpamGuard {

    public enum Verdict { OK, TOO_FAST, SLOW, REPEATED }

    /** A check result; {@code waitSeconds} is how long is left in slow mode. */
    public record Result(Verdict verdict, long waitSeconds) {
    }

    private record Last(long time, String text, long repeatAt) {
    }

    private final Map<UUID, Last> last = new ConcurrentHashMap<>();

    /**
     * Checks a message and, when it may be sent, remembers it. Delays are in milliseconds; 0 switches a check off.
     */
    public Result check(UUID player, String text, long now, long minDelayMs, long slowModeMs, long repeatWindowMs) {
        String normalized = normalize(text);
        Last previous = last.get(player);
        if (previous != null) {
            long since = now - previous.time();
            if (slowModeMs > 0 && since < slowModeMs) {
                return new Result(Verdict.SLOW, (slowModeMs - since + 999) / 1000);
            }
            if (minDelayMs > 0 && since < minDelayMs) {
                return new Result(Verdict.TOO_FAST, 0);
            }
            if (repeatWindowMs > 0 && !normalized.isEmpty() && normalized.equals(previous.text())
                    && now - previous.repeatAt() < repeatWindowMs) {
                return new Result(Verdict.REPEATED, 0);
            }
        }
        long repeatAt = previous != null && normalized.equals(previous.text()) ? previous.repeatAt() : now;
        last.put(player, new Last(now, normalized, repeatAt));
        return new Result(Verdict.OK, 0);
    }

    public void forget(UUID player) {
        last.remove(player);
    }

    static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
