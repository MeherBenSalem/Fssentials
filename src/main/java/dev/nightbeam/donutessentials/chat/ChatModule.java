package dev.nightbeam.donutessentials.chat;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import java.util.List;
import org.bukkit.event.Listener;

/**
 * Chat: the format with prefixes and placeholders, colours, [item], mentions, anti-spam, the filter, chat lock and
 * slow mode, join and quit messages, broadcasts, rotating announcements and nicknames.
 */
public final class ChatModule implements Module {

    private final DonutEssentialsPlugin plugin;
    private final SpamGuard spam = new SpamGuard();
    private volatile ChatSettings settings = ChatSettings.from(null);
    private volatile boolean locked;
    private volatile long slowSeconds;
    private Broadcaster broadcaster;
    private Announcer announcer;

    public ChatModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public void enable() {
        broadcaster = new Broadcaster(plugin);
        announcer = new Announcer(plugin);
        reload();
    }

    @Override
    public void reload() {
        settings = ChatSettings.from(plugin.getConfig().getConfigurationSection("chat"));
        announcer.start(plugin.getConfig().getConfigurationSection("announcements"));
    }

    @Override
    public void disable() {
        if (announcer != null) {
            announcer.stop();
        }
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new ChatCommands.ChatControl(plugin, this), new ChatCommands.Broadcast(plugin, this),
                new ChatCommands.Nick(plugin, this), new ChatCommands.Realname(plugin));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(new ChatListener(plugin, this), new JoinQuitListener(plugin, this));
    }

    ChatSettings settings() {
        return settings;
    }

    SpamGuard spam() {
        return spam;
    }

    Broadcaster broadcaster() {
        return broadcaster;
    }

    /** True while staff have locked the chat. */
    public boolean locked() {
        return locked;
    }

    void locked(boolean locked) {
        this.locked = locked;
    }

    /** Slow mode: seconds between messages, 0 when off. */
    public long slowSeconds() {
        return slowSeconds;
    }

    void slowSeconds(long slowSeconds) {
        this.slowSeconds = slowSeconds;
    }
}
