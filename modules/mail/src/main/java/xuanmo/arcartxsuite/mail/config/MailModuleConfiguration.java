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
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;

public record MailModuleConfiguration(
    boolean debug,
    MailStorageConfiguration storage,
    CrossServerChannelConfig crossServer,
    MailUiConfiguration ui,
    Map<String, CurrencyDefinition> currencies,
    MailPlayerSendConfiguration playerSend,
    MailModerationConfiguration moderation,
    MailRetentionConfiguration retention,
    String presetsDirectory
) {

    public static MailModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        boolean debug = configuration.getBoolean("settings.debug", false);

        ConfigurationSection storageSection = configuration.getConfigurationSection("storage");
        MailStorageConfiguration storage = new MailStorageConfiguration(
            MailPersistenceDialect.parse(storageSection == null ? null : storageSection.getString("mode", "sqlite")),
            storageSection == null ? "mail.db" : string(storageSection.getString("sqlite.file", "mail.db")),
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
            uiSection == null ? 45 : uiSection.getInt("notify-line-height", 45),
            uiSection == null ? 320 : uiSection.getInt("notify-max-line-width", 320),
            uiSection == null ? 160 : uiSection.getInt("notify-text-offset-x", 160),
            uiSection == null ? 20 : uiSection.getInt("notify-pad-right", 20),
            uiSection == null ? 100 : uiSection.getInt("notify-base-height", 100),
            uiSection == null ? 300 : uiSection.getInt("notify-min-width", 300)
        );

        Map<String, CurrencyDefinition> currencies = loadCurrencies(configuration.getConfigurationSection("currencies"));

        ConfigurationSection playerSendSection = configuration.getConfigurationSection("player-send");
        Map<String, Double> attachmentTaxRates = loadTaxRates(playerSendSection == null ? null : playerSendSection.getConfigurationSection("attachment-tax-rates"));
        if (playerSendSection != null && playerSendSection.contains("vault-tax-rate")) {
            LinkedHashMap<String, Double> adjustedRates = new LinkedHashMap<>(attachmentTaxRates);
            adjustedRates.put("money", Math.max(0.0D, playerSendSection.getDouble("vault-tax-rate", 0.0D)));
            attachmentTaxRates = Map.copyOf(adjustedRates);
        }
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
            compilePatterns(moderationSection == null ? List.of() : moderationSection.getStringList("blocked-lore-patterns"), logger, "moderation.blocked-lore-patterns")
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

        return new MailModuleConfiguration(debug, storage, crossServer, ui, currencies, playerSend, moderation, retention, presetsDirectory);
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

    private static Map<String, CurrencyDefinition> loadCurrencies(ConfigurationSection section) {
        LinkedHashMap<String, CurrencyDefinition> values = new LinkedHashMap<>();
        if (section == null) {
            values.put("money", new CurrencyDefinition("money", "vault", "金币", 2, "", "", "", "DOWN"));
            values.put("points", new CurrencyDefinition("points", "playerpoints", "点券", 0, "", "", "", "DOWN"));
            return Map.copyOf(values);
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null || !child.getBoolean("enabled", true)) {
                continue;
            }
            String id = lower(rawId);
            values.put(
                id,
                new CurrencyDefinition(
                    id,
                    lower(child.getString("provider", "vault")),
                    string(child.getString("display-name", rawId)),
                    Math.max(0, child.getInt("scale", 0)),
                    string(child.getString("balance-placeholder", "")),
                    string(child.getString("withdraw-command", "")),
                    string(child.getString("deposit-command", "")),
                    string(child.getString("rounding", "DOWN"))
                )
            );
        }
        values.putIfAbsent("money", new CurrencyDefinition("money", "vault", "金币", 2, "", "", "", "DOWN"));
        values.putIfAbsent("points", new CurrencyDefinition("points", "playerpoints", "点券", 0, "", "", "", "DOWN"));
        return Map.copyOf(values);
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
