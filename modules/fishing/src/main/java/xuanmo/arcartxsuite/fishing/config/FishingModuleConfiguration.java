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
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfigs;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.api.script.XpCurve;
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

/**
 * 钓鱼模块的完整配置快照，由主 yml 与附属目录（fishes/treasures/baits/rods）加载而成。
 * <p>
 * 关联模块：{@code fishing}。包含存储、钓鱼设置、鱼/宝藏/鱼饵/鱼竿定义、
 * 水域、奖池、UI 与命令配置等全部运行时所需的不可变数据。
 *
 * @param storage         存储配置（SQLite/MySQL 连接参数）
 * @param fishing         钓鱼基础设置（是否替换原版、小游戏 tick 间隔等）
 * @param fishes          鱼种定义列表
 * @param treasures       宝藏定义列表
 * @param baits           鱼饵定义列表
 * @param rods            鱼竿定义列表
 * @param specifiedWaters 显式声明的水域列表
 * @param defaultWater    默认水域（未命中任何指定水域时使用）
 * @param fishPools       鱼池映射（池名 → 鱼种 id 列表）
 * @param treasurePools   宝藏池映射（池名 → 宝藏 id 列表）
 * @param ui              UI 配置（小游戏/图鉴界面 id 等）
 * @param commands        命令配置
 * @param crossServer     跨服通道配置（多服架构下同步捕获记录等）
 * @param debug           是否输出调试日志（钓鱼事件、小游戏状态、发包详情等）
 */
public record FishingModuleConfiguration(
    @NotNull StorageConfiguration storage,
    @NotNull SeasonConfiguration season,
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
    @NotNull CommandsConfiguration commands,
    @NotNull CrossServerChannelConfig crossServer,
    boolean debug
) {

    public static FishingModuleConfiguration load(FileConfiguration yaml, File dataFolder, Logger logger) {
        return load(yaml, dataFolder, logger, null);
    }

    public static FishingModuleConfiguration load(FileConfiguration yaml, File dataFolder, Logger logger,
                                                   @Nullable AriaBridge ariaBridge) {
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
            SeasonConfiguration.load(yaml.getConfigurationSection("season"), ariaBridge),
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
            CommandsConfiguration.load(yaml.getConfigurationSection("commands")),
            CrossServerChannelConfigs.fromSection(yaml.getConfigurationSection("cross-server")),
            yaml.getBoolean("debug", false)
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

        List<FishDefinition.BehaviorEntry> behaviors = parseBehaviors(section);
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

        // ─── 小游戏机制（可选） ──────────────────────────
        Double progressDrainRate = section.contains("progress-drain-rate") ? section.getDouble("progress-drain-rate") : null;
        Double progressGainRate = section.contains("progress-gain-rate") ? section.getDouble("progress-gain-rate") : null;

        FishDefinition.FleeParams fleeParams = null;
        ConfigurationSection fleeSection = section.getConfigurationSection("flee-params");
        if (fleeSection != null) {
            fleeParams = new FishDefinition.FleeParams(
                fleeSection.contains("base-chance") ? fleeSection.getDouble("base-chance") : null,
                fleeSection.contains("chance-per-difficulty") ? fleeSection.getDouble("chance-per-difficulty") : null,
                fleeSection.contains("base-speed") ? fleeSection.getDouble("base-speed") : null,
                fleeSection.contains("speed-per-difficulty") ? fleeSection.getDouble("speed-per-difficulty") : null,
                fleeSection.contains("min-duration") ? fleeSection.getInt("min-duration") : null,
                fleeSection.contains("max-duration") ? fleeSection.getInt("max-duration") : null,
                fleeSection.contains("check-min-interval") ? fleeSection.getInt("check-min-interval") : null,
                fleeSection.contains("check-max-interval") ? fleeSection.getInt("check-max-interval") : null,
                fleeSection.contains("cooldown-min") ? fleeSection.getInt("cooldown-min") : null,
                fleeSection.contains("cooldown-max") ? fleeSection.getInt("cooldown-max") : null
            );
        }

        // 多阶段行为
        List<FishDefinition.BehaviorStage> behaviorStages = new ArrayList<>();
        ConfigurationSection stagesSection = section.getConfigurationSection("behavior-stages");
        if (stagesSection != null) {
            for (String stageKey : stagesSection.getKeys(false)) {
                ConfigurationSection stage = stagesSection.getConfigurationSection(stageKey);
                if (stage != null) {
                    double threshold = stage.getDouble("threshold", 0.0);
                    List<FishDefinition.BehaviorEntry> stageBehaviors = parseBehaviors(stage);
                    if (!stageBehaviors.isEmpty()) {
                        behaviorStages.add(new FishDefinition.BehaviorStage(threshold, stageBehaviors));
                    }
                }
            }
        }
        // 按 threshold 排序
        behaviorStages.sort(java.util.Comparator.comparingDouble(FishDefinition.BehaviorStage::threshold));

        // ─── 奖励（可选） ──────────────────────────────────
        // 多物品掉落
        List<FishDefinition.DropEntry> drops = new ArrayList<>();
        ConfigurationSection dropsSection = section.getConfigurationSection("drops");
        if (dropsSection != null) {
            for (String dropKey : dropsSection.getKeys(false)) {
                ConfigurationSection drop = dropsSection.getConfigurationSection(dropKey);
                if (drop != null) {
                    FishingItemRef dropItem = parseItemRef(drop, "item", "minecraft:air");
                    if (!dropItem.isEmpty()) {
                        double weight = drop.getDouble("weight", 1.0);
                        String condition = drop.getString("condition", "");
                        drops.add(new FishDefinition.DropEntry(dropItem, weight, condition));
                    }
                }
            }
        }

        // 命令奖励
        List<FishDefinition.CommandReward> commandRewards = new ArrayList<>();
        ConfigurationSection cmdSection = section.getConfigurationSection("command-rewards");
        if (cmdSection != null) {
            for (String cmdKey : cmdSection.getKeys(false)) {
                ConfigurationSection cmd = cmdSection.getConfigurationSection(cmdKey);
                if (cmd != null) {
                    String command = cmd.getString("command", "");
                    String condition = cmd.getString("condition", "");
                    if (!command.isEmpty()) {
                        commandRewards.add(new FishDefinition.CommandReward(command, condition));
                    }
                }
            }
        }

        // 多货币奖励
        List<FishDefinition.CurrencyReward> currencyRewards = new ArrayList<>();
        ConfigurationSection crsSection = section.getConfigurationSection("currency-rewards");
        if (crsSection != null) {
            for (String crKey : crsSection.getKeys(false)) {
                ConfigurationSection cr2 = crsSection.getConfigurationSection(crKey);
                if (cr2 != null) {
                    String currencyId = cr2.getString("currency-id", "");
                    double amount = cr2.getDouble("amount", 0);
                    if (!currencyId.isEmpty() && amount > 0) {
                        currencyRewards.add(new FishDefinition.CurrencyReward(currencyId, amount));
                    }
                }
            }
        }

        // ─── 出现条件（可选） ──────────────────────────────
        int minPlayerLevel = section.getInt("min-player-level", 0);
        String requirePermission = section.contains("require-permission") ? section.getString("require-permission", "") : null;
        if (requirePermission != null && requirePermission.isEmpty()) requirePermission = null;

        // 饵料偏好
        List<FishDefinition.BaitPreference> baitPreferences = new ArrayList<>();
        ConfigurationSection baitPrefSection = section.getConfigurationSection("bait-preferences");
        if (baitPrefSection != null) {
            for (String bpKey : baitPrefSection.getKeys(false)) {
                ConfigurationSection bp = baitPrefSection.getConfigurationSection(bpKey);
                if (bp != null) {
                    String baitId = bp.getString("bait-id", "");
                    double multiplier = bp.getDouble("multiplier", 1.0);
                    if (!baitId.isEmpty()) {
                        baitPreferences.add(new FishDefinition.BaitPreference(baitId, multiplier));
                    }
                }
            }
        }

        // biome 限制
        List<String> biomes = section.getStringList("biomes");

        // 深度限制
        FishDefinition.DepthRange depthRange = null;
        ConfigurationSection depthSection = section.getConfigurationSection("depth-range");
        if (depthSection != null) {
            int minDepth = depthSection.getInt("min-depth", 0);
            int maxDepth = depthSection.getInt("max-depth", 256);
            depthRange = new FishDefinition.DepthRange(minDepth, maxDepth);
        }

        // ─── 数据/展示（可选） ─────────────────────────────
        String description = section.contains("description") ? section.getString("description") : null;
        String iconTexture = section.contains("icon-texture") ? section.getString("icon-texture") : null;
        String iconTextureUrl = section.contains("icon-texture-url") ? section.getString("icon-texture-url") : null;
        double recordWeight = section.getDouble("record-weight", 1.0);

        return FishDefinition.builder()
            .id(id).displayName(displayName).rarity(rarity)
            .minSize(minSize).maxSize(maxSize).basePrice(basePrice).baseXp(baseXp)
            .seasons(seasons).weathers(weathers).waterTypes(waterTypes)
            .timeRanges(timeRanges).itemRef(itemRef).difficulty(difficulty).behaviors(behaviors)
            .currencyReward(currencyReward)
            .progressDrainRate(progressDrainRate).progressGainRate(progressGainRate)
            .fleeParams(fleeParams).behaviorStages(behaviorStages)
            .drops(drops).commandRewards(commandRewards).currencyRewards(currencyRewards)
            .minPlayerLevel(minPlayerLevel).requirePermission(requirePermission)
            .baitPreferences(baitPreferences).biomes(biomes).depthRange(depthRange)
            .description(description).iconTexture(iconTexture).iconTextureUrl(iconTextureUrl)
            .recordWeight(recordWeight)
            .build();
    }

    private static List<FishDefinition.BehaviorEntry> parseBehaviors(ConfigurationSection section) {
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
        return behaviors;
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
                        section.getDouble("treasure-chance-bonus", 0.0),
                        section.getInt("green-bar-height-bonus", 0),
                        section.getInt("catch-duration-bonus", 0),
                        section.getDouble("exp-multiplier", 1.0),
                        section.getInt("min-player-level", 0),
                        section.getInt("max-player-level", 0)
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

        /**
         * 判断是否为覆盖模式（模块自建连接池）。
         * <p>
         * 当 mode=mysql 时返回 true（模块独立连接 MySQL，向后兼容旧配置）；
         * mode=sqlite 时返回 false（使用本体共享数据源，各模块仍用各自的 .db 文件）。
         *
         * @return {@code true} 表示模块应自建连接池（旧路径）
         * @since 1.4.0
         */
        public boolean hasOverride() {
            return "mysql".equalsIgnoreCase(mode);
        }
    }

    public record FishingSettings(
        boolean replaceVanilla,
        int minigameTickInterval,
        double barGravity,
        double barBounceDamping,
        double barClickForce,
        double barForceMultiplier,
        double barDamping,
        int catchDurationTicks,
        int fishingCooldownTicks,
        double progressDrainRate,
        double progressGainRate,
        double progressRateMultiplier,
        int baseGreenBarHeight,
        int heightPerLevel,
        double difficultyBarShrink,
        double fishEdgeBounce,
        double treasureChance,
        double perfectBonusMultiplier,
        int baseXpPerLevel,
        int maxLevel,
        double levelModifierScale,
        double fishSpeedLevelScale,
        double fleeChanceLevelScale,
        double fleeSpeedLevelScale,
        double progressRateLevelScale,
        double fleeBaseChance,
        double fleeChancePerDifficulty,
        double fleeBaseSpeed,
        double fleeSpeedPerDifficulty,
        int fleeMinDuration,
        int fleeMaxDuration,
        int fleeCheckMinInterval,
        int fleeCheckMaxInterval,
        int fleeCooldownMin,
        int fleeCooldownMax
    ) {
        public static FishingSettings load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new FishingSettings(
                section.getBoolean("replace-vanilla", true),
                section.getInt("minigame-tick-interval", 1),
                section.getDouble("bar-gravity", 0.2),
                section.getDouble("bar-bounce-damping", 0.4),
                section.getDouble("bar-click-force", -0.6),
                section.getDouble("bar-force-multiplier", 0.03),
                section.getDouble("bar-damping", 0.85),
                section.getInt("catch-duration-ticks", 600),
                section.getInt("fishing-cooldown-ticks", 100),
                section.getDouble("progress-drain-rate", 0.5),
                section.getDouble("progress-gain-rate", 1.0),
                section.getDouble("progress-rate-multiplier", 0.008),
                section.getInt("base-green-bar-height", 96),
                section.getInt("height-per-level", 2),
                section.getDouble("difficulty-bar-shrink", 0.3),
                section.getDouble("fish-edge-bounce", 0.5),
                section.getDouble("treasure-chance", 0.15),
                section.getDouble("perfect-bonus-multiplier", 1.5),
                section.getInt("base-xp-per-level", 100),
                section.getInt("max-level", 100),
                section.getDouble("level-modifier-scale", 0.5),
                section.getDouble("fish-speed-level-scale", 1.0),
                section.getDouble("flee-chance-level-scale", 1.0),
                section.getDouble("flee-speed-level-scale", 1.0),
                section.getDouble("progress-rate-level-scale", 1.0),
                section.getDouble("flee-base-chance", 0.25),
                section.getDouble("flee-chance-per-difficulty", 0.25),
                section.getDouble("flee-base-speed", 0.04),
                section.getDouble("flee-speed-per-difficulty", 0.03),
                section.getInt("flee-min-duration", 15),
                section.getInt("flee-max-duration", 35),
                section.getInt("flee-check-min-interval", 20),
                section.getInt("flee-check-max-interval", 50),
                section.getInt("flee-cooldown-min", 30),
                section.getInt("flee-cooldown-max", 70)
            );
        }
    }

    public record UiConfiguration(
        boolean registerOnEnable,
        boolean overwriteUiFiles,
        String mainId,
        String minigameId,
        String collectionId,
        String talentsId,
        String rewardsId,
        String tasksId
    ) {
        public static UiConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new UiConfiguration(
                section.getBoolean("register-ui-on-enable", true),
                section.getBoolean("overwrite-ui-files", false),
                section.getString("main-ui-id", "fishing_main"),
                section.getString("minigame-ui-id", "fishing_minigame"),
                section.getString("collection-ui-id", "fishing_collection"),
                section.getString("talents-ui-id", "fishing_talents"),
                section.getString("rewards-ui-id", "fishing_rewards"),
                section.getString("tasks-ui-id", "fishing_tasks")
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

    /**
     * 赛季配置，定义当前赛季的基本参数与经验成长曲线。
     *
     * @param seasonId    赛季唯一标识
     * @param displayName 赛季显示名称
     * @param maxLevel    赛季最大等级
     * @param xpCurve     经验成长曲线（ARIA 表达式断点求值）
     * @param startDate   赛季开始日期（yyyy-MM-dd）
     * @param endDate     赛季结束日期（yyyy-MM-dd）
     * @param dispatchMode 结算奖励发放方式：ONLINE_DIRECT / MAIL / HYBRID
     */
    public record SeasonConfiguration(
        @NotNull String seasonId,
        @NotNull String displayName,
        int maxLevel,
        @NotNull XpCurve xpCurve,
        @NotNull java.time.LocalDate startDate,
        @NotNull java.time.LocalDate endDate,
        @NotNull String dispatchMode
    ) {
        public static SeasonConfiguration load(ConfigurationSection section, @Nullable AriaBridge ariaBridge) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            String seasonId = section.getString("season-id", "fishing-s1");
            String displayName = section.getString("display-name", "第一赛季");
            int maxLevel = section.getInt("max-level", 100);
            XpCurve xpCurve = parseXpCurve(section, ariaBridge);
            java.time.LocalDate startDate = parseDate(section.getString("start-date", "2026-01-01"));
            java.time.LocalDate endDate = parseDate(section.getString("end-date", "2030-12-31"));
            String dispatchMode = section.getString("settlement.dispatch-mode", "ONLINE_DIRECT").toUpperCase();
            return new SeasonConfiguration(seasonId, displayName, maxLevel, xpCurve, startDate, endDate, dispatchMode);
        }

        private static XpCurve parseXpCurve(ConfigurationSection section, @Nullable AriaBridge ariaBridge) {
            if (ariaBridge == null) {
                // Aria 不可用时回退为固定线性曲线
                return new XpCurve(List.of(new XpCurve.Breakpoint(1, "50 * global.level")), nullAriaBridge());
            }
            List<?> raw = section.getList("xp-curve");
            if (raw == null || raw.isEmpty()) {
                return new XpCurve(List.of(new XpCurve.Breakpoint(1, "50 * global.level")), ariaBridge);
            }
            List<XpCurve.Breakpoint> breakpoints = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof Map<?, ?> map) {
                    int level = ((Number) map.get("level")).intValue();
                    String formula = String.valueOf(map.get("formula"));
                    breakpoints.add(new XpCurve.Breakpoint(level, formula));
                }
            }
            if (breakpoints.isEmpty()) {
                return new XpCurve(List.of(new XpCurve.Breakpoint(1, "50 * global.level")), ariaBridge);
            }
            return new XpCurve(breakpoints, ariaBridge);
        }

        private static java.time.LocalDate parseDate(String value) {
            try {
                return java.time.LocalDate.parse(value.trim());
            } catch (Exception e) {
                return java.time.LocalDate.of(2026, 1, 1);
            }
        }

        /** 判断当前日期是否在赛季有效时间范围内。 */
        public boolean isActive() {
            java.time.LocalDate now = java.time.LocalDate.now();
            return !now.isBefore(startDate) && !now.isAfter(endDate);
        }

        private static AriaBridge nullAriaBridge() {
            return new AriaBridge() {
                @Override public boolean available() { return false; }
                @Override public String version() { return null; }
                @Override public Object eval(@NotNull String code, @NotNull Map<String, Object> bindings) {
                    return null;
                }
            };
        }
    }
}
