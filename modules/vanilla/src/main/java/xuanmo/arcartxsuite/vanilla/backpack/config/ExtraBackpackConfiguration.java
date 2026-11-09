package xuanmo.arcartxsuite.vanilla.backpack.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * ExtraBackpack 模块的根配置 record，承载调试开关、UI、存储与扩展背包分类的全部配置。
 * <p>
 * 通过 {@link #load(ConfigurationSection, Logger)} 从 Bukkit {@link ConfigurationSection} 解析构建，
 * 供 {@link xuanmo.arcartxsuite.vanilla.VanillaModule} 在启动时读取。
 *
 * @param ui          UI 相关配置（是否注册 UI、是否覆盖 UI 文件）
 * @param extraBackpack 扩展背包功能配置（是否启用、分类列表）
 */
public record ExtraBackpackConfiguration(
    UiConfiguration ui,
    ExtraBackpackConfig extraBackpack
) {

    /**
     * 从 Bukkit 配置文件解析并构建 {@link ExtraBackpackConfiguration}。
     *
     * @param configuration Bukkit 配置对象
     * @param logger        模块日志，用于输出配置警告；可为 {@code null}
     * @return 解析后的配置实例
     */
    public static ExtraBackpackConfiguration load(ConfigurationSection configuration, Logger logger) {
        ConfigurationSection uiSection = configuration.getConfigurationSection("ui");
        UiConfiguration ui = new UiConfiguration(
            uiSection == null || uiSection.getBoolean("register-ui-on-enable", true),
            uiSection != null && uiSection.getBoolean("overwrite-ui-files", false)
        );
        ExtraBackpackConfig extraBackpack =
            loadExtraBackpack(configuration, logger);
        return new ExtraBackpackConfiguration(ui, extraBackpack);
    }

    private static ExtraBackpackConfig loadExtraBackpack(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return ExtraBackpackConfig.DISABLED;
        }
        boolean enabled = section.getBoolean("enabled", false);
        LinkedHashMap<String, ExtraBackpackCategory> categories = new LinkedHashMap<>();
        ConfigurationSection categoriesSection = section.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String rawId : categoriesSection.getKeys(false)) {
                ConfigurationSection child = categoriesSection.getConfigurationSection(rawId);
                if (child == null) {
                    continue;
                }
                String id = normalizeId(rawId);
                // 该页网格显示上限：共通存储下页签只是过滤器，建议 >= 仓库 max-slots
                int max = Math.max(1, child.getInt("slots.max", 999));
                String path = readString(child, "match.nbt.path", readString(child, "nbt.path", ""));
                List<String> values = normalizeStringList(child.getStringList("match.nbt.values"));
                if (values.isEmpty()) {
                    values = normalizeStringList(child.getStringList("nbt.values"));
                }
                List<String> materialIds = normalizeStringList(child.getStringList("material-ids"));
                List<String> materialPrefixes = normalizeStringList(child.getStringList("material-prefixes"));
                boolean fallback = child.getBoolean("default", false);
                categories.put(id, new ExtraBackpackCategory(
                    id,
                    readString(child, "display-name", rawId),
                    child.getInt("priority", categories.size()),
                    max,
                    path,
                    values,
                    materialIds,
                    materialPrefixes,
                    fallback
                ));
            }
        }
        return new ExtraBackpackConfig(enabled, immutableCopy(categories));
    }

    private static String readString(ConfigurationSection section, String path, String defaultValue) {
        String value = section.getString(path, defaultValue);
        return value == null ? defaultValue : value.trim();
    }

    private static List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = normalizeId(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeId(String rawValue) {
        return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * UI 相关配置 record。
     *
     * @param registerUiOnEnable 启用时是否自动注册 UI 资源
     * @param overwriteUiFiles   启用时是否覆盖已存在的 UI 文件
     */
    public record UiConfiguration(
        boolean registerUiOnEnable,
        boolean overwriteUiFiles
    ) {
    }

    public record ExtraBackpackConfig(boolean enabled, Map<String, ExtraBackpackCategory> categories) {
        public static final ExtraBackpackConfig DISABLED = new ExtraBackpackConfig(false, Map.of());
    }

    public record ExtraBackpackCategory(
        String id,
        String displayName,
        int priority,
        int maxSlots,
        String nbtPath,
        List<String> values,
        List<String> materialIds,
        List<String> materialPrefixes,
        boolean fallback
    ) {
    }
}
