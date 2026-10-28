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
        return Hooks.vault() ? Holder.get(player, 0) : "";
    }

    public static String suffix(Player player) {
        return Hooks.vault() ? Holder.get(player, 1) : "";
    }

    public static String group(Player player) {
        return Hooks.vault() ? Holder.get(player, 2) : "";
    }

    /**
     * The only code that names Vault's classes, loaded once Vault is known to be there. (A lambda taking a {@code Chat}
     * in the outer class would need Vault as soon as it is linked.)
     */
    private static final class Holder {

        private static String get(Player player, int what) {
            try {
                RegisteredServiceProvider<Chat> registration = Bukkit.getServicesManager().getRegistration(Chat.class);
                Chat chat = registration == null ? null : registration.getProvider();
                if (chat == null) {
                    return "";
                }
                String value = switch (what) {
                    case 0 -> chat.getPlayerPrefix(player);
                    case 1 -> chat.getPlayerSuffix(player);
                    default -> chat.getPrimaryGroup(player);
                };
                return value == null ? "" : value;
            } catch (Throwable ignored) {
                return "";
            }
        }
    }
}
