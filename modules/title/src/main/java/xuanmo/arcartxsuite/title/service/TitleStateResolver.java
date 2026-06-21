package xuanmo.arcartxsuite.title.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import xuanmo.arcartxsuite.title.config.TitleDefinition;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.config.TitleSetDefinition;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

public final class TitleStateResolver {

    private TitleStateResolver() {
    }

    public static ResolvedTitleState resolve(PlayerTitleState state, TitleModuleConfiguration configuration, Instant now) {
        PlayerTitleState sanitized = state == null ? null : state.sanitize(now);
        if (sanitized == null) {
            return new ResolvedTitleState(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(), 0, 0, Map.of(), Map.of(), Map.of(), List.of(), null);
        }

        TitleDefinition totalDisplayTitle = resolveTotalDisplayTitle(sanitized, configuration);

        LinkedHashMap<String, Double> collectionAttributes = new LinkedHashMap<>();
        List<String> collectionAttributeLines = new ArrayList<>();
        for (PlayerOwnedTitle ownedTitle : sanitized.ownedTitles().values()) {
            TitleDefinition definition = configuration.title(ownedTitle.titleId());
            if (definition == null) {
                continue;
            }
            mergeAttributes(collectionAttributes, definition.collectionAttributes());
            mergeAttributeLines(collectionAttributeLines, definition.collectionAttributeLines());
        }

        LinkedHashMap<String, String> equippedTitleIdsByGroup = new LinkedHashMap<>();
        LinkedHashMap<String, TitleDefinition> equippedTitlesByGroup = new LinkedHashMap<>();
        LinkedHashMap<String, Double> displayAttributes = new LinkedHashMap<>();
        List<String> displayAttributeLines = new ArrayList<>();
        for (Map.Entry<String, String> entry : sanitized.equippedTitleIdsByGroup().entrySet()) {
            TitleDefinition equippedTitle = configuration.title(entry.getValue());
            if (equippedTitle == null || !entry.getKey().equals(equippedTitle.groupId())) {
                continue;
            }
            equippedTitleIdsByGroup.put(entry.getKey(), equippedTitle.id());
            equippedTitlesByGroup.put(entry.getKey(), equippedTitle);
            mergeAttributes(displayAttributes, equippedTitle.displayAttributes());
            mergeAttributeLines(displayAttributeLines, equippedTitle.displayAttributeLines());
        }

        LinkedHashMap<String, Integer> setCompletionCounts = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> setActiveMap = new LinkedHashMap<>();
        LinkedHashMap<String, Double> setBonusAttributes = new LinkedHashMap<>();
        List<String> setBonusAttributeLines = new ArrayList<>();
        for (Map.Entry<String, TitleSetDefinition> setEntry : configuration.sets().entrySet()) {
            TitleSetDefinition setDef = setEntry.getValue();
            int ownedInSet = 0;
            for (String requiredId : setDef.requiredTitleIds()) {
                if (sanitized.ownedTitles().containsKey(requiredId)) {
                    ownedInSet++;
                }
            }
            setCompletionCounts.put(setDef.id(), ownedInSet);
            boolean active = ownedInSet >= setDef.effectiveThreshold();
            setActiveMap.put(setDef.id(), active);
            if (active) {
                mergeAttributes(setBonusAttributes, setDef.bonusAttributes());
                mergeAttributeLines(setBonusAttributeLines, setDef.bonusAttributeLines());
            }
        }

        LinkedHashMap<String, Double> totalAttributes = new LinkedHashMap<>(collectionAttributes);
        mergeAttributes(totalAttributes, displayAttributes);
        mergeAttributes(totalAttributes, setBonusAttributes);
        List<String> totalAttributeLines = new ArrayList<>(collectionAttributeLines);
        totalAttributeLines.addAll(displayAttributeLines);
        totalAttributeLines.addAll(setBonusAttributeLines);

        return new ResolvedTitleState(
            equippedTitleIdsByGroup,
            equippedTitlesByGroup,
            displayAttributes,
            collectionAttributes,
            totalAttributes,
            TitleTextFormats.toSourceLines(displayAttributes, displayAttributeLines),
            TitleTextFormats.toSourceLines(collectionAttributes, collectionAttributeLines),
            TitleTextFormats.toSourceLines(totalAttributes, totalAttributeLines),
            sanitized.ownedTitles().size(),
            sanitized.hiddenCount(),
            setCompletionCounts,
            setActiveMap,
            setBonusAttributes,
            TitleTextFormats.toSourceLines(setBonusAttributes, setBonusAttributeLines),
            totalDisplayTitle
        );
    }

    private static TitleDefinition resolveTotalDisplayTitle(PlayerTitleState state, TitleModuleConfiguration configuration) {
        String displayTitleId = state.displayTitleId();
        if (!displayTitleId.isBlank()) {
            TitleDefinition explicit = configuration.title(displayTitleId);
            if (explicit != null && state.equippedTitleIdsByGroup().containsValue(displayTitleId)) {
                return explicit;
            }
        }
        List<String> groupOrder = configuration.displayTitleGroupOrder();
        for (String groupId : groupOrder) {
            String titleId = state.equippedTitleIdsByGroup().get(groupId);
            if (titleId == null) {
                continue;
            }
            TitleDefinition title = configuration.title(titleId);
            if (title != null) {
                return title;
            }
        }
        return null;
    }

    private static void mergeAttributes(Map<String, Double> target, Map<String, Double> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
    }

    private static void mergeAttributeLines(List<String> target, List<String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        target.addAll(source);
    }
}
