package xuanmo.arcartxsuite.tab.config;

import java.util.List;

public record TabDefinition(
    String id,
    boolean enabled,
    List<UiTarget> uiTargets,
    String clientRefreshPacketId,
    String clientRefreshAction,
    TabClientRefreshGuardConfiguration clientRefreshGuard,
    int maxEntries,
    List<TabSortKey> sortKeys,
    List<TabFilterRule> includeFilters,
    List<TabFilterRule> excludeFilters,
    boolean hideVanished,
    List<TabFilterRule> pinnedTop,
    List<TabFilterRule> pinnedBottom,
    boolean omitBlankValues,
    Object packTemplate,
    Boolean crossServer,
    String view,
    TabGroupingConfiguration grouping,
    TabPaginationConfiguration pagination,
    TabAggregateConfiguration aggregate
) {
    public TabDefinition {
        uiTargets = uiTargets == null || uiTargets.isEmpty()
            ? List.of()
            : List.copyOf(uiTargets);
        sortKeys = sortKeys == null || sortKeys.isEmpty()
            ? List.of(new TabSortKey(TabSortMode.NAME, "", false, List.of("default"), false))
            : List.copyOf(sortKeys);
        includeFilters = includeFilters == null ? List.of() : List.copyOf(includeFilters);
        excludeFilters = excludeFilters == null ? List.of() : List.copyOf(excludeFilters);
        pinnedTop = pinnedTop == null ? List.of() : List.copyOf(pinnedTop);
        pinnedBottom = pinnedBottom == null ? List.of() : List.copyOf(pinnedBottom);
        view = view == null || view.isBlank() ? "default" : view.trim();
        grouping = grouping == null ? TabGroupingConfiguration.disabled() : grouping;
        pagination = pagination == null ? TabPaginationConfiguration.disabled() : pagination;
        aggregate = aggregate == null ? TabAggregateConfiguration.disabled() : aggregate;
    }

    /** 向后兼容：返回首个 target 的 uiId（配置检验已保证至少 1 个 target）。 */
    public String uiId() {
        return uiTargets.isEmpty() ? "" : uiTargets.get(0).uiId();
    }

    /** 向后兼容：返回首个 target 的 packetHandler。 */
    public String packetHandler() {
        return uiTargets.isEmpty() ? "" : uiTargets.get(0).packetHandler();
    }

    /** 收集所有不重复的 uiId（用于 UI 注册）。 */
    public List<String> distinctUiIds() {
        return uiTargets.stream()
            .map(UiTarget::uiId)
            .distinct()
            .toList();
    }

    /** 跨服快照仍按单键发送，使用首个 sortKey 作为代表。 */
    public TabSortKey primarySortKey() {
        return sortKeys.get(0);
    }

    // ===== 旧字段访问器（向后兼容；仅基于 primarySortKey 推导） =====

    public TabSortMode sortMode() {
        return primarySortKey().mode();
    }

    public boolean sortDescending() {
        return primarySortKey().descending();
    }

    public String sortPapiKey() {
        return primarySortKey().papiKey();
    }

    public boolean sortPapiNumeric() {
        return primarySortKey().papiNumeric();
    }

    public List<String> sortPremGroups() {
        return primarySortKey().premGroups();
    }
}
