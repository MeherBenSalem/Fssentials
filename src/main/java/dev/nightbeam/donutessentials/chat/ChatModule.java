package dev.nightbeam.donutessentials.chat;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the chat module. */
public final class ChatModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public ChatModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public void enable() {
    }
}
