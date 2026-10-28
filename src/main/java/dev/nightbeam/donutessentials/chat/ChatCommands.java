package dev.nightbeam.donutessentials.chat;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /chat, /broadcast, /nick and /realname. */
final class ChatCommands {

    static final String NICK_OTHERS = "donutessentials.nick.others";
    static final String NICK_COLOR = "donutessentials.nick.color";
    private static final int CLEAR_LINES = 100;

    private ChatCommands() {
    }

    static String senderName(DonutEssentialsPlugin plugin, CommandSender sender) {
        return sender instanceof Player player ? player.getName() : plugin.messages().raw("common.console");
    }

    /** /chat clear | lock | unlock | slow <seconds|off> */
    static final class ChatControl extends EssCommand {
        private static final List<String> ACTIONS = List.of("clear", "lock", "unlock", "slow");
        private final ChatModule module;

        ChatControl(DonutEssentialsPlugin plugin, ChatModule module) {
            super(plugin, "chat");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            String by = senderName(plugin, sender);
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "clear" -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        plugin.scheduler().runForEntity(player, () -> {
                            if (!player.hasPermission(ChatListener.BYPASS)) {
                                for (int i = 0; i < CLEAR_LINES; i++) {
                                    player.sendMessage(Component.empty());
                                }
                            }
                            plugin.messages().send(player, "chat.cleared", "player", by);
                        });
                    }
                    if (!(sender instanceof Player)) {
                        send(sender, "chat.cleared", "player", by);
                    }
                }
                case "lock" -> {
                    module.locked(true);
                    announce("chat.locked-by", by);
                }
                case "unlock" -> {
                    module.locked(false);
                    announce("chat.unlocked-by", by);
                }
                case "slow" -> {
                    if (args.length < 2) {
                        usage(sender, label);
                        return;
                    }
                    long seconds;
                    if (args[1].equalsIgnoreCase("off")) {
                        seconds = 0;
                    } else {
                        try {
                            seconds = Math.max(0, Math.min(3600, Long.parseLong(args[1])));
                        } catch (NumberFormatException invalid) {
                            usage(sender, label);
                            return;
                        }
                    }
                    module.slowSeconds(seconds);
                    if (seconds == 0) {
                        announce("chat.slow-off", by);
                    } else {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            plugin.messages().send(player, "chat.slow-on", "seconds", seconds, "player", by);
                        }
                        if (!(sender instanceof Player)) {
                            send(sender, "chat.slow-on", "seconds", seconds, "player", by);
                        }
                    }
                }
                default -> usage(sender, label);
            }
        }

        private void announce(String key, String by) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.messages().send(player, key, "player", by);
            }
            plugin.messages().send(Bukkit.getConsoleSender(), key, "player", by);
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                return filter(ACTIONS, args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("slow")) {
                return filter(List.of("off", "3", "5", "10", "30"), args[1]);
            }
            return List.of();
        }
    }

    /** /broadcast <title> [| message] */
    static final class Broadcast extends EssCommand {
        private final ChatModule module;

        Broadcast(DonutEssentialsPlugin plugin, ChatModule module) {
            super(plugin, "broadcast");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            BroadcastParser.Parsed parsed = BroadcastParser.parse(args);
            if (parsed == null) {
                usage(sender, label);
                return;
            }
            module.broadcaster().broadcast(parsed.title(), parsed.message());
            if (sender instanceof Player) {
                send(sender, "chat.broadcast-sent");
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** /nick <name|off> [player] */
    static final class Nick extends EssCommand {
        private final ChatModule module;

        Nick(DonutEssentialsPlugin plugin, ChatModule module) {
            super(plugin, "nick");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            Player target;
            if (args.length > 1) {
                if (!allowed(sender, NICK_OTHERS)) {
                    return;
                }
                target = online(sender, args[1]);
            } else if (sender instanceof Player self) {
                target = self;
            } else {
                usage(sender, label);
                return;
            }
            if (target == null) {
                return;
            }
            boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
            String input = args[0];
            if (input.equalsIgnoreCase("off") || input.equalsIgnoreCase("reset")) {
                apply(sender, target, self, null, null);
                return;
            }
            boolean colours = sender.hasPermission(NICK_COLOR);
            if (!colours && ChatStyle.hasStyling(input)) {
                send(sender, "chat.nick-no-color");
                return;
            }
            String plain = ChatStyle.plain(input, colours);
            ChatSettings settings = module.settings();
            if (plain.length() < settings.nickMin()) {
                send(sender, "chat.nick-too-short", "min", settings.nickMin());
                return;
            }
            if (plain.length() > settings.nickMax()) {
                send(sender, "chat.nick-too-long", "max", settings.nickMax());
                return;
            }
            if (!settings.nickAllowed().matcher(plain).matches()) {
                send(sender, "chat.nick-invalid");
                return;
            }
            String stored = colours ? ChatStyle.safeMiniMessage(input) : Text.escape(plain);
            if (plain.equalsIgnoreCase(target.getName())) {
                apply(sender, target, self, stored, plain);
                return;
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(target.getUniqueId()) && plugin.names().plain(other).equalsIgnoreCase(plain)) {
                    send(sender, "chat.nick-taken", "nick", plain);
                    return;
                }
            }
            plugin.players().find(plain).whenComplete((found, error) -> {
                Runnable reply = () -> {
                    if (error == null && found.isPresent() && !found.get().uuid().equals(target.getUniqueId())) {
                        send(sender, "chat.nick-taken", "nick", plain);
                        return;
                    }
                    apply(sender, target, self, stored, plain);
                };
                if (sender instanceof Player player) {
                    plugin.scheduler().runForEntity(player, reply);
                } else {
                    plugin.scheduler().runGlobal(reply);
                }
            });
        }

        /** Saves the nickname (null clears it) on the target's thread and tells both players. */
        private void apply(CommandSender sender, Player target, boolean self, String stored, String plain) {
            plugin.scheduler().runForEntity(target, () -> {
                PlayerData data = plugin.players().get(target);
                data.nickname(stored);
                plugin.players().save(data);
                plugin.names().refresh(target);
                Component shown = plugin.names().display(target);
                if (stored == null) {
                    send(target, "chat.nick-cleared");
                    if (!self) {
                        send(sender, "chat.nick-cleared-other", "player", target.getName());
                    }
                } else {
                    send(target, "chat.nick-set", "nick", shown);
                    if (!self) {
                        send(sender, "chat.nick-set-other", "player", target.getName(), "nick", shown);
                    }
                }
            });
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                return filter(List.of("off"), args[0]);
            }
            if (args.length == 2 && sender.hasPermission(NICK_OTHERS)) {
                return players(sender, args[1]);
            }
            return List.of();
        }
    }

    /** /realname <nick> */
    static final class Realname extends EssCommand {

        Realname(DonutEssentialsPlugin plugin) {
            super(plugin, "realname");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            String wanted = join(args, 0).toLowerCase(Locale.ROOT);
            List<Player> matches = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
                if (data == null || data.nickname() == null || !plugin.vanish().canSee(sender, player)) {
                    continue;
                }
                if (plugin.names().plain(player).toLowerCase(Locale.ROOT).contains(wanted)) {
                    matches.add(player);
                }
            }
            if (matches.isEmpty()) {
                send(sender, "chat.realname-none", "nick", join(args, 0));
                return;
            }
            for (Player player : matches) {
                send(sender, "chat.realname", "nick", plugin.names().display(player), "player", player.getName());
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length != 1) {
                return List.of();
            }
            List<String> nicks = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
                if (data != null && data.nickname() != null && plugin.vanish().canSee(sender, player)) {
                    nicks.add(plugin.names().plain(player));
                }
            }
            return filter(nicks, args[0]);
        }
    }
}
