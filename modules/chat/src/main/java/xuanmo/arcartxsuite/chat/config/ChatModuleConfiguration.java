package xuanmo.arcartxsuite.chat.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfigs;

/**
 * 聊天模块顶层配置，聚合存储、跨服、卡片、功能、过滤器与频道定义等全部子配置。
 * <p>
 * 通过 {@link #load} 从 {@code ArcartXChat.yml} 主配置文件及频道目录加载，
 * 供 {@link ChatService} 在运行时读取。
 *
 * @param debug                 是否开启调试日志
 * @param serverId              当前服务器节点 ID
 * @param defaultChannelId      默认频道 ID
 * @param maxLength             单条消息最大长度
 * @param cooldownMillis        发消息冷却时间（毫秒）
 * @param duplicateWindowMillis 重复消息判定窗口（毫秒）
 * @param forceChatTakeover     是否强制接管其他聊天插件
 * @param storage               存储配置
 * @param crossServer           跨服通道配置
 * @param cards                 聊天卡片渲染配置
 * @param functions             聊天功能开关配置
 * @param filter                聊天过滤器配置
 * @param channelsDirectory     频道定义文件相对目录
 * @param channels              频道定义映射（key 为频道 ID，不可变）
 */
public record ChatModuleConfiguration(
    boolean debug,
    String serverId,
    String defaultChannelId,
    int maxLength,
    long cooldownMillis,
    long duplicateWindowMillis,
    boolean forceChatTakeover,
    ChatStorageConfiguration storage,
    CrossServerChannelConfig crossServer,
    ChatCardConfiguration cards,
    ChatFunctionConfiguration functions,
    ChatFilterConfiguration filter,
    String channelsDirectory,
    Map<String, ChatChannelDefinition> channels
) {

    /**
     * 从主配置文件和频道目录加载完整的聊天模块配置。
     *
     * @param configuration     主配置 yml
     * @param channelsDirectory 频道定义文件目录
     * @param logger            日志记录器，用于输出加载警告
     * @return 加载后的模块配置实例
     */
    public static ChatModuleConfiguration load(FileConfiguration configuration, File channelsDirectory, Logger logger) {
        boolean debug = configuration.getBoolean("settings.debug", false);
        String serverId = string(configuration.getString("settings.server-id", "default"));
        String defaultChannelId = normalizeId(configuration.getString("settings.default-channel", "normal"));
        int maxLength = Math.max(1, configuration.getInt("settings.max-length", 256));
        long cooldownMillis = Math.max(0L, configuration.getLong("settings.cooldown-millis", 750L));
        long duplicateWindowMillis = Math.max(0L, configuration.getLong("settings.duplicate-window-millis", 4000L));
        boolean forceChatTakeover = configuration.getBoolean("compatibility.other-chat-plugins.force-takeover", false);

        ConfigurationSection storageSection = configuration.getConfigurationSection("storage");
        ChatStorageConfiguration storage = new ChatStorageConfiguration(
            storageSection == null ? "sqlite" : storageSection.getString("mode", "sqlite"),
            storageSection == null ? "chat.db" : string(storageSection.getString("sqlite.file", "chat.db")),
            storageSection == null ? "127.0.0.1" : string(storageSection.getString("mysql.host", "127.0.0.1")),
            storageSection == null ? 3306 : Math.max(1, storageSection.getInt("mysql.port", 3306)),
            storageSection == null ? "arcartxsuite" : string(storageSection.getString("mysql.database", "arcartxsuite")),
            storageSection == null ? "root" : string(storageSection.getString("mysql.username", "root")),
            storageSection == null ? "" : string(storageSection.getString("mysql.password", "")),
            storageSection == null ? "axs_chat_" : string(storageSection.getString("mysql.table-prefix", "axs_chat_")),
            storageSection == null ? 4 : Math.max(1, storageSection.getInt("pool-size", 4))
        );

        CrossServerChannelConfig crossServer = CrossServerChannelConfigs.fromSection(
            configuration.getConfigurationSection("cross-server")
        );

        ConfigurationSection cardSection = configuration.getConfigurationSection("cards");
        ChatCardConfiguration cards = new ChatCardConfiguration(
            cardSection == null ? "" : string(cardSection.getString("mention-card-id", "")),
            cardSection == null ? "" : string(cardSection.getString("private-card-id", "")),
            cardSection == null ? "" : string(cardSection.getString("system-card-id", "")),
            cardSection == null ? "" : string(cardSection.getString("item-preview-card-id", "")),
            cardSection == null ? 26 : cardSection.getInt("char-width-full", 26),
            cardSection == null ? 14 : cardSection.getInt("char-width-half", 14),
            cardSection == null ? 45 : cardSection.getInt("line-height", 45),
            cardSection == null ? 320 : cardSection.getInt("max-line-width", 320),
            cardSection == null ? 160 : cardSection.getInt("text-offset-x", 160),
            cardSection == null ? 20 : cardSection.getInt("pad-right", 20),
            cardSection == null ? 100 : cardSection.getInt("base-height", 100),
            cardSection == null ? 300 : cardSection.getInt("min-width", 300)
        );

        ConfigurationSection functionSection = configuration.getConfigurationSection("function");
        List<ChatCustomComponent> customComponents = loadCustomComponents(functionSection == null ? null : functionSection.getConfigurationSection("custom-components"), logger);
        ChatFunctionConfiguration functions = new ChatFunctionConfiguration(
            functionSection == null || functionSection.getBoolean("mention.enabled", true),
            functionSection == null || functionSection.getBoolean("mention.allow-all", true),
            functionSection == null || functionSection.getBoolean("item.enabled", true),
            functionSection == null ? "[item]" : string(functionSection.getString("item.token", "[item]")),
            functionSection == null ? "&b[展示物品]" : string(functionSection.getString("item.format", "&b[展示物品]")),
            functionSection == null ? "&c[物品不可用]" : string(functionSection.getString("item.failed-format", "&c[物品不可用]")),
            List.copyOf(customComponents)
        );

        ConfigurationSection filterSection = configuration.getConfigurationSection("filter");
        ChatFilterConfiguration filter = new ChatFilterConfiguration(
            filterSection != null && filterSection.getBoolean("enabled", false),
            filterSection != null && filterSection.getBoolean("cancel-on-match", false),
            filterSection == null ? "*" : string(filterSection.getString("replacement", "*")),
            lowerList(filterSection == null ? List.of() : filterSection.getStringList("blocked-words")),
            compilePatterns(filterSection == null ? List.of() : filterSection.getStringList("blocked-patterns"), logger, "filter.blocked-patterns"),
            filterSection != null && filterSection.getBoolean("cloud.enabled", false),
            filterSection == null ? "" : string(filterSection.getString("cloud.url", "")),
            filterSection == null ? 60 : Math.max(1, filterSection.getInt("cloud.refresh-minutes", 60))
        );

        String relativeChannelsDirectory = string(configuration.getString("channels-directory", "chat/channels"));

        Map<String, ChatChannelDefinition> channels = loadChannels(channelsDirectory, logger);
        if (!channels.containsKey(defaultChannelId)) {
            defaultChannelId = channels.isEmpty() ? "normal" : channels.keySet().iterator().next();
        }

        return new ChatModuleConfiguration(
            debug,
            serverId.isBlank() ? "default" : serverId,
            defaultChannelId,
            maxLength,
            cooldownMillis,
            duplicateWindowMillis,
            forceChatTakeover,
            storage,
            crossServer,
            cards,
            functions,
            filter,
            relativeChannelsDirectory,
            Collections.unmodifiableMap(new LinkedHashMap<>(channels))
        );
    }

    /**
     * 按频道 ID 查询频道定义。
     *
     * @param channelId 频道 ID（不区分大小写）
     * @return 频道定义，不存在时返回 {@code null}
     */
    public ChatChannelDefinition channel(String channelId) {
        if (channelId == null) {
            return null;
        }
        return channels.get(channelId.trim().toLowerCase(Locale.ROOT));
    }

    private static Map<String, ChatChannelDefinition> loadChannels(File directory, Logger logger) {
        LinkedHashMap<String, ChatChannelDefinition> channels = new LinkedHashMap<>();
        if (directory == null || !directory.exists()) {
            return channels;
        }

        File[] files = directory.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return channels;
        }

        java.util.Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            String fileName = file.getName();
            String id = normalizeId(fileName.substring(0, fileName.length() - 4));
            ChatChannelDefinition definition = ChatChannelDefinition.load(id, YamlConfiguration.loadConfiguration(file));
            if (!definition.enabled()) {
                continue;
            }
            channels.put(definition.id(), definition);
        }
        if (channels.isEmpty()) {
            logger.warning("ArcartXChat 未加载到任何频道定义。");
        }
        return channels;
    }

    private static List<ChatCustomComponent> loadCustomComponents(ConfigurationSection section, Logger logger) {
        List<ChatCustomComponent> components = new ArrayList<>();
        if (section == null) {
            return components;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null || !child.getBoolean("enabled", true)) {
                continue;
            }
            String patternText = string(child.getString("pattern", ""));
            if (patternText.isBlank()) {
                continue;
            }
            try {
                components.add(new ChatCustomComponent(
                    normalizeId(rawId),
                    Pattern.compile(patternText),
                    string(child.getString("replacement", ""))
                ));
            } catch (PatternSyntaxException exception) {
                logger.warning("ArcartXChat 自定义组件 '" + rawId + "' 正则非法，已跳过: " + patternText);
            }
        }
        return components;
    }

    private static List<String> lowerList(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = string(value).toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static List<Pattern> compilePatterns(List<String> values, Logger logger, String path) {
        List<Pattern> result = new ArrayList<>();
        for (String value : values) {
            String normalized = string(value);
            if (normalized.isBlank()) {
                continue;
            }
            try {
                result.add(Pattern.compile(normalized, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException exception) {
                logger.warning("ArcartXChat 配置 '" + path + "' 包含非法正则，已跳过: " + normalized);
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeId(String rawValue) {
        return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
    }

    private static String string(String value) {
        return value == null ? "" : value.trim();
    }
}
