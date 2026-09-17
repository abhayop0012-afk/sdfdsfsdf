package com.riftcore.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckPermsHook {
    private final JavaPlugin plugin;
    private final boolean available;
    private Object api;

    public LuckPermsHook(JavaPlugin plugin) {
        this.plugin = plugin;
        boolean found = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
        try {
            Class.forName("net.luckperms.api.LuckPerms");
            this.api = Bukkit.getServicesManager().getRegistration(Class.forName("net.luckperms.api.LuckPerms")) != null
                    ? Bukkit.getServicesManager().getRegistration(Class.forName("net.luckperms.api.LuckPerms")).getProvider()
                    : null;
        } catch (ClassNotFoundException e) {
            this.api = null;
        }
        this.available = found;

        if (available) {
            plugin.getLogger().info("LuckPerms detected and hooked.");
        } else {
            plugin.getLogger().warning("LuckPerms not detected; prefix/rank data will be unavailable.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public Object getApi() {
        return api;
    }
}
