package xuanmo.arcartxsuite.mail.config;

import java.util.ArrayList;
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

/**
 * 邮件模块顶层配置，聚合存储、跨服、UI、玩家发送、审核、保留策略与预设目录等子配置。
 * <p>
 * 关联模块：mail。通过 {@link #load(FileConfiguration, Logger)} 从 {@code ArcartXMail.yml} 解析。
 *
 * @param debug           是否开启调试日志
 * @param storage         存储配置
 * @param crossServer     跨服通道配置
 * @param ui              UI 配置
 * @param playerSend      玩家发送配置
 * @param moderation      审核配置
 * @param retention       保留策略配置
 * @param cdk             CDK 兑换安全配置
 * @param presetsDirectory 预设邮件模板目录名
 */
public record MailModuleConfiguration(
    boolean debug,
    MailStorageConfiguration storage,
    CrossServerChannelConfig crossServer,
    MailUiConfiguration ui,
    MailPlayerSendConfiguration playerSend,
    MailModerationConfiguration moderation,
    MailRetentionConfiguration retention,
    MailCdkConfiguration cdk,
    String presetsDirectory
) {

    /**
     * 从 Bukkit {@link FileConfiguration} 加载并组装邮件模块完整配置。
     *
     * @param configuration Bukkit 配置对象
     * @param logger        日志器，用于记录正则编译警告
     * @return 解析后的模块配置
     */
    public static MailModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        boolean debug = configuration.getBoolean("settings.debug", false);

        MailStorageConfiguration storage = MailStorageConfiguration.load(configuration.getConfigurationSection("storage"));

        CrossServerChannelConfig crossServer = CrossServerChannelConfigs.fromSection(
            configuration.getConfigurationSection("cross-server")
        );

        ConfigurationSection uiSection = configuration.getConfigurationSection("ui");
        MailUiConfiguration ui = new MailUiConfiguration(
            uiSection == null ? "AXS:mail_inbox" : string(uiSection.getString("inbox-ui-id", "AXS:mail_inbox")),
            uiSection == null ? "AXS:mail_compose" : string(uiSection.getString("compose-ui-id", "AXS:mail_compose")),
            uiSection == null ? "AXS:mail_logs" : string(uiSection.getString("logs-ui-id", "AXS:mail_logs")),
            uiSection == null ? "AXS:mail_admin" : string(uiSection.getString("admin-ui-id", "AXS:mail_admin")),
            uiSection == null || uiSection.getBoolean("register-ui-on-enable", true),
            uiSection != null && uiSection.getBoolean("overwrite-ui-files", false),
            uiSection == null ? "AXS Mail Compose" : string(uiSection.getString("compose-inventory-title", "AXS Mail Compose")),
            uiSection == null ? "axs_mail_notify" : string(uiSection.getString("notify-card-id", "axs_mail_notify")),
            uiSection == null ? 26 : uiSection.getInt("notify-char-width-full", 26),
            uiSection == null ? 14 : uiSection.getInt("notify-char-width-half", 14),
            uiSection == null ? 320 : uiSection.getInt("notify-max-line-width", 320),
            uiSection == null ? 40 : Math.max(1, uiSection.getInt("inbox-preview-max-chars", 40))
        );

        ConfigurationSection playerSendSection = configuration.getConfigurationSection("player-send");
        Map<String, Double> attachmentTaxRates = loadTaxRates(playerSendSection == null ? null : playerSendSection.getConfigurationSection("attachment-tax-rates"));
        MailPlayerSendConfiguration playerSend = new MailPlayerSendConfiguration(
            playerSendSection == null || playerSendSection.getBoolean("enabled", true),
            playerSendSection == null || playerSendSection.getBoolean("require-permission", true),
            playerSendSection == null ? 120 : Math.max(0, playerSendSection.getInt("cooldown-seconds", 120)),
            playerSendSection == null ? 0.0D : Math.max(0.0D, playerSendSection.getDouble("base-fee", 0.0D)),
            playerSendSection == null ? 0.0D : Math.max(0.0D, playerSendSection.getDouble("item-fee", 0.0D)),
            playerSendSection == null ? "money" : lower(playerSendSection.getString("fee-currency", "money")),
            attachmentTaxRates,
            playerSendSection != null && playerSendSection.getBoolean("allow-self-send", false),
            playerSendSection == null || playerSendSection.getBoolean("allow-offline-send", true),
            playerSendSection == null || playerSendSection.getBoolean("allow-vault-attachment", true),
            playerSendSection == null ? 5 : Math.max(1, playerSendSection.getInt("max-attachments", 5)),
            playerSendSection == null ? 48 : Math.max(1, playerSendSection.getInt("subject-max-length", 48)),
            playerSendSection == null ? 400 : Math.max(1, playerSendSection.getInt("body-max-length", 400))
        );

        ConfigurationSection moderationSection = configuration.getConfigurationSection("moderation");
        MailModerationConfiguration moderation = new MailModerationConfiguration(
            lowerList(moderationSection == null ? List.of() : moderationSection.getStringList("blocked-words")),
            compilePatterns(moderationSection == null ? List.of() : moderationSection.getStringList("blocked-patterns"), logger, "moderation.blocked-patterns"),
            upperList(moderationSection == null ? List.of() : moderationSection.getStringList("blocked-materials")),
            compilePatterns(moderationSection == null ? List.of() : moderationSection.getStringList("blocked-lore-patterns"), logger, "moderation.blocked-lore-patterns"),
            compilePatterns(moderationSection == null ? List.of() : moderationSection.getStringList("blocked-displayname-patterns"), logger, "moderation.blocked-displayname-patterns"),
            lowerList(moderationSection == null ? List.of() : moderationSection.getStringList("blocked-nbt-keys")),
            loadNbtValues(moderationSection == null ? null : moderationSection.getConfigurationSection("blocked-nbt-values"))
        );

        ConfigurationSection retentionSection = configuration.getConfigurationSection("retention");
        MailRetentionConfiguration retention = new MailRetentionConfiguration(
            retentionSection == null ? 1200L : Math.max(20L, retentionSection.getLong("cleanup-interval-ticks", 1200L)),
            retentionSection == null ? 15 : Math.max(1, retentionSection.getInt("default-expire-after-days", 15)),
            retentionSection == null ? 7 : Math.max(0, retentionSection.getInt("claimed-retention-days", 7)),
            retentionSection == null ? 7 : Math.max(0, retentionSection.getInt("deleted-retention-days", 7)),
            retentionSection != null && retentionSection.getBoolean("allow-delete-with-unclaimed-attachments", false)
        );

        ConfigurationSection presetsSection = configuration.getConfigurationSection("presets");
        String presetsDirectory = presetsSection == null ? "presets" : string(presetsSection.getString("directory", "presets"));

        ConfigurationSection cdkSection = configuration.getConfigurationSection("cdk");
        MailCdkConfiguration cdk = new MailCdkConfiguration(
            cdkSection != null && cdkSection.getBoolean("require-permission", false),
            cdkSection == null ? 0 : Math.max(0, cdkSection.getInt("cooldown-seconds", 0)),
            cdkSection == null ? 0 : Math.max(0, cdkSection.getInt("max-fail-attempts", 0)),
            cdkSection == null ? 5 : Math.max(1, cdkSection.getInt("lockout-minutes", 5))
        );

        return new MailModuleConfiguration(debug, storage, crossServer, ui, playerSend, moderation, retention, cdk, presetsDirectory);
    }

    private static String string(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lower(String value) {
        return string(value).toLowerCase(Locale.ROOT);
    }

    private static List<String> lowerList(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(result);
    }

    private static List<String> upperList(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(value.trim().toUpperCase(Locale.ROOT));
        }
        return List.copyOf(result);
    }

    private static List<Pattern> compilePatterns(List<String> rawValues, Logger logger, String path) {
        List<Pattern> result = new ArrayList<>();
        for (String rawValue : rawValues) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            try {
                result.add(Pattern.compile(rawValue, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException exception) {
                logger.warning("ArcartXMail 配置 '" + path + "' 包含非法正则，已跳过: " + rawValue);
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, String> loadNbtValues(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                result.put(key.trim().toLowerCase(Locale.ROOT), value.trim());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, Double> loadTaxRates(ConfigurationSection section) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        if (section != null) {
            for (String rawId : section.getKeys(false)) {
                values.put(lower(rawId), Math.max(0.0D, section.getDouble(rawId, 0.0D)));
            }
        }
        if (!values.containsKey("money")) {
            values.put("money", 0.0D);
        }
        return Map.copyOf(values);
    }
}
