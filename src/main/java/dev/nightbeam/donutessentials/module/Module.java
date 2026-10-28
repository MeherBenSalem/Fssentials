package dev.nightbeam.donutessentials.module;

import dev.nightbeam.donutessentials.command.EssCommand;
import java.util.List;
import org.bukkit.event.Listener;

/**
 * A feature area switched by {@code modules.<id>} in config.yml. A disabled module registers no commands and no
 * listeners and runs no tasks.
 */
public interface Module {

    /** The id: the key under {@code modules}, and usually its config and messages section. */
    String id();

    /** Commands this module adds (registered after {@link #enable()}, from commands.yml). */
    default List<EssCommand> commands() {
        return List.of();
    }

    /** Listeners registered after {@link #enable()}. */
    default List<Listener> listeners() {
        return List.of();
    }

    /** Called once at start-up, after storage is ready: load caches, start tasks. */
    void enable();

    /** /donutessentials reload: re-read settings. Commands and listeners stay as they are. */
    default void reload() {
    }

    /** Server stop: save and cancel tasks. */
    default void disable() {
    }
}
