package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossPlaceholderContext;

/**
 * 单个 Boss 的配置定义，属于 entitytracker 模块的 boss 追踪子系统。
 * <p>
 * 持有 MythicMob ID、启用状态、优先级、观察范围、标题/副标题格式、
 * 聊天卡片配置以及伤害排名设置，是 BossSession 的配置基础。
 *
 * @param mythicMobId     MythicMob 内部 ID
 * @param enabled         是否启用该 Boss 的追踪
 * @param priority        显示优先级（数值越大越优先）
 * @param viewerRange     观察者有效范围（方块），小于等于 0 时使用全局默认值
 * @param titleFormat     标题格式模板，支持占位符替换
 * @param subtitleFormat  副标题格式模板，支持占位符替换
 * @param spawnChatCard   生成时聊天卡片配置 ID
 * @param deathChatCard   死亡时聊天卡片配置 ID
 * @param despawnChatCard 消失时聊天卡片配置 ID
 * @param damageRanking   伤害排名设置
 */
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

    /**
     * 从配置节解析 Boss 定义。
     *
     * @param mythicMobId  MythicMob ID
     * @param section      配置节
     * @param defaultRange 全局默认观察范围
     * @return 解析得到的 Boss 定义
     */
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

    /**
     * 返回有效观察范围，自身范围无效时使用回退值。
     *
     * @param fallback 回退范围
     * @return 有效观察范围
     */
    public double effectiveRange(double fallback) {
        return viewerRange > 0 ? viewerRange : fallback;
    }

    /**
     * 使用占位符上下文渲染标题文本。
     *
     * @param context 占位符上下文
     * @return 渲染后的标题文本（已转换颜色代码）
     */
    public String renderTitle(BossPlaceholderContext context) {
        return applyFormat(titleFormat, context);
    }

    /**
     * 使用占位符上下文渲染副标题文本。
     *
     * @param context 占位符上下文
     * @return 渲染后的副标题文本（已转换颜色代码）
     */
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

