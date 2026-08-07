package xuanmo.arcartxsuite.menu.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * 菜单定义加载器，从指定目录批量读取 YAML 文件并解析为 {@link MenuDefinition} 集合。
 * <p>
 * 支持多文档 YAML（以 {@code ---} 分隔），对解析失败的文档会记录警告并跳过，
 * 重复的菜单 ID 后者覆盖前者。
 */
public final class MenuDefinitionLoader {

    private MenuDefinitionLoader() {
    }

    /**
     * 加载目录下所有 YAML 文件并解析为菜单定义映射。
     *
     * @param directory           菜单配置目录
     * @param defaultLayout       默认布局类型
     * @param defaultColumns      默认列数
     * @param defaultButtonsPerPage 默认每页按钮数
     * @param logger              日志记录器
     * @return 菜单 ID 到菜单定义的有序映射
     * @throws IOException 读取文件失败时抛出
     */
    public static @NotNull Map<String, MenuDefinition> loadDirectory(
        @NotNull File directory,
        @NotNull MenuLayoutType defaultLayout,
        int defaultColumns,
        int defaultButtonsPerPage,
        @NotNull Logger logger
    ) throws IOException {
        Map<String, MenuDefinition> definitions = new LinkedHashMap<>();
        if (!directory.exists()) {
            directory.mkdirs();
            return definitions;
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return definitions;
        }
        for (File file : files) {
            loadFile(file, defaultLayout, defaultColumns, defaultButtonsPerPage, logger, definitions);
        }
        return definitions;
    }

    private static void loadFile(
        File file,
        MenuLayoutType defaultLayout,
        int defaultColumns,
        int defaultButtonsPerPage,
        Logger logger,
        Map<String, MenuDefinition> definitions
    ) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        List<String> documents = splitDocuments(content);
        for (int index = 0; index < documents.size(); index++) {
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.loadFromString(documents.get(index));
            } catch (Exception exception) {
                logger.warning("Menu 配置解析失败: " + file.getName() + " (#" + (index + 1) + ") -> " + exception.getMessage());
                continue;
            }
            try {
                MenuDefinition definition = MenuDefinition.load(
                    yaml,
                    file.getName() + "#" + (index + 1),
                    defaultLayout,
                    defaultColumns,
                    defaultButtonsPerPage
                );
                if (definitions.containsKey(definition.id())) {
                    logger.warning("Menu ID 重复，后者覆盖前者: " + definition.id() + " (" + file.getName() + ")");
                }
                definitions.put(definition.id(), definition);
            } catch (Exception exception) {
                logger.warning("Menu 定义无效: " + file.getName() + " (#" + (index + 1) + ") -> " + exception.getMessage());
            }
        }
    }

    /**
     * 将多文档 YAML 内容按 {@code ---} 分隔符拆分为多个文档字符串。
     *
     * @param content 原始文件内容
     * @return 拆分后的文档列表
     */
    static List<String> splitDocuments(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String normalized = content.replace("\r\n", "\n");
        String[] parts = normalized.split("\n---\n");
        List<String> documents = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                documents.add(part.trim());
            }
        }
        return documents.isEmpty() ? List.of(normalized.trim()) : documents;
    }
}
