package dev.nightbeam.fssentials;

import dev.nightbeam.fssentials.command.CommandRouter;
import dev.nightbeam.fssentials.service.AdminToolsListener;
import dev.nightbeam.fssentials.service.MessageService;
import dev.nightbeam.fssentials.service.PunishmentManager;
import dev.nightbeam.fssentials.service.PunishmentListener;
import dev.nightbeam.fssentials.service.VanishService;
import dev.nightbeam.fssentials.storage.YamlPunishmentStorage;
import dev.nightbeam.fssentials.util.FoliaScheduler;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class FssentialsPlugin extends JavaPlugin {
    private MessageService messageService;
    private PunishmentManager punishmentManager;
    private FoliaScheduler foliaScheduler;
    private VanishService vanishService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");

        // Folia supported v1.0+: centralized scheduler utility for async/global/entity task dispatch.
        this.foliaScheduler = new FoliaScheduler(this);
        this.vanishService = new VanishService(this);
        this.messageService = new MessageService(this);
        this.punishmentManager = new PunishmentManager(
                this,
                messageService,
            foliaScheduler,
                new YamlPunishmentStorage(this)
        );

        // Load punishment storage asynchronously to avoid blocking a tick thread.
        punishmentManager.loadAsync();

        CommandRouter router = new CommandRouter(this, messageService, punishmentManager, vanishService);
        registerCommands(router);
        getServer().getPluginManager().registerEvents(router, this);

        getServer().getPluginManager().registerEvents(new PunishmentListener(this, messageService, punishmentManager), this);
        getServer().getPluginManager().registerEvents(new AdminToolsListener(vanishService), this);
        // Temporary punishment expiration runs on Folia async scheduler; player messaging hops to entity/global schedulers.
        foliaScheduler.runAsyncTimer(punishmentManager::expirePunishments, 20L, 20L * 30L);

        getLogger().info("Fssentials is online and Folia-ready to keep things tidy.");
    }

    @Override
    public void onDisable() {
        if (punishmentManager != null) {
            punishmentManager.saveAsync();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        saveResourceIfMissing("messages.yml");
        messageService.reload();
        punishmentManager.reloadConfigSettings();
        punishmentManager.saveAsync();
        punishmentManager.loadAsync();
    }

    public FoliaScheduler getFoliaScheduler() {
        return foliaScheduler;
    }

    private void registerCommands(CommandRouter router) {
        List<String> commands = Arrays.asList(
                "kick", "ban", "mute", "warn", "note", "banip",
                "tempban", "tempmute", "tempwarn", "tempipban",
                "unban", "unmute", "unwarn", "unnote", "unpunish",
                "change-reason", "warns", "notes", "check", "banlist", "history",
                "fssentials", "systemprefs", "vanish", "invsee"
        );

        for (String cmd : commands) {
            PluginCommand pluginCommand = getCommand(cmd);
            if (pluginCommand == null) {
                getLogger().warning("Command not found in plugin.yml: " + cmd);
                continue;
            }
            pluginCommand.setExecutor(router);
            pluginCommand.setTabCompleter(router);
        }
    }

    private void saveResourceIfMissing(String path) {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        java.io.File file = new java.io.File(getDataFolder(), path);
        if (!file.exists()) {
            saveResource(path, false);
        }
    }
}
