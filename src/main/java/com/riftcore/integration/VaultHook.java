package com.riftcore.integration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class VaultHook {
    private final JavaPlugin plugin;
    private final boolean available;
    private Object economy;

    public VaultHook(JavaPlugin plugin) {
        this.plugin = plugin;
        boolean detected = Bukkit.getPluginManager().isPluginEnabled("Vault");
        this.available = detected;
        if (detected) {
            try {
                Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(economyClass);
                this.economy = provider != null ? provider.getProvider() : null;
                if (this.economy != null) {
                    plugin.getLogger().info("Vault economy provider detected.");
                } else {
                    plugin.getLogger().warning("Vault detected but no economy provider is registered.");
                }
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("Vault was detected but its API classes are missing.");
            }
        } else {
            plugin.getLogger().warning("Vault not detected; balance integration will be unavailable.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    public double getBalance(Player player) {
        return getBalance((OfflinePlayer) player);
    }

    public double getBalance(OfflinePlayer player) {
        if (!hasEconomy() || player == null) {
            return 0.0;
        }
        try {
            Method method = economy.getClass().getMethod("getBalance", OfflinePlayer.class);
            Object result = method.invoke(economy, player);
            return result instanceof Number number ? number.doubleValue() : 0.0;
        } catch (Exception e) {
            plugin.getLogger().warning("Unable to read Vault economy balance for " + player.getName() + ": " + e.getMessage());
            return 0.0;
        }
    }

    public PaymentResult transfer(Player sender, Player target, double amount) {
        if (!hasEconomy()) {
            return new PaymentResult(false, "economy unavailable");
        }
        try {
            Method withdraw = economy.getClass().getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class);
            Object withdrawal = withdraw.invoke(economy, sender, amount);
            if (!isSuccessful(withdrawal)) {
                return new PaymentResult(false, responseReason(withdrawal, "insufficient balance"));
            }

            Method deposit = economy.getClass().getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class);
            Object depositResult = deposit.invoke(economy, target, amount);
            if (!isSuccessful(depositResult)) {
                economy.getClass().getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class)
                        .invoke(economy, sender, amount);
                return new PaymentResult(false, responseReason(depositResult, "payment failed"));
            }
            return new PaymentResult(true, "");
        } catch (Exception exception) {
            plugin.getLogger().warning("Vault payment failed: " + exception.getMessage());
            return new PaymentResult(false, "payment failed");
        }
    }

    private boolean isSuccessful(Object response) throws ReflectiveOperationException {
        Method success = response.getClass().getMethod("transactionSuccess");
        Object value = success.invoke(response);
        return value instanceof Boolean result && result;
    }

    private String responseReason(Object response, String fallback) {
        try {
            Object error = response.getClass().getMethod("errorMessage").invoke(response);
            return error == null || error.toString().isBlank() ? fallback : error.toString();
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    public record PaymentResult(boolean successful, String reason) { }
}
