package xuanmo.arcartxsuite.fishing.talent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 天赋树配置加载器，从 {@code talents.yml} 解析为 {@link TalentEngine}。
 * <p>
 * 关联模块：{@code fishing}。
 */
public final class TalentLoader {

    private TalentLoader() {}

    /**
     * 从 YAML 配置加载天赋树引擎。
     *
     * @param yaml   talents.yml 配置
     * @param logger 日志记录器
     * @return 天赋树引擎实例
     */
    public static @NotNull TalentEngine load(@NotNull YamlConfiguration yaml, @NotNull Logger logger) {
        Map<String, TalentTreeDefinition> trees = new HashMap<>();
        List<TalentNode> links = new ArrayList<>();

        ConfigurationSection treesSection = yaml.getConfigurationSection("trees");
        if (treesSection != null) {
            for (String treeId : treesSection.getKeys(false)) {
                ConfigurationSection treeSection = treesSection.getConfigurationSection(treeId);
                if (treeSection == null) continue;
                String displayName = treeSection.getString("display-name", treeId);
                String description = treeSection.getString("description", "");
                IconDefinition icon = IconDefinition.load(treeSection, "icon");
                if (icon == null) {
                    icon = IconDefinition.load("minecraft:stone");
                }
                List<TalentNode> nodes = new ArrayList<>();
                List<?> rawNodes = treeSection.getList("nodes");
                if (rawNodes != null) {
                    for (Object item : rawNodes) {
                        if (item instanceof Map<?, ?> map) {
                            TalentNode node = parseNode(map, treeId, logger);
                            if (node != null) nodes.add(node);
                        }
                    }
                }
                trees.put(treeId, new TalentTreeDefinition(displayName, description, icon, nodes));
            }
        }

        // 加载联动天赋
        List<?> rawLinks = yaml.getList("links");
        if (rawLinks != null) {
            for (Object item : rawLinks) {
                if (item instanceof Map<?, ?> map) {
                    TalentNode link = parseLink(map, logger);
                    if (link != null) links.add(link);
                }
            }
        }

        // 读取前置等级要求映射（每个 tier 可独立配置）
        Map<String, Integer> prerequisiteLevels = new HashMap<>();
        // 兼容旧格式：prerequisite-level（全局统一）
        if (yaml.contains("prerequisite-level")) {
            int globalLevel = yaml.getInt("prerequisite-level", 3);
            prerequisiteLevels.put("2", globalLevel);
            prerequisiteLevels.put("3", globalLevel);
            prerequisiteLevels.put("4", globalLevel);
            prerequisiteLevels.put("5", globalLevel);
            prerequisiteLevels.put("link", globalLevel);
        }
        // 新格式：prerequisite-levels 映射（覆盖旧格式）
        ConfigurationSection prereqSection = yaml.getConfigurationSection("prerequisite-levels");
        if (prereqSection != null) {
            for (String key : prereqSection.getKeys(false)) {
                prerequisiteLevels.put(key, prereqSection.getInt(key, 1));
            }
        }
        if (prerequisiteLevels.isEmpty()) {
            // 默认值：所有 tier 统一要求 3 级
            prerequisiteLevels.put("2", 3);
            prerequisiteLevels.put("3", 3);
            prerequisiteLevels.put("4", 3);
            prerequisiteLevels.put("5", 3);
            prerequisiteLevels.put("link", 3);
        }

        return new TalentEngine(trees, links, prerequisiteLevels);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable TalentNode parseNode(Map<?, ?> map, @NotNull String treeId, @NotNull Logger logger) {
        try {
            String id = String.valueOf(map.get("id"));
            int tier = ((Number) map.get("tier")).intValue();
            String branch = String.valueOf(map.get("branch"));
            String displayName = String.valueOf(map.get("display-name"));
            String description = String.valueOf(map.get("description"));

            // 解析 effect-type 和 levels（多级）或 effect-value（单级兼容）
            String effectType = null;
            List<Double> levelValues = new ArrayList<>();
            List<TalentEffect> effects = new ArrayList<>();

            if (map.containsKey("effect-type")) {
                effectType = String.valueOf(map.get("effect-type"));
            }

            // 优先解析 levels 列表（多级节点）
            if (map.containsKey("levels")) {
                Object rawLevels = map.get("levels");
                if (rawLevels instanceof List<?> levelList) {
                    for (Object lv : levelList) {
                        levelValues.add(parseNumber(lv));
                    }
                }
            }

            // 兼容旧格式：effect-value（单级）
            if (levelValues.isEmpty() && map.containsKey("effect-value")) {
                levelValues.add(parseNumber(map.get("effect-value")));
            }

            // 多效果（effects 列表，仅联动节点使用）
            if (map.containsKey("effects")) {
                Object rawEffects = map.get("effects");
                if (rawEffects instanceof List<?> effectList) {
                    for (Object e : effectList) {
                        if (e instanceof Map<?, ?> em) {
                            String et = String.valueOf(em.get("effect-type"));
                            double ev = ((Number) em.get("effect-value")).doubleValue();
                            effects.add(new TalentEffect(et, ev));
                        }
                    }
                }
            }

            List<String> prerequisites = new ArrayList<>();
            Object rawPrereq = map.get("prerequisites");
            if (rawPrereq instanceof List<?> prereqList) {
                for (Object p : prereqList) {
                    prerequisites.add(String.valueOf(p));
                }
            }
            IconDefinition icon = iconFromMap(map, "icon");
            return new TalentNode(id, treeId, tier, branch, displayName, description,
                effectType, levelValues, effects, prerequisites, icon);
        } catch (Exception e) {
            logger.warning("解析天赋节点失败: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable TalentNode parseLink(Map<?, ?> map, @NotNull Logger logger) {
        try {
            String id = String.valueOf(map.get("id"));
            String displayName = String.valueOf(map.get("display-name"));
            String description = String.valueOf(map.get("description"));

            // 联动节点固定 1 级
            String effectType = null;
            List<Double> levelValues = new ArrayList<>();
            List<TalentEffect> effects = new ArrayList<>();

            if (map.containsKey("effect-type")) {
                effectType = String.valueOf(map.get("effect-type"));
                if (map.containsKey("effect-value")) {
                    levelValues.add(parseNumber(map.get("effect-value")));
                }
            }

            // 多效果联动（如 link-3）
            if (map.containsKey("effects")) {
                Object rawEffects = map.get("effects");
                if (rawEffects instanceof List<?> effectList) {
                    for (Object e : effectList) {
                        if (e instanceof Map<?, ?> em) {
                            String et = String.valueOf(em.get("effect-type"));
                            double ev = parseNumber(em.get("effect-value"));
                            effects.add(new TalentEffect(et, ev));
                        }
                    }
                }
            }

            List<String> prerequisites = new ArrayList<>();
            Object rawPrereq = map.get("prerequisites");
            if (rawPrereq instanceof List<?> prereqList) {
                for (Object p : prereqList) {
                    prerequisites.add(String.valueOf(p));
                }
            }
            IconDefinition icon = iconFromMap(map, "icon");
            // 联动节点 treeId 为空，固定 1 级
            if (levelValues.isEmpty()) levelValues.add(0.0);
            return new TalentNode(id, "", 0, "link", displayName, description,
                effectType, levelValues, effects, prerequisites, icon);
        } catch (Exception e) {
            logger.warning("解析联动天赋失败: " + e.getMessage());
            return null;
        }
    }

    // ─── 通用图标解析 ──────────────────────────────────────

    /**
     * 从 {@link Map} 解析图标定义（节点/联动级 icon）。
     * <p>
     * icon 为块式映射对象（Bukkit 反序列化为 {@code Map<?, ?>}），字段与
     * {@link IconDefinition#load(ConfigurationSection)} 兼容。
     * 解析失败或缺失时返回 null（UI 端回退到 effectType 映射）。
     *
     * @param map 所属 Map
     * @param key icon 字段名
     * @return 解析出的 {@link IconDefinition}，缺失时为 null
     */
    private static @Nullable IconDefinition iconFromMap(@NotNull Map<?, ?> map, @NotNull String key) {
        Object raw = map.get(key);
        if (!(raw instanceof Map<?, ?> iconMap)) {
            return null;
        }
        String material = strOr(iconMap.get("material"), "");
        String source = strOr(iconMap.get("source"), "");
        String sourceId = strOr(iconMap.get("id"), strOr(iconMap.get("item-id"), ""));
        String mmoType = strOr(iconMap.get("mmo-type"), "");
        String mmoId = strOr(iconMap.get("mmo-id"), "");
        String json = strOr(iconMap.get("json"), "");
        String texture = strOr(iconMap.get("texture"), "");
        String textureUrl = strOr(iconMap.get("texture-url"), strOr(iconMap.get("url"), ""));
        String name = strOr(iconMap.get("name"), "");
        String nbtString = strOr(iconMap.get("nbt"), "");
        String skullTexture = strOr(iconMap.get("skull-texture"), strOr(iconMap.get("skullTexture"), ""));
        String color = strOr(iconMap.get("color"), "");
        int amount = iconMap.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        int customModelData = iconMap.get("custom-model-data") instanceof Number cmd ? cmd.intValue()
            : iconMap.get("customModelData") instanceof Number cmd2 ? cmd2.intValue() : 0;
        boolean glow = iconMap.get("glow") instanceof Boolean g && g;
        List<String> lore = new ArrayList<>();
        Object rawLore = iconMap.get("lore");
        if (rawLore instanceof List<?> loreList) {
            for (Object line : loreList) {
                lore.add(String.valueOf(line));
            }
        }
        Map<String, String> nbt = new HashMap<>();
        Object rawNbt = iconMap.get("nbt");
        // nbt 如果是 Map（配置节），解析为键值对；如果是字符串，已在 nbtString 中处理
        if (rawNbt instanceof Map<?, ?> nbtMap && nbtString.isBlank()) {
            for (Map.Entry<?, ?> entry : nbtMap.entrySet()) {
                nbt.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        IconDefinition icon = new IconDefinition(
            material, amount, name, lore, customModelData,
            source, sourceId, mmoType, mmoId, json,
            texture, textureUrl, nbt, nbtString, glow, skullTexture, color
        );
        return icon.hasIcon() ? icon : null;
    }

    private static String strOr(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        String s = String.valueOf(value);
        return s.isBlank() ? defaultValue : s;
    }

    private static double parseNumber(@Nullable Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
