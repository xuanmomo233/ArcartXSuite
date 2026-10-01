package xuanmo.arcartxsuite.abyss.template;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * 地牢模板加载器：扫描 data/abyss/abysses/&lt;id&gt;/ 目录，
 * 读取 config.yml + monsters.yml + obstacles.yml + interacts.yml +
 * tasks.yml + rewards.yml + chest_rewards.yml + scripts.yml + npcs.yml + functions.js，
 * 并解析世界模板来源（world/ 目录 → maps/ 共享目录 → .schem/.schematic）。
 */
public final class AbyssTemplateLoader {

    private final File abyssesDir;
    private final File mapsDir;
    private final Logger logger;

    public AbyssTemplateLoader(File moduleDataFolder, Logger logger) {
        this.abyssesDir = new File(moduleDataFolder, "abysses");
        this.mapsDir = new File(moduleDataFolder, "maps");
        this.logger = logger;
    }

    public File abyssesDir() {
        return abyssesDir;
    }

    /**
     * 加载全部地牢模板。单个地牢失败不影响其他地牢。
     */
    public Map<String, AbyssTemplate> loadAll() {
        Map<String, AbyssTemplate> result = new LinkedHashMap<>();
        if (!abyssesDir.isDirectory()) {
            return result;
        }
        File[] dirs = abyssesDir.listFiles(File::isDirectory);
        if (dirs == null) return result;
        for (File dir : dirs) {
            try {
                AbyssTemplate t = load(dir.getName(), dir);
                if (t != null) {
                    result.put(t.id(), t);
                }
            } catch (Exception e) {
                logger.warning("[abyss] 加载地牢失败: " + dir.getName() + " - " + e.getMessage());
            }
        }
        return result;
    }

    public @Nullable AbyssTemplate load(String id, File dir) {
        File configFile = new File(dir, "config.yml");
        if (!configFile.isFile()) {
            logger.warning("[abyss] 地牢 " + id + " 缺少 config.yml，跳过");
            return null;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);

        // ── 世界模板来源解析 ──
        AbyssTemplate.WorldSource worldSource = resolveWorldSource(id, dir, cfg);
        if (worldSource == null) {
            logger.warning("[abyss] 地牢 " + id + " 未找到世界模板（world/ 目录、maps/<template>、或 schematic），跳过");
            return null;
        }

        MonsterConfig monsters = MonsterConfig.load(loadYaml(dir, "monsters.yml"));
        ObstacleConfig obstacles = ObstacleConfig.load(loadYaml(dir, "obstacles.yml"));
        InteractConfig interacts = InteractConfig.load(loadYaml(dir, "interacts.yml"));
        TaskConfig tasks = TaskConfig.load(loadYaml(dir, "tasks.yml"));
        ScriptConfig scripts = ScriptConfig.load(loadYaml(dir, "scripts.yml"));
        NpcConfig npcs = NpcConfig.load(loadYaml(dir, "npcs.yml"));
        QuestConfig quests = QuestConfig.load(loadYaml(dir, "quests.yml"));

        // floors 模式：floors.yml（层链+defaults）+ rooms.yml（房间模板池）
        AbyssTemplate.Mode mode;
        try {
            mode = AbyssTemplate.Mode.valueOf(
                cfg.getString("mode", "classic").toUpperCase());
        } catch (IllegalArgumentException e) {
            mode = AbyssTemplate.Mode.CLASSIC;
        }
        FloorConfig floors = mode == AbyssTemplate.Mode.FLOORS
            ? FloorConfig.load(loadYaml(dir, "floors.yml"), loadYaml(dir, "rooms.yml"))
            : FloorConfig.EMPTY;
        if (mode == AbyssTemplate.Mode.FLOORS && floors.floors().isEmpty()) {
            logger.warning("[abyss] 地牢 " + id + " mode=floors 但 floors.yml 无楼层定义，回退 classic");
            mode = AbyssTemplate.Mode.CLASSIC;
        }

        // rewards.yml + chest_rewards.yml 合并
        RewardConfig rewards = RewardConfig.load(loadYaml(dir, "rewards.yml"));
        Map<String, RewardConfig.ChestReward> chests =
            new LinkedHashMap<>(rewards.chestRewards());
        chests.putAll(RewardConfig.loadChestOnly(loadYaml(dir, "chest_rewards.yml")));
        rewards = new RewardConfig(rewards.rewards(), chests);

        String functions = readText(new File(dir, "functions.js"));
        if (functions == null) functions = readText(new File(dir, "functions.aria"));

        ConfigurationSection reqSection = cfg.getConfigurationSection("requirements");
        EntryRequirements requirements = reqSection != null
            ? EntryRequirements.load(reqSection)
            : EntryRequirements.load(cfg);

        AbyssTemplate.TimeoutAction timeoutAction;
        try {
            timeoutAction = AbyssTemplate.TimeoutAction.valueOf(
                cfg.getString("timeout-action", cfg.getString("timeout_action", "FAIL")).toUpperCase());
        } catch (IllegalArgumentException e) {
            timeoutAction = AbyssTemplate.TimeoutAction.FAIL;
        }

        // 作战方案表（进本前选择；空=无方案机制）
        Map<String, AbyssTemplate.PlanEntry> plans = new LinkedHashMap<>();
        ConfigurationSection plansSection = cfg.getConfigurationSection("plans");
        if (plansSection != null) {
            for (String pid : plansSection.getKeys(false)) {
                ConfigurationSection pe = plansSection.getConfigurationSection(pid);
                if (pe != null) {
                    plans.put(pid, new AbyssTemplate.PlanEntry(pid,
                        pe.getString("name", pid), pe.getStringList("lore")));
                }
            }
        }

        // 世界入口点（主世界物理入口，右键进本；列表逐项容错）
        List<AbyssTemplate.EntrancePoint> entrances = new java.util.ArrayList<>();
        for (Map<?, ?> m : cfg.getMapList("entrances")) {
            try {
                Object w = m.get("world");
                Object loc = m.containsKey("location") ? m.get("location") : m.get("loc");
                Object rg = m.get("region");
                if (w == null || (loc == null && rg == null)) continue;
                entrances.add(new AbyssTemplate.EntrancePoint(
                    String.valueOf(w),
                    loc != null ? String.valueOf(loc) : null,
                    rg != null ? String.valueOf(rg) : null,
                    m.get("plan") != null ? String.valueOf(m.get("plan")) : null,
                    m.get("name") != null ? String.valueOf(m.get("name")) : null));
            } catch (Exception e) {
                logger.warning("[abyss] 地牢 " + id + " 入口点解析失败: " + e.getMessage());
            }
        }

        return new AbyssTemplate(
            id,
            cfg.getString("name", id),
            cfg.getString("icon", "STONE"),
            cfg.getString("difficulty", "普通"),
            cfg.getStringList("description"),
            mode,
            worldSource,
            cfg.getString("world.spawn", cfg.getString("spawn", "0,64,0,0,0")),
            cfg.getInt("time-limit", cfg.getInt("time_limit", 0)),
            timeoutAction,
            cfg.getInt("max-instances", cfg.getInt("max_instances", 0)),
            requirements,
            WorldRules.load(cfg.getConfigurationSection("world")),
            monsters, obstacles, interacts, tasks, rewards, scripts, npcs,
            quests,
            AbyssTemplate.DungeonLinks.load(
                cfg.getConfigurationSection("links"), cfg),
            floors,
            plans, entrances,
            functions, dir
        );
    }

    private @Nullable AbyssTemplate.WorldSource resolveWorldSource(
        String id, File dir, YamlConfiguration cfg) {

        // 0) 显式声明虚空世界：world.template: void（或 world-source: void）
        String declared = cfg.getString("world.template", cfg.getString("template", ""));
        if ("void".equalsIgnoreCase(declared)
            || "void".equalsIgnoreCase(cfg.getString("world-source", ""))) {
            return new AbyssTemplate.WorldSource(AbyssTemplate.WorldSource.Kind.VOID, dir);
        }

        // 1) 地牢目录内 world/ 文件夹（优先）
        File worldDir = new File(dir, "world");
        if (worldDir.isDirectory() && new File(worldDir, "level.dat").isFile()) {
            return new AbyssTemplate.WorldSource(
                AbyssTemplate.WorldSource.Kind.FOLDER, worldDir);
        }

        // 2) config.yml world.template → data/abyss/maps/<name>（文件夹或 schematic）
        String templateName = declared;
        if (!templateName.isBlank()) {
            File f = new File(mapsDir, templateName);
            if (f.isDirectory()) {
                return new AbyssTemplate.WorldSource(
                    AbyssTemplate.WorldSource.Kind.FOLDER, f);
            }
            File schem = findSchematic(mapsDir, templateName);
            if (schem != null) {
                return new AbyssTemplate.WorldSource(
                    AbyssTemplate.WorldSource.Kind.SCHEMATIC, schem);
            }
        }

        // 3) 地牢目录内的 schematic 文件
        File schem = findSchematic(dir, null);
        if (schem != null) {
            return new AbyssTemplate.WorldSource(
                AbyssTemplate.WorldSource.Kind.SCHEMATIC, schem);
        }
        return null;
    }

    private static @Nullable File findSchematic(File dir, String baseName) {
        File[] files = dir.listFiles((d, n) -> {
            String lower = n.toLowerCase();
            boolean matchExt = lower.endsWith(".schem") || lower.endsWith(".schematic");
            return matchExt && (baseName == null || n.equalsIgnoreCase(baseName)
                || lower.startsWith(baseName.toLowerCase() + "."));
        });
        return files != null && files.length > 0 ? files[0] : null;
    }

    private static @Nullable ConfigurationSection loadYaml(File dir, String name) {
        File f = new File(dir, name);
        if (!f.isFile()) return null;
        return YamlConfiguration.loadConfiguration(f);
    }

    private static @Nullable String readText(File f) {
        try {
            return f.isFile() ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 导出示例地牢（jar 内 abysses/example → data/abyss/abysses/example）。 */
    public void exportExample(java.util.function.BiFunction<String, File, Boolean> exporter) {
        // 由模块调用，具体实现在 AbyssService（需要模块 classloader 导出资源）
    }
}
