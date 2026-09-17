package dev.nightbeam.donutessentials.service;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.util.Text;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageService {
    private static final String PREFIX_PATH = "prefix";

    private final DonutEssentialsPlugin plugin;
    private FileConfiguration messages;

    public MessageService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public String getRaw(String key) {
        return messages.getString(key, "&cMissing message: " + key);
    }

    public String get(String key) {
        return Text.color(getRaw(PREFIX_PATH) + getRaw(key));
    }

    public String get(String key, Map<String, String> placeholders) {
        Map<String, String> merged = new HashMap<>(placeholders);
        merged.put("prefix", Text.color(getRaw(PREFIX_PATH)));
        String raw = getRaw(PREFIX_PATH) + getRaw(key);
        return Text.format(raw, merged);
    }
}
