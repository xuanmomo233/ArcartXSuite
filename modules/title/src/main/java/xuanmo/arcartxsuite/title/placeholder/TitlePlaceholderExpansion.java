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
import xuanmo.arcartxsuite.title.config.TitleDisplayConfiguration;
import xuanmo.arcartxsuite.title.config.TitleGroupDefinition;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.config.TitleQualityDefinition;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;
import xuanmo.arcartxsuite.title.service.TitleService;
import xuanmo.arcartxsuite.title.service.TitleTextFormats;

public final class TitlePlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<TitleService> serviceProvider;
    private final Supplier<TitleModuleConfiguration> configurationProvider;

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

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
            return "";
        }

        var service = serviceProvider.get();
        TitleModuleConfiguration configuration = configurationProvider.get();
        if (service == null || configuration == null) {
            return "";
        }

        String normalized = params.trim().toLowerCase(Locale.ROOT);
        ResolvedTitleState resolvedState = service.resolveState(offlinePlayer.getUniqueId());
        PlayerTitleState state = service.getCachedState(offlinePlayer.getUniqueId());
        switch (normalized) {
            case "owned_count":
                return Integer.toString(resolvedState.ownedCount());
            case "hidden_count":
                return Integer.toString(resolvedState.hiddenCount());
            default:
                break;
        }

        String displayValue = displayPlaceholderValue(configuration, resolvedState, normalized);
        if (displayValue != null) {
            return displayValue;
        }

        String groupPlaceholderValue = groupPlaceholderValue(configuration, resolvedState, normalized);
        if (groupPlaceholderValue != null) {
            return groupPlaceholderValue;
        }

        if (normalized.startsWith("owned_")) {
            String titleId = normalized.substring("owned_".length());
            return Boolean.toString(state != null && state.hasOwnedTitle(titleId));
        }
        if (normalized.startsWith("hidden_")) {
            String titleId = normalized.substring("hidden_".length());
            PlayerOwnedTitle ownedTitle = service.getOwnedTitle(offlinePlayer.getUniqueId(), titleId);
            return Boolean.toString(ownedTitle != null && ownedTitle.hidden());
        }
        if (normalized.startsWith("remaining_")) {
            String titleId = normalized.substring("remaining_".length());
            PlayerOwnedTitle ownedTitle = service.getOwnedTitle(offlinePlayer.getUniqueId(), titleId);
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
            PlayerOwnedTitle ownedTitle = service.getOwnedTitle(offlinePlayer.getUniqueId(), titleId);
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
            PlayerOwnedTitle ownedTitle = service.getOwnedTitle(offlinePlayer.getUniqueId(), titleId);
            return Boolean.toString(ownedTitle != null && ownedTitle.isEffective(Instant.now()));
        }
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
        String setPlaceholder = setPlaceholderValue(resolvedState, normalized);
        if (setPlaceholder != null) {
            return setPlaceholder;
        }
        return null;
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
            String setId = normalized.substring("set_".length(), normalized.length() - "_completion".length());
            Integer count = resolvedState.setCompletionCounts().get(setId);
            return count == null ? "0" : Integer.toString(count);
        }
        if (normalized.startsWith("set_") && normalized.endsWith("_total")) {
            String setId = normalized.substring("set_".length(), normalized.length() - "_total".length());
            return resolvedState.setCompletionCounts().containsKey(setId) ? Integer.toString(resolvedState.setCompletionCounts().size()) : "0";
        }
        if (normalized.startsWith("set_") && normalized.endsWith("_active")) {
            String setId = normalized.substring("set_".length(), normalized.length() - "_active".length());
            Boolean active = resolvedState.setActiveMap().get(setId);
            return Boolean.toString(active != null && active);
        }
        return null;
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
