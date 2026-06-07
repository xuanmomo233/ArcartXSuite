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
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfigs;

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
            ChatPersistenceDialect.parse(storageSection == null ? null : storageSection.getString("mode", "sqlite")),
            storageSection == null ? "chat.db" : string(storageSection.getString("sqlite.file", "chat.db")),
            storageSection == null ? "127.0.0.1" : string(storageSection.getString("mysql.host", "127.0.0.1")),
            storageSection == null ? 3306 : Math.max(1, storageSection.getInt("mysql.port", 3306)),
            storageSection == null ? "arcartxsuite" : string(storageSection.getString("mysql.database", "arcartxsuite")),
            storageSection == null ? "root" : string(storageSection.getString("mysql.username", "root")),
            storageSection == null ? "" : string(storageSection.getString("mysql.password", "")),
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
