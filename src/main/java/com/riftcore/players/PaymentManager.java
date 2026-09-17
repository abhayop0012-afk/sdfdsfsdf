package com.riftcore.players;

import com.riftcore.RiftCore;
import com.riftcore.integration.VaultHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class PaymentManager implements Listener {
    private static final long TIMEOUT_TICKS = 60L * 20L;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");

    private final RiftCore plugin;
    private final Map<UUID, PendingPayment> pending = new ConcurrentHashMap<>();

    public PaymentManager(RiftCore plugin) {
        this.plugin = plugin;
    }

    public void begin(Player sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || target.equals(sender)) {
            sender.sendMessage(Component.text("That player is no longer available for payment."));
            return;
        }
        if (!sender.hasPermission(plugin.getConfigManager().getPayPermission())) {
            sender.sendMessage(Component.text("You do not have permission to pay players."));
            return;
        }
        if (!plugin.getVaultHook().hasEconomy()) {
            sender.sendMessage(Component.text("Payment unavailable: economy unavailable."));
            return;
        }

        pending.put(sender.getUniqueId(), new PendingPayment(target.getUniqueId(), null, System.currentTimeMillis() + 60_000L));
        sender.sendMessage(Component.text("Pay " + target.getName()));
        sender.sendMessage(Component.text("Enter the amount you want to pay " + target.getName() + " in chat."));
        scheduleExpiry(sender.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void captureAmount(AsyncPlayerChatEvent event) {
        if (!pending.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String input = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> acceptAmount(event.getPlayer(), input));
    }

    private void acceptAmount(Player sender, String input) {
        PendingPayment current = pending.get(sender.getUniqueId());
        if (current == null || expired(current)) {
            pending.remove(sender.getUniqueId());
            sender.sendMessage(Component.text("Payment input expired."));
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(input);
        } catch (NumberFormatException exception) {
            sender.sendMessage(Component.text("Invalid amount. Enter a positive numeric amount."));
            return;
        }
        if (amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0) {
            sender.sendMessage(Component.text("Invalid amount. Enter a positive amount with at most two decimals."));
            return;
        }
        double numeric = amount.doubleValue();
        if (!Double.isFinite(numeric) || numeric <= 0.0D) {
            sender.sendMessage(Component.text("Invalid amount."));
            return;
        }

        Player target = Bukkit.getPlayer(current.targetId());
        if (target == null || !target.isOnline() || target.equals(sender)) {
            pending.remove(sender.getUniqueId());
            sender.sendMessage(Component.text("Payment failed: target offline."));
            return;
        }
        pending.put(sender.getUniqueId(), new PendingPayment(target.getUniqueId(), amount, current.expiresAt()));
        sender.sendMessage(Component.text("Pay " + format(amount) + " to " + target.getName() + "?"));
        sender.sendMessage(Component.text("Confirm Payment")
                .clickEvent(ClickEvent.runCommand("/riftcore players pay confirm")));
        sender.sendMessage(Component.text("Cancel")
                .clickEvent(ClickEvent.runCommand("/riftcore players pay cancel")));
    }

    public void confirm(Player sender) {
        PendingPayment current = pending.remove(sender.getUniqueId());
        if (current == null || current.amount() == null || expired(current)) {
            sender.sendMessage(Component.text("Payment expired or is no longer pending."));
            return;
        }
        Player target = Bukkit.getPlayer(current.targetId());
        if (target == null || !target.isOnline() || target.equals(sender)) {
            sender.sendMessage(Component.text("Payment failed: target offline."));
            return;
        }
        if (!plugin.getSettingsManager().canReceivePay(target)) {
            sender.sendMessage(Component.text("✖ Payment failed."));
            sender.sendMessage(Component.text("This player has disabled receiving payments."));
            return;
        }
        VaultHook.PaymentResult result = plugin.getVaultHook().transfer(sender, target, current.amount().doubleValue());
        if (!result.successful()) {
            sender.sendMessage(Component.text("Payment failed: " + result.reason() + "."));
            return;
        }
        sender.sendMessage(Component.text("Successfully paid " + format(current.amount()) + " to " + target.getName() + "."));
        target.sendMessage(Component.text(sender.getName() + " paid you " + format(current.amount()) + "."));
    }

    public void cancel(Player sender) {
        if (pending.remove(sender.getUniqueId()) != null) {
            sender.sendMessage(Component.text("Payment cancelled."));
        }
    }

    public void clear(Player player) {
        pending.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pending.remove(playerId);
        pending.entrySet().removeIf(entry -> entry.getValue().targetId().equals(playerId));
    }

    private void scheduleExpiry(UUID senderId) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingPayment current = pending.get(senderId);
            if (current != null && expired(current)) {
                pending.remove(senderId);
                Player sender = Bukkit.getPlayer(senderId);
                if (sender != null) sender.sendMessage(Component.text("Payment input expired."));
            }
        }, TIMEOUT_TICKS);
    }

    private boolean expired(PendingPayment payment) {
        return payment.expiresAt() < System.currentTimeMillis();
    }

    private String format(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.DOWN).toPlainString();
    }

    private record PendingPayment(UUID targetId, BigDecimal amount, long expiresAt) { }
}