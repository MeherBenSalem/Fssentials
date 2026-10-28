package dev.nightbeam.donutessentials.hook;

import dev.nightbeam.donutessentials.util.Amounts;
import org.bukkit.OfflinePlayer;

/**
 * The economy every feature uses (teleport costs, kits, /pay...). When Donut Essentials' own economy is on and no
 * other economy plugin outranks it in Vault, it is used directly; otherwise the top Vault economy is. Looked up
 * again whenever it is missing, so an economy plugin that enables later is still found.
 */
public final class EconomyBridge implements Economy {

    private volatile Economy own;
    private volatile Object ownVaultFace;
    private volatile Economy delegate;

    /** Our own economy and the object it registered with Vault (null without Vault). */
    public void own(Economy economy, Object vaultFace) {
        this.own = economy;
        this.ownVaultFace = vaultFace;
        this.delegate = null;
    }

    /** True when our own economy is the one in use. */
    public boolean usingOwn() {
        Economy mine = own;
        if (mine == null) {
            return false;
        }
        if (!Hooks.vault() || ownVaultFace == null) {
            return true;
        }
        try {
            return VaultEconomyAdapter.topProviderIs(ownVaultFace) || VaultEconomyAdapter.find() == null;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private Economy resolve() {
        if (usingOwn()) {
            return own;
        }
        Economy current = delegate;
        if (current != null && current.available()) {
            return current;
        }
        if (!Hooks.vault()) {
            return null;
        }
        try {
            Economy vault = VaultEconomyAdapter.find();
            delegate = vault;
            return vault;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public boolean available() {
        Economy economy = resolve();
        return economy != null && economy.available();
    }

    @Override
    public String name() {
        Economy economy = resolve();
        return economy == null ? "none" : economy.name();
    }

    @Override
    public double balance(OfflinePlayer player) {
        Economy economy = resolve();
        return economy == null ? 0 : economy.balance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        Economy economy = resolve();
        return economy != null && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        Economy economy = resolve();
        return economy != null && economy.withdraw(player, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        Economy economy = resolve();
        return economy != null && economy.deposit(player, amount);
    }

    @Override
    public String format(double amount) {
        Economy economy = resolve();
        return economy == null ? "$" + Amounts.full(amount) : economy.format(amount);
    }
}
