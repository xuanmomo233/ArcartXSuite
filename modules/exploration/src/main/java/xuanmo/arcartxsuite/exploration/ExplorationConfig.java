package xuanmo.arcartxsuite.exploration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

record ExplorationConfig(String defaultKey, double toleranceSquared, Map<String, List<Region>> worlds) {

    enum Kind { GATHER, CHEST, QUEST, CONVERSATION, ABYSS }
    record Loot(String source, String id, int amount) {}
    record Outcome(Kind kind, int weight, List<Loot> items, String title, String target, String plan,
                   String model, double scale) {}

    record Point(int x, int y, int z) {
        static Point read(ConfigurationSection section, String key) {
            List<?> values = section.getList(key);
            if (values == null || values.size() != 3 || !values.stream().allMatch(Integer.class::isInstance)) {
                throw invalid(section, key + " 应为三个整数方块坐标");
            }
            return new Point((int) values.get(0), (int) values.get(1), (int) values.get(2));
        }
    }

    record Region(String id, String name, String world, Point min, Point max, int duration,
                  int cooldownSeconds, NamespacedKey cooldownKey, List<Outcome> outcomes, long totalWeight) {
        boolean contains(Location location) {
            return location.getWorld() != null && world.equals(location.getWorld().getName())
                && location.getBlockX() >= min.x && location.getBlockX() <= max.x
                && location.getBlockY() >= min.y && location.getBlockY() <= max.y
                && location.getBlockZ() >= min.z && location.getBlockZ() <= max.z;
        }

        Outcome draw(RandomGenerator random) {
            long roll = random.nextLong(totalWeight);
            for (Outcome outcome : outcomes) {
                roll -= outcome.weight;
                if (roll < 0) return outcome;
            }
            throw new IllegalStateException("探索结果权重不一致");
        }
    }

    static ExplorationConfig load(ConfigurationSection config) {
        String key = required(config, "default-key").toUpperCase(Locale.ROOT);
        double tolerance = config.getDouble("movement-tolerance", Double.NaN);
        if (!Double.isFinite(tolerance) || tolerance < 0 || tolerance > 1) {
            throw invalid(config, "movement-tolerance 应为 0 至 1 格");
        }
        Map<String, List<Region>> worlds = new HashMap<>();
        ConfigurationSection regions = section(config, "regions");
        for (String id : regions.getKeys(false)) {
            ConfigurationSection entry = section(regions, id);
            if (entry.contains("enabled") && !entry.isBoolean("enabled")) throw invalid(entry, "enabled 应为布尔值");
            if (!entry.getBoolean("enabled", true)) continue;
            if (!id.matches("[a-z0-9_-]{1,64}")) throw invalid(entry, "区域 ID 无效");
            Region region = readRegion(id, entry);
            worlds.computeIfAbsent(region.world, ignored -> new ArrayList<>()).add(region);
        }
        worlds.replaceAll((world, list) -> List.copyOf(list));
        return new ExplorationConfig(key, tolerance * tolerance, Map.copyOf(worlds));
    }

    private static Region readRegion(String id, ConfigurationSection entry) {
        Point a = Point.read(entry, "corner-a");
        Point b = Point.read(entry, "corner-b");
        Point min = new Point(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
        Point max = new Point(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
        ConfigurationSection pool = section(entry, "outcomes");
        List<Outcome> outcomes = new ArrayList<>();
        long total = 0;
        for (String name : pool.getKeys(false)) {
            ConfigurationSection item = section(pool, name);
            int weight = integer(item, "weight", 0);
            if (weight == 0) continue;
            Outcome outcome = readOutcome(item, weight);
            outcomes.add(outcome);
            total += weight;
        }
        if (outcomes.isEmpty()) throw invalid(pool, "至少需要一个权重大于零的结果");
        return new Region(id, required(entry, "name"), required(entry, "world"), min, max,
            integer(entry, "duration-ticks", 1), integer(entry, "cooldown-seconds", 0),
            NamespacedKey.fromString("exploration:cooldown/" + id), List.copyOf(outcomes), total);
    }

    private static Outcome readOutcome(ConfigurationSection entry, int weight) {
        Kind kind;
        try {
            kind = Kind.valueOf(required(entry, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw invalid(entry, "type 应为 gather、chest、quest、conversation 或 abyss");
        }
        List<Loot> items = switch (kind) {
            case CHEST, GATHER -> readLoot(entry);
            default -> List.of();
        };
        String target = switch (kind) {
            case QUEST -> required(entry, "quest");
            case CONVERSATION -> required(entry, "npc");
            case ABYSS -> required(entry, "dungeon");
            default -> null;
        };
        String model = null;
        double scale = 1;
        if (kind == Kind.CHEST) {
            model = entry.contains("model") ? required(entry, "model") : "exploration_chest";
            scale = entry.getDouble("scale", entry.contains("scale") ? Double.NaN : 1);
            if (!Double.isFinite(scale) || scale <= 0 || scale > 4) throw invalid(entry, "scale 应大于 0 且不超过 4");
        }
        return new Outcome(kind, weight, items, entry.getString("title", "探索宝箱"), target, entry.getString("plan"), model, scale);
    }

    private static List<Loot> readLoot(ConfigurationSection entry) {
        List<?> rows = entry.getList("items");
        if (rows == null || rows.isEmpty() || rows.size() > 54) throw invalid(entry, "items 需要 1 至 54 条物品");
        List<Loot> items = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> item) || !(item.get("source") instanceof String source)
                || source.isBlank() || !(item.get("id") instanceof String id) || id.isBlank()
                || !(item.get("amount") instanceof Integer amount) || amount < 1 || amount > 64) {
                throw invalid(entry, "每条物品需提供 source、id 和 1 至 64 的整数 amount");
            }
            items.add(new Loot(source.trim(), id.trim(), amount));
        }
        return List.copyOf(items);
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection value = parent.getConfigurationSection(key);
        if (value == null) throw invalid(parent, "缺少配置节 " + key);
        return value;
    }

    private static String required(ConfigurationSection section, String key) {
        String value = section.getString(key, "");
        if (!section.isString(key) || value.isBlank()) throw invalid(section, "缺少字符串 " + key);
        return value.trim();
    }

    private static int integer(ConfigurationSection section, String key, int min) {
        if (!section.isInt(key) || section.getInt(key) < min) throw invalid(section, key + " 应为不小于 " + min + " 的整数");
        return section.getInt(key);
    }

    private static IllegalArgumentException invalid(ConfigurationSection section, String reason) {
        return new IllegalArgumentException(section.getCurrentPath() + " " + reason);
    }
}
