package dev.nightbeam.donutessentials.rules;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /rules (with accept and decline for the rules screen) and /motd. */
final class RulesCommands {

    private RulesCommands() {
    }

    static final class Rules extends EssCommand {
        private final RulesModule module;

        Rules(DonutEssentialsPlugin plugin, RulesModule module) {
            super(plugin, "rules");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            RulesService rules = module.rules();
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            if (sender instanceof Player player && (sub.equals("accept") || sub.equals("decline"))) {
                if (sub.equals("accept")) {
                    rules.accept(player);
                } else {
                    rules.decline(player);
                }
                return;
            }
            if (sender instanceof Player player && rules.isPending(player)) {
                rules.prompt(player);
                return;
            }
            rules.show(sender);
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1 && sender instanceof Player player && module.rules().isPending(player)) {
                return filter(List.of("accept", "decline"), args[0]);
            }
            return List.of();
        }
    }

    static final class Motd extends EssCommand {
        private final RulesModule module;

        Motd(DonutEssentialsPlugin plugin, RulesModule module) {
            super(plugin, "motd");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            module.sendMotd(sender);
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }
}
