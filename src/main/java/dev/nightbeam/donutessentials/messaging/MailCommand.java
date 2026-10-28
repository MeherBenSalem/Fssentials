package dev.nightbeam.donutessentials.messaging;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.util.Durations;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mail send <player> <message> | read [page] | clear */
final class MailCommand extends EssCommand {

    private static final List<String> ACTIONS = List.of("send", "read", "clear");
    private static final int PAGE_SIZE = 5;
    private static final int MAX_LENGTH = 256;

    private final MessagingModule module;
    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();

    MailCommand(DonutEssentialsPlugin plugin, MessagingModule module) {
        super(plugin, "mail");
        this.module = module;
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        String action = args.length == 0 ? "read" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "send" -> send(sender, label, args);
            case "read" -> {
                Player player = player(sender);
                if (player != null) {
                    read(player, args.length > 1 ? parsePage(args[1]) : 1);
                }
            }
            case "clear" -> {
                Player player = player(sender);
                if (player != null) {
                    module.mail().clear(player.getUniqueId());
                    send(player, "messaging.mail-cleared");
                }
            }
            default -> usage(sender, label);
        }
    }

    private void send(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            usage(sender, label);
            return;
        }
        UUID senderId = sender instanceof Player player ? player.getUniqueId() : null;
        long cooldown = Math.max(0, plugin.getConfig().getLong("messaging.mail.cooldown-seconds", 10)) * 1000L;
        if (senderId != null && cooldown > 0 && !sender.hasPermission(MessagingModule.BYPASS)) {
            Long last = lastSent.get(senderId);
            long now = System.currentTimeMillis();
            if (last != null && now - last < cooldown) {
                send(sender, "messaging.mail-cooldown", "time", Durations.compact(cooldown - (now - last)));
                return;
            }
        }
        String body = join(args, 2);
        if (body.length() > MAX_LENGTH) {
            body = body.substring(0, MAX_LENGTH);
        }
        String text = body;
        String senderName = sender instanceof Player player ? player.getName() : plugin.messages().raw("common.console");
        int max = Math.max(1, plugin.getConfig().getInt("messaging.mail.max-per-player", 50));
        offline(sender, args[1], recipient -> {
            if (senderId != null && recipient.uuid().equals(senderId)) {
                send(sender, "messaging.mail-self");
                return;
            }
            Player online = Bukkit.getPlayer(recipient.uuid());
            boolean ignored = senderId != null && plugin.ignores().ignores(recipient.uuid(), senderId)
                    && !sender.hasPermission(MessagingModule.EXEMPT);
            if (ignored) {
                // The sender is told it went through, as with ignored private messages.
                send(sender, "messaging.mail-sent", "player", recipient.name());
                return;
            }
            module.mail().count(recipient.uuid(), false).thenAccept(count -> reply(sender, () -> {
                if (count >= max) {
                    send(sender, "messaging.mail-full", "player", recipient.name());
                    return;
                }
                module.mail().send(recipient.uuid(), senderId, senderName, text);
                if (senderId != null) {
                    lastSent.put(senderId, System.currentTimeMillis());
                }
                send(sender, "messaging.mail-sent", "player", recipient.name());
                if (online != null) {
                    plugin.scheduler().runForEntity(online, () -> {
                        plugin.messages().send(online, "messaging.mail-new", "player", senderName,
                                "button", plugin.messages().button("read-mail", "/mail read"));
                        plugin.sounds().play(online, "message");
                    });
                }
            }));
        });
    }

    private void read(Player player, int page) {
        module.mail().readAll(player.getUniqueId()).thenAccept(mails -> plugin.scheduler().runForEntity(player, () -> {
            if (mails.isEmpty()) {
                send(player, "messaging.mail-empty");
                return;
            }
            int pages = (mails.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            int shown = Math.max(1, Math.min(page, pages));
            send(player, "messaging.mail-header", "count", mails.size(), "page", shown, "pages", pages);
            long now = System.currentTimeMillis();
            for (int i = (shown - 1) * PAGE_SIZE; i < Math.min(mails.size(), shown * PAGE_SIZE); i++) {
                MailService.Mail mail = mails.get(i);
                Component marker = mail.read() ? Component.empty() : plugin.messages().component("messaging.mail-unread-marker");
                plugin.messages().sendPlain(player, "messaging.mail-line", "new", marker, "player", mail.senderName(),
                        "ago", Durations.ago(mail.sent(), now), "message", mail.body());
            }
            if (shown < pages) {
                plugin.messages().sendPlain(player, "messaging.mail-more",
                        "button", plugin.messages().button("next-mail", "/mail read " + (shown + 1), "page", shown + 1));
            }
        }));
    }

    private void reply(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            plugin.scheduler().runForEntity(player, task);
        } else {
            plugin.scheduler().runGlobal(task);
        }
    }

    private static int parsePage(String text) {
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return filter(ACTIONS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            return players(sender, args[1]);
        }
        return List.of();
    }
}
