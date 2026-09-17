package com.riftcore.integration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class PlaceholderHook {
    private final JavaPlugin plugin;
    private final boolean available;

    public PlaceholderHook(JavaPlugin plugin) {
        this.plugin = plugin;
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        this.available = papi != null && papi.isEnabled();
        if (available) {
            plugin.getLogger().info("PlaceholderAPI detected and hooked.");
        } else {
            plugin.getLogger().warning("PlaceholderAPI not detected; placeholders will be unavailable.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String replace(String text) {
        if (!available || text == null || text.isBlank()) {
            return text;
        }
        try {
            Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = clazz.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            return (String) method.invoke(null, (OfflinePlayer) null, text);
        } catch (Exception e) {
            plugin.getLogger().warning("PlaceholderAPI is enabled but placeholder resolution failed: " + e.getMessage());
            return text;
        }
    }
}
