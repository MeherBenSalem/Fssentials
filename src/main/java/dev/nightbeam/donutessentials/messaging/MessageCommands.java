package dev.nightbeam.donutessentials.messaging;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /msg, /reply, /msgtoggle, /ignore, /ignorelist and /socialspy. */
final class MessageCommands {

    private MessageCommands() {
    }

    /** /msg <player|console> <message> */
    static final class Msg extends EssCommand {
        private final MessagingModule module;

        Msg(DonutEssentialsPlugin plugin, MessagingModule module) {
            super(plugin, "msg");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length < 2) {
                usage(sender, label);
                return;
            }
            CommandSender target;
            if (args[0].equalsIgnoreCase("console")) {
                target = Bukkit.getConsoleSender();
            } else {
                target = online(sender, args[0]);
                if (target == null) {
                    return;
                }
            }
            module.deliver(sender, target, join(args, 1));
        }
    }

    /** /reply <message> */
    static final class Reply extends EssCommand {
        private final MessagingModule module;

        Reply(DonutEssentialsPlugin plugin, MessagingModule module) {
            super(plugin, "reply");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            CommandSender target = module.partner(sender);
            if (target == null) {
                send(sender, "messaging.no-reply");
                return;
            }
            module.deliver(sender, target, join(args, 0));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** /msgtoggle: refuse or accept private messages. */
    static final class MsgToggle extends EssCommand {

        MsgToggle(DonutEssentialsPlugin plugin) {
            super(plugin, "msgtoggle");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            PlayerData data = plugin.players().get(player);
            boolean off = data.toggle(PlayerFlag.MSG_OFF);
            plugin.players().save(data);
            send(player, off ? "messaging.toggled-off" : "messaging.toggled-on");
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** /ignore <player>: toggles. */
    static final class Ignore extends EssCommand {

        Ignore(DonutEssentialsPlugin plugin) {
            super(plugin, "ignore");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            offline(player, args[0], data -> {
                if (data.uuid().equals(player.getUniqueId())) {
                    send(player, "messaging.ignore-self");
                    return;
                }
                boolean already = plugin.ignores().ignores(player.getUniqueId(), data.uuid());
                Player online = Bukkit.getPlayer(data.uuid());
                if (!already && online != null && online.hasPermission(MessagingModule.EXEMPT)) {
                    send(player, "messaging.ignore-exempt", "player", data.name());
                    return;
                }
                boolean ignoring = plugin.ignores().toggle(player.getUniqueId(), data.uuid());
                send(player, ignoring ? "messaging.ignored" : "messaging.unignored", "player", data.name());
            });
        }
    }

    /** /ignorelist */
    static final class IgnoreList extends EssCommand {

        IgnoreList(DonutEssentialsPlugin plugin) {
            super(plugin, "ignorelist");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            List<UUID> ignored = new ArrayList<>(plugin.ignores().list(player.getUniqueId()));
            if (ignored.isEmpty()) {
                send(player, "messaging.ignore-list-empty");
                return;
            }
            List<CompletableFuture<Optional<PlayerData>>> lookups = new ArrayList<>();
            for (UUID uuid : ignored) {
                lookups.add(plugin.players().find(uuid));
            }
            CompletableFuture.allOf(lookups.toArray(new CompletableFuture[0])).whenComplete((done, error) -> {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < lookups.size(); i++) {
                    Optional<PlayerData> found = lookups.get(i).getNow(Optional.empty());
                    names.add(found.map(PlayerData::name).orElse(ignored.get(i).toString().substring(0, 8)));
                }
                names.sort(String.CASE_INSENSITIVE_ORDER);
                plugin.scheduler().runForEntity(player, () ->
                        send(player, "messaging.ignore-list", "count", names.size(), "players", String.join(", ", names)));
            });
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** /socialspy: read other players' private messages. */
    static final class SocialSpy extends EssCommand {

        SocialSpy(DonutEssentialsPlugin plugin) {
            super(plugin, "socialspy");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            PlayerData data = plugin.players().get(player);
            boolean on = data.toggle(PlayerFlag.SOCIAL_SPY);
            plugin.players().save(data);
            send(player, on ? "messaging.spy-on" : "messaging.spy-off");
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }
}
