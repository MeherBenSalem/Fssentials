package dev.nightbeam.donutessentials.teleport;

import java.util.UUID;

/**
 * A teleport request. With {@code here} false the sender wants to go to the target (/tpa); with {@code here} true
 * the sender wants the target to come to them (/tpahere).
 */
public record TpaRequest(UUID sender, String senderName, UUID target, String targetName, boolean here, long created) {

    public boolean expired(long now, long expireMillis) {
        return now - created >= expireMillis;
    }
}
