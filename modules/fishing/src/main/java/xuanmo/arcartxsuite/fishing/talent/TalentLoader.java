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
                String icon = treeSection.getString("icon", "minecraft:stone");
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

        return new TalentEngine(trees, links);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable TalentNode parseNode(Map<?, ?> map, @NotNull String treeId, @NotNull Logger logger) {
        try {
            String id = String.valueOf(map.get("id"));
            int tier = ((Number) map.get("tier")).intValue();
            String branch = String.valueOf(map.get("branch"));
            String displayName = String.valueOf(map.get("display-name"));
            String description = String.valueOf(map.get("description"));
            List<TalentEffect> effects = new ArrayList<>();
            // 单效果（effect-type + effect-value）或多效果（effects 列表）
            if (map.containsKey("effect-type")) {
                String effectType = String.valueOf(map.get("effect-type"));
                double effectValue = ((Number) map.get("effect-value")).doubleValue();
                effects.add(new TalentEffect(effectType, effectValue));
            }
            if (map.containsKey("effects")) {
                Object rawEffects = map.get("effects");
                if (rawEffects instanceof List<?> effectList) {
                    for (Object e : effectList) {
                        if (e instanceof Map<?, ?> em) {
                            String effectType = String.valueOf(em.get("effect-type"));
                            double effectValue = ((Number) em.get("effect-value")).doubleValue();
                            effects.add(new TalentEffect(effectType, effectValue));
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
            Object rawIcon = map.get("icon");
            String icon = rawIcon != null ? String.valueOf(rawIcon) : "";
            return new TalentNode(id, treeId, tier, branch, displayName, description, effects, prerequisites, icon);
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
            List<TalentEffect> effects = new ArrayList<>();
            if (map.containsKey("effect-type")) {
                String effectType = String.valueOf(map.get("effect-type"));
                double effectValue = ((Number) map.get("effect-value")).doubleValue();
                effects.add(new TalentEffect(effectType, effectValue));
            }
            if (map.containsKey("effects")) {
                Object rawEffects = map.get("effects");
                if (rawEffects instanceof List<?> effectList) {
                    for (Object e : effectList) {
                        if (e instanceof Map<?, ?> em) {
                            String effectType = String.valueOf(em.get("effect-type"));
                            double effectValue = ((Number) em.get("effect-value")).doubleValue();
                            effects.add(new TalentEffect(effectType, effectValue));
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
            Object rawIcon = map.get("icon");
            String icon = rawIcon != null ? String.valueOf(rawIcon) : "";
            // 联动节点 treeId 为空
            return new TalentNode(id, "", 0, "link", displayName, description, effects, prerequisites, icon);
        } catch (Exception e) {
            logger.warning("解析联动天赋失败: " + e.getMessage());
            return null;
        }
    }
}
