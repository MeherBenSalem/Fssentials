package dev.nightbeam.donutessentials.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Static access to the API: {@code DonutEssentials.getApi().balance(player)}. */
public final class DonutEssentials {

    private DonutEssentials() {
    }

    /** The API, or null when Donut Essentials is not enabled. */
    public static DonutEssentialsApi getApi() {
        RegisteredServiceProvider<DonutEssentialsApi> registration = Bukkit.getServicesManager().getRegistration(DonutEssentialsApi.class);
        return registration == null ? null : registration.getProvider();
    }
}
