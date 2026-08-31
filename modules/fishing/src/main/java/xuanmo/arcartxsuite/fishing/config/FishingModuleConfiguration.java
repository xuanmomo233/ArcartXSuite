package xuanmo.arcartxsuite.fishing.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.item.ItemMatcherLoader;
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.FishRarity;
import xuanmo.arcartxsuite.fishing.model.RodDefinition;
import xuanmo.arcartxsuite.fishing.model.TreasureDefinition;
import xuanmo.arcartxsuite.fishing.model.WaterArea;
import xuanmo.arcartxsuite.api.util.PointInPolygon;

/**
 * 钓鱼模块的完整配置快照，由主 yml 与附属目录（fishes/treasures/baits/rods/waters）加载而成。
 * <p>
 * 关联模块：{@code fishing}。包含存储、钓鱼设置、鱼/宝藏/鱼饵/鱼竿定义、
 * 水域、UI 与命令配置等全部运行时所需的不可变数据。
 *
 * @param storage         存储配置（SQLite/MySQL 连接参数）
 * @param fishing         钓鱼基础设置（是否替换原版、小游戏 tick 间隔等）
 * @param fishes          鱼种定义列表
 * @param treasures       宝藏定义列表
 * @param baits           鱼饵定义列表
 * @param rods            鱼竿定义列表
 * @param specifiedWaters 显式声明的水域列表（从 waters/ 目录加载，不含默认水域）
 * @param defaultWater    默认水域（water-default.yml，未命中任何指定水域时使用）
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
    @NotNull UiConfiguration ui,
    @NotNull CommandsConfiguration commands,
    @NotNull CrossServerChannelConfig crossServer,
    boolean debug
) {

    // ─── 索引 Map（懒加载，供 O(1) 查找） ────────────────────────
    // record 不允许实例字段，用 ThreadLocal 持有懒加载缓存
    private static final java.util.concurrent.ConcurrentHashMap<FishingModuleConfiguration, IndexHolder> INDEX_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final class IndexHolder {
        final Map<String, FishDefinition> fishIndex;
        final Map<String, BaitDefinition> baitIndex;
        final Map<String, RodDefinition> rodIndex;
        final Map<String, TreasureDefinition> treasureIndex;

        IndexHolder(FishingModuleConfiguration cfg) {
            Map<String, FishDefinition> fi = new HashMap<>();
            for (FishDefinition f : cfg.fishes) fi.put(f.id(), f);
            this.fishIndex = Collections.unmodifiableMap(fi);

            Map<String, BaitDefinition> bi = new HashMap<>();
            for (BaitDefinition b : cfg.baits) bi.put(b.id(), b);
            this.baitIndex = Collections.unmodifiableMap(bi);

            Map<String, RodDefinition> ri = new HashMap<>();
            for (RodDefinition r : cfg.rods) ri.put(r.id(), r);
            this.rodIndex = Collections.unmodifiableMap(ri);

            Map<String, TreasureDefinition> ti = new HashMap<>();
            for (TreasureDefinition t : cfg.treasures) ti.put(t.id(), t);
            this.treasureIndex = Collections.unmodifiableMap(ti);
        }
    }

    private IndexHolder indexHolder() {
        return INDEX_CACHE.computeIfAbsent(this, IndexHolder::new);
    }

    /** 按 ID 查找鱼种定义（O(1)）。 */
    public @Nullable FishDefinition findFish(@NotNull String id) {
        return indexHolder().fishIndex.get(id);
    }

    /** 按 ID 查找饵料定义（O(1)）。 */
    public @Nullable BaitDefinition findBait(@NotNull String id) {
        return indexHolder().baitIndex.get(id);
    }

    /** 按 ID 查找鱼竿定义（O(1)）。 */
    public @Nullable RodDefinition findRod(@NotNull String id) {
        return indexHolder().rodIndex.get(id);
    }

    /** 按 ID 查找宝藏定义（O(1)）。 */
    public @Nullable TreasureDefinition findTreasure(@NotNull String id) {
        return indexHolder().treasureIndex.get(id);
    }

    public static FishingModuleConfiguration load(FileConfiguration yaml, File dataFolder, Logger logger) {
        return load(yaml, dataFolder, logger, null);
    }

    public static FishingModuleConfiguration load(FileConfiguration yaml, File dataFolder, Logger logger,
                                                   @Nullable AriaBridge ariaBridge) {
        List<FishDefinition> fishes = loadFishesFromDir(new File(dataFolder, "fishes"), logger);
        List<TreasureDefinition> treasures = loadTreasuresFromDir(new File(dataFolder, "treasures"), logger);
        List<BaitDefinition> baits = loadBaitsFromDir(new File(dataFolder, "baits"), logger);
        List<RodDefinition> rods = loadRodsFromDir(new File(dataFolder, "rods"), logger);

        // 从 waters/ 目录加载水域文件
        WaterArea[] waterAreas = loadWatersFromDir(new File(dataFolder, "waters"), logger);
        List<WaterArea> specified = new ArrayList<>();
        WaterArea defaultWater = null;
        for (WaterArea wa : waterAreas) {
            if (wa.isDefault()) {
                defaultWater = wa;
            } else if (wa.enabled()) {
                specified.add(wa);
            }
        }
        if (defaultWater == null) {
            defaultWater = createFallbackDefaultWater();
        }

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

        IconDefinition itemRef = parseIcon(section, "item", "minecraft:cod");
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
        // 鱼级别覆盖全局曲线的 base-rate（formula 沿用全局）
        Double progressDrainRate = null;
        ConfigurationSection fishDrainSection = section.getConfigurationSection("progress-drain");
        if (fishDrainSection != null && fishDrainSection.contains("base-rate")) {
            progressDrainRate = fishDrainSection.getDouble("base-rate");
        }
        Double progressGainRate = null;
        ConfigurationSection fishGainSection = section.getConfigurationSection("progress-gain");
        if (fishGainSection != null && fishGainSection.contains("base-rate")) {
            progressGainRate = fishGainSection.getDouble("base-rate");
        }

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
                    IconDefinition dropItem = parseIcon(drop, "item", "minecraft:air");
                    if (dropItem != null && dropItem.hasIcon()) {
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
        int maxPlayerLevel = section.getInt("max-player-level", 0);
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
        // icon: 独立图标定义（IconDefinition），未配置时回退到 itemRef
        IconDefinition iconRef = IconDefinition.load(section, "icon");
        double recordWeight = section.getDouble("record-weight", 1.0);

        return FishDefinition.builder()
            .id(id).displayName(displayName).rarity(rarity)
            .minSize(minSize).maxSize(maxSize).basePrice(basePrice).baseXp(baseXp)
            .seasons(seasons).weathers(weathers)
            .timeRanges(timeRanges).itemRef(itemRef).difficulty(difficulty).behaviors(behaviors)
            .currencyReward(currencyReward)
            .progressDrainRate(progressDrainRate).progressGainRate(progressGainRate)
            .fleeParams(fleeParams).behaviorStages(behaviorStages)
            .drops(drops).commandRewards(commandRewards).currencyRewards(currencyRewards)
            .minPlayerLevel(minPlayerLevel).maxPlayerLevel(maxPlayerLevel).requirePermission(requirePermission)
            .baitPreferences(baitPreferences).biomes(biomes).depthRange(depthRange)
            .description(description).iconRef(iconRef)
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
                        parseIcon(section, "item", "minecraft:stick"),
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
                        parseIcon(section, "item", "minecraft:string"),
                        ItemMatcherLoader.load(section.getConfigurationSection("match"), logger, "baits/" + p.getFileName()),
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
                        section.getInt("max-player-level", 0),
                        ItemMatcherLoader.load(section.getConfigurationSection("match"), logger, "rods/" + p.getFileName()),
                        Math.max(1, section.getInt("bait-slots", 3))
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

    // ─── 通用图标解析 ──────────────────────────────────────

    private static @NotNull IconDefinition parseIcon(@NotNull ConfigurationSection section,
                                                      @NotNull String key,
                                                      @NotNull String defaultMaterial) {
        IconDefinition icon = IconDefinition.load(section, key);
        if (icon != null && icon.hasIcon()) {
            return icon;
        }
        return IconDefinition.load(defaultMaterial);
    }

    // ─── 水域解析（从 waters/ 目录加载） ────────────────────────

    /**
     * 从 waters/ 目录加载所有水域文件（water-*.yml）。
     * <p>
     * 文件名格式：{@code water-<id>.yml}，其中 {@code water-default.yml} 为默认水域。
     * 返回数组中包含所有已加载的水域（含默认水域），调用方负责分类。
     *
     * @param watersDir waters/ 目录
     * @param logger    日志
     * @return 水域数组（可能为空）
     */
    private static WaterArea[] loadWatersFromDir(@NotNull File watersDir, @NotNull Logger logger) {
        List<WaterArea> result = new ArrayList<>();
        if (!watersDir.isDirectory()) return result.toArray(new WaterArea[0]);

        File[] files = watersDir.listFiles((dir, name) ->
            name.startsWith("water-") && name.endsWith(".yml"));
        if (files == null) return result.toArray(new WaterArea[0]);

        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                WaterArea area = parseWaterAreaFromFile(file, yaml);
                if (area != null) {
                    result.add(area);
                }
            } catch (Exception e) {
                logger.warning("加载水域文件失败: " + file.getName() + " - " + e.getMessage());
            }
        }
        return result.toArray(new WaterArea[0]);
    }

    private static @Nullable WaterArea parseWaterAreaFromFile(@NotNull File file,
                                                               @NotNull YamlConfiguration yaml) {
        // 从文件名提取 ID：water-dragon_lake.yml → dragon_lake
        String fileName = file.getName();
        String id = fileName.substring("water-".length(), fileName.length() - ".yml".length());

        String displayName = yaml.getString("display-name", id);
        String world = yaml.getString("world", null);
        if (world != null && world.isEmpty()) world = null;
        boolean enabled = yaml.getBoolean("enabled", true);

        // 多边形顶点
        List<PointInPolygon.Vertex> vertices = new ArrayList<>();
        List<String> posList = yaml.getStringList("pos");
        for (String pos : posList) {
            String[] parts = pos.split(",");
            if (parts.length >= 2) {
                try {
                    int x = Integer.parseInt(parts[0].trim());
                    int z = Integer.parseInt(parts[1].trim());
                    vertices.add(new PointInPolygon.Vertex(x, z));
                } catch (NumberFormatException ignored) {}
            }
        }

        // 鱼类和宝藏列表（直接写 ID）
        List<String> fishes = yaml.getStringList("fishes");
        List<String> treasures = yaml.getStringList("treasures");

        // 难度公式
        String difficultyFormula = yaml.getString("difficulty-formula", "1.0");

        // 等级限制
        int minPlayerLevel = yaml.getInt("min-player-level", 0);
        int maxPlayerLevel = yaml.getInt("max-player-level", 0);

        // 饵料倍率
        Map<String, Double> baitMultipliers = new LinkedHashMap<>();
        ConfigurationSection baitSection = yaml.getConfigurationSection("bait-multipliers");
        if (baitSection != null) {
            for (String bk : baitSection.getKeys(false)) {
                baitMultipliers.put(bk, baitSection.getDouble(bk, 1.0));
            }
        }

        // 权限
        String requirePermission = yaml.getString("require-permission", null);
        if (requirePermission != null && requirePermission.isEmpty()) requirePermission = null;

        return new WaterArea(id, displayName, world, enabled,
            Collections.unmodifiableList(vertices),
            Collections.unmodifiableList(fishes),
            Collections.unmodifiableList(treasures),
            difficultyFormula, minPlayerLevel, maxPlayerLevel,
            Collections.unmodifiableMap(baitMultipliers), requirePermission);
    }

    /**
     * 创建兜底默认水域（当 waters/ 目录不存在或无 water-default.yml 时使用）。
     */
    private static WaterArea createFallbackDefaultWater() {
        return new WaterArea("default", "&7普通水域", null, true,
            List.of(), List.of(), List.of(), "1.0", 0, 0, Map.of(), null);
    }

    /**
     * 存储配置 record，描述 SQLite/MySQL 连接参数。
     * <p>
     * 当 {@code shared=true} 时，模块使用本体共享数据源（由本体 config.yml 的 storage 节统一管控）；
     * 当 {@code shared=false} 时，模块自建独立连接池（不推荐）。
     *
     * @param shared         是否使用共享存储模式（true=共享本体数据源，false=自建连接池）
     * @param mode           存储模式（sqlite / mysql），仅在 shared=false 时生效
     * @param sqliteFileName SQLite 文件名，仅在 shared=false 时生效
     * @param host           MySQL 主机地址，仅在 shared=false 时生效
     * @param port           MySQL 端口，仅在 shared=false 时生效
     * @param database       MySQL 数据库名，仅在 shared=false 时生效
     * @param username       MySQL 用户名，仅在 shared=false 时生效
     * @param password       MySQL 密码，仅在 shared=false 时生效
     * @param tablePrefix    MySQL 表名前缀，仅在 shared=false 时生效
     * @param poolSize       连接池大小，仅在 shared=false 时生效
     */
    public record StorageConfiguration(
        boolean shared,
        String mode,
        String sqliteFileName,
        String host,
        int port,
        String database,
        String username,
        String password,
        String tablePrefix,
        int poolSize
    ) {
        /**
         * 从配置节加载存储配置。
         * <p>
         * shared=true 时使用默认值（不读取其余字段）；shared=false 时读取 mode/sqlite/mysql 配置。
         *
         * @param section 配置节，为 {@code null} 时使用默认值（shared=true）
         * @return 解析后的 {@link StorageConfiguration} 实例
         */
        public static StorageConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            boolean shared = section.getBoolean("shared", true);
            // 向后兼容：如果 shared 字段不存在但 mode=mysql 存在，视为 shared=false
            if (!section.isSet("shared") && "mysql".equalsIgnoreCase(section.getString("mode", "sqlite"))) {
                shared = false;
            }
            if (shared) {
                return new StorageConfiguration(true, "sqlite", "fishing.db",
                    "127.0.0.1", 3306, "arcartxsuite", "root", "",
                    "axs_fishing_", 1);
            }
            return new StorageConfiguration(false,
                section.getString("mode", "sqlite"),
                section.getString("sqlite.file", "fishing.db"),
                section.getString("mysql.host", "127.0.0.1"),
                section.getInt("mysql.port", 3306),
                section.getString("mysql.database", "arcartxsuite"),
                section.getString("mysql.username", "root"),
                section.getString("mysql.password", ""),
                section.getString("mysql.table-prefix", "axs_fishing_"),
                section.getInt("pool-size", 1)
            );
        }

        /**
         * 判断当前存储模式是否为 SQLite（非 mysql 均视为 SQLite）。
         *
         * @return {@code true} 表示使用 SQLite
         */
        public boolean isSqlite() {
            return !"mysql".equalsIgnoreCase(mode);
        }

        /**
         * 将存储配置转换为存储描述符。
         *
         * @return 对应的 {@link StorageDescriptor}
         */
        public StorageDescriptor toDescriptor() {
            if (!isSqlite()) {
                return StorageDescriptor.mysql(host, port, database, username, password, poolSize, tablePrefix);
            }
            return StorageDescriptor.sqlite(sqliteFileName);
        }

        /**
         * 判断是否为覆盖模式（模块自建连接池）。
         *
         * @return {@code true} 表示模块应自建连接池（shared=false）
         * @since 1.4.0
         */
        public boolean hasOverride() {
            return !shared;
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
        @NotNull xuanmo.arcartxsuite.fishing.minigame.ProgressCurve gainCurve,
        @NotNull xuanmo.arcartxsuite.fishing.minigame.ProgressCurve drainCurve,
        int streakThreshold,
        double streakBonusPerStep,
        double streakMaxBonus,
        double catchDurationDifficultyScale,
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
        double progressGainLevelScale,
        double progressDrainLevelScale,
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
                section.getInt("catch-duration-ticks", 400),
                section.getInt("fishing-cooldown-ticks", 100),
                loadProgressCurve(section, "progress-gain", xuanmo.arcartxsuite.fishing.minigame.ProgressCurve.defaultGain()),
                loadProgressCurve(section, "progress-drain", xuanmo.arcartxsuite.fishing.minigame.ProgressCurve.defaultDrain()),
                section.getInt("streak-threshold", 20),
                section.getDouble("streak-bonus-per-step", 0.1),
                section.getDouble("streak-max-bonus", 0.5),
                section.getDouble("catch-duration-difficulty-scale", 0.5),
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
                section.getDouble("progress-gain-level-scale", 0.3),
                section.getDouble("progress-drain-level-scale", 0.5),
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

        /**
         * 从 yml 加载进度曲线配置。
         * <p>
         * 配置格式：
         * <pre>{@code
         * progress-gain:
         *   base-rate: 0.01
         *   formula: "base_rate * (1 + progress * 0.5)"
         * }</pre>
         * 鱼级别覆盖同样使用 progress-gain / progress-drain 配置节（可仅写 base-rate，formula 沿用全局）。
         */
        private static @NotNull xuanmo.arcartxsuite.fishing.minigame.ProgressCurve loadProgressCurve(
                ConfigurationSection parent, @NotNull String prefix,
                @NotNull xuanmo.arcartxsuite.fishing.minigame.ProgressCurve fallback) {
            ConfigurationSection curveSection = parent.getConfigurationSection(prefix);
            if (curveSection != null) {
                double baseRate = curveSection.getDouble("base-rate", fallback.baseRate());
                String formula = curveSection.contains("formula") ? curveSection.getString("formula") : null;
                if (formula == null || formula.isBlank()) {
                    formula = fallback.formula(); // 沿用全局公式（鱼级别仅覆盖 base-rate 时）
                }
                return new xuanmo.arcartxsuite.fishing.minigame.ProgressCurve(baseRate, formula);
            }
            return fallback;
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
        String tasksId,
        String baitsId
    ) {
        public static UiConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new UiConfiguration(
                section.getBoolean("register-ui-on-enable", true),
                section.getBoolean("overwrite-ui-files", false),
                section.getString("main-ui-id", "AXS:fishing_main"),
                section.getString("minigame-ui-id", "AXS:fishing_minigame"),
                section.getString("collection-ui-id", "AXS:fishing_collection"),
                section.getString("talents-ui-id", "AXS:fishing_talents"),
                section.getString("rewards-ui-id", "AXS:fishing_rewards"),
                section.getString("tasks-ui-id", "AXS:fishing_tasks"),
                section.getString("baits-ui-id", "AXS:fishing_baits")
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
     */
    public record SeasonConfiguration(
        @NotNull String seasonId,
        @NotNull String displayName,
        int maxLevel,
        @NotNull XpCurve xpCurve,
        @NotNull java.time.LocalDate startDate,
        @NotNull java.time.LocalDate endDate
    ) {
        public static SeasonConfiguration load(ConfigurationSection section, @Nullable AriaBridge ariaBridge) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            String seasonId = section.getString("season-id", "fishing-s1");
            String displayName = section.getString("display-name", "第一赛季");
            int maxLevel = section.getInt("max-level", 100);
            XpCurve xpCurve = parseXpCurve(section, ariaBridge);
            java.time.LocalDate startDate = parseDate(section.getString("start-date", "2026-01-01"));
            java.time.LocalDate endDate = parseDate(section.getString("end-date", "2030-12-31"));
            return new SeasonConfiguration(seasonId, displayName, maxLevel, xpCurve, startDate, endDate);
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
