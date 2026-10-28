package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/** /fly, /god, /heal, /feed, /speed and the game mode commands. */
final class StateCommands {

    private StateCommands() {
    }

    static boolean flightFree(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    static final class Fly extends TargetCommand {
        Fly(DonutEssentialsPlugin plugin) {
            super(plugin, "fly", 0);
        }

        @Override
        protected void apply(CommandSender sender, Player target, String[] args, boolean self) {
            PlayerData data = plugin.players().get(target);
            boolean on = !data.has(PlayerFlag.FLY) && !(target.getAllowFlight() && !flightFree(target));
            data.set(PlayerFlag.FLY, on);
            plugin.players().save(data);
            if (!flightFree(target)) {
                target.setAllowFlight(on);
                if (!on) {
                    target.setFlying(false);
                }
            }
            tell(sender, target, self, on ? "fly-on" : "fly-off");
        }
    }

    static final class God extends TargetCommand {
        God(DonutEssentialsPlugin plugin) {
            super(plugin, "god", 0);
        }

        @Override
        protected void apply(CommandSender sender, Player target, String[] args, boolean self) {
            PlayerData data = plugin.players().get(target);
            boolean on = data.toggle(PlayerFlag.GOD);
            plugin.players().save(data);
            if (on) {
                target.setFireTicks(0);
                target.setRemainingAir(target.getMaximumAir());
            }
            tell(sender, target, self, on ? "god-on" : "god-off");
        }
    }

    static final class Heal extends TargetCommand {
        Heal(DonutEssentialsPlugin plugin) {
            super(plugin, "heal", 0);
        }

        @Override
        @SuppressWarnings("deprecation")
        protected void apply(CommandSender sender, Player target, String[] args, boolean self) {
            if (target.isDead()) {
                plugin.messages().send(sender, "utility.heal-dead", "player", target.getName());
                return;
            }
            target.setHealth(target.getMaxHealth());
            target.setFoodLevel(20);
            target.setSaturation(20f);
            target.setExhaustion(0f);
            target.setFireTicks(0);
            target.setRemainingAir(target.getMaximumAir());
            target.removePotionEffect(PotionEffectType.POISON);
            target.removePotionEffect(PotionEffectType.WITHER);
            plugin.sounds().play(target, "heal");
            tell(sender, target, self, "healed");
        }
    }

    static final class Feed extends TargetCommand {
        Feed(DonutEssentialsPlugin plugin) {
            super(plugin, "feed", 0);
        }

        @Override
        protected void apply(CommandSender sender, Player target, String[] args, boolean self) {
            target.setFoodLevel(20);
            target.setSaturation(20f);
            target.setExhaustion(0f);
            tell(sender, target, self, "fed");
        }
    }

    /** /speed <1-10> [walk|fly] [player]. Without a type, the speed the player is using now. */
    static final class Speed extends TargetCommand {
        Speed(DonutEssentialsPlugin plugin) {
            super(plugin, "speed", 2);
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            int level = Speeds.parseLevel(args[0]);
            if (level < 0) {
                plugin.messages().send(sender, "utility.speed-invalid");
                return;
            }
            String type = null;
            int targetAt = 1;
            if (args.length > 1 && (args[1].equalsIgnoreCase("walk") || args[1].equalsIgnoreCase("fly"))) {
                type = args[1].toLowerCase(Locale.ROOT);
                targetAt = 2;
            }
            Player target = target(sender, args, targetAt);
            if (target == null) {
                return;
            }
            boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
            String chosen = type;
            plugin.scheduler().forPlayer(target, () -> {
                boolean fly = chosen == null ? target.isFlying() : chosen.equals("fly");
                if (fly) {
                    target.setFlySpeed(Speeds.fly(level));
                } else {
                    target.setWalkSpeed(Speeds.walk(level));
                }
                tell(sender, target, self, "speed-set", "type", plugin.messages().component(fly ? "utility.speed-fly" : "utility.speed-walk"),
                        "speed", level);
            });
        }

        @Override
        protected void apply(CommandSender sender, Player target, String[] args, boolean self) {
            // run() handles the arguments.
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                List<String> levels = new ArrayList<>();
                for (int i = 1; i <= 10; i++) {
                    levels.add(String.valueOf(i));
                }
                return filter(levels, args[0]);
            }
            if (args.length == 2) {
                List<String> options = new ArrayList<>(filter(List.of("walk", "fly"), args[1]));
                if (sender.hasPermission(othersPermission())) {
                    options.addAll(players(sender, args[1]));
                }
                return options;
            }
            if (args.length == 3 && sender.hasPermission(othersPermission())) {
                return players(sender, args[2]);
            }
            return List.of();
        }
    }

    /**
     * /gm <mode> [player], and /gmc, /gms, /gma, /gmsp with the mode built in. Each mode also needs
     * {@code donutessentials.gamemode.<mode>}.
     */
    static final class GameModeCommand extends TargetCommand {
        private final GameMode fixed;

        GameModeCommand(DonutEssentialsPlugin plugin, String name, GameMode fixed) {
            super(plugin, name, fixed == null ? 1 : 0);
            this.fixed = fixed;
        }

        @Override
        protected String othersPermission() {
            return "donutessentials.gamemode.others";
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            GameMode mode = fixed;
            if (mode == null) {
                if (args.length == 0) {
                    usage(sender, label);
                    return;
                }
                mode = Speeds.gameMode(args[0]);
                if (mode == null) {
                    plugin.messages().send(sender, "utility.gamemode-invalid", "mode", args[0]);
                    return;
                }
            }
            if (!allowed(sender, "donutessentials.gamemode." + Speeds.key(mode))) {
                return;
            }
            super.run(sender, label, args);
        }

        @Override
        protected void apply(CommandSender sender, Player target, String[] args, boolean self) {
            GameMode mode = fixed != null ? fixed : Speeds.gameMode(args[0]);
            target.setGameMode(mode);
            PlayerData data = plugin.players().get(target);
            if (data.has(PlayerFlag.FLY) && !flightFree(target)) {
                target.setAllowFlight(true);
            }
            tell(sender, target, self, "gamemode-set", "mode", plugin.messages().component("utility.gamemodes." + Speeds.key(mode)));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (fixed == null && args.length == 1) {
                return filter(List.of("survival", "creative", "adventure", "spectator"), args[0]);
            }
            return super.complete(sender, label, args);
        }
    }
}
