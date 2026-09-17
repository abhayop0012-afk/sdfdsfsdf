package com.riftcore.players;

import com.riftcore.RiftCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class SettingsManager implements Listener {
    private static final String PACK_NAME = "RiftCoreSettings";
    private final RiftCore plugin;
    private final Path file;
    private final Path packRoot;
    private final Map<UUID, EnumMap<Setting, Boolean>> values = new java.util.concurrent.ConcurrentHashMap<>();

    public SettingsManager(RiftCore plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("settings.yml");
        Path worldFolder = Bukkit.getWorlds().isEmpty()
                ? Bukkit.getWorldContainer().toPath().resolve("world")
                : Bukkit.getWorlds().get(0).getWorldFolder().toPath();
        this.packRoot = worldFolder.resolve("datapacks").resolve(PACK_NAME);
        load();
    }

    public boolean isEnabled(Player player, Setting setting) {
        return values.computeIfAbsent(player.getUniqueId(), ignored -> defaults()).get(setting);
    }

    public void set(Player player, Setting setting, boolean enabled) {
        values.computeIfAbsent(player.getUniqueId(), ignored -> defaults()).put(setting, enabled);
        save();
        open(player);
    }

    public void open(Player player) {
        String id = "settings_" + player.getUniqueId().toString().replace('-', '_');
        writeDialog(id, settingsDialog(player, id));
        plugin.getServer().reloadData();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                        "dialog show " + player.getName() + " riftcore:" + id);
            }
        });
    }

    public boolean isReceivingTpa(Player player) {
        return isEnabled(player, Setting.TPA_REQUESTS);
    }

    public boolean hasTpaNotifications(Player player) {
        return isEnabled(player, Setting.TPA_NOTIFICATIONS);
    }

    public boolean canReceivePay(Player player) {
        return isEnabled(player, Setting.RECEIVE_PAY);
    }

    public boolean canSeeChat(Player player) {
        return isEnabled(player, Setting.CHAT);
    }

    public void reload() {
        load();
    }

    public void shutdown() {
        save();
    }

    private String settingsDialog(Player player, String id) {
        return "{\n" +
                "  \"type\": \"minecraft:multi_action\",\n" +
                "  \"title\": \"⚙ SETTINGS\",\n" +
                "  \"can_close_with_escape\": true,\n" +
                "  \"pause\": true,\n" +
                "  \"after_action\": \"close\",\n" +
                "  \"actions\": [" +
                settingActions(player, Setting.TPA_REQUESTS) + "," +
                settingActions(player, Setting.TPA_NOTIFICATIONS) + "," +
                settingActions(player, Setting.RECEIVE_PAY) + "," +
                settingActions(player, Setting.CHAT) +
                "],\n" +
                "  \"exit_action\": {\"label\": \"← Back\", \"action\": {\"type\": \"run_command\", \"command\": \"/rift menu\"}}\n" +
                "}\n";
    }

    private String settingActions(Player player, Setting setting) {
        String state = isEnabled(player, setting) ? "✓ ON" : "ON";
        String off = isEnabled(player, setting) ? "OFF" : "✓ OFF";
        return action(setting.label + "  [ " + state + " ]", "/riftcore settings set " + setting.key + " on") + "," +
                action("          [ " + off + " ]", "/riftcore settings set " + setting.key + " off");
    }

    private String action(String label, String command) {
        return "{\"label\": " + json(label) + ", \"action\": {\"type\": \"run_command\", \"command\": " + json(command) + "}}";
    }

    private String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void writeDialog(String id, String json) {
        try {
            Files.createDirectories(packRoot.resolve("data/riftcore/dialog"));
            Files.writeString(packRoot.resolve("pack.mcmeta"),
                    "{\n  \"pack\": {\n    \"description\": \"RiftCore player settings\",\n    \"pack_format\": 80\n  }\n}\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(packRoot.resolve("data/riftcore/dialog/" + id + ".json"), json,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write settings Dialog", exception);
        }
    }

    private EnumMap<Setting, Boolean> defaults() {
        EnumMap<Setting, Boolean> defaults = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) defaults.put(setting, true);
        return defaults;
    }

    private void load() {
        values.clear();
        if (!Files.exists(file)) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file.toFile());
        for (String rawUuid : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                EnumMap<Setting, Boolean> loaded = defaults();
                for (Setting setting : Setting.values()) {
                    loaded.put(setting, data.getBoolean(rawUuid + "." + setting.key, true));
                }
                values.put(uuid, loaded);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid settings UUID: " + rawUuid);
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        values.forEach((uuid, settings) -> {
            for (Setting setting : Setting.values()) data.set(uuid + "." + setting.key, settings.get(setting));
        });
        try {
            Files.createDirectories(file.getParent());
            data.save(file.toFile());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save settings.yml: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void filterChat(AsyncPlayerChatEvent event) {
        event.getRecipients().removeIf(player -> !canSeeChat(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        save();
    }

    public enum Setting {
        TPA_REQUESTS("tpa_requests", "📩 TPA Requests"),
        TPA_NOTIFICATIONS("tpa_notifications", "🔔 TPA Notifications"),
        RECEIVE_PAY("receive_pay", "💰 Receive Pay"),
        CHAT("chat", "💬 Chat");

        private final String key;
        private final String label;

        Setting(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public static Setting fromKey(String key) {
            for (Setting setting : values()) if (setting.key.equalsIgnoreCase(key)) return setting;
            return null;
        }
    }
}
