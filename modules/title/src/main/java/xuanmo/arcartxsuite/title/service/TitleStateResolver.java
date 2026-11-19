package xuanmo.arcartxsuite.title.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import xuanmo.arcartxsuite.title.config.TitleDefinition;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.config.TitleSetDefinition;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

/**
 * 称号状态解析器。
 * <p>
 * 将 {@link xuanmo.arcartxsuite.title.model.PlayerTitleState} 与
 * {@link xuanmo.arcartxsuite.title.config.TitleModuleConfiguration} 合并，
 * 生成包含已装备称号定义、属性汇总、套装完成度的 {@link ResolvedTitleState}。
 * 属于 title 模块服务层。
 */
public final class TitleStateResolver {
    static final ResolvedTitleState EMPTY = new ResolvedTitleState(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
        Map.of(), Map.of(), List.of(), List.of(), List.of(), 0, 0, Map.of(), Map.of(), Map.of(), List.of(), null);

    private TitleStateResolver() {
    }

    /**
     * 解析玩家称号状态，生成包含属性汇总与套装信息的解析结果。
     *
     * @param state              玩家称号状态，为 {@code null} 时返回空状态
     * @param configuration      模块配置
     * @param now                当前时间
     * @param accessibleGroupIds 玩家可访问的分组 ID 集合
     * @return 解析后的称号状态
     */
    public static ResolvedTitleState resolve(
        PlayerTitleState state,
        TitleModuleConfiguration configuration,
        Instant now,
        Set<String> accessibleGroupIds
    ) {
        if (state == null) return EMPTY;
        PlayerTitleState sanitized = state.sanitize(now);
        TitleDefinition totalDisplayTitle = resolveTotalDisplayTitle(sanitized, configuration, accessibleGroupIds);

        LinkedHashMap<String, Double> collectionAttributes = new LinkedHashMap<>();
        List<String> collectionAttributeLines = new ArrayList<>();
        int ownedCount = 0;
        int hiddenCount = 0;
        for (PlayerOwnedTitle ownedTitle : sanitized.ownedTitles().values()) {
            TitleDefinition definition = configuration.title(ownedTitle.titleId());
            if (definition == null || !isAccessible(configuration, accessibleGroupIds, definition.groupId())) {
                continue;
            }
            ownedCount++;
            if (ownedTitle.hidden()) hiddenCount++;
            mergeAttributes(collectionAttributes, definition.collectionAttributes());
            mergeAttributeLines(collectionAttributeLines, definition.collectionAttributeLines());
        }

        LinkedHashMap<String, String> equippedTitleIdsByGroup = new LinkedHashMap<>();
        LinkedHashMap<String, TitleDefinition> equippedTitlesByGroup = new LinkedHashMap<>();
        LinkedHashMap<String, Double> displayAttributes = new LinkedHashMap<>();
        LinkedHashMap<org.bukkit.potion.PotionEffectType, Integer> displayEffects = new LinkedHashMap<>();
        LinkedHashMap<String, xuanmo.arcartxsuite.api.modattr.ModAttributeModifier> permanentDisplayBonuses =
            new LinkedHashMap<>();
        List<String> displayAttributeLines = new ArrayList<>();
        for (Map.Entry<String, String> entry : sanitized.equippedTitleIdsByGroup().entrySet()) {
            TitleDefinition equippedTitle = configuration.title(entry.getValue());
            if (equippedTitle == null
                || !entry.getKey().equals(equippedTitle.groupId())
                || !isAccessible(configuration, accessibleGroupIds, equippedTitle.groupId())) {
                continue;
            }
            equippedTitleIdsByGroup.put(entry.getKey(), equippedTitle.id());
            equippedTitlesByGroup.put(entry.getKey(), equippedTitle);
            mergeAttributes(displayAttributes, equippedTitle.displayAttributes());
            mergeEffects(displayEffects, equippedTitle.displayEffects());
            mergeAttributeLines(displayAttributeLines, equippedTitle.displayAttributeLines());
            // 永久加成：仅当该称号为永久拥有（expiresAt == null）时生效
            PlayerOwnedTitle owned = sanitized.ownedTitles().get(equippedTitle.id());
            if (owned != null && owned.expiresAt() == null) {
                mergeModBonuses(permanentDisplayBonuses, equippedTitle.permanentDisplayBonuses());
            }
        }

        LinkedHashMap<String, Integer> setCompletionCounts = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> setActiveMap = new LinkedHashMap<>();
        LinkedHashMap<String, Double> setBonusAttributes = new LinkedHashMap<>();
        List<String> setBonusAttributeLines = new ArrayList<>();
        for (Map.Entry<String, TitleSetDefinition> setEntry : configuration.sets().entrySet()) {
            TitleSetDefinition setDef = setEntry.getValue();
            int ownedInSet = 0;
            for (String requiredId : setDef.requiredTitleIds()) {
                TitleDefinition requiredTitle = configuration.title(requiredId);
                if (requiredTitle != null
                    && isAccessible(configuration, accessibleGroupIds, requiredTitle.groupId())
                    && sanitized.ownedTitles().containsKey(requiredId)) {
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
            displayEffects,
            permanentDisplayBonuses,
            totalAttributes,
            TitleTextFormats.toSourceLines(displayAttributes, displayAttributeLines),
            TitleTextFormats.toSourceLines(collectionAttributes, collectionAttributeLines),
            TitleTextFormats.toSourceLines(totalAttributes, totalAttributeLines),
            ownedCount,
            hiddenCount,
            setCompletionCounts,
            setActiveMap,
            setBonusAttributes,
            TitleTextFormats.toSourceLines(setBonusAttributes, setBonusAttributeLines),
            totalDisplayTitle
        );
    }

    private static TitleDefinition resolveTotalDisplayTitle(
        PlayerTitleState state,
        TitleModuleConfiguration configuration,
        Set<String> accessibleGroupIds
    ) {
        String displayTitleId = state.displayTitleId();
        if (!displayTitleId.isBlank()) {
            TitleDefinition explicit = configuration.title(displayTitleId);
            if (explicit != null
                && isAccessible(configuration, accessibleGroupIds, explicit.groupId())
                && state.equippedTitleIdsByGroup().containsValue(displayTitleId)) {
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
            if (title != null
                && isAccessible(configuration, accessibleGroupIds, title.groupId())) {
                return title;
            }
        }
        return null;
    }

    private static boolean isAccessible(
        TitleModuleConfiguration configuration,
        Set<String> accessibleGroupIds,
        String groupId
    ) {
        return configuration.group(groupId) == null
            || accessibleGroupIds == null
            || accessibleGroupIds.contains(groupId);
    }

    private static void mergeAttributes(Map<String, Double> target, Map<String, Double> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
    }

    /**
     * 合并药水效果：同类型效果取多个装备称号中的最大等级。
     */
    private static void mergeEffects(
        Map<org.bukkit.potion.PotionEffectType, Integer> target,
        Map<org.bukkit.potion.PotionEffectType, Integer> source
    ) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<org.bukkit.potion.PotionEffectType, Integer> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Math::max);
        }
    }

    /**
     * 合并 Mod 属性加成：同一属性来自多个装备中的永久称号时，
     * 运算模式相同的数值加总；模式不同的保留先出现的条目（不建议混用模式）。
     */
    private static void mergeModBonuses(
        Map<String, xuanmo.arcartxsuite.api.modattr.ModAttributeModifier> target,
        Map<String, xuanmo.arcartxsuite.api.modattr.ModAttributeModifier> source
    ) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, xuanmo.arcartxsuite.api.modattr.ModAttributeModifier> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), (existing, incoming) ->
                existing.operation() == incoming.operation()
                    ? new xuanmo.arcartxsuite.api.modattr.ModAttributeModifier(
                        existing.attributeId(), existing.amount() + incoming.amount(), existing.operation())
                    : existing);
        }
    }

    private static void mergeAttributeLines(List<String> target, List<String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        target.addAll(source);
    }
}
