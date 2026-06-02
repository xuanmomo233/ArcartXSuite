package xuanmo.arcartxsuite.title.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import xuanmo.arcartxsuite.title.config.TitleDefinition;
import xuanmo.arcartxsuite.title.config.TitleDisplayConfiguration;
import xuanmo.arcartxsuite.title.config.TitleGroupDefinition;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.config.TitleQualityDefinition;
import xuanmo.arcartxsuite.title.config.TitleSetDefinition;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

public final class TitleMenuPacketFactory {

    private TitleMenuPacketFactory() {
    }

    public static Map<String, Object> build(
        TitleModuleConfiguration configuration,
        PlayerTitleState state,
        ResolvedTitleState resolvedState,
        String selectedTitleId,
        Instant now
    ) {
        TitleDefinition selectedDefinition = configuration.title(selectedTitleId);
        PlayerOwnedTitle selectedOwnedTitle = selectedTitleId.isBlank() ? null : state.ownedTitles().get(selectedTitleId);

        Map<String, Object> titles = new LinkedHashMap<>();
        for (TitleDefinition definition : configuration.orderedTitles()) {
            TitleGroupDefinition groupDefinition = configuration.group(definition.groupId());
            TitleQualityDefinition qualityDefinition = configuration.quality(definition.qualityId());
            PlayerOwnedTitle ownedTitle = state.ownedTitles().get(definition.id());

            Map<String, Object> titleData = new LinkedHashMap<>();
            titleData.put("id", definition.id());
            titleData.put("kind", definition.kind().configKey());
            titleData.put("display_name", definition.displayName());
            titleData.put("chat_prefix", definition.chatPrefix());
            titleData.put("chat_suffix", definition.chatSuffix());
            titleData.put("tab_prefix", definition.tabPrefix());
            titleData.put("tab_suffix", definition.tabSuffix());
            titleData.put("description", definition.description());
            titleData.put("source", definition.source());
            titleData.put("group_id", definition.groupId());
            titleData.put("group_name", groupDefinition == null ? definition.groupId() : groupDefinition.name());
            titleData.put("group_sort", groupDefinition == null ? Integer.MAX_VALUE : groupDefinition.sortOrder());
            titleData.put("quality_id", definition.qualityId());
            titleData.put("quality_name", qualityDefinition == null ? definition.qualityId() : qualityDefinition.name());
            titleData.put("quality_sort", qualityDefinition == null ? Integer.MAX_VALUE : qualityDefinition.sortOrder());
            titleData.put("sort_order", definition.sortOrder());
            titleData.put("owned", ownedTitle != null);
            titleData.put("hidden", ownedTitle != null && ownedTitle.hidden());
            titleData.put("equipped", definition.id().equals(resolvedState.equippedTitleIdsByGroup().get(definition.groupId())));
            titleData.put("selected", definition.id().equals(selectedTitleId));
            titleData.put("permanent", ownedTitle != null && ownedTitle.expiresAt() == null);
            titleData.put("remaining_text", ownedTitle == null ? "未拥有" : TitleTextFormats.formatRemaining(ownedTitle, now));
            titleData.put(
                "display_attributes_text",
                TitleTextFormats.formatAttributes(
                    definition.displayAttributes(),
                    TitleTextFormats.toSourceLines(definition.displayAttributes(), definition.displayAttributeLines())
                )
            );
            titleData.put(
                "collection_attributes_text",
                TitleTextFormats.formatAttributes(
                    definition.collectionAttributes(),
                    TitleTextFormats.toSourceLines(definition.collectionAttributes(), definition.collectionAttributeLines())
                )
            );
            titles.put(definition.id(), titleData);
        }

        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("titles", titles);
        packet.put("groups", buildGroupPacket(configuration));
        packet.put("qualities", buildQualityPacket(configuration));
        packet.put("selected_id", selectedTitleId);
        packet.put("equipped_summary", equippedSummary(configuration, resolvedState));
        packet.put("owned_count", resolvedState.ownedCount());
        packet.put("hidden_count", resolvedState.hiddenCount());
        packet.put("selected_display_name", selectedDefinition == null ? "" : selectedDefinition.displayName());
        packet.put("selected_kind", selectedDefinition == null ? "" : selectedDefinition.kind().configKey());
        packet.put("selected_chat_prefix", selectedDefinition == null ? "" : selectedDefinition.chatPrefix());
        packet.put("selected_chat_suffix", selectedDefinition == null ? "" : selectedDefinition.chatSuffix());
        packet.put("selected_tab_prefix", selectedDefinition == null ? "" : selectedDefinition.tabPrefix());
        packet.put("selected_tab_suffix", selectedDefinition == null ? "" : selectedDefinition.tabSuffix());
        packet.put("selected_group_id", selectedDefinition == null ? "" : selectedDefinition.groupId());
        packet.put(
            "selected_group_equipped_name",
            selectedDefinition == null
                ? ""
                : equippedNameForGroup(resolvedState, selectedDefinition.groupId())
        );
        packet.put("selected_description", selectedDefinition == null ? "" : selectedDefinition.description());
        packet.put("selected_source", selectedDefinition == null ? "" : selectedDefinition.source());
        packet.put(
            "selected_group_name",
            selectedDefinition == null || configuration.group(selectedDefinition.groupId()) == null
                ? ""
                : configuration.group(selectedDefinition.groupId()).name()
        );
        packet.put(
            "selected_quality_name",
            selectedDefinition == null || configuration.quality(selectedDefinition.qualityId()) == null
                ? ""
                : configuration.quality(selectedDefinition.qualityId()).name()
        );
        packet.put("selected_owned", selectedOwnedTitle != null);
        packet.put("selected_hidden", selectedOwnedTitle != null && selectedOwnedTitle.hidden());
        packet.put("selected_remaining_text", selectedOwnedTitle == null ? "未拥有" : TitleTextFormats.formatRemaining(selectedOwnedTitle, now));
        List<String> selectedDisplayLines = selectedDefinition == null
            ? List.of()
            : TitleTextFormats.formatAttributesAsList(
                selectedDefinition.displayAttributes(),
                TitleTextFormats.toSourceLines(selectedDefinition.displayAttributes(), selectedDefinition.displayAttributeLines())
            );
        List<String> selectedCollectionLines = selectedDefinition == null
            ? List.of()
            : TitleTextFormats.formatAttributesAsList(
                selectedDefinition.collectionAttributes(),
                TitleTextFormats.toSourceLines(selectedDefinition.collectionAttributes(), selectedDefinition.collectionAttributeLines())
            );
        List<String> displayLines = TitleTextFormats.formatAttributesAsList(
            resolvedState.displayAttributes(),
            resolvedState.displayAttributeSourceLines()
        );
        List<String> collectionLines = TitleTextFormats.formatAttributesAsList(
            resolvedState.collectionAttributes(),
            resolvedState.collectionAttributeSourceLines()
        );
        List<String> totalLines = TitleTextFormats.formatAttributesAsList(
            resolvedState.totalAttributes(),
            resolvedState.totalAttributeSourceLines()
        );
        List<String> setBonusLines = TitleTextFormats.formatAttributesAsList(
            resolvedState.setBonusAttributes(),
            resolvedState.setBonusAttributeSourceLines()
        );

        // 6 个属性字段统一发 List<String>，颜色前缀与空占位符来自 ArcartXTitle.yml 的 ui 配置
        // ArcartX Text 控件的 texts 字段拿到 List 时会自动按多行渲染
        String colorPrefix = configuration.ui().attributeLineColorPrefix();
        String emptyPlaceholder = configuration.ui().emptyAttributePlaceholder();
        packet.put("selected_display_attributes_text", colorize(selectedDisplayLines, colorPrefix, emptyPlaceholder));
        packet.put("selected_collection_attributes_text", colorize(selectedCollectionLines, colorPrefix, emptyPlaceholder));
        packet.put("display_attributes_text", colorize(displayLines, colorPrefix, emptyPlaceholder));
        packet.put("collection_attributes_text", colorize(collectionLines, colorPrefix, emptyPlaceholder));
        packet.put("total_attributes_text", colorize(totalLines, colorPrefix, emptyPlaceholder));
        packet.put("set_bonus_attributes_text", colorize(setBonusLines, colorPrefix, emptyPlaceholder));
        packet.put("sets", buildSetPacket(configuration, resolvedState, state));
        packet.put("display_title_name", buildDisplayTitle(configuration, resolvedState, TitleDefinition::displayName));
        packet.put("display_title_chat_prefix", buildDisplayTitle(configuration, resolvedState, TitleDefinition::chatPrefix));
        packet.put("display_title_chat_suffix", buildDisplayTitle(configuration, resolvedState, TitleDefinition::chatSuffix));
        packet.put("display_title_tab_prefix", buildDisplayTitle(configuration, resolvedState, TitleDefinition::tabPrefix));
        packet.put("display_title_tab_suffix", buildDisplayTitle(configuration, resolvedState, TitleDefinition::tabSuffix));
        return packet;
    }

    private static final java.util.regex.Pattern LEADING_COLOR_CODE = java.util.regex.Pattern.compile(
        "^(?:[&§][0-9a-fk-orA-FK-OR])+.*"
    );

    private static List<String> colorize(List<String> lines, String colorPrefix, String emptyPlaceholder) {
        String prefix = colorPrefix == null ? "" : colorPrefix;
        if (lines == null || lines.isEmpty()) {
            return List.of(prefix + (emptyPlaceholder == null ? "-" : emptyPlaceholder));
        }
        List<String> result = new java.util.ArrayList<>(lines.size());
        for (String line : lines) {
            // 行已自带颜色码（如 "&4暴击率: 5(%)"）则保留，不再拼默认前缀
            if (line != null && LEADING_COLOR_CODE.matcher(line).matches()) {
                result.add(line);
            } else {
                result.add(prefix + line);
            }
        }
        return List.copyOf(result);
    }

    private static String buildDisplayTitle(
        TitleModuleConfiguration configuration,
        ResolvedTitleState resolvedState,
        Function<TitleDefinition, String> fieldExtractor
    ) {
        TitleDisplayConfiguration displayConfig = configuration.displayTitle();
        List<String> groupOrder = configuration.displayTitleGroupOrder();
        StringJoiner joiner = new StringJoiner(displayConfig.separator());
        for (String groupId : groupOrder) {
            TitleDefinition title = resolvedState.equippedTitlesByGroup().get(groupId);
            if (title == null) {
                continue;
            }
            String value = fieldExtractor.apply(title);
            if (value != null && !value.isEmpty()) {
                joiner.add(value);
            }
        }
        return joiner.length() == 0 ? displayConfig.emptyText() : joiner.toString();
    }

    private static Map<String, Object> buildGroupPacket(TitleModuleConfiguration configuration) {
        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("all", Map.of("id", "all", "name", "全部分组", "sort_order", -1));
        for (TitleGroupDefinition groupDefinition : configuration.groups().values()) {
            groups.put(
                groupDefinition.id(),
                Map.of(
                    "id", groupDefinition.id(),
                    "name", groupDefinition.name(),
                    "sort_order", groupDefinition.sortOrder()
                )
            );
        }
        return groups;
    }

    private static Map<String, Object> buildQualityPacket(TitleModuleConfiguration configuration) {
        Map<String, Object> qualities = new LinkedHashMap<>();
        qualities.put("all", Map.of("id", "all", "name", "全部品质", "sort_order", -1));
        for (TitleQualityDefinition qualityDefinition : configuration.qualities().values()) {
            qualities.put(
                qualityDefinition.id(),
                Map.of(
                    "id", qualityDefinition.id(),
                    "name", qualityDefinition.name(),
                    "sort_order", qualityDefinition.sortOrder()
                )
            );
        }
        return qualities;
    }

    private static String equippedSummary(TitleModuleConfiguration configuration, ResolvedTitleState resolvedState) {
        if (resolvedState.equippedTitlesByGroup().isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (TitleGroupDefinition groupDefinition : configuration.groups().values()) {
            TitleDefinition title = resolvedState.equippedTitlesByGroup().get(groupDefinition.id());
            if (title == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("  ");
            }
            builder.append(groupDefinition.name()).append(": ").append(title.displayName());
        }
        return builder.isEmpty() ? "无" : builder.toString();
    }

    private static String equippedNameForGroup(ResolvedTitleState resolvedState, String groupId) {
        TitleDefinition title = resolvedState.equippedTitlesByGroup().get(groupId);
        return title == null ? "无" : title.displayName();
    }

    private static Map<String, Object> buildSetPacket(
        TitleModuleConfiguration configuration,
        ResolvedTitleState resolvedState,
        PlayerTitleState state
    ) {
        Map<String, Object> sets = new LinkedHashMap<>();
        for (TitleSetDefinition setDef : configuration.sets().values()) {
            Map<String, Object> setData = new LinkedHashMap<>();
            setData.put("id", setDef.id());
            setData.put("display_name", setDef.displayName());
            int owned = resolvedState.setCompletionCounts().getOrDefault(setDef.id(), 0);
            int total = setDef.effectiveThreshold();
            setData.put("owned_count", owned);
            setData.put("total_count", total);
            setData.put("active", resolvedState.setActiveMap().getOrDefault(setDef.id(), false));
            setData.put("progress", owned + "/" + total);
            setData.put(
                "bonus_text",
                TitleTextFormats.formatAttributes(
                    setDef.bonusAttributes(),
                    TitleTextFormats.toSourceLines(setDef.bonusAttributes(), setDef.bonusAttributeLines())
                )
            );
            StringBuilder titleNames = new StringBuilder();
            for (String reqId : setDef.requiredTitleIds()) {
                TitleDefinition def = configuration.title(reqId);
                if (def == null) continue;
                boolean playerOwns = state.ownedTitles().containsKey(reqId);
                if (!titleNames.isEmpty()) titleNames.append("  ");
                titleNames.append(playerOwns ? "&a✔ " : "&c✘ ").append("&0").append(def.displayName());
            }
            setData.put("required_titles_text", titleNames.toString());
            sets.put(setDef.id(), setData);
        }
        return sets;
    }
}
