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
        List<TalentNode> allNodes = new ArrayList<>();
        List<TalentNode> links = new ArrayList<>();

        // 新版格式：talents 为统一大树的扁平列表
        List<?> rawTalents = yaml.getList("talents");
        if (rawTalents != null) {
            Map<String, List<TalentNode>> treeNodes = new HashMap<>();
            Map<String, String> treeDisplayNames = new HashMap<>();
            Map<String, String> treeDescriptions = new HashMap<>();
            Map<String, IconDefinition> treeIcons = new HashMap<>();

            for (Object item : rawTalents) {
                if (item instanceof Map<?, ?> map) {
                    TalentNode node = parseNode(map, logger);
                    if (node == null) continue;
                    allNodes.add(node);

                    // 按 tree 字段分组；T1/T2 无 tree 字段，单独放在 meta 树
                    String treeId = node.treeId();
                    if (treeId.isBlank() || treeId.equals("meta")) {
                        treeId = "meta";
                    }
                    treeNodes.computeIfAbsent(treeId, k -> new ArrayList<>()).add(node);

                    // T3 节点作为原 6 棵树的根，继承显示名/图标
                    if (node.tier() == 3) {
                        treeDisplayNames.put(treeId, node.displayName());
                        treeDescriptions.put(treeId, node.description());
                        treeIcons.put(treeId, node.icon() != null ? node.icon() : IconDefinition.load("minecraft:stone"));
                    }
                }
            }

            // 构建 TalentTreeDefinition
            for (Map.Entry<String, List<TalentNode>> entry : treeNodes.entrySet()) {
                String treeId = entry.getKey();
                String displayName = treeDisplayNames.getOrDefault(treeId, treeId);
                String description = treeDescriptions.getOrDefault(treeId, "");
                IconDefinition icon = treeIcons.getOrDefault(treeId, IconDefinition.load("minecraft:stone"));
                trees.put(treeId, new TalentTreeDefinition(displayName, description, icon, entry.getValue()));
            }
        }

        // 兼容旧版 trees 结构（fallback）
        if (trees.isEmpty()) {
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
                                if (node != null) {
                                    nodes.add(node);
                                    allNodes.add(node);
                                }
                            }
                        }
                    }
                    trees.put(treeId, new TalentTreeDefinition(displayName, description, icon, nodes));
                }
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

        // 新版天赋固定 1 级，前置等级统一为 1
        Map<String, Integer> prerequisiteLevels = new HashMap<>();
        prerequisiteLevels.put("2", 1);
        prerequisiteLevels.put("3", 1);
        prerequisiteLevels.put("4", 1);
        prerequisiteLevels.put("5", 1);
        prerequisiteLevels.put("6", 1);
        prerequisiteLevels.put("7", 1);
        prerequisiteLevels.put("link", 1);

        return new TalentEngine(trees, links, prerequisiteLevels);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable TalentNode parseNode(@NotNull Map<?, ?> map, @NotNull Logger logger) {
        try {
            String id = String.valueOf(map.get("id"));
            int tier = ((Number) map.get("tier")).intValue();
            String branch = String.valueOf(map.get("branch"));
            Object treeRaw = map.get("tree");
            String treeId = (treeRaw != null && !String.valueOf(treeRaw).isBlank() && !"null".equals(String.valueOf(treeRaw))) ? String.valueOf(treeRaw) : "meta";
            String displayName = String.valueOf(map.get("display-name"));
            String description = String.valueOf(map.get("description"));

            // 解析单效果
            String effectType = null;
            List<Double> levelValues = new ArrayList<>();
            if (map.containsKey("effect-type")) {
                effectType = String.valueOf(map.get("effect-type"));
                double value = parseNumber(map.get("effect-value"));
                levelValues.add(value);
            }

            // 兼容旧格式：levels 列表（取首级作为单级值）
            if (levelValues.isEmpty() && map.containsKey("levels")) {
                Object rawLevels = map.get("levels");
                if (rawLevels instanceof List<?> levelList && !levelList.isEmpty()) {
                    levelValues.add(parseNumber(levelList.get(0)));
                }
            }

            // 多效果（联动/特殊）
            List<TalentEffect> effects = new ArrayList<>();
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
            return new TalentNode(id, treeId, tier, branch, displayName, description,
                effectType, levelValues, effects, prerequisites, icon);
        } catch (Exception e) {
            logger.warning("解析天赋节点失败: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable TalentNode parseNode(@NotNull Map<?, ?> map, @NotNull String treeId, @NotNull Logger logger) {
        TalentNode node = parseNode(map, logger);
        if (node == null) return null;
        // 兼容旧格式：传入 treeId 时覆盖
        return new TalentNode(node.id(), treeId, node.tier(), node.branch(),
            node.displayName(), node.description(), node.effectType(),
            node.levelValues(), node.effects(), node.prerequisites(), node.icon());
    }

    @SuppressWarnings("unchecked")
    private static @Nullable TalentNode parseLink(@NotNull Map<?, ?> map, @NotNull Logger logger) {
        try {
            String id = String.valueOf(map.get("id"));
            String displayName = String.valueOf(map.get("display-name"));
            String description = String.valueOf(map.get("description"));

            String effectType = null;
            List<Double> levelValues = new ArrayList<>();
            List<TalentEffect> effects = new ArrayList<>();

            if (map.containsKey("effect-type")) {
                effectType = String.valueOf(map.get("effect-type"));
                levelValues.add(parseNumber(map.get("effect-value")));
            }

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
            if (levelValues.isEmpty()) levelValues.add(0.0);
            return new TalentNode(id, "", 0, "link", displayName, description,
                effectType, levelValues, effects, prerequisites, icon);
        } catch (Exception e) {
            logger.warning("解析联动天赋失败: " + e.getMessage());
            return null;
        }
    }

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
