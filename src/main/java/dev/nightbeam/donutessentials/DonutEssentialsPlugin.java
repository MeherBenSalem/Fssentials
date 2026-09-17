package dev.nightbeam.donutessentials;

import dev.nightbeam.donutessentials.announcement.BroadcastCommand;
import dev.nightbeam.donutessentials.announcement.BroadcastService;
import dev.nightbeam.donutessentials.command.CommandRouter;
import dev.nightbeam.donutessentials.maintenance.MaintenanceCommand;
import dev.nightbeam.donutessentials.maintenance.MaintenanceConfig;
import dev.nightbeam.donutessentials.maintenance.MaintenanceListener;
import dev.nightbeam.donutessentials.maintenance.MaintenanceManager;
import dev.nightbeam.donutessentials.maintenance.MaintenanceTimer;
import dev.nightbeam.donutessentials.security.IpLockCommand;
import dev.nightbeam.donutessentials.security.IpLockListener;
import dev.nightbeam.donutessentials.security.IpLockManager;
import dev.nightbeam.donutessentials.service.AdminToolsListener;
import dev.nightbeam.donutessentials.service.MessageService;
import dev.nightbeam.donutessentials.service.PunishmentManager;
import dev.nightbeam.donutessentials.service.PunishmentListener;
import dev.nightbeam.donutessentials.service.VanishService;
import dev.nightbeam.donutessentials.storage.YamlPunishmentStorage;
import dev.nightbeam.donutessentials.util.FoliaScheduler;
import dev.nightbeam.donutessentials.util.ModrinthUpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

public class DonutEssentialsPlugin extends JavaPlugin {
    private MessageService messageService;
    private PunishmentManager punishmentManager;
    private FoliaScheduler foliaScheduler;
    private VanishService vanishService;
    private MaintenanceConfig maintenanceConfig;
    private MaintenanceManager maintenanceManager;
    private MaintenanceTimer maintenanceTimer;
    private BroadcastService broadcastService;
    private IpLockManager ipLockManager;

    @Override
    public void onEnable() {
        migrateLegacyDataFolder();
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");

        if (getConfig().getBoolean("bstats", true)) {
            try {
                new org.bstats.bukkit.Metrics(this, 34102);
            } catch (Throwable t) {
                getLogger().warning("bStats failed: " + t.getMessage());
            }
        }

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
        this.maintenanceConfig = new MaintenanceConfig(this);
        this.maintenanceManager = new MaintenanceManager(this, maintenanceConfig);
        this.maintenanceTimer = new MaintenanceTimer(this, maintenanceManager);
        this.broadcastService = new BroadcastService(this);
        this.ipLockManager = new IpLockManager(this);
        this.ipLockManager.load();

        // Load punishment storage asynchronously to avoid blocking a tick thread.
        punishmentManager.loadAsync();

        CommandRouter router = new CommandRouter(this, messageService, punishmentManager, vanishService);
        registerCommands(
            router,
            new MaintenanceCommand(this, maintenanceManager, maintenanceTimer),
            new BroadcastCommand(broadcastService),
            new IpLockCommand(ipLockManager, messageService)
        );
        getServer().getPluginManager().registerEvents(router, this);

        getServer().getPluginManager().registerEvents(new PunishmentListener(this, messageService, punishmentManager), this);
        getServer().getPluginManager().registerEvents(new AdminToolsListener(vanishService), this);
        getServer().getPluginManager().registerEvents(new MaintenanceListener(maintenanceManager, maintenanceTimer), this);
        getServer().getPluginManager().registerEvents(new IpLockListener(this, ipLockManager), this);
        // Temporary punishment expiration runs on Folia async scheduler; player messaging hops to entity/global schedulers.
        foliaScheduler.runAsyncTimer(punishmentManager::expirePunishments, 20L, 20L * 30L);

        ModrinthUpdateChecker.checkAsync(this, foliaScheduler, getConfig().getBoolean("update-check", true));

        getLogger().info("Donut Essentials is online and Folia-ready.");
    }

    @Override
    public void onDisable() {
        if (punishmentManager != null) {
            punishmentManager.saveSync();
        }
        if (ipLockManager != null) {
            ipLockManager.save();
        }
        if (maintenanceTimer != null) {
            maintenanceTimer.abortTimer();
        }
        if (foliaScheduler != null) {
            foliaScheduler.cancelAll();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        saveResourceIfMissing("messages.yml");
        messageService.reload();
        punishmentManager.reloadConfigSettings();
        punishmentManager.saveAsync();
        punishmentManager.loadAsync();
        if (maintenanceManager != null) {
            maintenanceManager.reloadEverything();
        }
        if (ipLockManager != null) {
            ipLockManager.reload();
        }
    }

    public FoliaScheduler getFoliaScheduler() {
        return foliaScheduler;
    }

    private void registerCommands(CommandRouter router, MaintenanceCommand maintenanceCommand, BroadcastCommand broadcastCommand, IpLockCommand ipLockCommand) {
        List<String> commands = Arrays.asList(
                "kick", "ban", "mute", "warn", "note", "banip",
                "tempban", "tempmute", "tempwarn", "tempipban",
                "unban", "unmute", "unwarn", "unnote", "unpunish",
                "change-reason", "warns", "notes", "check", "banlist", "history",
                "donutessentials", "systemprefs", "vanish", "offlinetp", "playerlist", "punishment", "invsee"
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

        PluginCommand maintenance = getCommand("maintenance");
        if (maintenance == null) {
            getLogger().warning("Command not found in plugin.yml: maintenance");
        } else {
            maintenance.setExecutor(maintenanceCommand);
            maintenance.setTabCompleter(maintenanceCommand);
        }

        PluginCommand broadcast = getCommand("broadcast");
        if (broadcast == null) {
            getLogger().warning("Command not found in plugin.yml: broadcast");
            return;
        }
        broadcast.setExecutor(broadcastCommand);
        broadcast.setTabCompleter(broadcastCommand);

        PluginCommand ipLock = getCommand("iplock");
        if (ipLock == null) {
            getLogger().warning("Command not found in plugin.yml: iplock");
            return;
        }
        ipLock.setExecutor(ipLockCommand);
        ipLock.setTabCompleter(ipLockCommand);
    }

    private void saveResourceIfMissing(String path) {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File file = new File(getDataFolder(), path);
        if (!file.exists()) {
            saveResource(path, false);
        }
    }

    private void migrateLegacyDataFolder() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.isFile()) {
            return;
        }

        File legacyFolder = new File(getDataFolder().getParentFile(), "Fssentials");
        if (!legacyFolder.isDirectory()) {
            return;
        }

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create data folder for legacy migration.");
            return;
        }

        String[] migrateFiles = {"config.yml", "messages.yml", "punishments.yml", "ip-locks.yml"};
        boolean migratedAny = false;
        for (String fileName : migrateFiles) {
            File source = new File(legacyFolder, fileName);
            if (!source.isFile()) {
                continue;
            }
            File destination = new File(getDataFolder(), fileName);
            try {
                Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                migratedAny = true;
                if ("config.yml".equals(fileName)) {
                    rewriteLegacyPermissionsInConfig(destination);
                }
            } catch (IOException ex) {
                getLogger().warning("Failed to migrate " + fileName + " from Fssentials: " + ex.getMessage());
            }
        }

        if (migratedAny) {
            getLogger().info("Migrated plugin data from plugins/Fssentials to DonutEssentials.");
        }
    }

    private void rewriteLegacyPermissionsInConfig(File configFile) {
        try {
            String contents = Files.readString(configFile.toPath());
            String updated = contents.replace("fssentials.", "donutessentials.");
            if (!updated.equals(contents)) {
                Files.writeString(configFile.toPath(), updated);
            }
        } catch (IOException ex) {
            getLogger().warning("Failed to rewrite legacy permission nodes in config.yml: " + ex.getMessage());
        }
    }
}
