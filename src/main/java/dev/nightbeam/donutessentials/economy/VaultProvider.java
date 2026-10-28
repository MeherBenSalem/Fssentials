package dev.nightbeam.donutessentials.economy;

import java.util.List;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

/**
 * Donut Essentials' economy as seen by other plugins through Vault. Registered at low priority, so a dedicated
 * economy plugin outranks it. Per-world balances and banks are not supported. Only loaded when Vault is installed.
 */
@SuppressWarnings("deprecation")
final class VaultProvider implements Economy {

    private final Plugin plugin;
    private volatile NativeEconomy economy;

    private VaultProvider(Plugin plugin, NativeEconomy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    /** Registers with Bukkit's services and returns the provider (to unregister later). */
    static Object register(Plugin plugin, NativeEconomy economy) {
        VaultProvider provider = new VaultProvider(plugin, economy);
        Bukkit.getServicesManager().register(Economy.class, provider, plugin, ServicePriority.Low);
        return provider;
    }

    /**
     * Registers before the economy exists (while plugins load), so plugins that look Vault's economy up when they
     * enable find it even when they enable before Donut Essentials. Until {@link #attach} it reports itself disabled.
     */
    static Object registerEarly(Plugin plugin) {
        return register(plugin, null);
    }

    /** Connects an early registration to the economy once it is loaded. */
    static void attach(Object provider, NativeEconomy economy) {
        if (provider instanceof VaultProvider vault) {
            vault.economy = economy;
        }
    }

    private static EconomyResponse starting() {
        return new EconomyResponse(0, 0, ResponseType.FAILURE, "Donut Essentials is still starting");
    }

    static void unregister(Object provider) {
        if (provider instanceof VaultProvider vault) {
            Bukkit.getServicesManager().unregister(Economy.class, vault);
        }
    }

    /** A player by name: online first, then the server's cache of known players (never a web lookup). */
    private static OfflinePlayer byName(String name) {
        if (name == null) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }

    private EconomyResponse response(NativeEconomy.Result result, double amount, OfflinePlayer player) {
        NativeEconomy economy = this.economy;
        double balance = player == null || economy == null ? 0 : economy.balance(player.getUniqueId());
        return switch (result) {
            case OK -> new EconomyResponse(amount, balance, ResponseType.SUCCESS, null);
            case NOT_ENOUGH -> new EconomyResponse(0, balance, ResponseType.FAILURE, "Insufficient funds");
            case OVER_LIMIT -> new EconomyResponse(0, balance, ResponseType.FAILURE, "Balance limit reached");
            case INVALID -> new EconomyResponse(0, balance, ResponseType.FAILURE, "Invalid amount");
        };
    }

    private static EconomyResponse unknown() {
        return new EconomyResponse(0, 0, ResponseType.FAILURE, "Unknown player");
    }

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Donut Essentials has no banks");
    }

    @Override
    public boolean isEnabled() {
        NativeEconomy economy = this.economy;
        return plugin.isEnabled() && economy != null && economy.available();
    }

    @Override
    public String getName() {
        return "DonutEssentials";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        NativeEconomy economy = this.economy;
        return economy == null ? 2 : economy.settings().decimals();
    }

    @Override
    public String format(double amount) {
        NativeEconomy economy = this.economy;
        return economy == null ? String.format(java.util.Locale.ROOT, "%.2f", amount) : economy.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        NativeEconomy economy = this.economy;
        return economy == null ? "" : economy.settings().namePlural();
    }

    @Override
    public String currencyNameSingular() {
        NativeEconomy economy = this.economy;
        return economy == null ? "" : economy.settings().nameSingular();
    }

    @Override
    public boolean hasAccount(String playerName) {
        OfflinePlayer player = byName(playerName);
        return player != null && hasAccount(player);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        NativeEconomy economy = this.economy;
        return player != null && economy != null && economy.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = byName(playerName);
        return player == null ? 0 : getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        NativeEconomy economy = this.economy;
        return player == null || economy == null ? 0 : economy.balance(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        OfflinePlayer player = byName(playerName);
        return player != null && has(player, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        NativeEconomy economy = this.economy;
        return player != null && economy != null && economy.balance(player.getUniqueId()) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = byName(playerName);
        return player == null ? unknown() : withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return unknown();
        }
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Cannot withdraw negative funds");
        }
        NativeEconomy economy = this.economy;
        if (economy == null) {
            return starting();
        }
        return response(economy.withdraw(player.getUniqueId(), amount, "Vault"), amount, player);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = byName(playerName);
        return player == null ? unknown() : depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return unknown();
        }
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Cannot deposit negative funds");
        }
        NativeEconomy economy = this.economy;
        if (economy == null) {
            return starting();
        }
        return response(economy.deposit(player.getUniqueId(), amount, "Vault"), amount, player);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        OfflinePlayer player = byName(playerName);
        return player != null && createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        NativeEconomy economy = this.economy;
        return player != null && economy != null && economy.createAccount(player.getUniqueId());
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }
}
