package dev.nightbeam.donutessentials.hook;

import org.bukkit.OfflinePlayer;

/** The money the plugin works with: its own economy or another plugin's through Vault. */
public interface Economy {

    boolean available();

    String name();

    double balance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String format(double amount);
}
