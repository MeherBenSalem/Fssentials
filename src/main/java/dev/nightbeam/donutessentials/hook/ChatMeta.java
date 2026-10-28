package dev.nightbeam.donutessentials.hook;

import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Prefixes, suffixes and groups from the permissions plugin (LuckPerms and others) through Vault's chat API. Empty
 * strings without Vault or a chat provider.
 */
public final class ChatMeta {

    private ChatMeta() {
    }

    public static String prefix(Player player) {
        return Holder.call(chat -> chat.getPlayerPrefix(player));
    }

    public static String suffix(Player player) {
        return Holder.call(chat -> chat.getPlayerSuffix(player));
    }

    public static String group(Player player) {
        return Holder.call(chat -> chat.getPrimaryGroup(player));
    }

    /** Keeps Vault classes out of the way until Vault is known to be there. */
    private static final class Holder {

        private static String call(java.util.function.Function<Chat, String> getter) {
            if (!Hooks.vault()) {
                return "";
            }
            try {
                RegisteredServiceProvider<Chat> registration = Bukkit.getServicesManager().getRegistration(Chat.class);
                if (registration == null || registration.getProvider() == null) {
                    return "";
                }
                String value = getter.apply(registration.getProvider());
                return value == null ? "" : value;
            } catch (Throwable ignored) {
                return "";
            }
        }
    }
}
