package dev.nightbeam.donutessentials.chat;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.hook.ChatMeta;
import dev.nightbeam.donutessentials.hook.Placeholders;
import dev.nightbeam.donutessentials.menu.Menu;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Chat: lock, slow mode, anti-spam and the filter first (low priority), then formatting: colours by permission,
 * {@code [item]}, mentions, the format with prefix and suffix, and dropping viewers who ignore the sender.
 */
final class ChatListener implements Listener {

    static final String BYPASS = "donutessentials.chat.bypass";
    static final String COLOR = "donutessentials.chat.color";
    static final String ITEM = "donutessentials.chat.item";
    private static final Pattern ITEM_TAG = Pattern.compile("(?i)\\[item]");
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final DonutEssentialsPlugin plugin;
    private final ChatModule module;

    ChatListener(DonutEssentialsPlugin plugin, ChatModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCheck(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ChatSettings settings = module.settings();
        String text = PLAIN.serialize(event.message());
        if (player.hasPermission(BYPASS)) {
            return;
        }
        if (module.locked()) {
            event.setCancelled(true);
            plugin.messages().send(player, "chat.locked");
            return;
        }
        if (settings.filterEnabled() && settings.filter().blocksLink(text)) {
            event.setCancelled(true);
            plugin.messages().send(player, "chat.link-blocked");
            return;
        }
        SpamGuard.Result result = module.spam().check(player.getUniqueId(), text, System.currentTimeMillis(),
                settings.antiSpam() ? settings.minDelayMs() : 0, module.slowSeconds() * 1000L,
                settings.antiSpam() ? settings.repeatWindowMs() : 0);
        switch (result.verdict()) {
            case SLOW -> {
                event.setCancelled(true);
                plugin.messages().send(player, "chat.slow", "seconds", result.waitSeconds());
                return;
            }
            case TOO_FAST -> {
                event.setCancelled(true);
                plugin.messages().send(player, "chat.too-fast");
                return;
            }
            case REPEATED -> {
                event.setCancelled(true);
                plugin.messages().send(player, "chat.repeated");
                return;
            }
            default -> {
            }
        }
        if (settings.filterEnabled()) {
            String filtered = settings.filter().apply(text);
            if (!filtered.equals(text)) {
                event.message(Component.text(filtered));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRender(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        event.viewers().removeIf(audience -> audience instanceof Player viewer && !viewer.getUniqueId().equals(sender.getUniqueId())
                && plugin.ignores().ignores(viewer.getUniqueId(), sender.getUniqueId()));
        ChatSettings settings = module.settings();
        if (!settings.formatEnabled()) {
            return;
        }
        String text = PLAIN.serialize(event.message());
        Component message = ChatStyle.style(text, sender.hasPermission(COLOR));
        if (settings.itemEnabled() && sender.hasPermission(ITEM) && ITEM_TAG.matcher(text).find()) {
            message = withItem(event, sender, message);
        }
        Set<Player> mentioned = new LinkedHashSet<>();
        if (settings.mentionsEnabled()) {
            message = withMentions(sender, message, settings.mentionsRequireAt(), mentioned);
        }
        event.message(message);

        String group = ChatMeta.group(sender);
        String template = Placeholders.apply(sender, settings.formatFor(group));
        Object[] values = {
                "prefix", plugin.messages().render(ChatMeta.prefix(sender)),
                "suffix", plugin.messages().render(ChatMeta.suffix(sender)),
                "group", group,
                "name", sender.getName(),
                "display_name", plugin.names().display(sender),
                "world", sender.getWorld().getName(),
        };
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, body) ->
                plugin.messages().render(template, Menu.concat(values, new Object[] {"message", body}))));

        for (Player target : mentioned) {
            PlayerData data = plugin.players().getIfOnline(target.getUniqueId());
            if (data == null || data.has(PlayerFlag.MENTIONS_OFF) || plugin.ignores().ignores(target.getUniqueId(), sender.getUniqueId())) {
                continue;
            }
            plugin.scheduler().runForEntity(target, () -> {
                plugin.sounds().play(target, "mention");
                plugin.messages().actionBar(target, "chat.mentioned", "player", sender.getName());
            });
        }
    }

    /** Replaces [item] with the held item's name, which shows its tooltip on hover. */
    private Component withItem(AsyncChatEvent event, Player sender, Component message) {
        ItemStack held = heldItem(event, sender);
        if (held == null || held.getType() == Material.AIR || held.getAmount() <= 0) {
            return message;
        }
        Component name = held.displayName().hoverEvent(held.asHoverEvent());
        if (held.getAmount() > 1) {
            name = name.append(plugin.messages().component("chat.item-amount", "amount", held.getAmount()));
        }
        Component replacement = name;
        return message.replaceText(TextReplacementConfig.builder().match(ITEM_TAG).replacement(replacement).times(3).build());
    }

    /** The held item, read on the player's thread (chat usually arrives on another one). */
    private ItemStack heldItem(AsyncChatEvent event, Player sender) {
        if (!event.isAsynchronous() || plugin.scheduler().owns(sender)) {
            return sender.getInventory().getItemInMainHand().clone();
        }
        CompletableFuture<ItemStack> future = new CompletableFuture<>();
        plugin.scheduler().runForEntity(sender, () -> future.complete(sender.getInventory().getItemInMainHand().clone()));
        try {
            return future.get(500, TimeUnit.MILLISECONDS);
        } catch (Exception timedOut) {
            return null;
        }
    }

    /** Highlights the names of online players the sender can see, and collects who was mentioned. */
    private Component withMentions(Player sender, Component message, boolean requireAt, Set<Player> mentioned) {
        Map<String, Player> candidates = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getUniqueId().equals(sender.getUniqueId()) && plugin.vanish().canSee(sender, player)) {
                candidates.put(player.getName().toLowerCase(Locale.ROOT), player);
            }
        }
        if (candidates.isEmpty()) {
            return message;
        }
        String plain = PLAIN.serialize(message).toLowerCase(Locale.ROOT);
        List<String> present = new ArrayList<>();
        for (String name : candidates.keySet()) {
            if (plain.contains(name)) {
                present.add(name);
            }
        }
        if (present.isEmpty()) {
            return message;
        }
        present.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder alternatives = new StringBuilder();
        for (String name : present) {
            alternatives.append(alternatives.length() == 0 ? "" : "|").append(Pattern.quote(name));
        }
        Pattern pattern = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + (requireAt ? "@" : "@?") + "(" + alternatives + ")(?![A-Za-z0-9_])");
        return message.replaceText(TextReplacementConfig.builder().match(pattern).replacement((result, builder) -> {
            Player target = candidates.get(result.group(1).toLowerCase(Locale.ROOT));
            if (target == null) {
                return builder;
            }
            mentioned.add(target);
            return plugin.messages().component("chat.mention-highlight", "name", target.getName());
        }).build());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.spam().forget(event.getPlayer().getUniqueId());
    }
}
