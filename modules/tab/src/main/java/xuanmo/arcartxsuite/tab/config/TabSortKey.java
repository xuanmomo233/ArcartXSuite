package xuanmo.arcartxsuite.tab.config;

import java.util.List;

/**
 * 单个排序键，用于 sort-keys 复合排序。
 *
 * <p>多个 TabSortKey 组合时按列表顺序优先级递减。
 */
public record TabSortKey(
    TabSortMode mode,
    String papiKey,
    boolean papiNumeric,
    List<String> premGroups,
    boolean descending
) {
    public TabSortKey {
        papiKey = papiKey == null ? "" : papiKey;
        premGroups = premGroups == null || premGroups.isEmpty() ? List.of("default") : List.copyOf(premGroups);
    }
}
