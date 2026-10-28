package dev.nightbeam.donutessentials.menu;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Text input for menus and commands. On Minecraft 1.21.7+ (with {@code dialogs.enabled}) a native dialog opens;
 * otherwise the next chat line is the answer ({@code cancel} cancels).
 */
public final class InputService implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final Dialogs dialogs;
    private final Map<UUID, Consumer<String>> prompts = new ConcurrentHashMap<>();

    public InputService(DonutEssentialsPlugin plugin, Dialogs dialogs) {
        this.plugin = plugin;
        this.dialogs = dialogs;
    }

    public boolean dialogsAvailable() {
        return dialogs != null;
    }

    /** The dialogs, when available and switched on; otherwise null. */
    public Dialogs dialogs() {
        return dialogs != null && plugin.getConfig().getBoolean("dialogs.enabled", true) ? dialogs : null;
    }

    /**
     * Asks for one line of text. {@code titlePath} and {@code promptPath} are messages.yml paths. The answer runs on
     * the player's thread.
     */
    public void text(Player player, String titlePath, String promptPath, int maxLength, Consumer<String> answer) {
        Dialogs active = dialogs();
        if (active != null) {
            try {
                player.closeInventory();
                active.text(player, plugin.messages().component(titlePath), plugin.messages().component(promptPath), "", maxLength,
                        value -> plugin.scheduler().forPlayer(player, () -> answer.accept(value.trim())),
                        () -> plugin.messages().send(player, "common.input-cancelled"));
                return;
            } catch (Throwable error) {
                plugin.getLogger().warning("Dialogs failed, using chat prompts instead: " + error);
            }
        }
        player.closeInventory();
        prompts.put(player.getUniqueId(), answer);
        plugin.messages().send(player, promptPath);
        plugin.messages().send(player, "common.input-chat");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        if (text.equalsIgnoreCase("cancel") || text.isEmpty()) {
            plugin.messages().send(player, "common.input-cancelled");
            return;
        }
        plugin.scheduler().runForEntity(player, () -> prompt.accept(text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }
}
