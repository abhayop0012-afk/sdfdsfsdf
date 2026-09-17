package com.riftcore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class ConfigManager {
    private final Plugin plugin;
    private FileConfiguration config;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public boolean isMenuEnabled() {
        return config.getBoolean("menu.enabled", true);
    }

    public boolean isPermissionCheckEnabled() {
        return config.getBoolean("menu.permission-check", true);
    }

    public String getMenuTitle() {
        return config.getString("menu.title", "RIFT SMP");
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("debug.enabled", false);
    }

    public String getAdminPermission() {
        return config.getString("permissions.admin", "riftcore.admin");
    }

    public String getMenuPermission() {
        return config.getString("permissions.menu", "riftcore.menu");
    }

    public boolean isPlayersEnabled() {
        return config.getBoolean("players.enabled", true);
    }

    public boolean isShowSelf() {
        return config.getBoolean("players.show_self", false);
    }

    public int getPlayersPerPage() {
        return Math.max(1, config.getInt("players.per-page", 12));
    }

    public String getPlayersPermission() {
        return config.getString("permissions.players", "riftcore.players");
    }

    public boolean isTpaEnabled() {
        return config.getBoolean("players.tpa.enabled", true);
    }

    public int getTpaTimeoutSeconds() {
        return Math.max(1, config.getInt("players.tpa.timeout-seconds", 60));
    }

    public int getTpaCooldownSeconds() {
        return Math.max(0, config.getInt("players.tpa.cooldown-seconds", 5));
    }

    public String getTpaPermission() {
        return config.getString("permissions.players-tpa", "riftcore.players.tpa");
    }

    public boolean isFriendsEnabled() {
        return config.getBoolean("players.friends.enabled", true);
    }

    public int getFriendRequestTimeoutSeconds() {
        return Math.max(1, config.getInt("players.friends.request-timeout-seconds", 60));
    }

    public String getFriendPermission() {
        return config.getString("permissions.players-friend", "riftcore.players.friend");
    }

    public String getPayPermission() {
        return config.getString("permissions.players-pay", "riftcore.players.pay");
    }

    public String getSettingsPermission() {
        return config.getString("permissions.settings", "riftcore.settings");
    }

    public ConfigurationSection getButtonSection(String buttonKey) {
        return config.getConfigurationSection("buttons." + buttonKey);
    }

    public List<String> getOrderedButtonKeys() {
        List<String> defaultOrder = List.of(
                "homes",
                "balance",
            "players",
                "shop",
                "auction_house",
                "rtp",
                "pvp",
                "profile",
                "leaderboards",
                "kits",
                "settings",
                "discord"
        );

        List<String> keys = new ArrayList<>();
        for (String key : defaultOrder) {
            if (config.getConfigurationSection("buttons") == null || config.getConfigurationSection("buttons").contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    public String getButtonLabel(String buttonKey) {
        return config.getString("buttons." + buttonKey + ".label", buttonKey);
    }

    public String getButtonCommand(String buttonKey) {
        return config.getString("buttons." + buttonKey + ".action",
                config.getString("buttons." + buttonKey + ".command", ""));
    }

    public String getButtonDescription(String buttonKey) {
        return config.getString("buttons." + buttonKey + ".description", "");
    }

    public String getButtonPermission(String buttonKey) {
        return config.getString("buttons." + buttonKey + ".permission", "");
    }

    public boolean buttonClosesDialog(String buttonKey) {
        return config.getBoolean("buttons." + buttonKey + ".close", true);
    }

    public boolean isButtonEnabled(String buttonKey) {
        return config.getBoolean("buttons." + buttonKey + ".enabled", true);
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
