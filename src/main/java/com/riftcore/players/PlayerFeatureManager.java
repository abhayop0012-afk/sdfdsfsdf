package com.riftcore.players;

import com.riftcore.RiftCore;
import com.riftcore.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerFeatureManager implements Listener {
    private static final int MAX_PLAYER_SLOTS = 12;
    private final RiftCore plugin;
    private final ConfigManager config;
    private final Map<UUID, TpaRequest> tpaRequests = new HashMap<>();
    private final Map<UUID, FriendRequest> friendRequests = new HashMap<>();
    private final Map<UUID, Long> tpaCooldowns = new HashMap<>();
    private final Map<UUID, Set<UUID>> friends = new HashMap<>();
    private final File friendFile;
    private final Map<UUID, List<UUID>> playerSlots = new HashMap<>();
    private final Map<UUID, UUID> selectedPlayers = new HashMap<>();

    public PlayerFeatureManager(RiftCore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.friendFile = new File(plugin.getDataFolder(), "friends.yml");
        loadFriends();
    }

    public boolean isEnabled(Player player) {
        return config.isPlayersEnabled() && player.hasPermission(config.getPlayersPermission());
    }

    public void openPlayersMenu(Player viewer) {
        openPlayersMenu(viewer, 0);
    }

    public void selectPlayer(Player viewer, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            viewer.sendMessage(Component.text("That player is no longer online."));
            return;
        }
        openPlayerActions(viewer, target.getUniqueId());
    }

    public void selectPlayerSlot(Player viewer, int slot) {
        List<UUID> slots = playerSlots.getOrDefault(viewer.getUniqueId(), List.of());
        if (slot < 0 || slot >= slots.size()) {
            openPlayersMenu(viewer);
            return;
        }
        openPlayerActions(viewer, slots.get(slot));
    }

    public void playerAction(Player viewer, String action) {
        UUID targetId = selectedPlayers.get(viewer.getUniqueId());
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline() || target.equals(viewer)) {
            openPlayersMenu(viewer);
            return;
        }
        switch (action.toLowerCase()) {
            case "pay" -> {
                if (viewer.hasPermission(config.getPayPermission())) plugin.getPaymentManager().begin(viewer, target.getName());
            }
            case "tpa" -> {
                if (config.isTpaEnabled() && viewer.hasPermission(config.getTpaPermission())) sendTpa(viewer, target.getUniqueId());
            }
            case "friend" -> {
                if (config.isFriendsEnabled() && viewer.hasPermission(config.getFriendPermission())) sendFriendRequest(viewer, target.getUniqueId());
            }
            default -> openPlayersMenu(viewer);
        }
    }

    public void tpaAction(Player player, String action, String targetName) {
        if (action.equalsIgnoreCase("accept")) {
            TpaRequest request = tpaRequests.get(player.getUniqueId());
            if (request != null) acceptTpa(player, request);
            return;
        }
        if (action.equalsIgnoreCase("deny")) {
            TpaRequest request = tpaRequests.get(player.getUniqueId());
            if (request != null) denyTpa(player, request);
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) sendTpa(player, target.getUniqueId());
    }

    public void friendAction(Player player, String action, String targetName) {
        if (action.equalsIgnoreCase("accept")) {
            FriendRequest request = friendRequests.get(player.getUniqueId());
            if (request != null) acceptFriend(player, request);
            return;
        }
        if (action.equalsIgnoreCase("deny")) {
            FriendRequest request = friendRequests.get(player.getUniqueId());
            if (request != null) denyFriend(player, request);
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) sendFriendRequest(player, target.getUniqueId());
    }

    public void openPlayersMenu(Player viewer, int page) {
        if (!isEnabled(viewer)) {
            viewer.sendMessage(Component.text("You do not have permission to view online players."));
            return;
        }

        List<Player> online = onlinePlayers(viewer);
        int perPage = Math.min(MAX_PLAYER_SLOTS, config.getPlayersPerPage());
        int maxPage = Math.max(0, (online.size() - 1) / perPage);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * perPage;
        int end = Math.min(start + perPage, online.size());
        List<UUID> slots = online.subList(start, end).stream().map(Player::getUniqueId).toList();
        playerSlots.put(viewer.getUniqueId(), slots);
        publishPlayerNames(online.subList(start, end));
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                "dialog show " + viewer.getName() + " riftcore:players");
    }

    private void publishPlayerNames(List<Player> players) {
        String values = players.stream()
                .map(player -> "\"" + escapeNbt(player.getName()) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                "data modify storage riftcore:players players set value " + values);
    }

    private String escapeNbt(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void openPlayerActions(Player viewer, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline() || target.equals(viewer)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> openPlayersMenu(viewer));
            return;
        }
        selectedPlayers.put(viewer.getUniqueId(), target.getUniqueId());
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
            "data modify storage riftcore:players selected set value \"" + escapeNbt(target.getName()) + "\"");
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
            "dialog show " + viewer.getName() + " riftcore:player_actions");
    }

    public void sendTpa(Player requester, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (!canSendTpa(requester, target)) {
            return;
        }
        if (!plugin.getSettingsManager().isReceivingTpa(target)) {
            requester.sendMessage(Component.text("✖ This player has disabled TPA Requests."));
            return;
        }
        long cooldownUntil = tpaCooldowns.getOrDefault(requester.getUniqueId(), 0L);
        if (cooldownUntil > System.currentTimeMillis()) {
            requester.sendMessage(Component.text("Please wait before sending another teleport request."));
            return;
        }
        if (tpaRequests.containsKey(requester.getUniqueId()) || tpaRequests.containsKey(targetId)) {
            requester.sendMessage(Component.text("One of you already has an active teleport request."));
            return;
        }

        TpaRequest request = new TpaRequest(requester.getUniqueId(), targetId);
        tpaRequests.put(requester.getUniqueId(), request);
        tpaRequests.put(targetId, request);
        tpaCooldowns.put(requester.getUniqueId(), System.currentTimeMillis() + config.getTpaCooldownSeconds() * 1000L);
        requester.sendMessage(Component.text("Teleport request sent to " + target.getName() + "."));
        if (plugin.getSettingsManager().hasTpaNotifications(target)) {
            showTpaRequest(target, request);
        }
        scheduleTpaExpiry(request);
    }

    public void sendFriendRequest(Player requester, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (!config.isFriendsEnabled() || !requester.hasPermission(config.getFriendPermission()) || target == null || requester.equals(target)) {
            requester.sendMessage(Component.text("You cannot send a friend request to that player."));
            return;
        }
        if (areFriends(requester.getUniqueId(), targetId)) {
            requester.sendMessage(Component.text("You are already friends with " + target.getName() + "."));
            return;
        }
        if (friendRequests.containsKey(requester.getUniqueId()) || friendRequests.containsKey(targetId)) {
            requester.sendMessage(Component.text("One of you already has an active friend request."));
            return;
        }

        FriendRequest request = new FriendRequest(requester.getUniqueId(), targetId);
        friendRequests.put(requester.getUniqueId(), request);
        friendRequests.put(targetId, request);
        requester.sendMessage(Component.text("Friend request sent to " + target.getName() + "."));
        showFriendRequest(target, request);
        scheduleFriendExpiry(request);
    }

    public void addFriend(Player requester, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            requester.sendMessage(Component.text("That player must be online."));
            return;
        }
        sendFriendRequest(requester, target.getUniqueId());
    }

    public void removeFriend(Player requester, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            requester.sendMessage(Component.text("That player has not joined this server."));
            return;
        }
        UUID requesterId = requester.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (!areFriends(requesterId, targetId)) {
            requester.sendMessage(Component.text("You are not friends with " + target.getName() + "."));
            return;
        }
        friends.computeIfAbsent(requesterId, ignored -> new HashSet<>()).remove(targetId);
        friends.computeIfAbsent(targetId, ignored -> new HashSet<>()).remove(requesterId);
        saveFriends();
        requester.sendMessage(Component.text("Removed " + (target.getName() == null ? targetName : target.getName()) + " from your friends."));
    }

    public void listFriends(Player player) {
        List<String> names = new ArrayList<>();
        for (UUID id : friends.getOrDefault(player.getUniqueId(), Set.of())) {
            OfflinePlayer friend = Bukkit.getOfflinePlayer(id);
            names.add(friend.getName() == null ? id.toString() : friend.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        player.sendMessage(Component.text(names.isEmpty() ? "You have no friends saved." : "Friends: " + String.join(", ", names)));
    }

    public void reload() {
        loadFriends();
    }

    public void shutdown() {
        saveFriends();
        tpaRequests.clear();
        friendRequests.clear();
    }

    private void showTpaRequest(Player target, TpaRequest request) {
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) return;
        target.sendMessage(Component.text(requester.getName() + " wants to teleport to you."));
        target.sendMessage(Component.text("ACCEPT")
            .clickEvent(ClickEvent.runCommand("/riftcore players tpa accept")));
        target.sendMessage(Component.text("DENY")
            .clickEvent(ClickEvent.runCommand("/riftcore players tpa deny")));
    }

    private void showFriendRequest(Player target, FriendRequest request) {
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) return;
        target.sendMessage(Component.text(requester.getName() + " wants to add you as a friend."));
        target.sendMessage(Component.text("ACCEPT")
            .clickEvent(ClickEvent.runCommand("/riftcore players friend accept")));
        target.sendMessage(Component.text("DENY")
            .clickEvent(ClickEvent.runCommand("/riftcore players friend deny")));
    }

    private void acceptTpa(Player target, TpaRequest request) {
        if (!removeTpa(request)) return;
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null || !requester.isOnline() || !target.isOnline()) return;
        requester.teleport(target.getLocation());
        requester.sendMessage(Component.text("Your teleport request was accepted."));
        target.sendMessage(Component.text("Teleport request accepted."));
    }

    private void denyTpa(Player target, TpaRequest request) {
        if (!removeTpa(request)) return;
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) requester.sendMessage(Component.text(target.getName() + " denied your teleport request."));
        target.sendMessage(Component.text("Teleport request denied."));
    }

    private void acceptFriend(Player target, FriendRequest request) {
        if (!removeFriendRequest(request)) return;
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) return;
        friends.computeIfAbsent(request.requester(), ignored -> new HashSet<>()).add(request.target());
        friends.computeIfAbsent(request.target(), ignored -> new HashSet<>()).add(request.requester());
        saveFriends();
        requester.sendMessage(Component.text(target.getName() + " accepted your friend request."));
        target.sendMessage(Component.text("You are now friends with " + requester.getName() + "."));
    }

    private void denyFriend(Player target, FriendRequest request) {
        if (!removeFriendRequest(request)) return;
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) requester.sendMessage(Component.text(target.getName() + " denied your friend request."));
        target.sendMessage(Component.text("Friend request denied."));
    }

    private boolean canSendTpa(Player requester, Player target) {
        if (!config.isTpaEnabled() || !requester.hasPermission(config.getTpaPermission()) || target == null || !target.isOnline() || requester.equals(target)) {
            requester.sendMessage(Component.text("You cannot send a teleport request to that player."));
            return false;
        }
        return true;
    }

    private List<Player> onlinePlayers(Player viewer) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> config.isShowSelf() || !player.equals(viewer))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .map(player -> (Player) player)
                .toList();
    }

    private void scheduleTpaExpiry(TpaRequest request) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (removeTpa(request)) {
                Player requester = Bukkit.getPlayer(request.requester());
                if (requester != null) requester.sendMessage(Component.text("Your teleport request expired."));
            }
        }, config.getTpaTimeoutSeconds() * 20L);
    }

    private void scheduleFriendExpiry(FriendRequest request) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (removeFriendRequest(request)) {
                Player requester = Bukkit.getPlayer(request.requester());
                if (requester != null) requester.sendMessage(Component.text("Your friend request expired."));
            }
        }, config.getFriendRequestTimeoutSeconds() * 20L);
    }

    private boolean removeTpa(TpaRequest request) {
        boolean active = tpaRequests.get(request.requester()) == request || tpaRequests.get(request.target()) == request;
        tpaRequests.remove(request.requester(), request);
        tpaRequests.remove(request.target(), request);
        return active;
    }

    private boolean removeFriendRequest(FriendRequest request) {
        boolean active = friendRequests.get(request.requester()) == request || friendRequests.get(request.target()) == request;
        friendRequests.remove(request.requester(), request);
        friendRequests.remove(request.target(), request);
        return active;
    }

    private boolean areFriends(UUID first, UUID second) {
        return friends.getOrDefault(first, Set.of()).contains(second);
    }

    private void loadFriends() {
        friends.clear();
        if (!friendFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(friendFile);
        for (String key : data.getConfigurationSection("friends") == null ? Set.<String>of() : data.getConfigurationSection("friends").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                Set<UUID> entries = new HashSet<>();
                for (String value : data.getStringList("friends." + key)) {
                    try { entries.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
                }
                friends.put(uuid, entries);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveFriends() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> entry : friends.entrySet()) {
            data.set("friends." + entry.getKey(), entry.getValue().stream().map(UUID::toString).toList());
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(friendFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save friends.yml: " + exception.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        TpaRequest tpa = tpaRequests.get(playerId);
        if (tpa != null) removeTpa(tpa);
        FriendRequest friend = friendRequests.get(playerId);
        if (friend != null) removeFriendRequest(friend);
        tpaCooldowns.remove(playerId);
        playerSlots.remove(playerId);
        selectedPlayers.remove(playerId);
    }

    private record TpaRequest(UUID requester, UUID target) { }
    private record FriendRequest(UUID requester, UUID target) { }
}
