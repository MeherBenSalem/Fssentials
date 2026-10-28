package dev.nightbeam.donutessentials;

import dev.nightbeam.donutessentials.afk.AfkModule;
import dev.nightbeam.donutessentials.api.DonutEssentialsApi;
import dev.nightbeam.donutessentials.api.DonutEssentialsApiImpl;
import dev.nightbeam.donutessentials.chat.ChatModule;
import dev.nightbeam.donutessentials.command.AdminCommand;
import dev.nightbeam.donutessentials.command.CommandRegistry;
import dev.nightbeam.donutessentials.command.CommandRules;
import dev.nightbeam.donutessentials.config.ConfigFiles;
import dev.nightbeam.donutessentials.config.Messages;
import dev.nightbeam.donutessentials.economy.EconomyModule;
import dev.nightbeam.donutessentials.homes.HomesModule;
import dev.nightbeam.donutessentials.hook.DiscordWebhook;
import dev.nightbeam.donutessentials.hook.EconomyBridge;
import dev.nightbeam.donutessentials.hook.Hooks;
import dev.nightbeam.donutessentials.hook.UpdateChecker;
import dev.nightbeam.donutessentials.importer.LegacyImporter;
import dev.nightbeam.donutessentials.iplock.IpLockModule;
import dev.nightbeam.donutessentials.kits.KitsModule;
import dev.nightbeam.donutessentials.maintenance.MaintenanceModule;
import dev.nightbeam.donutessentials.menu.InputService;
import dev.nightbeam.donutessentials.menu.MenuManager;
import dev.nightbeam.donutessentials.messaging.MessagingModule;
import dev.nightbeam.donutessentials.moderation.ModerationModule;
import dev.nightbeam.donutessentials.module.ModuleManager;
import dev.nightbeam.donutessentials.placeholder.PlaceholderSupport;
import dev.nightbeam.donutessentials.player.IgnoreService;
import dev.nightbeam.donutessentials.player.Names;
import dev.nightbeam.donutessentials.player.NoticeService;
import dev.nightbeam.donutessentials.player.PlayerListener;
import dev.nightbeam.donutessentials.player.PlayerService;
import dev.nightbeam.donutessentials.rtp.RtpModule;
import dev.nightbeam.donutessentials.rules.RulesModule;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import dev.nightbeam.donutessentials.staff.StaffModule;
import dev.nightbeam.donutessentials.storage.DataWriter;
import dev.nightbeam.donutessentials.storage.Database;
import dev.nightbeam.donutessentials.teleport.CombatService;
import dev.nightbeam.donutessentials.teleport.TeleportModule;
import dev.nightbeam.donutessentials.teleport.TeleportService;
import dev.nightbeam.donutessentials.utility.UtilityModule;
import dev.nightbeam.donutessentials.util.SoundService;
import dev.nightbeam.donutessentials.vanish.VanishModule;
import dev.nightbeam.donutessentials.vanish.VanishService;
import dev.nightbeam.donutessentials.warps.WarpsModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Donut Essentials: homes, warps, teleports, kits, economy, chat, moderation and staff tools in one plugin for Paper,
 * Folia and Purpur 1.20.1 – 26.3. This class wires the shared services and starts the modules switched on in
 * config.yml.
 */
public final class DonutEssentialsPlugin extends JavaPlugin implements Listener {

    /** bStats plugin id (shared with 2.x). */
    private static final int BSTATS_ID = 34102;

    private final Messages messages = new Messages();
    private FoliaScheduler scheduler;
    private SoundService sounds;
    private MenuManager menus;
    private InputService input;
    private CommandRegistry commands;
    private CommandRules commandRules;
    private ModuleManager modules;
    private Database database;
    private DataWriter writer;
    private PlayerService players;
    private IgnoreService ignores;
    private NoticeService notices;
    private Names names;
    private TeleportService teleports;
    private CombatService combat;
    private VanishService vanish;
    private final EconomyBridge economy = new EconomyBridge();
    private DiscordWebhook discord;
    private UpdateChecker updates;
    private DonutEssentialsApiImpl api;
    private boolean enabled;

    @Override
    public void onLoad() {
        // The economy registers with Vault before any plugin enables (see EconomyModule.registerEarly).
        try {
            java.io.File config = new java.io.File(getDataFolder(), "config.yml");
            boolean economyOn = !config.isFile() || YamlConfiguration.loadConfiguration(config).getBoolean("modules.economy", true);
            if (economyOn && getServer().getPluginManager().getPlugin("Vault") != null) {
                EconomyModule.registerEarly(this);
            }
        } catch (Throwable error) {
            getLogger().fine("Early Vault registration skipped: " + error);
        }
    }

    @Override
    public void onEnable() {
        if (!paperApiPresent()) {
            getLogger().severe("Donut Essentials needs Paper or a Paper fork (Purpur, Folia, Pufferfish...) 1.20.1 or newer. "
                    + "Spigot and CraftBukkit are not supported.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ConfigFiles.Result files = ConfigFiles.prepare(this);
        reloadConfig();
        this.scheduler = new FoliaScheduler(this);
        this.sounds = new SoundService();
        this.menus = new MenuManager(this);
        loadTexts();

        this.database = Database.open(this, getConfig().getConfigurationSection("storage"));
        try {
            database.initialize(message -> getLogger().info(message));
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Could not open the Donut Essentials database (" + database.typeName() + "). "
                    + "Check storage in config.yml.", exception);
            database.shutdown();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.writer = new DataWriter(database, getLogger());
        this.players = new PlayerService(writer, getLogger());
        players.start();
        this.ignores = new IgnoreService(writer);
        this.notices = new NoticeService(this);
        this.names = new Names(this);
        this.combat = new CombatService();
        this.teleports = new TeleportService(this);
        this.vanish = new VanishService(this);
        this.discord = new DiscordWebhook(getLogger());
        this.input = new InputService(this, DialogSupport.create(this));
        this.commands = new CommandRegistry(this);
        this.commandRules = new CommandRules(this);
        this.updates = new UpdateChecker(this);
        applySettings();

        LegacyImporter.run(this, files.legacyConfig());

        this.modules = new ModuleManager(this);
        modules.add(new EconomyModule(this));
        modules.add(new TeleportModule(this));
        modules.add(new HomesModule(this));
        modules.add(new WarpsModule(this));
        modules.add(new RtpModule(this));
        modules.add(new KitsModule(this));
        modules.add(new ChatModule(this));
        modules.add(new MessagingModule(this));
        modules.add(new AfkModule(this));
        modules.add(new ModerationModule(this));
        modules.add(new VanishModule(this));
        modules.add(new StaffModule(this));
        modules.add(new MaintenanceModule(this));
        modules.add(new IpLockModule(this));
        modules.add(new RulesModule(this));
        modules.add(new UtilityModule(this));

        commands.configure(ConfigFiles.load(this, "commands.yml"), getConfig().getStringList("commands.take-over-vanilla"));
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(players), this);
        pm.registerEvents(ignores, this);
        pm.registerEvents(notices, this);
        pm.registerEvents(teleports, this);
        pm.registerEvents(menus, this);
        pm.registerEvents(input, this);
        pm.registerEvents(this, this);
        modules.enableAll();
        EconomyModule.dropEarly();
        commandRules.load(getConfig().getConfigurationSection("command-rules"));
        pm.registerEvents(commandRules, this);
        commands.register(List.of(new AdminCommand(this)));

        this.api = new DonutEssentialsApiImpl(this);
        getServer().getServicesManager().register(DonutEssentialsApi.class, api, this, ServicePriority.Normal);
        boolean placeholders = PlaceholderSupport.register(this);
        startMetrics();
        updates.check();
        enabled = true;

        for (String note : files.notes()) {
            getLogger().info(note);
        }
        if (!commands.leftToServer().isEmpty()) {
            getLogger().info("Left to vanilla (add to commands.take-over-vanilla to use ours): /" + String.join(", /", commands.leftToServer())
                    + ". Ours stay reachable as /donutessentials:<name>.");
        }
        if (!commands.takenByOthers().isEmpty()) {
            getLogger().info("Used by other plugins (ours stay reachable as /donutessentials:<name>): " + String.join(", ", commands.takenByOthers()));
        }
        getLogger().info("Donut Essentials " + version() + " enabled on " + platform() + ": " + modules.enabledIds().size() + " modules, "
                + commands.count() + " commands, storage " + database.typeName() + ", economy " + economyName()
                + ", dialogs " + input.dialogsAvailable() + ", PlaceholderAPI " + placeholders + ".");
    }

    @Override
    public void onDisable() {
        if (!enabled) {
            return;
        }
        enabled = false;
        if (menus != null) {
            menus.closeAll();
        }
        if (modules != null) {
            modules.disableAll();
        }
        if (commands != null) {
            commands.unregisterAll();
        }
        if (players != null) {
            for (var data : players.online()) {
                data.foldSession(true);
                players.save(data);
            }
        }
        getServer().getServicesManager().unregisterAll(this);
        if (discord != null) {
            discord.shutdown();
        }
        if (writer != null) {
            writer.shutdown();
        }
        if (database != null) {
            database.shutdown();
        }
    }

    /** /donutessentials reload: config, messages, menus and every module's settings (storage and commands need a restart). */
    public void reloadPlugin() {
        ConfigFiles.prepare(this);
        reloadConfig();
        loadTexts();
        applySettings();
        commandRules.load(getConfig().getConfigurationSection("command-rules"));
        modules.reloadAll();
    }

    private void loadTexts() {
        messages.load(ConfigFiles.load(this, "messages.yml"));
        menus.config().load(ConfigFiles.load(this, "menus.yml"));
        sounds.load(getConfig().getConfigurationSection("sounds"));
    }

    private void applySettings() {
        teleports.load(getConfig().getConfigurationSection("teleport"));
        combat.load(getConfig().getConfigurationSection("combat"));
        discord.configure(getConfig().getString("discord.webhook-url", ""), getConfig().getString("discord.username", "Donut Essentials"));
    }

    private void startMetrics() {
        if (!getConfig().getBoolean("metrics.enabled", true)) {
            return;
        }
        try {
            Metrics metrics = new Metrics(this, BSTATS_ID);
            metrics.addCustomChart(new org.bstats.charts.SimplePie("storage", database::typeName));
            metrics.addCustomChart(new org.bstats.charts.SimplePie("platform", this::platform));
            metrics.addCustomChart(new org.bstats.charts.SimplePie("economy", this::economyName));
            metrics.addCustomChart(new org.bstats.charts.AdvancedPie("modules", () -> {
                java.util.Map<String, Integer> map = new java.util.HashMap<>();
                for (String id : modules.enabledIds()) {
                    map.put(id, 1);
                }
                return map;
            }));
        } catch (Throwable error) {
            getLogger().fine("bStats is unavailable: " + error);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        vanish.applyJoin(player);
        scheduler.forPlayer(player, () -> names.refresh(player));
        scheduler.runLaterForEntity(player, () -> updates.notify(player), 60L);
    }

    // ── Lookups ─────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * An online player the sender can see: exact name first, then a unique name prefix, then a nickname. Vanished
     * players are invisible to senders without {@code donutessentials.vanish.see}.
     */
    public Player visibleTarget(CommandSender sender, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null && vanish.canSee(sender, exact)) {
            return exact;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        List<Player> matches = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!vanish.canSee(sender, player)) {
                continue;
            }
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(player);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (vanish.canSee(sender, player) && names.plain(player).toLowerCase(Locale.ROOT).equals(lower)) {
                return player;
            }
        }
        return null;
    }

    public String version() {
        return getPluginMeta().getVersion();
    }

    public String platform() {
        if (scheduler != null && scheduler.isFolia()) {
            return "Folia";
        }
        String name = Bukkit.getName();
        return name.toLowerCase(Locale.ROOT).contains("purpur") ? "Purpur" : "Paper";
    }

    public String economyName() {
        return economy.available() ? (economy.usingOwn() ? "Donut Essentials" : economy.name()) : "none";
    }

    private static boolean paperApiPresent() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    // ── Services ────────────────────────────────────────────────────────────────────────────────────────────────

    public Messages messages() {
        return messages;
    }

    public FoliaScheduler scheduler() {
        return scheduler;
    }

    public SoundService sounds() {
        return sounds;
    }

    public MenuManager menus() {
        return menus;
    }

    public InputService input() {
        return input;
    }

    public CommandRegistry commands() {
        return commands;
    }

    public ModuleManager modules() {
        return modules;
    }

    /** The module when it is enabled, otherwise null. */
    public <T extends dev.nightbeam.donutessentials.module.Module> T module(Class<T> type) {
        return modules == null ? null : modules.get(type);
    }

    public Database database() {
        return database;
    }

    public DataWriter writer() {
        return writer;
    }

    public PlayerService players() {
        return players;
    }

    public IgnoreService ignores() {
        return ignores;
    }

    public NoticeService notices() {
        return notices;
    }

    public Names names() {
        return names;
    }

    public TeleportService teleports() {
        return teleports;
    }

    public CombatService combat() {
        return combat;
    }

    public VanishService vanish() {
        return vanish;
    }

    public EconomyBridge economy() {
        return economy;
    }

    public DiscordWebhook discord() {
        return discord;
    }

    public UpdateChecker updates() {
        return updates;
    }

    public DonutEssentialsApi api() {
        return api;
    }

    /** A YAML file in the data folder with its shipped defaults. */
    public YamlConfiguration loadFile(String name) {
        return ConfigFiles.load(this, name);
    }

    /** Whether an optional plugin is installed and enabled. */
    public boolean hook(String name) {
        return Hooks.present(name);
    }
}
