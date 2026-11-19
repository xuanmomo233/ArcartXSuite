package xuanmo.arcartxsuite.title.placeholder;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.title.config.TitleDefinition;
import xuanmo.arcartxsuite.title.config.TitleGroupDefinition;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.config.TitleQualityDefinition;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;
import xuanmo.arcartxsuite.title.service.TitleService;
import xuanmo.arcartxsuite.title.service.TitleTextFormats;

/**
 * 称号模块的 PlaceholderAPI 变量扩展。
 * <p>
 * 注册 {@code %axstitle_*%} 系列占位符，提供拥有数量、隐藏数量、
 * 展示称号信息、分组装备信息、属性值、套装完成度等数据查询。
 * 属于 title 模块占位符层。
 */
public final class TitlePlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<TitleService> serviceProvider;
    private final Supplier<TitleModuleConfiguration> configurationProvider;

    /**
     * @param plugin               插件实例
     * @param serviceProvider      称号服务提供者
     * @param configurationProvider 模块配置提供者
     */
    public TitlePlaceholderExpansion(
        JavaPlugin plugin,
        Supplier<TitleService> serviceProvider,
        Supplier<TitleModuleConfiguration> configurationProvider
    ) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
        this.configurationProvider = configurationProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axstitle";
    }

    @Override
    public @NotNull String getAuthor() {
        return "墨墨墨";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    /**
     * 处理占位符请求。
     *
     * @param offlinePlayer 离线玩家
     * @param params        占位符参数
     * @return 占位符值；无法识别时返回 {@code null}
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }
        var playerId = offlinePlayer.getUniqueId();
        if (playerId == null) return "";

        var service = serviceProvider.get();
        TitleModuleConfiguration configuration = configurationProvider.get();
        if (service == null || configuration == null) {
            return "";
        }

        String normalized = params.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("owned_") && !normalized.equals("owned_count")) {
            String titleId = normalized.substring("owned_".length());
            PlayerTitleState state = service.getCachedState(playerId);
            return Boolean.toString(state != null && state.hasOwnedTitle(titleId));
        }
        if (normalized.startsWith("hidden_") && !normalized.equals("hidden_count")) {
            String titleId = normalized.substring("hidden_".length());
            PlayerOwnedTitle ownedTitle = service.getOwnedTitle(playerId, titleId);
            return Boolean.toString(ownedTitle != null && ownedTitle.hidden());
        }
        if (normalized.startsWith("remaining_")) {
            String titleId = normalized.substring("remaining_".length());
            PlayerOwnedTitle ownedTitle = service.getOwnedTitle(playerId, titleId);
            if (ownedTitle == null) {
                return "";
            }
            if (ownedTitle.expiresAt() == null) {
                return "永久";
            }
            return Long.toString(Math.max(0L, ownedTitle.remainingMillis(Instant.now())));
        }
        if (normalized.startsWith("activates_")) {
            String titleId = normalized.substring("activates_".length());
            PlayerOwnedTitle ownedTitle = service.getOwnedTitle(playerId, titleId);
            if (ownedTitle == null) {
                return "";
            }
            if (ownedTitle.activatesAt() == null) {
                return "";
            }
            return Long.toString(ownedTitle.activatesAt().toEpochMilli());
        }
        if (normalized.startsWith("effective_")) {
            String titleId = normalized.substring("effective_".length());
            PlayerOwnedTitle ownedTitle = service.getOwnedTitle(playerId, titleId);
            return Boolean.toString(ownedTitle != null && ownedTitle.isEffective(Instant.now()));
        }
        if (normalized.startsWith("set_") && normalized.endsWith("_total")
            && !normalized.startsWith("set_bonus_attr_")) {
            var set = configuration.set(setId(normalized, "_total"));
            return set == null ? "0" : Integer.toString(set.requiredTitleIds().size());
        }

        ResolvedTitleState resolvedState = service.resolveState(playerId);
        switch (normalized) {
            case "owned_count":
                return Integer.toString(resolvedState.ownedCount());
            case "hidden_count":
                return Integer.toString(resolvedState.hiddenCount());
            default:
                break;
        }
        String displayValue = displayPlaceholderValue(configuration, resolvedState, normalized);
        if (displayValue != null) return displayValue;
        String groupValue = groupPlaceholderValue(configuration, resolvedState, normalized);
        if (groupValue != null) return groupValue;

        if (normalized.startsWith("display_attr_")) {
            return attributeValue(resolvedState.displayAttributes(), normalized.substring("display_attr_".length()));
        }
        if (normalized.startsWith("collection_attr_")) {
            return attributeValue(resolvedState.collectionAttributes(), normalized.substring("collection_attr_".length()));
        }
        if (normalized.startsWith("total_attr_")) {
            return attributeValue(resolvedState.totalAttributes(), normalized.substring("total_attr_".length()));
        }
        if (normalized.startsWith("set_bonus_attr_")) {
            return attributeValue(resolvedState.setBonusAttributes(), normalized.substring("set_bonus_attr_".length()));
        }
        return setPlaceholderValue(resolvedState, normalized);
    }

    static String groupPlaceholderValue(
        TitleModuleConfiguration configuration,
        ResolvedTitleState resolvedState,
        String normalized
    ) {
        if (normalized.startsWith("chat_") && normalized.endsWith("_prefix")) {
            TitleDefinition title = equippedTitleForGroup(resolvedState, normalized, "chat_", "_prefix");
            return title == null || title.chatPrefix() == null ? "" : title.chatPrefix();
        }
        if (normalized.startsWith("chat_") && normalized.endsWith("_suffix")) {
            TitleDefinition title = equippedTitleForGroup(resolvedState, normalized, "chat_", "_suffix");
            return title == null || title.chatSuffix() == null ? "" : title.chatSuffix();
        }
        if (normalized.startsWith("tab_") && normalized.endsWith("_prefix")) {
            TitleDefinition title = equippedTitleForGroup(resolvedState, normalized, "tab_", "_prefix");
            return title == null || title.tabPrefix() == null ? "" : title.tabPrefix();
        }
        if (normalized.startsWith("tab_") && normalized.endsWith("_suffix")) {
            TitleDefinition title = equippedTitleForGroup(resolvedState, normalized, "tab_", "_suffix");
            return title == null || title.tabSuffix() == null ? "" : title.tabSuffix();
        }
        if (!normalized.startsWith("equipped_")) {
            return null;
        }
        if (normalized.endsWith("_id")) {
            TitleDefinition title = equippedTitleForGroup(resolvedState, normalized, "equipped_", "_id");
            return title == null ? "" : title.id();
        }
        if (normalized.endsWith("_name")) {
            TitleDefinition title = equippedTitleForGroup(resolvedState, normalized, "equipped_", "_name");
            return title == null ? "" : title.displayName();
        }
        if (normalized.endsWith("_group")) {
            TitleDefinition title = equippedTitleForGroup(resolvedState, normalized, "equipped_", "_group");
            return nameOfGroup(configuration, title);
        }
        if (normalized.endsWith("_quality")) {
            TitleDefinition title = equippedTitleForGroup(resolvedState, normalized, "equipped_", "_quality");
            return nameOfQuality(configuration, title);
        }
        return null;
    }

    private static TitleDefinition equippedTitleForGroup(
        ResolvedTitleState resolvedState,
        String normalized,
        String prefix,
        String suffix
    ) {
        if (normalized.length() <= prefix.length() + suffix.length()) {
            return null;
        }
        String groupId = normalized.substring(prefix.length(), normalized.length() - suffix.length());
        if (groupId.isBlank()) {
            return null;
        }
        return resolvedState.equippedTitlesByGroup().get(groupId);
    }

    private static String nameOfGroup(TitleModuleConfiguration configuration, TitleDefinition equippedTitle) {
        if (equippedTitle == null) {
            return "";
        }
        TitleGroupDefinition definition = configuration.group(equippedTitle.groupId());
        return definition == null ? "" : definition.name();
    }

    private static String nameOfQuality(TitleModuleConfiguration configuration, TitleDefinition equippedTitle) {
        if (equippedTitle == null) {
            return "";
        }
        TitleQualityDefinition definition = configuration.quality(equippedTitle.qualityId());
        return definition == null ? "" : definition.name();
    }

    private static String setPlaceholderValue(ResolvedTitleState resolvedState, String normalized) {
        if (normalized.startsWith("set_") && normalized.endsWith("_completion")) {
            String setId = setId(normalized, "_completion");
            Integer count = resolvedState.setCompletionCounts().get(setId);
            return count == null ? "0" : Integer.toString(count);
        }
        if (normalized.startsWith("set_") && normalized.endsWith("_active")) {
            String setId = setId(normalized, "_active");
            Boolean active = resolvedState.setActiveMap().get(setId);
            return Boolean.toString(active != null && active);
        }
        return null;
    }

    private static String setId(String parameter, String suffix) {
        int end = parameter.length() - suffix.length();
        return end <= "set_".length() ? "" : parameter.substring("set_".length(), end);
    }

    static String displayPlaceholderValue(
        TitleModuleConfiguration configuration,
        ResolvedTitleState resolvedState,
        String normalized
    ) {
        return switch (normalized) {
            case "display", "display_name" -> displayTitleField(configuration, resolvedState, TitleDefinition::displayName);
            case "display_chat_prefix" -> displayTitleField(configuration, resolvedState, TitleDefinition::chatPrefix);
            case "display_chat_suffix" -> displayTitleField(configuration, resolvedState, TitleDefinition::chatSuffix);
            case "display_tab_prefix" -> displayTitleField(configuration, resolvedState, TitleDefinition::tabPrefix);
            case "display_tab_suffix" -> displayTitleField(configuration, resolvedState, TitleDefinition::tabSuffix);
            default -> null;
        };
    }

    private static String displayTitleField(
        TitleModuleConfiguration configuration,
        ResolvedTitleState resolvedState,
        Function<TitleDefinition, String> fieldExtractor
    ) {
        TitleDefinition displayTitle = resolvedState.totalDisplayTitle();
        if (displayTitle == null) {
            return configuration.displayTitle().emptyText();
        }
        String value = fieldExtractor.apply(displayTitle);
        return value == null ? "" : value;
    }

    private static String attributeValue(Map<String, Double> values, String key) {
        if (values == null || values.isEmpty()) {
            return "0";
        }
        Double value = values.get(key);
        return value == null ? "0" : TitleTextFormats.formatNumber(value);
    }
}
