package com.riftcore.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class EssentialsHook {
    private final JavaPlugin plugin;
    private final boolean available;
    private Object essentials;

    public EssentialsHook(JavaPlugin plugin) {
        this.plugin = plugin;
        boolean found = Bukkit.getPluginManager().isPluginEnabled("Essentials")
                || Bukkit.getPluginManager().isPluginEnabled("EssentialsX");
        this.essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        this.available = found;

        if (available) {
            plugin.getLogger().info("EssentialsX detected and hooked.");
        } else {
            plugin.getLogger().warning("EssentialsX not detected; homes and warps integrations will be unavailable.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public Object getEssentials() {
        return essentials;
    }
}
