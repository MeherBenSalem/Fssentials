package dev.nightbeam.donutessentials.economy;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.api.event.PaymentEvent;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.hook.Economy;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import dev.nightbeam.donutessentials.util.Amounts;
import dev.nightbeam.donutessentials.util.Text;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /balance, /pay, /paytoggle, /baltop and /eco. They use whichever economy is active (ours or another plugin's). */
final class EconomyCommands {

    static final String OTHERS = "donutessentials.balance.others";

    private EconomyCommands() {
    }

    /** The economy is there; tells the sender when it is not. */
    static boolean ready(DonutEssentialsPlugin plugin, CommandSender sender) {
        if (plugin.economy().available()) {
            return true;
        }
        plugin.messages().send(sender, "economy.no-economy");
        return false;
    }

    static String money(DonutEssentialsPlugin plugin, double amount) {
        return plugin.economy().format(amount);
    }

    static final class Balance extends EssCommand {
        Balance(DonutEssentialsPlugin plugin, EconomyModule module) {
            super(plugin, "balance");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (!ready(plugin, sender)) {
                return;
            }
            if (args.length == 0 || (sender instanceof Player self && args[0].equalsIgnoreCase(self.getName()))) {
                Player player = player(sender);
                if (player != null) {
                    send(player, "economy.balance", "balance", money(plugin, plugin.economy().balance(player)));
                }
                return;
            }
            if (!allowed(sender, OTHERS)) {
                return;
            }
            offline(sender, args[0], data -> send(sender, "economy.balance-other", "player", data.name(),
                    "balance", money(plugin, plugin.economy().balance(Bukkit.getOfflinePlayer(data.uuid())))));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 && sender.hasPermission(OTHERS) ? players(sender, args[0]) : List.of();
        }
    }

    static final class Pay extends EssCommand {
        private final EconomyModule module;

        Pay(DonutEssentialsPlugin plugin, EconomyModule module) {
            super(plugin, "pay");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player payer = player(sender);
            if (payer == null) {
                return;
            }
            if (args.length < 2) {
                usage(sender, label);
                return;
            }
            if (!ready(plugin, sender)) {
                return;
            }
            double amount = Amounts.parse(args[1]);
            if (amount <= 0) {
                send(sender, "economy.invalid-amount", "amount", args[1]);
                return;
            }
            double min = module.economy().settings().minPayment();
            if (amount < min) {
                send(sender, "economy.min-payment", "amount", money(plugin, min));
                return;
            }
            offline(payer, args[0], target -> pay(payer, target, amount));
        }

        private void pay(Player payer, PlayerData target, double requested) {
            if (target.uuid().equals(payer.getUniqueId())) {
                send(payer, "economy.pay-self");
                return;
            }
            if (target.has(PlayerFlag.PAY_OFF)) {
                send(payer, "economy.pay-refused", "player", target.name());
                return;
            }
            OfflinePlayer to = Bukkit.getOfflinePlayer(target.uuid());
            PaymentEvent event = new PaymentEvent(payer, to, requested);
            plugin.getServer().getPluginManager().callEvent(event);
            double amount = event.getAmount();
            if (event.isCancelled() || !(amount > 0)) {
                return;
            }
            Economy economy = plugin.economy();
            if (!economy.has(payer, amount)) {
                send(payer, "economy.not-enough", "amount", money(plugin, amount), "balance", money(plugin, economy.balance(payer)));
                return;
            }
            if (plugin.economy().usingOwn()) {
                NativeEconomy own = module.economy();
                NativeEconomy.Result taken = own.withdraw(payer.getUniqueId(), amount, "/pay to " + target.name());
                if (taken != NativeEconomy.Result.OK) {
                    send(payer, "economy.not-enough", "amount", money(plugin, amount), "balance", money(plugin, own.balance(payer.getUniqueId())));
                    return;
                }
                NativeEconomy.Result given = own.deposit(target.uuid(), amount, "/pay from " + payer.getName());
                if (given != NativeEconomy.Result.OK) {
                    own.deposit(payer.getUniqueId(), amount, "/pay refund");
                    send(payer, given == NativeEconomy.Result.OVER_LIMIT ? "economy.over-limit" : "economy.pay-failed", "player", target.name());
                    return;
                }
            } else {
                if (!economy.withdraw(payer, amount)) {
                    send(payer, "economy.pay-failed", "player", target.name());
                    return;
                }
                if (!economy.deposit(to, amount)) {
                    economy.deposit(payer, amount);
                    send(payer, "economy.pay-failed", "player", target.name());
                    return;
                }
            }
            String formatted = money(plugin, amount);
            send(payer, "economy.paid", "player", target.name(), "amount", formatted, "balance", money(plugin, economy.balance(payer)));
            plugin.sounds().play(payer, "payment-sent");
            Player online = Bukkit.getPlayer(target.uuid());
            if (online != null) {
                plugin.scheduler().forPlayer(online, () -> {
                    send(online, "economy.received", "player", payer.getName(), "amount", formatted);
                    plugin.sounds().play(online, "payment-received");
                });
            } else {
                String body = messages().raw("economy.received-offline")
                        .replace("<player>", Text.escape(payer.getName()))
                        .replace("<amount>", Text.escape(formatted));
                plugin.notices().add(target.uuid(), "payment", body);
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                return players(sender, args[0]);
            }
            return args.length == 2 ? filter(List.of("100", "1k", "10k"), args[1]) : List.of();
        }
    }

    static final class PayToggle extends EssCommand {
        PayToggle(DonutEssentialsPlugin plugin, EconomyModule module) {
            super(plugin, "paytoggle");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            PlayerData data = plugin.players().get(player);
            boolean refused = data.toggle(PlayerFlag.PAY_OFF);
            plugin.players().save(data);
            send(player, refused ? "economy.paytoggle-off" : "economy.paytoggle-on");
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    static final class BalTop extends EssCommand {
        private static final int PER_PAGE = 10;
        private final EconomyModule module;

        BalTop(DonutEssentialsPlugin plugin, EconomyModule module) {
            super(plugin, "baltop");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (!plugin.economy().usingOwn()) {
                send(sender, "economy.baltop-unavailable", "economy", plugin.economy().name());
                return;
            }
            boolean chat = args.length > 0 && args[0].equalsIgnoreCase("chat");
            if (sender instanceof Player player && !chat) {
                new BaltopMenu(plugin, module, player).open();
                return;
            }
            int page = 1;
            String pageArg = chat ? (args.length > 1 ? args[1] : null) : (args.length > 0 ? args[0] : null);
            if (pageArg != null) {
                try {
                    page = Math.max(1, Integer.parseInt(pageArg));
                } catch (NumberFormatException ignored) {
                    page = 1;
                }
            }
            List<Baltop.Entry> entries = module.baltop().entries();
            if (entries.isEmpty()) {
                send(sender, "economy.baltop-empty");
                return;
            }
            int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
            page = Math.min(page, pages);
            messages().sendPlain(sender, "economy.baltop-header", "total", money(plugin, module.baltop().total()),
                    "page", page, "pages", pages);
            for (int i = (page - 1) * PER_PAGE; i < Math.min(entries.size(), page * PER_PAGE); i++) {
                Baltop.Entry entry = entries.get(i);
                messages().sendPlain(sender, "economy.baltop-line", "rank", entry.rank(), "player", entry.name(),
                        "balance", money(plugin, entry.balance()));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? filter(List.of("chat"), args[0]) : List.of();
        }
    }

    static final class Eco extends EssCommand {
        private static final List<String> ACTIONS = List.of("give", "take", "set", "reset");
        private final EconomyModule module;

        Eco(DonutEssentialsPlugin plugin, EconomyModule module) {
            super(plugin, "eco");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length < 2 || !ACTIONS.contains(args[0].toLowerCase(Locale.ROOT))) {
                usage(sender, label);
                return;
            }
            if (!ready(plugin, sender)) {
                return;
            }
            String action = args[0].toLowerCase(Locale.ROOT);
            double amount;
            if (action.equals("reset")) {
                amount = module.economy().settings().startingBalance();
            } else if (args.length < 3) {
                usage(sender, label);
                return;
            } else if (action.equals("set") && args[2].trim().matches("0+([.,]0*)?")) {
                amount = 0;
            } else {
                amount = Amounts.parse(args[2]);
                if (amount <= 0) {
                    send(sender, "economy.invalid-amount", "amount", args[2]);
                    return;
                }
            }
            double value = amount;
            offline(sender, args[1], data -> apply(sender, action, data, value));
        }

        private void apply(CommandSender sender, String action, PlayerData data, double amount) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(data.uuid());
            Economy economy = plugin.economy();
            String reason = "/eco " + action + " by " + sender.getName();
            boolean ok;
            if (plugin.economy().usingOwn()) {
                NativeEconomy own = module.economy();
                NativeEconomy.Result result = switch (action) {
                    case "give" -> own.deposit(data.uuid(), amount, reason);
                    case "take" -> own.withdraw(data.uuid(), amount, reason);
                    default -> own.set(data.uuid(), amount, reason);
                };
                ok = result == NativeEconomy.Result.OK;
            } else {
                double current = economy.balance(target);
                double change = switch (action) {
                    case "give" -> amount;
                    case "take" -> -amount;
                    default -> amount - current;
                };
                ok = change >= 0 ? economy.deposit(target, change) : economy.withdraw(target, -change);
            }
            if (!ok) {
                send(sender, "economy.eco-failed", "player", data.name());
                return;
            }
            send(sender, "economy.eco-" + action, "player", data.name(), "amount", money(plugin, amount),
                    "balance", money(plugin, economy.balance(target)));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                return filter(ACTIONS, args[0]);
            }
            if (args.length == 2) {
                return players(sender, args[1]);
            }
            return args.length == 3 && !args[0].equalsIgnoreCase("reset") ? filter(List.of("100", "1k", "10k"), args[2]) : List.of();
        }
    }
}
