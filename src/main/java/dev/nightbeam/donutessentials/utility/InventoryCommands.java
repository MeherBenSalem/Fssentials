package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** /craft, /enderchest, /trash, /hat, /repair and /clearinventory. */
final class InventoryCommands {

    private InventoryCommands() {
    }

    /** A command for the sender only, with no arguments. */
    abstract static class SelfCommand extends EssCommand {
        SelfCommand(DonutEssentialsPlugin plugin, String name) {
            super(plugin, name);
        }

        abstract void apply(Player player, String[] args);

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player != null) {
                apply(player, args);
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    static final class Craft extends SelfCommand {
        Craft(DonutEssentialsPlugin plugin) {
            super(plugin, "craft");
        }

        @Override
        void apply(Player player, String[] args) {
            player.openWorkbench(null, true);
        }
    }

    static final class EnderChest extends SelfCommand {
        EnderChest(DonutEssentialsPlugin plugin) {
            super(plugin, "enderchest");
        }

        @Override
        void apply(Player player, String[] args) {
            player.openInventory(player.getEnderChest());
            plugin.sounds().play(player, "enderchest");
        }
    }

    /** A chest whose contents are thrown away when it closes. */
    static final class Trash extends SelfCommand {
        Trash(DonutEssentialsPlugin plugin) {
            super(plugin, "trash");
        }

        @Override
        void apply(Player player, String[] args) {
            player.openInventory(Bukkit.createInventory(null, 36, plugin.messages().component("utility.trash-title")));
        }
    }

    static final class Hat extends SelfCommand {
        Hat(DonutEssentialsPlugin plugin) {
            super(plugin, "hat");
        }

        @Override
        void apply(Player player, String[] args) {
            PlayerInventory inventory = player.getInventory();
            ItemStack hand = inventory.getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                plugin.messages().send(player, "utility.hat-empty");
                return;
            }
            ItemStack helmet = inventory.getHelmet();
            if (helmet != null && helmet.containsEnchantment(Enchantment.BINDING_CURSE)
                    && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                plugin.messages().send(player, "utility.hat-cursed");
                return;
            }
            inventory.setHelmet(hand);
            inventory.setItemInMainHand(helmet == null ? new ItemStack(Material.AIR) : helmet);
            plugin.messages().send(player, "utility.hat");
        }
    }

    /** /repair: the held item; /repair all (donutessentials.repair.all): everything you carry and wear. */
    static final class Repair extends EssCommand {
        Repair(DonutEssentialsPlugin plugin) {
            super(plugin, "repair");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
                if (!allowed(sender, "donutessentials.repair.all")) {
                    return;
                }
                int repaired = 0;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (repair(item)) {
                        repaired++;
                    }
                }
                send(player, repaired == 0 ? "utility.repair-nothing" : "utility.repaired-all", "count", repaired);
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                send(player, "utility.repair-empty");
                return;
            }
            send(player, repair(hand) ? "utility.repaired" : "utility.repair-not-damaged");
        }

        /** Removes all damage from an item; true when it had any. */
        static boolean repair(ItemStack item) {
            if (item == null || item.getType() == Material.AIR) {
                return false;
            }
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof Damageable damageable) || !damageable.hasDamage() || damageable.getDamage() <= 0) {
                return false;
            }
            damageable.setDamage(0);
            item.setItemMeta(meta);
            return true;
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 && sender.hasPermission("donutessentials.repair.all") ? filter(List.of("all"), args[0]) : List.of();
        }
    }

    static final class ClearInventory extends TargetCommand {
        ClearInventory(DonutEssentialsPlugin plugin) {
            super(plugin, "clearinventory", 0);
        }

        @Override
        protected void apply(CommandSender sender, Player target, String[] args, boolean self) {
            target.getInventory().clear();
            target.setItemOnCursor(null);
            tell(sender, target, self, "cleared");
        }
    }
}
