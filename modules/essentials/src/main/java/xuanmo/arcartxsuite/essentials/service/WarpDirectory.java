package xuanmo.arcartxsuite.essentials.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

public final class WarpDirectory {
    private final EssentialsRepository repository;
    private final Path configFile;
    private final Logger logger;
    private volatile Map<String, Warp> configured;

    public WarpDirectory(EssentialsRepository repository, Path configFile, YamlConfiguration yaml, Logger logger) {
        this.repository = repository;
        this.configFile = configFile.toAbsolutePath();
        this.logger = logger;
        this.configured = loadWarps(yaml.getConfigurationSection("warps"), logger);
    }

    public List<Warp> visible(Player player) {
        return configured.values().stream().filter(warp -> canUse(player, warp)).toList();
    }

    public List<String> names(Player player, boolean admin) {
        return (admin ? configured.values().stream() : visible(player).stream())
            .map(Warp::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<String> adminNames(String query) {
        String filter = key(query);
        return configured.values().stream()
            .filter(warp -> matches(warp, filter))
            .map(Warp::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public Location destination(Player player, String name) {
        Warp warp = configured.get(key(name));
        if (warp == null || !canUse(player, warp)) return null;
        var world = Bukkit.getWorld(warp.world());
        return world == null ? null : new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch());
    }

    public String favoriteKey(Player player, String name) {
        String id = key(name);
        Warp warp = configured.get(id);
        if (warp == null || !canUse(player, warp)) {
            throw new IllegalArgumentException("找不到可用的传送点 " + name);
        }
        return id;
    }

    public boolean toggleFavorite(UUID player, String id) throws SQLException {
        boolean favorite = !repository.warpFavorites(player).contains(id);
        repository.setWarpFavorite(player, id, favorite);
        return favorite;
    }

    /** 调用方在主线程捕获可见配置项，查询个人收藏可在数据库线程运行。 */
    public List<Entry> list(UUID player, List<Warp> visible, String query, boolean favoritesOnly) throws SQLException {
        var favorites = repository.warpFavorites(player);
        String filter = key(query);
        return visible.stream()
            .filter(warp -> matches(warp, filter))
            .map(warp -> new Entry(warp.name(), warp.group(), favorites.contains(key(warp.name()))))
            .filter(entry -> !favoritesOnly || entry.favorite())
            .sorted(Comparator.comparing(Entry::favorite).reversed().thenComparing(Entry::group, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public synchronized void setWarp(String name, String world, double x, double y, double z, float yaw, float pitch)
        throws IOException {
        requireValidName(name);
        if (world.isBlank() || !validCoordinate(x) || !validCoordinate(y) || !validCoordinate(z)
            || !validCoordinate(yaw) || !validCoordinate(pitch)) {
            throw new IllegalArgumentException("传送点世界或坐标无效。");
        }

        String original = Files.readString(configFile, StandardCharsets.UTF_8);
        YamlConfiguration yaml = parse(original);
        ConfigurationSection warps = warpSection(yaml);
        if (warps == null) warps = yaml.createSection("warps");
        String existing = findKey(warps, name);
        String id = existing == null ? name : existing;
        ConfigurationSection entry = warps.getConfigurationSection(id);
        if (entry == null) entry = warps.createSection(id);
        entry.set("world", world);
        entry.set("x", x);
        entry.set("y", y);
        entry.set("z", z);
        entry.set("yaw", yaw);
        entry.set("pitch", pitch);
        save(yaml, warps, original);
    }

    public synchronized boolean deleteWarp(String name) throws IOException {
        requireValidName(name);
        String original = Files.readString(configFile, StandardCharsets.UTF_8);
        YamlConfiguration yaml = parse(original);
        ConfigurationSection warps = warpSection(yaml);
        String existing = warps == null ? null : findKey(warps, name);
        if (existing == null) return false;
        warps.set(existing, null);
        save(yaml, warps, original);
        return true;
    }

    private static YamlConfiguration parse(String text) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        try {
            yaml.loadFromString(text);
            return yaml;
        } catch (InvalidConfigurationException e) {
            throw new IOException("config.yml 无法解析，请修正后重试", e);
        }
    }

    private static ConfigurationSection warpSection(YamlConfiguration yaml) throws IOException {
        ConfigurationSection warps = yaml.getConfigurationSection("warps");
        if (warps == null && yaml.isSet("warps")) {
            throw new IOException("config.yml 的 warps 必须是配置节");
        }
        return warps;
    }

    private void save(YamlConfiguration yaml, ConfigurationSection warps, String original) throws IOException {
        Map<String, Warp> next = loadWarps(warps, logger);
        Path temporary = Files.createTempFile(configFile.getParent(), "essentials-config-", ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            if (!Files.readString(configFile, StandardCharsets.UTF_8).equals(original)) {
                throw new IOException("config.yml 已被其他操作修改，请重试");
            }
            Files.move(temporary, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            configured = next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String findKey(ConfigurationSection warps, String name) {
        String match = null;
        String normalized = key(name);
        for (String existing : warps.getKeys(false)) {
            if (!key(existing).equals(normalized)) continue;
            if (match != null) throw new IllegalArgumentException("配置中有多个同名传送点，请先修正 warps 节。");
            match = existing;
        }
        return match;
    }

    private static void requireValidName(String name) {
        if (!validName(name)) {
            throw new IllegalArgumentException("传送点名称须为 1 至 64 个字符，且不能包含句点或控制字符。");
        }
    }

    private static boolean validName(String name) {
        return !name.isBlank() && name.length() <= 64 && !name.contains(".") && name.chars().noneMatch(Character::isISOControl);
    }

    private static boolean validCoordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) < 30_000_000;
    }

    private static boolean matches(Warp warp, String filter) {
        return key(warp.name()).contains(filter) || key(warp.group()).contains(filter);
    }

    private static boolean canUse(Player player, Warp warp) {
        return warp.enabled() && (warp.permission().isEmpty() || player.hasPermission(warp.permission()));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static Map<String, Warp> loadWarps(ConfigurationSection root, Logger logger) {
        if (root == null) return Map.of();
        Map<String, Warp> result = new LinkedHashMap<>();
        root.getValues(false).forEach((id, raw) -> {
            String key = id.toLowerCase(Locale.ROOT);
            if (!validName(id)) {
                logger.warning("Essentials 跳过无效 Warp 名称 " + id);
                return;
            }
            if (result.containsKey(key)) {
                logger.warning("Essentials Warp " + id + " 与其他配置重名，已停用。");
                result.put(key, new Warp(id, "", 0, 0, 0, 0, 0, "默认", "", false));
                return;
            }
            try {
                if (!(raw instanceof ConfigurationSection entry)) throw new IllegalArgumentException("需要配置节");
                if (entry.isSet("enabled") && !entry.isBoolean("enabled")) throw new IllegalArgumentException("enabled 必须为布尔值");
                boolean enabled = entry.getBoolean("enabled", true);
                String world = entry.getString("world", "").trim();
                if (enabled && world.isEmpty()) throw new IllegalArgumentException("需要 world");
                double x = coordinate(entry, "x", enabled);
                double y = coordinate(entry, "y", enabled);
                double z = coordinate(entry, "z", enabled);
                float yaw = (float) coordinate(entry, "yaw", false);
                float pitch = (float) coordinate(entry, "pitch", false);
                String group = entry.getString("group", "默认").trim();
                result.put(key, new Warp(id, world, x, y, z, yaw, pitch, group.isEmpty() ? "默认" : group,
                    entry.getString("permission", "").trim(), enabled));
            } catch (IllegalArgumentException error) {
                logger.warning("Essentials Warp " + id + " 已停用：" + error.getMessage());
                result.put(key, new Warp(id, "", 0, 0, 0, 0, 0, "默认", "", false));
            }
        });
        return Map.copyOf(result);
    }

    private static double coordinate(ConfigurationSection entry, String field, boolean required) {
        Object value = entry.get(field);
        if (!required && value == null) return 0;
        if (!(value instanceof Number number) || !validCoordinate(number.doubleValue())) {
            throw new IllegalArgumentException(field + " 必须为有效数字");
        }
        return number.doubleValue();
    }

    public record Warp(String name, String world, double x, double y, double z, float yaw, float pitch,
                       String group, String permission, boolean enabled) {}

    public record Entry(String name, String group, boolean favorite) {}
}
