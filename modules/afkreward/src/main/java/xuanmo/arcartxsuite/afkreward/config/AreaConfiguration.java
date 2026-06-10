package xuanmo.arcartxsuite.afkreward.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.afkreward.model.AfkArea;

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

        for (File file : files) {
            String fileName = file.getName();
            // 提取 ID：area-<id>.yml
            String id = fileName.substring("area-".length(), fileName.length() - ".yml".length());
            if (id.isBlank()) {
                logger.warning("[AfkReward] 跳过无效区域文件名: " + fileName);
                continue;
            }
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                AfkArea area = parseArea(id, yaml, logger);
                if (area != null) {
                    areas.put(area.name(), area);
                }
            } catch (Exception e) {
                logger.warning("[AfkReward] 加载区域文件失败 " + fileName + ": " + e.getMessage());
            }
        }
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
        boolean manualEnabled = yaml.getBoolean("manual-enabled", true);

        List<String> posList = yaml.getStringList("pos");
        List<AfkArea.Point> points = new ArrayList<>();
        for (String pos : posList) {
            String[] parts = pos.split(",");
            if (parts.length >= 2) {
                try {
                    int x = Integer.parseInt(parts[0].trim());
                    int z = Integer.parseInt(parts[1].trim());
                    points.add(new AfkArea.Point(x, z));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (points.size() < 3) {
            logger.warning("[AfkReward] 区域 '" + name + "' (" + id + ") 的坐标点不足 3 个，已跳过。");
            return null;
        }

        // 传送点
        Location teleport = null;
        ConfigurationSection tpSec = yaml.getConfigurationSection("teleport");
        if (tpSec != null) {
            String tpWorld = tpSec.getString("world", world);
            double tpx = tpSec.getDouble("x", 0);
            double tpy = tpSec.getDouble("y", 64);
            double tpz = tpSec.getDouble("z", 0);
            float yaw = (float) tpSec.getDouble("yaw", 0);
            float pitch = (float) tpSec.getDouble("pitch", 0);
            World w = org.bukkit.Bukkit.getWorld(tpWorld);
            if (w != null) {
                teleport = new Location(w, tpx, tpy, tpz, yaw, pitch);
            }
        }

        return new AfkArea(id, name, enabled, world, type,
            Collections.unmodifiableList(points), teleport, manualEnabled);
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
