package xuanmo.arcartxsuite.tab.config;

import java.util.List;

/**
 * 按 PAPI 表达式将玩家分组，并在每组前插入 header-pack。
 * map 形态的 pack 不支持分组，将退化为不分组并打印警告。
 */
public record TabGroupingConfiguration(
    boolean enabled,
    String groupByPapi,
    List<String> groupOrder,
    Object headerPack,
    boolean includeUnordered
) {
    public TabGroupingConfiguration {
        groupByPapi = groupByPapi == null ? "" : groupByPapi.trim();
        groupOrder = groupOrder == null ? List.of() : List.copyOf(groupOrder);
    }

    public static TabGroupingConfiguration disabled() {
        return new TabGroupingConfiguration(false, "", List.of(), null, true);
    }
}
