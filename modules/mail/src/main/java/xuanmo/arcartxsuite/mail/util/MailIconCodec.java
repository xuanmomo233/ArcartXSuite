package xuanmo.arcartxsuite.mail.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 邮件附件图标编解码器，负责 {@link IconDefinition} 与 YAML 字符串 / Map 之间的双向转换。
 * <p>
 * 用于数据库持久化（icon 列存 YAML 字符串）和预设保存（IconDefinition → Map 写回 YAML 文件）。
 *
 * 关联模块：mail。
 */
public final class MailIconCodec {

    private MailIconCodec() {
    }

    /**
     * 从原始配置值（String 或 Map）加载 IconDefinition。
     *
     * @param rawIcon 原始值：String（材质名或 JSON）或 Map（完整配置节）
     * @return IconDefinition，无有效图标时返回 {@code null}
     */
    public static IconDefinition loadIcon(Object rawIcon) {
        if (rawIcon == null) {
            return null;
        }
        if (rawIcon instanceof String s) {
            return IconDefinition.load(s);
        }
        if (rawIcon instanceof Map<?, ?> map) {
            return IconDefinition.load(mapToSection(map));
        }
        return null;
    }

    /**
     * 将 IconDefinition 序列化为 YAML 字符串，用于数据库存储。
     *
     * @param icon 图标定义，为 null 或无有效图标时返回空字符串
     * @return YAML 字符串
     */
    public static String toYamlString(IconDefinition icon) {
        if (icon == null || !icon.hasIcon()) {
            return "";
        }
        Map<String, Object> map = toMap(icon);
        if (map.isEmpty()) {
            return "";
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }
        return yaml.saveToString();
    }

    /**
     * 从 YAML 字符串反序列化 IconDefinition，用于数据库读取。
     *
     * @param yamlStr YAML 字符串，为 null 或空时返回 null
     * @return IconDefinition，解析失败时返回 null
     */
    public static IconDefinition fromYamlString(String yamlStr) {
        if (yamlStr == null || yamlStr.isBlank()) {
            return null;
        }
        String trimmed = yamlStr.trim();
        // 简单字符串：当作材质名或 JSON
        if (!trimmed.contains("\n")) {
            return IconDefinition.load(trimmed);
        }
        // YAML 配置节
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(trimmed);
        } catch (Exception ignored) {
            return null;
        }
        return IconDefinition.load(yaml);
    }

    /**
     * 将 IconDefinition 转为 Map，用于预设保存回 YAML 文件。
     *
     * @param icon 图标定义，为 null 时返回空 Map
     * @return 字段 Map（仅包含非空/非默认值的字段）
     */
    public static Map<String, Object> toMap(IconDefinition icon) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (icon == null || !icon.hasIcon()) {
            return map;
        }
        if (icon.material() != null && !icon.material().isBlank()) {
            map.put("material", icon.material());
        }
        if (icon.amount() > 1) {
            map.put("amount", icon.amount());
        }
        if (icon.name() != null && !icon.name().isBlank()) {
            map.put("name", icon.name());
        }
        if (icon.lore() != null && !icon.lore().isEmpty()) {
            map.put("lore", icon.lore());
        }
        if (icon.customModelData() > 0) {
            map.put("custom-model-data", icon.customModelData());
        }
        if (icon.source() != null && !icon.source().isBlank()) {
            map.put("source", icon.source());
        }
        if (icon.sourceId() != null && !icon.sourceId().isBlank()) {
            map.put("id", icon.sourceId());
        }
        if (icon.mmoType() != null && !icon.mmoType().isBlank()) {
            map.put("mmo-type", icon.mmoType());
        }
        if (icon.mmoId() != null && !icon.mmoId().isBlank()) {
            map.put("mmo-id", icon.mmoId());
        }
        if (icon.json() != null && !icon.json().isBlank()) {
            map.put("json", icon.json());
        }
        if (icon.texture() != null && !icon.texture().isBlank()) {
            map.put("texture", icon.texture());
        }
        if (icon.textureUrl() != null && !icon.textureUrl().isBlank()) {
            map.put("texture-url", icon.textureUrl());
        }
        if (icon.nbt() != null && !icon.nbt().isEmpty()) {
            map.put("nbt", icon.nbt());
        }
        if (icon.nbtString() != null && !icon.nbtString().isBlank()) {
            map.put("nbt", icon.nbtString());
        }
        if (icon.glow()) {
            map.put("glow", true);
        }
        if (icon.skullTexture() != null && !icon.skullTexture().isBlank()) {
            map.put("skull-texture", icon.skullTexture());
        }
        if (icon.color() != null && !icon.color().isBlank()) {
            map.put("color", icon.color());
        }
        return map;
    }

    /**
     * 将 Map 转为 YamlConfiguration 配置节，用于 IconDefinition.load(section)。
     */
    private static ConfigurationSection mapToSection(Map<?, ?> map) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            yaml.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return yaml;
    }
}
