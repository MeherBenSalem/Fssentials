package dev.nightbeam.donutessentials.economy;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.hook.Hooks;
import dev.nightbeam.donutessentials.module.Module;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Donut Essentials' own economy: balances, /pay, /baltop and /eco, registered with Vault at low priority so another
 * economy plugin wins automatically (the commands then use that one).
 */
public final class EconomyModule implements Module, Listener {

    private final DonutEssentialsPlugin plugin;
    private NativeEconomy economy;
    private Baltop baltop;
    private Object vaultProvider;

    public EconomyModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "economy";
    }

    @Override
    public void enable() {
        economy = new NativeEconomy(plugin);
        economy.settings(EconomySettings.from(plugin.getConfig().getConfigurationSection("economy")));
        try {
            economy.loadAll();
        } catch (Exception error) {
            throw new IllegalStateException("Could not load balances", error);
        }
        if (Hooks.vault()) {
            try {
                vaultProvider = VaultProvider.register(plugin, economy);
            } catch (Throwable error) {
                plugin.getLogger().warning("Could not register the economy with Vault: " + error);
            }
        }
        plugin.economy().own(economy, vaultProvider);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            economy.createAccount(player.getUniqueId());
        }
        baltop = new Baltop(plugin, economy);
        baltop.start(economy.settings().baltopRefreshSeconds());
    }

    @Override
    public void reload() {
        economy.settings(EconomySettings.from(plugin.getConfig().getConfigurationSection("economy")));
        baltop.start(economy.settings().baltopRefreshSeconds());
    }

    @Override
    public void disable() {
        if (baltop != null) {
            baltop.stop();
        }
        if (vaultProvider != null) {
            try {
                VaultProvider.unregister(vaultProvider);
            } catch (Throwable ignored) {
                // Vault may already be gone.
            }
            vaultProvider = null;
        }
        plugin.economy().own(null, null);
        if (economy != null) {
            economy.shutdown();
        }
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new EconomyCommands.Balance(plugin, this), new EconomyCommands.Pay(plugin, this),
                new EconomyCommands.PayToggle(plugin, this), new EconomyCommands.BalTop(plugin, this), new EconomyCommands.Eco(plugin, this));
    }

    @Override
    public List<org.bukkit.event.Listener> listeners() {
        return List.of(this);
    }

    /** Every player gets an account (with the starting balance) the first time they join. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        economy.createAccount(event.getPlayer().getUniqueId());
    }

    /** Our own economy (also when another plugin's economy outranks it in Vault). */
    public NativeEconomy economy() {
        return economy;
    }

    /** The leaderboard (refreshed in the background). */
    public Baltop baltop() {
        return baltop;
    }

    DonutEssentialsPlugin plugin() {
        return plugin;
    }
}
