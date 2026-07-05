package xuanmo.arcartxsuite.fishing.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.fishing.model.BaitDefinition;
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.FishRarity;
import xuanmo.arcartxsuite.fishing.model.FishingItemRef;
import xuanmo.arcartxsuite.fishing.model.RodDefinition;
import xuanmo.arcartxsuite.fishing.model.TreasureDefinition;
import xuanmo.arcartxsuite.fishing.model.WaterArea;
import xuanmo.arcartxsuite.fishing.model.WaterArea.AreaType;

public record FishingModuleConfiguration(
    @NotNull StorageConfiguration storage,
    @NotNull FishingSettings fishing,
    @NotNull List<FishDefinition> fishes,
    @NotNull List<TreasureDefinition> treasures,
    @NotNull List<BaitDefinition> baits,
    @NotNull List<RodDefinition> rods,
    @NotNull List<WaterArea> specifiedWaters,
    @NotNull WaterArea defaultWater,
    @NotNull Map<String, List<String>> fishPools,
    @NotNull Map<String, List<String>> treasurePools,
    @NotNull UiConfiguration ui,
    @NotNull CommandsConfiguration commands
) {

    public static FishingModuleConfiguration load(FileConfiguration yaml, File dataFolder, Logger logger) {
        List<FishDefinition> fishes = loadFishesFromDir(new File(dataFolder, "fishes"), logger);
        List<TreasureDefinition> treasures = loadTreasuresFromDir(new File(dataFolder, "treasures"), logger);
        List<BaitDefinition> baits = loadBaitsFromDir(new File(dataFolder, "baits"), logger);
        List<RodDefinition> rods = loadRodsFromDir(new File(dataFolder, "rods"), logger);

        ConfigurationSection watersSection = yaml.getConfigurationSection("waters");
        List<WaterArea> specified = loadSpecifiedWaters(watersSection);
        WaterArea defaultWater = loadDefaultWater(watersSection);

        Map<String, List<String>> fishPools = loadPools(yaml.getConfigurationSection("fish-pools"));
        Map<String, List<String>> treasurePools = loadPools(yaml.getConfigurationSection("treasure-pools"));

        return new FishingModuleConfiguration(
            StorageConfiguration.load(yaml.getConfigurationSection("storage")),
            FishingSettings.load(yaml.getConfigurationSection("fishing")),
            Collections.unmodifiableList(fishes),
            Collections.unmodifiableList(treasures),
            Collections.unmodifiableList(baits),
            Collections.unmodifiableList(rods),
            Collections.unmodifiableList(specified),
            defaultWater,
            Collections.unmodifiableMap(fishPools),
            Collections.unmodifiableMap(treasurePools),
            UiConfiguration.load(yaml.getConfigurationSection("ui")),
            CommandsConfiguration.load(yaml.getConfigurationSection("commands"))
        );
    }

    // ─── 从 fishes/ 目录加载 ─────────────────────────────────

    private static List<FishDefinition> loadFishesFromDir(File dir, Logger logger) {
        List<FishDefinition> result = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) return result;
        try (Stream<Path> paths = Files.list(dir.toPath())) {
            paths.filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                try {
                    ConfigurationSection section = YamlConfiguration.loadConfiguration(p.toFile());
                    result.add(parseFish(section, p.getFileName().toString()));
                } catch (Exception e) {
                    logger.warning("加载鱼种文件失败: " + p + " — " + e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warning("读取 fishes 目录失败: " + e.getMessage());
        }
        return result;
    }

    private static FishDefinition parseFish(ConfigurationSection section, String key) {
        String id = section.getString("id", key.replace(".yml", ""));
        String displayName = section.getString("display-name", id);
        FishRarity rarity;
        try {
            rarity = FishRarity.valueOf(section.getString("rarity", "COMMON").toUpperCase());
        } catch (IllegalArgumentException e) {
            rarity = FishRarity.COMMON;
        }
        int minSize = section.getInt("min-size", 1);
        int maxSize = section.getInt("max-size", 10);
        int basePrice = section.getInt("base-price", 10);
        int baseXp = section.getInt("base-xp", 5);
        List<String> seasons = section.getStringList("seasons");
        if (seasons.isEmpty()) seasons = List.of("spring", "summer", "fall", "winter");
        List<String> weathers = section.getStringList("weathers");
        if (weathers.isEmpty()) weathers = List.of("clear", "rain");
        List<String> waterTypes = section.getStringList("water-types");
        if (waterTypes.isEmpty()) waterTypes = List.of("river", "ocean", "lake");

        List<FishDefinition.TimeRange> timeRanges = new ArrayList<>();
        ConfigurationSection trSection = section.getConfigurationSection("time-ranges");
        if (trSection != null) {
            for (String trKey : trSection.getKeys(false)) {
                ConfigurationSection tr = trSection.getConfigurationSection(trKey);
                if (tr != null) {
                    timeRanges.add(new FishDefinition.TimeRange(
                        tr.getString("start", "06:00"),
                        tr.getString("end", "20:00")
                    ));
                }
            }
        }
        if (timeRanges.isEmpty()) {
            timeRanges.add(new FishDefinition.TimeRange("06:00", "20:00"));
        }

        FishingItemRef itemRef = parseItemRef(section, "item", "minecraft:cod");
        int difficulty = section.getInt("difficulty", 30);

        List<FishDefinition.BehaviorEntry> behaviors = new ArrayList<>();
        ConfigurationSection bhSection = section.getConfigurationSection("behaviors");
        if (bhSection != null) {
            for (String bhKey : bhSection.getKeys(false)) {
                ConfigurationSection bh = bhSection.getConfigurationSection(bhKey);
                if (bh != null) {
                    FishBehaviorType type;
                    try {
                        type = FishBehaviorType.valueOf(bh.getString("type", "SMOOTH").toUpperCase());
                    } catch (IllegalArgumentException e) {
                        type = FishBehaviorType.SMOOTH;
                    }
                    behaviors.add(new FishDefinition.BehaviorEntry(type, bh.getDouble("weight", 1.0)));
                }
            }
        }
        if (behaviors.isEmpty()) {
            behaviors.add(new FishDefinition.BehaviorEntry(FishBehaviorType.SMOOTH, 1.0));
        }

        FishDefinition.CurrencyReward currencyReward = null;
        ConfigurationSection crSection = section.getConfigurationSection("currency-reward");
        if (crSection != null) {
            String currencyId = crSection.getString("currency-id", "");
            double amount = crSection.getDouble("amount", 0);
            if (!currencyId.isEmpty() && amount > 0) {
                currencyReward = new FishDefinition.CurrencyReward(currencyId, amount);
            }
        }

        return FishDefinition.builder()
            .id(id).displayName(displayName).rarity(rarity)
            .minSize(minSize).maxSize(maxSize).basePrice(basePrice).baseXp(baseXp)
            .seasons(seasons).weathers(weathers).waterTypes(waterTypes)
            .timeRanges(timeRanges).itemRef(itemRef).difficulty(difficulty).behaviors(behaviors)
            .currencyReward(currencyReward)
            .build();
    }

    // ─── 从 treasures/ 目录加载 ────────────────────────────────

    private static List<TreasureDefinition> loadTreasuresFromDir(File dir, Logger logger) {
        List<TreasureDefinition> result = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) return result;
        try (Stream<Path> paths = Files.list(dir.toPath())) {
            paths.filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                try {
                    ConfigurationSection section = YamlConfiguration.loadConfiguration(p.toFile());
                    result.add(new TreasureDefinition(
                        section.getString("id", p.getFileName().toString().replace(".yml", "")),
                        section.getString("display-name", ""),
                        parseItemRef(section, "item", "minecraft:stick"),
                        section.getDouble("chance", 0.1)
                    ));
                } catch (Exception e) {
                    logger.warning("加载宝藏文件失败: " + p + " — " + e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warning("读取 treasures 目录失败: " + e.getMessage());
        }
        return result;
    }

    // ─── 从 baits/ 目录加载 ──────────────────────────────────

    private static List<BaitDefinition> loadBaitsFromDir(File dir, Logger logger) {
        List<BaitDefinition> result = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) return result;
        try (Stream<Path> paths = Files.list(dir.toPath())) {
            paths.filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                try {
                    ConfigurationSection section = YamlConfiguration.loadConfiguration(p.toFile());
                    String id = section.getString("id", p.getFileName().toString().replace(".yml", ""));

                    Map<String, Double> modifiers = new LinkedHashMap<>();
                    ConfigurationSection modSection = section.getConfigurationSection("fish-attract-modifiers");
                    if (modSection != null) {
                        for (String mk : modSection.getKeys(false)) {
                            modifiers.put(mk, modSection.getDouble(mk, 1.0));
                        }
                    }

                    result.add(new BaitDefinition(
                        id,
                        section.getString("display-name", id),
                        parseItemRef(section, "item", "minecraft:string"),
                        section.getBoolean("default", false),
                        Collections.unmodifiableMap(modifiers),
                        section.getDouble("treasure-chance-boost", 0.0),
                        section.getInt("max-durability-bonus", 0)
                    ));
                } catch (Exception e) {
                    logger.warning("加载饵料文件失败: " + p + " — " + e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warning("读取 baits 目录失败: " + e.getMessage());
        }
        return result;
    }

    // ─── 从 rods/ 目录加载 ─────────────────────────────────

    private static List<RodDefinition> loadRodsFromDir(File dir, Logger logger) {
        List<RodDefinition> result = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) return result;
        try (Stream<Path> paths = Files.list(dir.toPath())) {
            paths.filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                try {
                    ConfigurationSection section = YamlConfiguration.loadConfiguration(p.toFile());
                    String id = section.getString("id", p.getFileName().toString().replace(".yml", ""));
                    result.add(new RodDefinition(
                        id,
                        section.getString("display-name", id),
                        parseItemRef(section, "item", "minecraft:fishing_rod"),
                        section.getDouble("treasure-chance-bonus", 0.0),
                        section.getInt("green-bar-height-bonus", 0),
                        section.getInt("catch-duration-bonus", 0),
                        section.getDouble("exp-multiplier", 1.0),
                        section.getInt("min-player-level", 0)
                    ));
                } catch (Exception e) {
                    logger.warning("加载钓竿文件失败: " + p + " — " + e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warning("读取 rods 目录失败: " + e.getMessage());
        }
        return result;
    }

    // ─── 通用物品引用解析 ──────────────────────────────────────

    private static @NotNull FishingItemRef parseItemRef(@NotNull ConfigurationSection section,
                                                         @NotNull String key,
                                                         @NotNull String defaultMaterial) {
        ConfigurationSection itemSection = section.getConfigurationSection(key);
        if (itemSection == null) {
            // 简单字符串格式: item: "minecraft:xxx"
            String raw = section.getString(key, "");
            if (!raw.isEmpty()) {
                return FishingItemRef.fromMaterial(raw, 1);
            }
            return FishingItemRef.fromMaterial(defaultMaterial, 1);
        }
        return new FishingItemRef(
            itemSection.getString("source", "minecraft"),
            itemSection.getString("item-id", ""),
            itemSection.getString("mmo-type", ""),
            itemSection.getString("mmo-id", ""),
            itemSection.getString("json", ""),
            itemSection.getString("texture", ""),
            itemSection.getString("texture-url", ""),
            Math.max(1, itemSection.getInt("amount", 1))
        );
    }

    // ─── 水域解析 ─────────────────────────────────────────────

    private static List<WaterArea> loadSpecifiedWaters(ConfigurationSection watersSection) {
        List<WaterArea> result = new ArrayList<>();
        if (watersSection == null) return result;
        ConfigurationSection specified = watersSection.getConfigurationSection("specified");
        if (specified == null) return result;
        for (String key : specified.getKeys(false)) {
            ConfigurationSection ws = specified.getConfigurationSection(key);
            if (ws == null) continue;
            try {
                result.add(parseWaterArea(ws));
            } catch (Exception e) {
                // skip malformed water area
            }
        }
        return result;
    }

    private static WaterArea loadDefaultWater(ConfigurationSection watersSection) {
        if (watersSection != null) {
            ConfigurationSection def = watersSection.getConfigurationSection("default");
            if (def != null) {
                try {
                    return parseWaterArea(def);
                } catch (Exception ignored) {}
            }
        }
        return new WaterArea("default", "&7普通水域", AreaType.DEFAULT, null,
            null, 0, null, null, "default", "default", 1.0,
            Map.of(), null);
    }

    private static WaterArea parseWaterArea(ConfigurationSection section) {
        String name = section.getString("name", "unknown");
        String displayName = section.getString("display-name", name);
        String typeStr = section.getString("type", "default").toUpperCase();
        AreaType type;
        try {
            type = AreaType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            type = AreaType.DEFAULT;
        }
        String world = section.getString("world", null);
        if (world != null && world.isEmpty()) world = null;

        double[] center = null;
        List<?> centerList = section.getList("center");
        if (centerList != null && centerList.size() >= 3) {
            center = new double[]{
                ((Number) centerList.get(0)).doubleValue(),
                ((Number) centerList.get(1)).doubleValue(),
                ((Number) centerList.get(2)).doubleValue()
            };
        }
        double radius = section.getDouble("radius", 0);

        double[] min = null, max = null;
        List<?> minList = section.getList("min");
        if (minList != null && minList.size() >= 3) {
            min = new double[]{
                ((Number) minList.get(0)).doubleValue(),
                ((Number) minList.get(1)).doubleValue(),
                ((Number) minList.get(2)).doubleValue()
            };
        }
        List<?> maxList = section.getList("max");
        if (maxList != null && maxList.size() >= 3) {
            max = new double[]{
                ((Number) maxList.get(0)).doubleValue(),
                ((Number) maxList.get(1)).doubleValue(),
                ((Number) maxList.get(2)).doubleValue()
            };
        }

        String fishPool = section.getString("fish-pool", "default");
        String treasurePool = section.getString("treasure-pool", "default");
        double diffMod = section.getDouble("difficulty-modifier", 1.0);

        Map<String, Double> baitMultipliers = new LinkedHashMap<>();
        ConfigurationSection baitSection = section.getConfigurationSection("bait-multipliers");
        if (baitSection != null) {
            for (String bk : baitSection.getKeys(false)) {
                baitMultipliers.put(bk, baitSection.getDouble(bk, 1.0));
            }
        }

        String requirePermission = section.getString("require-permission", null);
        if (requirePermission != null && requirePermission.isEmpty()) requirePermission = null;

        return new WaterArea(name, displayName, type, world, center, radius, min, max,
            fishPool, treasurePool, diffMod, Collections.unmodifiableMap(baitMultipliers), requirePermission);
    }

    // ─── 池解析 ───────────────────────────────────────────────

    private static Map<String, List<String>> loadPools(ConfigurationSection section) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            List<String> ids = section.getStringList(key);
            if (!ids.isEmpty()) {
                result.put(key, Collections.unmodifiableList(ids));
            }
        }
        return result;
    }

    public record StorageConfiguration(
        String mode,
        String sqliteFileName,
        int poolSize,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword
    ) {
        public static StorageConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new StorageConfiguration(
                section.getString("mode", "sqlite"),
                section.getString("sqlite-file-name", "fishing.db"),
                section.getInt("pool-size", 1),
                section.getString("mysql-host", "localhost"),
                section.getInt("mysql-port", 3306),
                section.getString("mysql-database", "axs_fishing"),
                section.getString("mysql-username", "root"),
                section.getString("mysql-password", "")
            );
        }

        public StorageDescriptor toDescriptor() {
            boolean isMysql = "mysql".equalsIgnoreCase(mode);
            if (isMysql) {
                return StorageDescriptor.mysql(mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, poolSize, "");
            }
            return StorageDescriptor.sqlite(sqliteFileName);
        }
    }

    public record FishingSettings(
        boolean replaceVanilla,
        int minigameTickInterval,
        double barGravity,
        double barBounceDamping,
        double barClickForce,
        int catchDurationTicks,
        double progressDrainRate,
        double progressGainRate,
        int baseGreenBarHeight,
        int heightPerLevel,
        double treasureChance,
        double perfectBonusMultiplier,
        int baseXpPerLevel
    ) {
        public static FishingSettings load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new FishingSettings(
                section.getBoolean("replace-vanilla", true),
                section.getInt("minigame-tick-interval", 1),
                section.getDouble("bar-gravity", 0.3),
                section.getDouble("bar-bounce-damping", 0.7),
                section.getDouble("bar-click-force", -0.6),
                section.getInt("catch-duration-ticks", 600),
                section.getDouble("progress-drain-rate", 0.5),
                section.getDouble("progress-gain-rate", 1.0),
                section.getInt("base-green-bar-height", 96),
                section.getInt("height-per-level", 4),
                section.getDouble("treasure-chance", 0.15),
                section.getDouble("perfect-bonus-multiplier", 1.5),
                section.getInt("base-xp-per-level", 100)
            );
        }
    }

    public record UiConfiguration(
        boolean registerOnEnable,
        String minigameId,
        String collectionId
    ) {
        public static UiConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new UiConfiguration(
                section.getBoolean("register-on-enable", true),
                section.getString("minigame-id", "fishing_minigame"),
                section.getString("collection-id", "fishing_collection")
            );
        }
    }

    public record CommandsConfiguration(
        boolean enabled,
        String basePermission
    ) {
        public static CommandsConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new CommandsConfiguration(
                section.getBoolean("enabled", true),
                section.getString("base-permission", "axs.fishing.use")
            );
        }
    }
}
