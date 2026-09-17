package com.riftcore.command;

import com.riftcore.RiftCore;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.riftcore.players.SettingsManager;

import java.util.Collection;
import java.util.List;

public final class RiftCoreCommand implements BasicCommand {
    private final RiftCore plugin;

    public RiftCoreCommand(RiftCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.openMenu(player);
            } else {
                sender.sendMessage(Component.text("The RiftCore menu can only be opened by a player."));
            }
            return;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "reload" -> {
                if (!sender.hasPermission(plugin.getConfigManager().getAdminPermission())) {
                    sender.sendMessage(Component.text("You do not have permission to reload RiftCore."));
                    return;
                }
                plugin.getConfigManager().reload();
                plugin.reloadFeatures();
                plugin.getMenuManager().clearCache();
                sender.sendMessage(Component.text("RiftCore configuration reloaded."));
            }
            case "debug" -> {
                if (!sender.hasPermission(plugin.getConfigManager().getAdminPermission())) {
                    sender.sendMessage(Component.text("You do not have permission to run RiftCore debug."));
                    return;
                }
                sender.sendMessage(buildDebugMessage());
            }
            case "menu" -> {
                if (sender instanceof Player player) {
                    plugin.openMenu(player);
                } else {
                    sender.sendMessage(Component.text("The console cannot open the menu."));
                }
            }
            case "players" -> handlePlayers(sender, args);
            case "leaderboards" -> handleLeaderboards(sender);
            case "leaderboard" -> handleLeaderboard(sender, args);
            case "settings" -> handleSettings(sender, args);
            case "friend" -> handleFriend(sender, args);
            default -> {
                sender.sendMessage(Component.text("Usage: /riftcore [reload|debug|menu|players|leaderboards|leaderboard|friend]"));
            }
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            return List.of("reload", "debug", "menu", "players", "leaderboards", "leaderboard", "settings", "friend");
        }
        if (args[0].equalsIgnoreCase("friend") && args.length == 2) {
            return List.of("add", "remove", "list");
        }
        if (args[0].equalsIgnoreCase("friend") && args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return true;
    }

    @Override
    public String permission() {
        return "riftcore.menu";
    }

    private Component buildDebugMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("==== RiftCore Debug ====" + System.lineSeparator());
        sb.append("Version: 1.0.0" + System.lineSeparator());
        sb.append("Paper version: ").append(plugin.getServer().getVersion()).append(System.lineSeparator());
        sb.append("Java version: ").append(Runtime.version()).append(System.lineSeparator());
        sb.append("Native Dialog datapack: Available" + System.lineSeparator());
        sb.append("Vault: ").append(plugin.getVaultHook().isAvailable() ? "Hooked" : "Unavailable").append(System.lineSeparator());
        sb.append("Economy: ").append(plugin.getVaultHook().hasEconomy() ? "Available" : "Missing").append(System.lineSeparator());
        sb.append("LuckPerms: ").append(plugin.getLuckPermsHook().isAvailable() ? "Hooked" : "Unavailable").append(System.lineSeparator());
        sb.append("EssentialsX: ").append(plugin.getEssentialsHook().isAvailable() ? "Hooked" : "Unavailable").append(System.lineSeparator());
        sb.append("PlaceholderAPI: ").append(plugin.getPlaceholderHook().isAvailable() ? "Hooked" : "Unavailable").append(System.lineSeparator());
        sb.append("Menu: ").append(plugin.getMenuManager().isMenuEnabled() ? "Enabled" : "Disabled").append(System.lineSeparator());
        sb.append("Vanilla ESC menu integration: unsupported by Paper/server-side API").append(System.lineSeparator());
        sb.append("Native Dialog menu: available").append(System.lineSeparator());
        return Component.text(sb.toString());
    }

    private void handlePlayers(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("The players menu can only be opened by a player."));
            return;
        }
        if (args.length == 1) {
            plugin.getPlayerFeatureManager().openPlayersMenu(player);
        } else if (args[1].equalsIgnoreCase("page") && args.length >= 3) {
            plugin.getPlayerFeatureManager().openPlayersMenu(player, parsePage(args[2]));
        } else if (args[1].equalsIgnoreCase("select") && args.length >= 3) {
            plugin.getPlayerFeatureManager().selectPlayer(player, args[2]);
        } else if (args[1].equalsIgnoreCase("slot") && args.length >= 3) {
            plugin.getPlayerFeatureManager().selectPlayerSlot(player, parsePage(args[2]));
        } else if (args[1].equalsIgnoreCase("action") && args.length >= 3) {
            plugin.getPlayerFeatureManager().playerAction(player, args[2]);
        } else if (args[1].equalsIgnoreCase("pay") && args.length >= 3) {
            if (args[2].equalsIgnoreCase("confirm")) {
                plugin.getPaymentManager().confirm(player);
            } else if (args[2].equalsIgnoreCase("cancel")) {
                plugin.getPaymentManager().cancel(player);
            } else {
                plugin.getPaymentManager().begin(player, args[2]);
            }
        } else if (args[1].equalsIgnoreCase("tpa") && args.length >= 3) {
            plugin.getPlayerFeatureManager().tpaAction(player, args[2], args.length >= 4 ? args[3] : "");
        } else if (args[1].equalsIgnoreCase("friend") && args.length >= 3) {
            plugin.getPlayerFeatureManager().friendAction(player, args[2], args.length >= 4 ? args[3] : "");
        } else {
            player.sendMessage(Component.text("Usage: /riftcore players [page|select|tpa|friend] ..."));
        }
    }

    private int parsePage(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void handleLeaderboards(CommandSender sender) {
        if (sender instanceof Player player) {
            plugin.getLeaderboardManager().openCategoryMenu(player);
        } else {
            sender.sendMessage(Component.text("Leaderboards can only be opened by a player."));
        }
    }

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Leaderboards can only be opened by a player."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /riftcore leaderboard [kills|deaths|money|played_time|distance]"));
            return;
        }
        plugin.getLeaderboardManager().openLeaderboard(player, args[1]);
    }

    private void handleSettings(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Settings can only be opened by a player."));
            return;
        }
        if (!player.hasPermission(plugin.getConfigManager().getSettingsPermission())) {
            player.sendMessage(Component.text("You do not have permission to use settings."));
            return;
        }
        if (args.length == 1) {
            plugin.getSettingsManager().open(player);
            return;
        }
        if (args.length >= 4 && args[1].equalsIgnoreCase("set")) {
            SettingsManager.Setting setting = SettingsManager.Setting.fromKey(args[2]);
            boolean enabled = args[3].equalsIgnoreCase("on");
            if (setting != null && (enabled || args[3].equalsIgnoreCase("off"))) {
                plugin.getSettingsManager().set(player, setting, enabled);
                return;
            }
        }
        player.sendMessage(Component.text("Invalid settings action."));
    }

    private void handleFriend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("The friend command can only be used by a player."));
            return;
        }
        if (!player.hasPermission(plugin.getConfigManager().getFriendPermission())) {
            player.sendMessage(Component.text("You do not have permission to use friends."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /riftcore friend [add|remove|list] [player]"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) player.sendMessage(Component.text("Usage: /riftcore friend add <player>"));
                else plugin.getPlayerFeatureManager().addFriend(player, args[2]);
            }
            case "remove" -> {
                if (args.length < 3) player.sendMessage(Component.text("Usage: /riftcore friend remove <player>"));
                else plugin.getPlayerFeatureManager().removeFriend(player, args[2]);
            }
            case "list" -> plugin.getPlayerFeatureManager().listFriends(player);
            default -> player.sendMessage(Component.text("Usage: /riftcore friend [add|remove|list] [player]"));
        }
    }
}
