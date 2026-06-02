package xuanmo.arcartxsuite.tab.transport;

import java.util.List;

/**
 * 跨服快照中的单个玩家条目。
 *
 * <p>v2 协议在 v1 基础上新增三个字段（{@code sortValues / sortStringValues / groupKey}），用于跨服 grouping
 * 与多键复合排序。旧节点（v1）发出的快照会把这些字段填充为兼容值（首键复制 + 空 groupKey），由 v2 节点解码时
 * 自动按 v1 行为回退；v2 节点发出的快照对 v1 节点而言会丢失这三个字段，但首键 sortValue / sortStringValue
 * 仍然可用，等价于阶段 1 的行为。
 */
public record TabRemoteEntry(
    String playerUuid,
    String playerName,
    double sortValue,
    String sortStringValue,
    List<Double> sortValues,
    List<String> sortStringValues,
    String groupKey,
    Object renderedPack
) {

    public TabRemoteEntry {
        sortValues = sortValues == null ? List.of(sortValue) : List.copyOf(sortValues);
        sortStringValues = sortStringValues == null
            ? List.of(sortStringValue == null ? "" : sortStringValue)
            : List.copyOf(sortStringValues);
        groupKey = groupKey == null ? "" : groupKey;
    }

    /** v1 兼容构造器：仅首键。 */
    public TabRemoteEntry(
        String playerUuid,
        String playerName,
        double sortValue,
        String sortStringValue,
        Object renderedPack
    ) {
        this(playerUuid, playerName, sortValue, sortStringValue,
            List.of(sortValue),
            List.of(sortStringValue == null ? "" : sortStringValue),
            "",
            renderedPack);
    }
}

