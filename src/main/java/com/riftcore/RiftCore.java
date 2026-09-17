package com.riftcore;

import com.riftcore.command.RiftCoreCommand;
import com.riftcore.config.ConfigManager;
import com.riftcore.integration.EssentialsHook;
import com.riftcore.integration.LuckPermsHook;
import com.riftcore.integration.PlaceholderHook;
import com.riftcore.integration.VaultHook;
import com.riftcore.menu.RiftMenuManager;
import com.riftcore.players.PlayerFeatureManager;
import com.riftcore.players.PaymentManager;
import com.riftcore.players.LeaderboardManager;
import com.riftcore.players.SettingsManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class RiftCore extends JavaPlugin {
    private ConfigManager configManager;
    private RiftMenuManager menuManager;
    private VaultHook vaultHook;
    private LuckPermsHook luckPermsHook;
    private EssentialsHook essentialsHook;
    private PlaceholderHook placeholderHook;
    private PlayerFeatureManager playerFeatureManager;
    private PaymentManager paymentManager;
    private LeaderboardManager leaderboardManager;
    private SettingsManager settingsManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.reload();

        this.menuManager = new RiftMenuManager(this, configManager);
        this.playerFeatureManager = new PlayerFeatureManager(this, configManager);
        this.paymentManager = new PaymentManager(this);
        this.leaderboardManager = new LeaderboardManager(this);
        this.settingsManager = new SettingsManager(this);
        getServer().getPluginManager().registerEvents(playerFeatureManager, this);
        getServer().getPluginManager().registerEvents(paymentManager, this);
        getServer().getPluginManager().registerEvents(settingsManager, this);
        this.vaultHook = new VaultHook(this);
        this.luckPermsHook = new LuckPermsHook(this);
        this.essentialsHook = new EssentialsHook(this);
        this.placeholderHook = new PlaceholderHook(this);

        registerCommands();
        logStartupStatus();
    }

    @Override
    public void onDisable() {
        if (this.menuManager != null) {
            this.menuManager.clearCache();
        }
        if (this.playerFeatureManager != null) {
            this.playerFeatureManager.shutdown();
        }
        if (this.settingsManager != null) {
            this.settingsManager.shutdown();
        }
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register("riftcore", List.of("rift"), new RiftCoreCommand(this));
        });
    }

    private void logStartupStatus() {
        getLogger().info("RiftCore v1.0.0 enabled.");
        getLogger().info("Paper version: " + getServer().getVersion());
        getLogger().info("Java version: " + Runtime.version());
        getLogger().info("Dialog API: Available");
        getLogger().info("Menu status: " + (menuManager != null && menuManager.isMenuEnabled()));
        getLogger().info("Pause-menu integration: native pause_screen_additions Dialog enabled.");

        if (isDebugEnabled()) {
            getLogger().info("Vault: " + (vaultHook.isAvailable() ? "Hooked" : "Unavailable"));
            getLogger().info("Economy provider: " + (vaultHook.hasEconomy() ? "Available" : "Missing"));
            getLogger().info("LuckPerms: " + (luckPermsHook.isAvailable() ? "Hooked" : "Unavailable"));
            getLogger().info("EssentialsX: " + (essentialsHook.isAvailable() ? "Hooked" : "Unavailable"));
            getLogger().info("PlaceholderAPI: " + (placeholderHook.isAvailable() ? "Hooked" : "Unavailable"));
        }
    }

    public boolean isDebugEnabled() {
        return configManager != null && configManager.isDebugEnabled();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RiftMenuManager getMenuManager() {
        return menuManager;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public EssentialsHook getEssentialsHook() {
        return essentialsHook;
    }

    public PlaceholderHook getPlaceholderHook() {
        return placeholderHook;
    }

    public PlayerFeatureManager getPlayerFeatureManager() {
        return playerFeatureManager;
    }

    public PaymentManager getPaymentManager() {
        return paymentManager;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public void reloadFeatures() {
        playerFeatureManager.reload();
        settingsManager.reload();
    }

    public void openMenu(Player player) {
        if (menuManager == null) {
            player.sendMessage("RiftCore menu is unavailable.");
            return;
        }
        menuManager.openMenu(player);
    }

}
