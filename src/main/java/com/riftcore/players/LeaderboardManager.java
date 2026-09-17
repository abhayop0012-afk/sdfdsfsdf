package com.riftcore.players;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riftcore.RiftCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LeaderboardManager {
    private static final int LIMIT = 10;
    private static final long CACHE_MILLIS = 30_000L;
    private final RiftCore plugin;
    private final Map<Category, List<Score>> cache = new EnumMap<>(Category.class);
    private long cacheExpiresAt;

    public LeaderboardManager(RiftCore plugin) {
        this.plugin = plugin;
    }

    public void openCategoryMenu(Player player) {
        refreshCacheIfNeeded();
        show(player, "riftcore:leaderboards");
    }

    public void openLeaderboard(Player player, String category) {
        Category selected = Category.from(category);
        if (selected == null) {
            player.sendMessage(Component.text("Unknown leaderboard category."));
            return;
        }
        refreshCacheIfNeeded();
        show(player, "riftcore:leaderboard_" + selected.id);
    }

    private void refreshCacheIfNeeded() {
        if (System.currentTimeMillis() < cacheExpiresAt) return;
        Map<UUID, ScoreData> scores = new HashMap<>();
        Path statsRoot = Bukkit.getWorlds().isEmpty()
                ? Bukkit.getWorldContainer().toPath().resolve("world/stats")
                : Bukkit.getWorlds().get(0).getWorldFolder().toPath().resolve("stats");
        if (Files.isDirectory(statsRoot)) {
            try (var files = Files.list(statsRoot)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(path -> readStats(path, scores));
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not read stored Minecraft statistics: " + exception.getMessage());
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) scores.put(player.getUniqueId(), ScoreData.from(player, plugin));
        for (Map.Entry<UUID, ScoreData> entry : scores.entrySet()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            if (player.getName() == null) continue;
            entry.getValue().name = player.getName();
            entry.getValue().money = Math.round(plugin.getVaultHook().getBalance(player));
        }
        for (Category category : Category.values()) {
            List<Score> values = scores.values().stream()
                    .map(data -> new Score(data.name, data.value(category)))
                    .sorted(Comparator.comparingLong(Score::value).reversed()
                            .thenComparing(Score::name, String.CASE_INSENSITIVE_ORDER))
                    .limit(LIMIT)
                    .toList();
            cache.put(category, values);
            publish(category, values);
        }
        cacheExpiresAt = System.currentTimeMillis() + CACHE_MILLIS;
    }

    private void publish(Category category, List<Score> scores) {
        String values = java.util.stream.IntStream.range(0, LIMIT)
                .mapToObj(index -> {
                    if (index >= scores.size()) return "\"\"";
                    Score score = scores.get(index);
                    return "\"" + escapeNbt(String.format(Locale.ROOT, "#%d %s - %s", index + 1,
                            score.name(), displayValue(category, score.value()))) + "\"";
                })
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                "data modify storage riftcore:leaderboards " + category.id + " set value " + values);
    }

    private String escapeNbt(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void readStats(Path path, Map<UUID, ScoreData> scores) {
        try {
            UUID uuid = UUID.fromString(path.getFileName().toString().replace(".json", ""));
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            scores.put(uuid, ScoreData.from(root.has("stats") ? root.getAsJsonObject("stats") : root));
        } catch (Exception exception) {
            plugin.getLogger().warning("Skipping invalid statistics file " + path.getFileName() + ": " + exception.getMessage());
        }
    }

    private void show(Player player, String dialogId) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            try {
            boolean executed = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                "dialog show " + player.getName() + " " + dialogId);
            if (!executed) plugin.getLogger().severe("Native Dialog " + dialogId
                + " is unavailable in minecraft:dialog; verify the RiftCore datapack is enabled.");
            } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not show registered Dialog " + dialogId + ": " + exception.getMessage());
            }
        });
    }

    private String displayValue(Category category, long value) {
        return switch (category) {
            case MONEY -> String.format(Locale.ROOT, "$%,d", value);
            case PLAYED_TIME -> formatTicks(value);
            case DISTANCE -> String.format(Locale.ROOT, "%,d m", value / 100);
            default -> Long.toString(value);
        };
    }

    private enum Category {
        KILLS("kills", "⚔ TOP KILLS"), DEATHS("deaths", "☠ TOP DEATHS"), MONEY("money", "💰 TOP MONEY"),
        PLAYED_TIME("played_time", "⏱ TOP PLAYED TIME"), DISTANCE("distance", "🚶 TOP TRAVEL DISTANCE");
        private final String id;
        private final String title;
        Category(String id, String title) { this.id = id; this.title = title; }
        static Category from(String value) { for (Category category : values()) if (category.id.equalsIgnoreCase(value)) return category; return null; }
    }

    private record Score(String name, long value) { }

    private static final class ScoreData {
        private String name;
        private long kills;
        private long deaths;
        private long money;
        private long playedTime;
        private long distance;

        static ScoreData from(Player player, RiftCore plugin) {
            ScoreData data = new ScoreData();
            data.name = player.getName();
            data.kills = player.getStatistic(Statistic.PLAYER_KILLS);
            data.deaths = player.getStatistic(Statistic.DEATHS);
            data.playedTime = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            data.distance = distance(player);
            data.money = Math.round(plugin.getVaultHook().getBalance(player));
            return data;
        }

        static ScoreData from(JsonObject stats) {
            ScoreData data = new ScoreData();
            data.kills = number(stats, "minecraft:player_kills");
            data.deaths = number(stats, "minecraft:deaths");
            data.playedTime = number(stats, "minecraft:play_time");
            data.distance = number(stats, "minecraft:walk_one_cm") + number(stats, "minecraft:sprint_one_cm")
                    + number(stats, "minecraft:swim_one_cm") + number(stats, "minecraft:fly_one_cm")
                    + number(stats, "minecraft:boat_one_cm") + number(stats, "minecraft:minecart_one_cm")
                    + number(stats, "minecraft:horse_one_cm") + number(stats, "minecraft:pig_one_cm")
                    + number(stats, "minecraft:strider_one_cm") + number(stats, "minecraft:aviate_one_cm");
            return data;
        }

        long value(Category category) { return switch (category) { case KILLS -> kills; case DEATHS -> deaths; case MONEY -> money; case PLAYED_TIME -> playedTime; case DISTANCE -> distance; }; }
        private static long number(JsonObject object, String key) { JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() ? value.getAsLong() : 0; }
        private static long distance(Player player) { return player.getStatistic(Statistic.WALK_ONE_CM) + player.getStatistic(Statistic.SPRINT_ONE_CM) + player.getStatistic(Statistic.SWIM_ONE_CM) + player.getStatistic(Statistic.FLY_ONE_CM) + player.getStatistic(Statistic.BOAT_ONE_CM) + player.getStatistic(Statistic.MINECART_ONE_CM) + player.getStatistic(Statistic.HORSE_ONE_CM) + player.getStatistic(Statistic.PIG_ONE_CM) + player.getStatistic(Statistic.STRIDER_ONE_CM) + player.getStatistic(Statistic.AVIATE_ONE_CM); }
    }

    private static String formatTicks(long ticks) { return String.format(Locale.ROOT, "%dh %02dm", ticks / 72000, (ticks / 1200) % 60); }
}
