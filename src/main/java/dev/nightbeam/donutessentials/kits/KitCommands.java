package dev.nightbeam.donutessentials.kits;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.util.Amounts;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** /kit, /kits, /createkit, /editkit and /delkit. */
final class KitCommands {

    static final String OTHERS = "donutessentials.kit.others";

    private KitCommands() {
    }

    /** The money text for a kit's cost ("Free" when there is none or no economy). */
    static String price(DonutEssentialsPlugin plugin, double cost) {
        return cost > 0 && plugin.economy().available() ? plugin.economy().format(cost) : plugin.messages().raw("kits.free");
    }

    static String cooldownText(DonutEssentialsPlugin plugin, long seconds) {
        return KitTimes.describe(seconds, plugin.messages().raw("kits.cooldown-none"), plugin.messages().raw("kits.cooldown-once"));
    }

    /** Tells the sender the kit does not exist; returns null then. */
    static Kit find(DonutEssentialsPlugin plugin, KitsModule module, CommandSender sender, String name) {
        Kit kit = module.kits().kit(name);
        if (kit == null) {
            plugin.messages().send(sender, "kits.not-found", "kit", name);
        }
        return kit;
    }

    static final class KitCommand extends EssCommand {
        private final KitsModule module;

        KitCommand(DonutEssentialsPlugin plugin, KitsModule module) {
            super(plugin, "kit");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                if (sender instanceof Player player) {
                    new KitsMenu(plugin, module, player).open();
                } else {
                    list(plugin, module, sender);
                }
                return;
            }
            Kit kit = find(plugin, module, sender, args[0]);
            if (kit == null) {
                return;
            }
            if (args.length > 1) {
                if (!allowed(sender, OTHERS)) {
                    return;
                }
                Player target = online(sender, args[1]);
                if (target == null) {
                    return;
                }
                plugin.scheduler().forPlayer(target, () -> {
                    if (module.kits().grant(target, kit, false)) {
                        send(target, "kits.received", "kit", kit.name());
                        send(sender, "kits.given", "kit", kit.name(), "player", target.getName());
                    } else {
                        send(sender, "kits.give-failed", "kit", kit.name(), "player", target.getName());
                    }
                });
                return;
            }
            Player player = player(sender);
            if (player != null) {
                module.kits().claim(player, kit);
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                return filter(module.kits().names(), args[0]);
            }
            return args.length == 2 && sender.hasPermission(OTHERS) ? players(sender, args[1]) : List.of();
        }
    }

    static void list(DonutEssentialsPlugin plugin, KitsModule module, CommandSender sender) {
        List<String> names = module.kits().names();
        if (names.isEmpty()) {
            plugin.messages().send(sender, "kits.none");
        } else {
            plugin.messages().send(sender, "kits.list", "kits", String.join(", ", names));
        }
    }

    static final class KitsCommand extends EssCommand {
        private final KitsModule module;

        KitsCommand(DonutEssentialsPlugin plugin, KitsModule module) {
            super(plugin, "kits");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (sender instanceof Player player) {
                new KitsMenu(plugin, module, player).open();
            } else {
                list(plugin, module, sender);
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    static final class CreateKit extends EssCommand {
        private final KitsModule module;

        CreateKit(DonutEssentialsPlugin plugin, KitsModule module) {
            super(plugin, "createkit");
            this.module = module;
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
            String name = args[0];
            if (!KitService.NAME.matcher(name).matches()) {
                send(sender, "kits.invalid-name");
                return;
            }
            if (module.kits().kit(name) != null) {
                send(sender, "kits.exists", "kit", name);
                return;
            }
            long cooldown = 0;
            if (args.length > 1) {
                try {
                    cooldown = KitTimes.parse(args[1]);
                } catch (IllegalArgumentException invalid) {
                    send(sender, "kits.invalid-cooldown", "value", args[1]);
                    return;
                }
            }
            double cost = 0;
            if (args.length > 2 && !args[2].trim().matches("0+([.,]0*)?")) {
                cost = Amounts.parse(args[2]);
                if (cost <= 0) {
                    send(sender, "kits.invalid-cost", "value", args[2]);
                    return;
                }
            }
            PlayerInventory inventory = player.getInventory();
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack item : inventory.getContents()) {
                if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                    items.add(item.clone());
                }
            }
            if (items.isEmpty()) {
                send(sender, "kits.empty-inventory");
                return;
            }
            ItemStack held = inventory.getItemInMainHand();
            String icon = (held.getType() != Material.AIR ? held.getType() : items.get(0).getType()).name();
            Kit kit = Kit.of(name, items, cooldown, cost, icon);
            module.kits().save(kit);
            int count = 0;
            for (ItemStack item : items) {
                count += item.getAmount();
            }
            send(sender, "kits.created", "kit", name, "count", count, "cooldown", cooldownText(plugin, cooldown),
                    "cost", price(plugin, cost));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 2) {
                return filter(List.of("0", "30m", "1h", "1d", "7d", "once"), args[1]);
            }
            return args.length == 3 ? filter(List.of("0", "100", "1k"), args[2]) : List.of();
        }
    }

    static final class EditKit extends EssCommand {
        private final KitsModule module;

        EditKit(DonutEssentialsPlugin plugin, KitsModule module) {
            super(plugin, "editkit");
            this.module = module;
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
            Kit kit = find(plugin, module, sender, args[0]);
            if (kit != null) {
                module.editor().open(player, kit);
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? filter(module.kits().names(), args[0]) : List.of();
        }
    }

    static final class DelKit extends EssCommand {
        private final KitsModule module;

        DelKit(DonutEssentialsPlugin plugin, KitsModule module) {
            super(plugin, "delkit");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            Kit kit = find(plugin, module, sender, args[0]);
            if (kit != null) {
                module.kits().delete(kit);
                send(sender, "kits.deleted", "kit", kit.name());
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? filter(module.kits().names(), args[0]) : List.of();
        }
    }
}
