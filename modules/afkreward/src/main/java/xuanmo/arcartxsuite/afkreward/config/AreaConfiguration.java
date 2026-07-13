package xuanmo.arcartxsuite.afkreward.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.afkreward.model.AfkArea;
import xuanmo.arcartxsuite.afkreward.model.AfkRewardType;

/**
 * 区域独立配置文件加载器。
 * <p>
 * 扫描 {@code data/<areasDirectory>/} 下的 {@code area-*.yml} 文件，
 * 每个文件解析为一个 {@link AfkArea}。文件名中的 ID（去掉前缀/后缀）作为区域内部 ID。
 */
public final class AreaConfiguration {

    public static Map<String, AfkArea> loadAreas(File dataFolder, String areasDirectory, Logger logger) {
        File dir = new File(dataFolder, areasDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        Map<String, AfkArea> areas = new LinkedHashMap<>();

        File[] files = dir.listFiles((d, name) -> name.startsWith("area-") && name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            logger.info("[AfkReward] 未在 " + dir.getPath() + " 发现区域配置文件，请先创建 area-*.yml 文件。");
            return Collections.unmodifiableMap(areas);
        }

        Set<String> usedNames = new java.util.HashSet<>();
        int skipped = 0;
        for (File file : files) {
            String fileName = file.getName();
            // 提取 ID：area-<id>.yml
            String id = fileName.substring("area-".length(), fileName.length() - ".yml".length());
            if (id.isBlank()) {
                logger.warning("[AfkReward] 跳过无效区域文件名: " + fileName);
                skipped++;
                continue;
            }
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                AfkArea area = parseArea(id, yaml, logger);
                if (area != null) {
                    if (!usedNames.add(area.name())) {
                        logger.warning("[AfkReward] 区域名称重复 '" + area.name()
                            + "'，文件 " + fileName + " 已跳过。");
                        skipped++;
                        continue;
                    }
                    areas.put(area.name(), area);
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                logger.warning("[AfkReward] 加载区域文件失败 " + fileName + ": " + e.getMessage());
                skipped++;
            }
        }
        logger.info("[AfkReward] 区域加载完成：成功" + areas.size() + "个，跳过" + skipped + "个。");
        return Collections.unmodifiableMap(areas);
    }

    private static AfkArea parseArea(String id, YamlConfiguration yaml, Logger logger) {
        String name = yaml.getString("name", id);
        if (name == null || name.isBlank()) {
            name = id;
        }
        boolean enabled = yaml.getBoolean("enabled", true);
        String world = yaml.getString("world", "");
        String type = yaml.getString("type", id);
        if (world.isBlank()) {
            logger.warning("[AfkReward] 区域 '" + name + "' (" + id + ") 未配置 world，已跳过。");
            return null;
        }
        boolean manualEnabled = yaml.getBoolean("manual-enabled", true);
        double rewardWeight = yaml.getDouble("reward-weight", 1.0);

        List<String> posList = yaml.getStringList("pos");
        List<AfkArea.Point> points = new ArrayList<>();
        for (String pos : posList) {
            String[] parts = pos.split(",");
            if (parts.length == 2) {
                try {
                    int x = Integer.parseInt(parts[0].trim());
                    int z = Integer.parseInt(parts[1].trim());
                    points.add(new AfkArea.Point(x, z));
                } catch (NumberFormatException e) {
                    logger.warning("[AfkReward] 区域 '" + name + "' (" + id
                        + ") 存在非法坐标 '" + pos + "'，已跳过。");
                    return null;
                }
            } else {
                logger.warning("[AfkReward] 区域 '" + name + "' (" + id
                    + ") 存在非法坐标 '" + pos + "'，已跳过。");
                return null;
            }
        }
        if (points.size() < 3) {
            logger.warning("[AfkReward] 区域 '" + name + "' (" + id + ") 的坐标点不足 3 个，已跳过。");
            return null;
        }

        // 传送点
        Location teleport = null;
        ConfigurationSection tpSec = yaml.getConfigurationSection("teleport");
        if (tpSec == null) {
            logger.warning("[AfkReward] 区域 '" + name + "' (" + id + ") 缺少 teleport 配置，已跳过。");
            return null;
        }
        {
            String tpWorld = tpSec.getString("world", world);
            double tpx = tpSec.getDouble("x", 0);
            double tpy = tpSec.getDouble("y", 64);
            double tpz = tpSec.getDouble("z", 0);
            float yaw = (float) tpSec.getDouble("yaw", 0);
            float pitch = (float) tpSec.getDouble("pitch", 0);
            World w = org.bukkit.Bukkit.getWorld(tpWorld);
            if (w != null) {
                teleport = new Location(w, tpx, tpy, tpz, yaw, pitch);
            } else {
                logger.warning("[AfkReward] 区域 '" + name + "' (" + id
                    + ") 的 teleport 世界 '" + tpWorld + "' 未加载，已跳过。");
                return null;
            }
        }

        ConfigurationSection rewardSec = yaml.getConfigurationSection("reward");
        AfkRewardType rewardType = parseRewardType(id, type, rewardSec);
        AfkArea.RewardConfig reward = new AfkArea.RewardConfig(
            rewardSec != null ? Math.max(1, rewardSec.getInt("round", 15)) : 15,
            new AfkArea.RewardConfig.MaxConfig(
                rewardSec == null || rewardSec.getBoolean("max.enable", true),
                rewardSec != null ? Math.max(1, rewardSec.getInt("max.limit", 32)) : 32
            ),
            new AfkArea.RewardConfig.PlayerLimitConfig(
                rewardSec == null || rewardSec.getBoolean("player.enable", true),
                rewardSec != null ? Math.max(1, rewardSec.getInt("player.limit", 30)) : 30
            ),
            new AfkArea.RewardConfig.OverflowConfig(
                rewardSec != null && rewardSec.getBoolean("overflow-to-mail.enable", false)
            ),
            rewardType
        );

        ConfigurationSection multiplierSec = yaml.getConfigurationSection("multiplier");
        List<AfkArea.ScheduleConfig> schedules = new ArrayList<>();
        if (multiplierSec != null) {
            for (Map<?, ?> raw : multiplierSec.getMapList("schedules")) {
                String days = String.valueOf(raw.containsKey("days") ? raw.get("days") : "ALL");
                String start = String.valueOf(raw.containsKey("start") ? raw.get("start") : "00:00");
                String end = String.valueOf(raw.containsKey("end") ? raw.get("end") : "23:59");
                double value = raw.get("multiplier") instanceof Number n ? n.doubleValue() : 1.0;
                schedules.add(new AfkArea.ScheduleConfig(days, start, end, value));
            }
        }
        AfkArea.MultiplierConfig multiplier = new AfkArea.MultiplierConfig(
            multiplierSec != null && multiplierSec.getBoolean("enable", false),
            multiplierSec != null ? multiplierSec.getDouble("base", 1.0) : 1.0,
            multiplierSec != null ? multiplierSec.getDouble("weekend", 1.0) : 1.0,
            multiplierSec != null ? multiplierSec.getString("combine", "MAX") : "MAX",
            Collections.unmodifiableList(schedules)
        );

        return new AfkArea(id, name, enabled, world, type,
            Collections.unmodifiableList(points), teleport, manualEnabled, rewardWeight,
            reward, multiplier);
    }

    private static AfkRewardType parseRewardType(
        String id, String typeName, ConfigurationSection rewardSec
    ) {
        String name = typeName == null || typeName.isBlank() ? id : typeName;
        String describe = rewardSec != null ? rewardSec.getString("describe", "") : "";
        List<String> mailPresets = rewardSec != null
            ? new ArrayList<>(rewardSec.getStringList("mail-presets")) : List.of();
        String fullInventoryMail = rewardSec != null
            ? rewardSec.getString("full-inventory-mail", "") : "";
        Map<String, List<String>> tiers = new LinkedHashMap<>();
        if (rewardSec != null) {
            for (String tierKey : rewardSec.getKeys(false)) {
                if ("round".equals(tierKey) || "max".equals(tierKey)
                    || "player".equals(tierKey) || "overflow-to-mail".equals(tierKey)
                    || "describe".equals(tierKey) || "mail-presets".equals(tierKey)
                    || "full-inventory-mail".equals(tierKey)) {
                    continue;
                }
                List<String> commands = rewardSec.getStringList(tierKey);
                if (!commands.isEmpty()) {
                    tiers.put(tierKey, new ArrayList<>(commands));
                }
            }
        }
        return new AfkRewardType(
            name,
            describe,
            Collections.unmodifiableMap(tiers),
            Collections.unmodifiableList(mailPresets),
            fullInventoryMail
        );
    }

    /**
     * 导出内置默认区域文件到目标目录。
     */
    public static void exportDefaultArea(ClassLoader classLoader, File dataFolder, String areasDirectory) {
        File dir = new File(dataFolder, areasDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File target = new File(dir, "area-default.yml");
        if (target.exists()) {
            return;
        }
        try (java.io.InputStream in = classLoader.getResourceAsStream("area-default.yml")) {
            if (in == null) return;
            try (java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                in.transferTo(out);
            }
        } catch (Exception ignored) {}
    }
}
