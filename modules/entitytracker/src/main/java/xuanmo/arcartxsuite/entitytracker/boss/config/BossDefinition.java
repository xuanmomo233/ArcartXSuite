package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossPlaceholderContext;

public record BossDefinition(
    String mythicMobId,
    boolean enabled,
    int priority,
    double viewerRange,
    String titleFormat,
    String subtitleFormat,
    String spawnChatCard,
    String deathChatCard,
    String despawnChatCard,
    BossDamageRankingSettings damageRanking
) {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    public static BossDefinition from(String mythicMobId, ConfigurationSection section, double defaultRange) {
        return new BossDefinition(
            mythicMobId,
            section.getBoolean("enabled", true),
            section.getInt("priority", 0),
            section.getDouble("viewer-range", defaultRange),
            section.getString("title-format", "{display_name}"),
            section.getString("subtitle-format", "{mob_id}"),
            section.getString("spawn-chat-card", ""),
            section.getString("death-chat-card", ""),
            section.getString("despawn-chat-card", ""),
            BossDamageRankingSettings.from(section.getConfigurationSection("damage-ranking"))
        );
    }

    public double effectiveRange(double fallback) {
        return viewerRange > 0 ? viewerRange : fallback;
    }

    public String renderTitle(BossPlaceholderContext context) {
        return applyFormat(titleFormat, context);
    }

    public String renderSubtitle(BossPlaceholderContext context) {
        return applyFormat(subtitleFormat, context);
    }

    private String applyFormat(String template, BossPlaceholderContext context) {
        String safeTemplate = template == null ? "" : template;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(safeTemplate);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String placeholderKey = matcher.group(1);
            String resolved = context.resolve(placeholderKey);
            matcher.appendReplacement(
                rendered,
                Matcher.quoteReplacement(resolved == null ? matcher.group(0) : resolved)
            );
        }
        matcher.appendTail(rendered);
        return ChatColor.translateAlternateColorCodes('&', rendered.toString());
    }
}

