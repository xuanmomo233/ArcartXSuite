package xuanmo.arcartxsuite.conversation.config;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import xuanmo.arcartxsuite.api.config.UiIdParser;

public record ConversationModuleConfiguration(
    DebugConfiguration debugConfig,
    ThemeConfiguration themeConfig,
    ClientConfiguration clientConfig,
    InteractionConfiguration interactionConfig,
    List<NpcAppearanceEntry> npcAppearances
) {

    private static final boolean DEFAULT_DEBUG_ENABLED = false;
    private static final String DEFAULT_THEME_NAME = "ArcartXConversation";
    private static final String DEFAULT_PACKET_ID = "AXS_CONVERSATION";
    private static final String DEFAULT_DIALOG_UI_ID = "AXS:conversation_menu";
    private static final String DEFAULT_SELECTOR_UI_ID = "AXS:conversation_selector_hud";
    private static final boolean DEFAULT_REGISTER_UI_ON_ENABLE = true;
    private static final boolean DEFAULT_OVERWRITE_UI_FILES = false;
    private static final boolean DEFAULT_INTERACTION_ENABLED = true;
    private static final double DEFAULT_SCAN_RANGE = 6.0D;
    private static final long DEFAULT_SCAN_PERIOD_TICKS = 10L;
    private static final long DEFAULT_SELECTOR_STICKY_MS = 1500L;
    private static final long DEFAULT_OPEN_COOLDOWN_MS = 350L;
    private static final long DEFAULT_REPLY_DEBOUNCE_MS = 250L;
    private static final long DEFAULT_SUPPRESS_REOPEN_MS = 500L;

    public static ConversationModuleConfiguration load(FileConfiguration configuration) {
        ConfigurationSection debugSection = configuration.getConfigurationSection("debug");
        DebugConfiguration debug = new DebugConfiguration(
            debugSection != null && debugSection.getBoolean("enabled", DEFAULT_DEBUG_ENABLED)
        );

        ConfigurationSection themeSection = configuration.getConfigurationSection("theme");
        ThemeConfiguration theme = new ThemeConfiguration(
            themeSection == null ? DEFAULT_THEME_NAME : string(themeSection.getString("name"), DEFAULT_THEME_NAME)
        );

        ConfigurationSection clientSection = configuration.getConfigurationSection("client");
        ClientConfiguration client = new ClientConfiguration(
            clientSection == null ? DEFAULT_PACKET_ID : string(clientSection.getString("packet-id"), DEFAULT_PACKET_ID),
            UiIdParser.readUiIds(clientSection, "dialog-ui-id", DEFAULT_DIALOG_UI_ID),
            UiIdParser.readUiIds(clientSection, "selector-ui-id", DEFAULT_SELECTOR_UI_ID),
            clientSection == null || clientSection.getBoolean("register-ui-on-enable", DEFAULT_REGISTER_UI_ON_ENABLE),
            clientSection != null && clientSection.getBoolean("overwrite-ui-files", DEFAULT_OVERWRITE_UI_FILES)
        );

        ConfigurationSection interactionSection = configuration.getConfigurationSection("interaction");
        InteractionConfiguration interaction = new InteractionConfiguration(
            interactionSection == null || interactionSection.getBoolean("enabled", DEFAULT_INTERACTION_ENABLED),
            readPositiveDouble(interactionSection, "scan-range", DEFAULT_SCAN_RANGE),
            readPositiveLong(interactionSection, "scan-period-ticks", DEFAULT_SCAN_PERIOD_TICKS),
            readNonNegativeLong(interactionSection, "selector-sticky-ms", DEFAULT_SELECTOR_STICKY_MS),
            readNonNegativeLong(interactionSection, "open-cooldown-ms", DEFAULT_OPEN_COOLDOWN_MS),
            readNonNegativeLong(interactionSection, "reply-debounce-ms", DEFAULT_REPLY_DEBOUNCE_MS),
            readNonNegativeLong(interactionSection, "suppress-reopen-ms", DEFAULT_SUPPRESS_REOPEN_MS)
        );

        List<NpcAppearanceEntry> npcAppearances = NpcAppearanceEntry.loadList(configuration);

        return new ConversationModuleConfiguration(debug, theme, client, interaction, npcAppearances);
    }

    public boolean debug() {
        return debugConfig.enabled();
    }

    public String themeName() {
        return themeConfig.name();
    }

    public String clientPacketId() {
        return clientConfig.packetId();
    }

    public String dialogUiId() {
        return clientConfig.dialogUiId();
    }

    public String uiId() {
        return dialogUiId();
    }

    public String selectorUiId() {
        return clientConfig.selectorUiId();
    }

    public boolean registerUiOnEnable() {
        return clientConfig.registerUiOnEnable();
    }

    public boolean selectorRegisterUiOnEnable() {
        return clientConfig.registerUiOnEnable();
    }

    public boolean overwriteUiFile() {
        return clientConfig.overwriteUiFiles();
    }

    public boolean selectorOverwriteUiFile() {
        return clientConfig.overwriteUiFiles();
    }

    public boolean interactionEnabled() {
        return interactionConfig.enabled();
    }

    public InteractionConfiguration interaction() {
        return interactionConfig;
    }


    private static String string(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static double readPositiveDouble(ConfigurationSection section, String path, double fallback) {
        if (section == null) {
            return fallback;
        }
        Object raw = section.get(path);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return value > 0.0D ? value : fallback;
        }
        if (raw instanceof String string) {
            try {
                double value = Double.parseDouble(string.trim());
                return value > 0.0D ? value : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long readPositiveLong(ConfigurationSection section, String path, long fallback) {
        if (section == null) {
            return fallback;
        }
        Object raw = section.get(path);
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value > 0L ? value : fallback;
        }
        if (raw instanceof String string) {
            try {
                long value = Long.parseLong(string.trim());
                return value > 0L ? value : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long readNonNegativeLong(ConfigurationSection section, String path, long fallback) {
        if (section == null) {
            return fallback;
        }
        Object raw = section.get(path);
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value >= 0L ? value : fallback;
        }
        if (raw instanceof String string) {
            try {
                long value = Long.parseLong(string.trim());
                return value >= 0L ? value : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public record DebugConfiguration(boolean enabled) {
    }

    public record ThemeConfiguration(String name) {
    }

    public record ClientConfiguration(
        String packetId,
        List<String> dialogUiIds,
        List<String> selectorUiIds,
        boolean registerUiOnEnable,
        boolean overwriteUiFiles
    ) {
        /** 向后兼容 */
        public String dialogUiId() { return dialogUiIds.isEmpty() ? "" : dialogUiIds.get(0); }
        public String selectorUiId() { return selectorUiIds.isEmpty() ? "" : selectorUiIds.get(0); }
    }

    public record InteractionConfiguration(
        boolean enabled,
        double scanRange,
        long scanPeriodTicks,
        long selectorStickyMs,
        long openCooldownMs,
        long replyDebounceMs,
        long suppressReopenMs
    ) {
    }

}
