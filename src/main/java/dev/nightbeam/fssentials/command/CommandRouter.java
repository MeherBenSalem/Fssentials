package dev.nightbeam.fssentials.command;

import dev.nightbeam.fssentials.FssentialsPlugin;
import dev.nightbeam.fssentials.model.InvseeInventoryHolder;
import dev.nightbeam.fssentials.model.Punishment;
import dev.nightbeam.fssentials.model.PunishmentType;
import dev.nightbeam.fssentials.service.MessageService;
import dev.nightbeam.fssentials.service.PunishmentManager;
import dev.nightbeam.fssentials.service.TimeParser;
import dev.nightbeam.fssentials.service.VanishService;
import dev.nightbeam.fssentials.util.FoliaScheduler;
import dev.nightbeam.fssentials.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CommandRouter implements CommandExecutor, TabCompleter, Listener {
    // Increase/decrease this to change pagination density for banlist/history outputs.
    private static final int PAGE_SIZE = 7;

    private final FssentialsPlugin plugin;
    private final MessageService messageService;
    private final PunishmentManager punishmentManager;
    private final VanishService vanishService;
    private final FoliaScheduler foliaScheduler;

    public CommandRouter(FssentialsPlugin plugin, MessageService messageService, PunishmentManager punishmentManager, VanishService vanishService) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.punishmentManager = punishmentManager;
        this.vanishService = vanishService;
        this.foliaScheduler = plugin.getFoliaScheduler();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        try {
            return switch (cmd) {
                case "kick" -> handleKick(sender, args);
                case "ban" -> handleDirectPunishment(sender, args, PunishmentType.BAN, "ban");
                case "mute" -> handleDirectPunishment(sender, args, PunishmentType.MUTE, "mute");
                case "warn" -> handleDirectPunishment(sender, args, PunishmentType.WARN, "warn");
                case "note" -> handleDirectPunishment(sender, args, PunishmentType.NOTE, "note");
                case "banip" -> handleIpPunishment(sender, args, false);
                case "tempban" -> handleTemporaryPunishment(sender, args, PunishmentType.BAN, "tempban");
                case "tempmute" -> handleTemporaryPunishment(sender, args, PunishmentType.MUTE, "tempmute");
                case "tempwarn" -> handleTemporaryPunishment(sender, args, PunishmentType.WARN, "tempwarn");
                case "tempipban" -> handleIpPunishment(sender, args, true);
                case "unban" -> handleUnpunish(sender, args, PunishmentType.BAN);
                case "unmute" -> handleUnpunish(sender, args, PunishmentType.MUTE);
                case "unwarn" -> handleUnpunish(sender, args, PunishmentType.WARN);
                case "unnote" -> handleUnpunish(sender, args, PunishmentType.NOTE);
                case "unpunish" -> handleUnpunish(sender, args, null);
                case "change-reason" -> handleChangeReason(sender, args);
                case "warns" -> handleListByType(sender, args, PunishmentType.WARN, "warns-header");
                case "notes" -> handleListByType(sender, args, PunishmentType.NOTE, "notes-header");
                case "check" -> handleCheck(sender, args);
                case "banlist" -> handleBanlist(sender, args);
                case "history" -> handleHistory(sender, args);
                case "fssentials" -> handleFssentials(sender, args);
                case "systemprefs" -> handleSystemPrefs(sender);
                case "vanish" -> handleVanish(sender);
                case "invsee" -> handleInvsee(sender, args);
                default -> false;
            };
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(messageService.get("errors.invalid-time", Map.of("input", ex.getMessage())));
            return true;
        }
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (!hasPerm(sender, "fssentials.punish.kick")) {
            return true;
        }

        ParseResult parsed = parseSilent(args);
        if (parsed.args.length < 2) {
            sender.sendMessage(messageService.get("usage.kick"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(parsed.args[0]);
        if (target == null) {
            sender.sendMessage(messageService.get("errors.player-not-found", Map.of("player", parsed.args[0])));
            return true;
        }

        String reason = punishmentManager.resolveReasonOrLayout(joinFrom(parsed.args, 1));
        foliaScheduler.runAtEntity(
            target,
            () -> target.kick(LegacyComponentSerializer.legacySection().deserialize(messageService.get("enforcement.kick", Map.of("reason", reason))))
        );

        String announce = messageService.get("punishment.kick", basePlaceholders(sender, target.getName(), reason, "N/A"));
        punishmentManager.announcePunishment(announce, parsed.silent);
        sender.sendMessage(messageService.get("punishment.saved", Map.of("id", "KICK", "type", "KICK", "player", target.getName())));
        return true;
    }

    private boolean handleDirectPunishment(CommandSender sender, String[] args, PunishmentType type, String permNode) {
        if (!hasPerm(sender, "fssentials.punish." + permNode)) {
            return true;
        }

        ParseResult parsed = parseSilent(args);
        if (parsed.args.length < 2) {
            sender.sendMessage(messageService.get("usage." + permNode));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(parsed.args[0]);
        if (target == null || target.getName() == null) {
            sender.sendMessage(messageService.get("errors.player-not-found", Map.of("player", parsed.args[0])));
            return true;
        }

        String reason = punishmentManager.resolveReasonOrLayout(joinFrom(parsed.args, 1));
        Punishment punishment = punishmentManager.createPunishment(
                type,
                target,
                null,
                sender.getName(),
                reason,
                null,
                parsed.silent
        );

        if (type == PunishmentType.BAN) {
            Player online = target.getPlayer();
            if (online != null) {
            foliaScheduler.runAtEntity(
                online,
                () -> online.kick(LegacyComponentSerializer.legacySection().deserialize(messageService.get("enforcement.ban-kick", Map.of(
                    "id", punishment.getId(),
                    "reason", reason,
                    "until", "Never"
                ))))
            );
            }
        }

        if (type == PunishmentType.WARN && target.isOnline()) {
            Player online = target.getPlayer();
            if (online != null) {
                foliaScheduler.runAtEntity(online, () -> online.sendMessage(messageService.get("target.warn", Map.of("player", online.getName(), "reason", reason))));
            }
        }

        String announce = messageService.get("punishment.applied", basePlaceholders(sender, target.getName(), reason, punishment.getId(), type.name(), "Never"));
        punishmentManager.announcePunishment(announce, parsed.silent);
        sender.sendMessage(messageService.get("punishment.saved", Map.of("id", punishment.getId(), "type", type.name(), "player", target.getName())));
        return true;
    }

    private boolean handleTemporaryPunishment(CommandSender sender, String[] args, PunishmentType type, String permNode) {
        if (!hasPerm(sender, "fssentials.punish." + permNode)) {
            return true;
        }

        ParseResult parsed = parseSilent(args);
        if (parsed.args.length < 3) {
            sender.sendMessage(messageService.get("usage." + permNode));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(parsed.args[0]);
        if (target == null || target.getName() == null) {
            sender.sendMessage(messageService.get("errors.player-not-found", Map.of("player", parsed.args[0])));
            return true;
        }

        String durationToken = punishmentManager.resolveTimeLayout(parsed.args[1]);
        long durationMillis = TimeParser.parseDurationMillis(durationToken);
        long expiresAt = System.currentTimeMillis() + durationMillis;
        String reason = punishmentManager.resolveReasonOrLayout(joinFrom(parsed.args, 2));

        Punishment punishment = punishmentManager.createPunishment(
                type,
                target,
                null,
                sender.getName(),
                reason,
                expiresAt,
                parsed.silent
        );

        if (type == PunishmentType.BAN) {
            Player online = target.getPlayer();
            if (online != null) {
            foliaScheduler.runAtEntity(
                online,
                () -> online.kick(LegacyComponentSerializer.legacySection().deserialize(messageService.get("enforcement.ban-kick", Map.of(
                    "id", punishment.getId(),
                    "reason", reason,
                    "until", Text.formatTimestamp(expiresAt)
                ))))
            );
            }
        }

        if (type == PunishmentType.WARN && target.isOnline()) {
            Player online = target.getPlayer();
            if (online != null) {
                foliaScheduler.runAtEntity(online, () -> online.sendMessage(messageService.get("target.warn", Map.of("player", online.getName(), "reason", reason))));
            }
        }

        String announce = messageService.get("punishment.applied", basePlaceholders(
                sender,
                target.getName(),
                reason,
                punishment.getId(),
                type.name(),
                Text.formatTimestamp(expiresAt)
        ));
        punishmentManager.announcePunishment(announce, parsed.silent);
        sender.sendMessage(messageService.get("punishment.saved", Map.of("id", punishment.getId(), "type", type.name(), "player", target.getName())));
        return true;
    }

    private boolean handleIpPunishment(CommandSender sender, String[] args, boolean temporary) {
        String perm = temporary ? "tempipban" : "banip";
        if (!hasPerm(sender, "fssentials.punish." + perm)) {
            return true;
        }

        ParseResult parsed = parseSilent(args);
        int minArgs = temporary ? 3 : 2;
        if (parsed.args.length < minArgs) {
            sender.sendMessage(messageService.get("usage." + perm));
            return true;
        }

        String token = parsed.args[0];
        String ip = null;
        OfflinePlayer player = null;

        if (looksLikeIp(token)) {
            ip = token;
        } else {
            player = resolveOfflinePlayer(token);
            if (player == null || player.getName() == null) {
                sender.sendMessage(messageService.get("errors.player-not-found", Map.of("player", token)));
                return true;
            }
            ip = punishmentManager.findLastKnownIp(player.getUniqueId());
            if (ip == null) {
                sender.sendMessage(messageService.get("errors.ip-not-found", Map.of("player", player.getName())));
                return true;
            }
        }

        Long expiresAt = null;
        String reason;
        if (temporary) {
            String durationToken = punishmentManager.resolveTimeLayout(parsed.args[1]);
            long durationMillis = TimeParser.parseDurationMillis(durationToken);
            expiresAt = System.currentTimeMillis() + durationMillis;
            reason = punishmentManager.resolveReasonOrLayout(joinFrom(parsed.args, 2));
        } else {
            reason = punishmentManager.resolveReasonOrLayout(joinFrom(parsed.args, 1));
        }

        Punishment punishment = punishmentManager.createPunishment(
                PunishmentType.IP_BAN,
                player,
                ip,
                sender.getName(),
                reason,
                expiresAt,
                parsed.silent
        );

        final String resolvedIp = ip;
        final Long resolvedExpiresAt = expiresAt;

        foliaScheduler.runGlobal(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getAddress() != null
                        && online.getAddress().getAddress() != null
                        && resolvedIp.equals(online.getAddress().getAddress().getHostAddress())) {
                    String until = resolvedExpiresAt == null ? "Never" : Text.formatTimestamp(resolvedExpiresAt);
                    foliaScheduler.runAtEntity(
                            online,
                            () -> online.kick(LegacyComponentSerializer.legacySection().deserialize(messageService.get("enforcement.ban-kick", Map.of(
                                    "id", punishment.getId(),
                                    "reason", reason,
                                    "until", until
                            ))))
                    );
                }
            }
        });

        String playerName = player != null ? player.getName() : "IP:" + ip;
        String until = expiresAt == null ? "Never" : Text.formatTimestamp(expiresAt);
        String announce = messageService.get("punishment.applied", basePlaceholders(sender, playerName, reason, punishment.getId(), "IP_BAN", until));
        punishmentManager.announcePunishment(announce, parsed.silent);
        sender.sendMessage(messageService.get("punishment.saved", Map.of("id", punishment.getId(), "type", "IP_BAN", "player", playerName)));
        return true;
    }

    private boolean handleUnpunish(CommandSender sender, String[] args, PunishmentType expected) {
        if (!hasPerm(sender, "fssentials.punish.unpunish")) {
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageService.get("usage.unpunish"));
            return true;
        }

        String token = args[0];
        String id = token.toUpperCase(Locale.ROOT);
        boolean success = expected == null
                ? punishmentManager.deactivatePunishment(id)
                : punishmentManager.deactivateByType(id, expected);

        if (!success && (expected == PunishmentType.BAN || expected == PunishmentType.MUTE)) {
            Optional<String> removedId = punishmentManager.deactivateLatestByTypeAndPlayerName(token, expected);
            if (removedId.isPresent()) {
                sender.sendMessage(messageService.get("punishment.removed", Map.of("id", removedId.get())));
                return true;
            }
        }

        if (!success) {
            sender.sendMessage(messageService.get("errors.invalid-id", Map.of("id", id)));
            return true;
        }

        sender.sendMessage(messageService.get("punishment.removed", Map.of("id", id)));
        return true;
    }

    private boolean handleChangeReason(CommandSender sender, String[] args) {
        if (!hasPerm(sender, "fssentials.punish.change-reason")) {
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageService.get("usage.change-reason"));
            return true;
        }

        String id = args[0].toUpperCase(Locale.ROOT);
        String reason = joinFrom(args, 1);
        if (!punishmentManager.changeReason(id, reason)) {
            sender.sendMessage(messageService.get("errors.invalid-id", Map.of("id", id)));
            return true;
        }

        sender.sendMessage(messageService.get("punishment.reason-updated", Map.of("id", id, "reason", reason)));
        return true;
    }

    private boolean handleListByType(CommandSender sender, String[] args, PunishmentType type, String headerKey) {
        if (!hasPerm(sender, "fssentials.view." + type.name().toLowerCase(Locale.ROOT))) {
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageService.get("usage." + (type == PunishmentType.WARN ? "warns" : "notes")));
            return true;
        }

        List<Punishment> list = punishmentManager.getByTypeForPlayer(args[0], type);
        sender.sendMessage(messageService.get(headerKey, Map.of("player", args[0], "count", String.valueOf(list.size()))));

        if (list.isEmpty()) {
            sender.sendMessage(messageService.get("list.empty"));
            return true;
        }

        for (Punishment punishment : list) {
            sender.sendMessage(formatListLine(punishment));
        }
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!hasPerm(sender, "fssentials.view.check")) {
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageService.get("usage.check"));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[0]);
        if (target == null || target.getName() == null) {
            sender.sendMessage(messageService.get("errors.player-not-found", Map.of("player", args[0])));
            return true;
        }

        String ip = punishmentManager.findLastKnownIp(target.getUniqueId());
        Optional<Punishment> activeBan = punishmentManager.getActiveBan(target.getUniqueId(), ip);
        Optional<Punishment> activeMute = punishmentManager.getActiveMute(target.getUniqueId());

        int warns = punishmentManager.getByTypeForPlayer(target.getName(), PunishmentType.WARN).size();
        int notes = punishmentManager.getByTypeForPlayer(target.getName(), PunishmentType.NOTE).size();

        sender.sendMessage(messageService.get("check.header", Map.of("player", target.getName())));
        sender.sendMessage(messageService.get("check.uuid", Map.of("uuid", target.getUniqueId().toString())));
        sender.sendMessage(messageService.get("check.ip", Map.of("ip", ip == null ? "Unknown" : ip)));
        sender.sendMessage(messageService.get("check.country", Map.of("country", "Unknown (geo lookup disabled)")));
        sender.sendMessage(messageService.get("check.ban", Map.of("status", activeBan.isPresent() ? "&cActive" : "&aNone")));
        sender.sendMessage(messageService.get("check.mute", Map.of("status", activeMute.isPresent() ? "&eActive" : "&aNone")));
        sender.sendMessage(messageService.get("check.warn", Map.of("count", String.valueOf(warns))));
        sender.sendMessage(messageService.get("check.note", Map.of("count", String.valueOf(notes))));
        return true;
    }

    private boolean handleBanlist(CommandSender sender, String[] args) {
        if (!hasPerm(sender, "fssentials.view.banlist")) {
            return true;
        }

        int page = parsePage(args, 1);
        List<Punishment> active = punishmentManager.getActivePunishments();
        List<Punishment> pageItems = punishmentManager.page(active, page, PAGE_SIZE);

        sender.sendMessage(messageService.get("banlist.header", Map.of(
                "page", String.valueOf(page),
                "max", String.valueOf(Math.max(1, punishmentManager.pageCount(active, PAGE_SIZE))),
                "count", String.valueOf(active.size())
        )));

        if (pageItems.isEmpty()) {
            sender.sendMessage(messageService.get("list.empty"));
            return true;
        }

        for (Punishment punishment : pageItems) {
            sender.sendMessage(formatListLine(punishment));
        }
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!hasPerm(sender, "fssentials.view.history")) {
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageService.get("usage.history"));
            return true;
        }

        int page = args.length >= 2 ? parsePage(new String[]{args[1]}, 1) : 1;
        List<Punishment> history = punishmentManager.getHistoryForPlayer(args[0]);
        List<Punishment> pageItems = punishmentManager.page(history, page, PAGE_SIZE);

        sender.sendMessage(messageService.get("history.header", Map.of(
                "player", args[0],
                "page", String.valueOf(page),
                "max", String.valueOf(Math.max(1, punishmentManager.pageCount(history, PAGE_SIZE))),
                "count", String.valueOf(history.size())
        )));

        if (pageItems.isEmpty()) {
            sender.sendMessage(messageService.get("list.empty"));
            return true;
        }

        for (Punishment punishment : pageItems) {
            sender.sendMessage(formatListLine(punishment));
        }
        return true;
    }

    private boolean handleFssentials(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(messageService.get("help.header"));
            // Help text is fully configurable through config.yml help-lines.
            List<String> lines = plugin.getConfig().getStringList("help-lines");
            for (String line : lines) {
                sender.sendMessage(Text.color(line));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!hasPerm(sender, "fssentials.admin.reload")) {
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(messageService.get("admin.reloaded"));
            return true;
        }

        sender.sendMessage(messageService.get("usage.fssentials"));
        return true;
    }

    private boolean handleSystemPrefs(CommandSender sender) {
        if (!hasPerm(sender, "fssentials.admin.systemprefs")) {
            return true;
        }

        sender.sendMessage(messageService.get("system.header"));
        sender.sendMessage(messageService.get("system.version", Map.of("version", plugin.getPluginMeta().getVersion())));
        sender.sendMessage(messageService.get("system.java", Map.of("version", System.getProperty("java.version"))));
        sender.sendMessage(messageService.get("system.players", Map.of("online", String.valueOf(Bukkit.getOnlinePlayers().size()))));
        sender.sendMessage(messageService.get("system.storage", Map.of("state", plugin.getConfig().getBoolean("storage.persistence-enabled", true) ? "Enabled" : "Disabled")));
        return true;
    }

    private boolean handleVanish(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageService.get("errors.player-only"));
            return true;
        }

        if (!hasPerm(sender, "fssentials.vanish")) {
            return true;
        }

        // Folia-safe: toggle and feedback run on the player's own scheduler.
        foliaScheduler.runAtEntity(player, () -> {
            boolean nowVanished = vanishService.toggle(player);
            player.sendMessage(messageService.get(nowVanished ? "vanish.enabled" : "vanish.disabled"));
        });
        return true;
    }

    private boolean handleInvsee(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(messageService.get("errors.player-only"));
            return true;
        }

        if (!hasPerm(sender, "fssentials.invsee")) {
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageService.get("usage.invsee"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(messageService.get("errors.invsee-offline", Map.of("player", args[0])));
            return true;
        }

        // Folia-safe inventory viewing: snapshot target inventory on target entity scheduler.
        foliaScheduler.runAtEntity(target, () -> {
            ItemStack[] storageContents = cloneItems(target.getInventory().getStorageContents());
            ItemStack[] armorContents = cloneItems(target.getInventory().getArmorContents());
            ItemStack offHand = cloneItem(target.getInventory().getItemInOffHand());

            // Then open GUI on the viewer entity scheduler.
            foliaScheduler.runAtEntity(viewer, () -> {
                Inventory inv = Bukkit.createInventory(
                        new InvseeInventoryHolder(target.getUniqueId()),
                        54,
                    Component.text(Text.color("&8InvSee: &e" + target.getName()))
                );

                for (int i = 0; i < Math.min(storageContents.length, 36); i++) {
                    inv.setItem(i, storageContents[i]);
                }

                // Top-right area reserved for equipped items.
                inv.setItem(45, armorContents.length > 3 ? armorContents[3] : null); // Helmet
                inv.setItem(46, armorContents.length > 2 ? armorContents[2] : null); // Chestplate
                inv.setItem(47, armorContents.length > 1 ? armorContents[1] : null); // Leggings
                inv.setItem(48, armorContents.length > 0 ? armorContents[0] : null); // Boots
                inv.setItem(49, offHand); // Off-hand

                viewer.openInventory(inv);
                viewer.sendMessage(messageService.get("invsee.opened", Map.of("player", target.getName())));
            });
        });

        return true;
    }

    private ParseResult parseSilent(String[] args) {
        if (args.length == 0) {
            return new ParseResult(false, args);
        }
        if (!"-s".equalsIgnoreCase(args[0])) {
            return new ParseResult(false, args);
        }
        String[] shifted = Arrays.copyOfRange(args, 1, args.length);
        return new ParseResult(true, shifted);
    }

    private String joinFrom(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    private boolean hasPerm(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(messageService.get("errors.no-permission"));
        return false;
    }

    private String formatListLine(Punishment punishment) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", punishment.getId());
        placeholders.put("type", punishment.getType().name());
        placeholders.put("player", punishment.getTargetName());
        placeholders.put("reason", punishment.getReason());
        placeholders.put("actor", punishment.getActor());
        placeholders.put("created", Text.formatTimestamp(punishment.getCreatedAt()));
        placeholders.put("until", punishment.getExpiresAt() == null ? "Never" : Text.formatTimestamp(punishment.getExpiresAt()));
        placeholders.put("active", punishment.isActive() ? "&aACTIVE" : "&7INACTIVE");
        return messageService.get("list.line", placeholders);
    }

    private Map<String, String> basePlaceholders(CommandSender sender, String player, String reason, String id) {
        return basePlaceholders(sender, player, reason, id, "UNKNOWN", "Never");
    }

    private Map<String, String> basePlaceholders(CommandSender sender, String player, String reason, String id, String type, String until) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("staff", sender.getName());
        placeholders.put("player", player);
        placeholders.put("reason", reason);
        placeholders.put("id", id);
        placeholders.put("type", type);
        placeholders.put("until", until);
        return placeholders;
    }

    private int parsePage(String[] args, int defaultPage) {
        if (args.length < 1) {
            return defaultPage;
        }
        try {
            return Math.max(1, Integer.parseInt(args[0]));
        } catch (NumberFormatException ex) {
            return defaultPage;
        }
    }

    private boolean looksLikeIp(String value) {
        try {
            InetAddress.getByName(value);
            return value.chars().filter(ch -> ch == '.').count() == 3;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        // Folia-safe optimization: avoid blocking profile lookups by using cached player data only.
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private ItemStack[] cloneItems(ItemStack[] input) {
        ItemStack[] copy = new ItemStack[input.length];
        for (int i = 0; i < input.length; i++) {
            copy[i] = cloneItem(input[i]);
        }
        return copy;
    }

    private ItemStack cloneItem(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInvseeClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvseeInventoryHolder holder)) {
            return;
        }

        Player target = Bukkit.getPlayer(holder.getTargetUuid());
        if (target == null || !target.isOnline()) {
            return;
        }

        Inventory inv = event.getView().getTopInventory();
        ItemStack[] storage = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            storage[i] = cloneItem(inv.getItem(i));
        }

        ItemStack[] armor = new ItemStack[4];
        armor[3] = cloneItem(inv.getItem(45)); // Helmet
        armor[2] = cloneItem(inv.getItem(46)); // Chestplate
        armor[1] = cloneItem(inv.getItem(47)); // Leggings
        armor[0] = cloneItem(inv.getItem(48)); // Boots
        ItemStack offHand = cloneItem(inv.getItem(49));

        foliaScheduler.runAtEntity(target, () -> {
            target.getInventory().setStorageContents(storage);
            target.getInventory().setArmorContents(armor);
            target.getInventory().setItemInOffHand(offHand);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvseeClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvseeInventoryHolder)) {
            return;
        }

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            int raw = event.getRawSlot();
            if (!isInvseeEditableSlot(raw)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvseeDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvseeInventoryHolder)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize() && !isInvseeEditableSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isInvseeEditableSlot(int rawSlot) {
        return (rawSlot >= 0 && rawSlot <= 35) || (rawSlot >= 45 && rawSlot <= 49);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            if (command.getName().equalsIgnoreCase("invsee")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }

            values.add("-s");
            values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            return values.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }

        if (args.length == 2 && command.getName().startsWith("temp")) {
            return Arrays.asList("30m", "1h", "1d", "7d", "1mo", "#default");
        }

        return List.of();
    }

    private record ParseResult(boolean silent, String[] args) {
    }
}
