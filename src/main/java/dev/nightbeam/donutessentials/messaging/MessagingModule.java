package dev.nightbeam.donutessentials.messaging;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.afk.AfkModule;
import dev.nightbeam.donutessentials.chat.ChatStyle;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/** Private messages, replies, ignoring, social spy and offline mail. */
public final class MessagingModule implements Module {

    /** Stands for the console in reply tracking. */
    static final UUID CONSOLE = new UUID(0L, 0L);
    static final String BYPASS = "donutessentials.msg.bypass";
    static final String COLOR = "donutessentials.msg.color";
    static final String EXEMPT = "donutessentials.ignore.exempt";
    static final String SPY = "donutessentials.command.socialspy";

    private final DonutEssentialsPlugin plugin;
    private final Map<UUID, UUID> partners = new ConcurrentHashMap<>();
    private MailService mail;

    public MessagingModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "messaging";
    }

    @Override
    public void enable() {
        mail = new MailService(plugin);
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new MessageCommands.Msg(plugin, this), new MessageCommands.Reply(plugin, this),
                new MessageCommands.MsgToggle(plugin), new MessageCommands.Ignore(plugin), new MessageCommands.IgnoreList(plugin),
                new MessageCommands.SocialSpy(plugin), new MailCommand(plugin, this));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(new MessagingListener(plugin, this));
    }

    MailService mail() {
        return mail;
    }

    static UUID id(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE;
    }

    /** Whoever {@code sender} last talked with, if they are still reachable. */
    CommandSender partner(CommandSender sender) {
        UUID partner = partners.get(id(sender));
        if (partner == null) {
            return null;
        }
        if (partner.equals(CONSOLE)) {
            return Bukkit.getConsoleSender();
        }
        Player player = Bukkit.getPlayer(partner);
        return player != null && plugin.vanish().canSee(sender, player) ? player : null;
    }

    void forget(UUID player) {
        partners.remove(player);
    }

    Component name(CommandSender sender) {
        return sender instanceof Player player ? plugin.names().display(player) : plugin.messages().component("common.console");
    }

    /**
     * Sends a private message. The sender always sees it as sent; a recipient who ignores the sender just never gets
     * it. Social spies see every message between two other people.
     */
    void deliver(CommandSender from, CommandSender to, String text) {
        if (id(from).equals(id(to))) {
            plugin.messages().send(from, "messaging.self");
            return;
        }
        PlayerData targetData = to instanceof Player target ? plugin.players().getIfOnline(target.getUniqueId()) : null;
        if (targetData != null && targetData.has(PlayerFlag.MSG_OFF) && !from.hasPermission(BYPASS)) {
            plugin.messages().send(from, "messaging.target-disabled", "player", ((Player) to).getName());
            return;
        }
        Component message = ChatStyle.style(text, from.hasPermission(COLOR));
        Component fromName = name(from);
        Component toName = name(to);
        plugin.messages().sendPlain(from, "messaging.to", "player", toName, "message", message);
        partners.put(id(from), id(to));
        boolean ignored = from instanceof Player sender && to instanceof Player target
                && plugin.ignores().ignores(target.getUniqueId(), sender.getUniqueId()) && !sender.hasPermission(EXEMPT);
        if (!ignored) {
            plugin.messages().sendPlain(to, "messaging.from", "player", fromName, "message", message);
            partners.put(id(to), id(from));
            if (to instanceof Player target) {
                plugin.scheduler().runForEntity(target, () -> plugin.sounds().play(target, "message"));
            }
        }
        if (targetData != null && targetData.afk()) {
            AfkModule afk = plugin.module(AfkModule.class);
            String reason = afk == null ? null : afk.reason(targetData.uuid());
            if (reason == null) {
                plugin.messages().send(from, "messaging.target-afk", "player", toName);
            } else {
                plugin.messages().send(from, "messaging.target-afk-reason", "player", toName, "reason", reason);
            }
        }
        for (Player spy : Bukkit.getOnlinePlayers()) {
            if (spy.getUniqueId().equals(id(from)) || spy.getUniqueId().equals(id(to))) {
                continue;
            }
            PlayerData spyData = plugin.players().getIfOnline(spy.getUniqueId());
            if (spyData != null && spyData.has(PlayerFlag.SOCIAL_SPY) && spy.hasPermission(SPY)) {
                plugin.messages().sendPlain(spy, "messaging.spy", "from", fromName, "to", toName, "message", message);
            }
        }
    }
}
