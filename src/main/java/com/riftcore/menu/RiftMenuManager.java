package com.riftcore.menu;

import com.riftcore.RiftCore;
import com.riftcore.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class RiftMenuManager {
    private final RiftCore plugin;
    private final ConfigManager configManager;

    public RiftMenuManager(RiftCore plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public boolean isMenuEnabled() {
        return configManager != null && configManager.isMenuEnabled();
    }

    public void clearCache() {
    }

    public void openMenu(Player player) {
        if (!isMenuEnabled()) {
            player.sendMessage(Component.text("The RiftCore menu is currently disabled."));
            return;
        }

        if (configManager.isPermissionCheckEnabled() && !player.hasPermission(configManager.getMenuPermission())) {
            player.sendMessage(Component.text("You do not have permission to open the RIFT SMP menu."));
            return;
        }

        plugin.getServer().dispatchCommand(
            plugin.getServer().getConsoleSender(),
            "dialog show " + player.getName() + " riftcore:rift_smp"
        );
    }
}
