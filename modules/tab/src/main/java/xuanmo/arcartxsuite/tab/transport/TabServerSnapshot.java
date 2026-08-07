package xuanmo.arcartxsuite.tab.transport;

import java.util.List;

/**
 * Tab 服务端快照。
 * <p>
 * 表示某一节点在指定时刻对某个 Tab 定义的完整条目快照，
 * 用于跨服同步 Tab 列表内容。
 * 关联模块：{@code tab}，由 transport 层传输。
 *
 * @param nodeId       来源节点标识
 * @param definitionId 对应的 Tab 定义 id
 * @param timestamp    快照时间戳（毫秒）
 * @param entries      Tab 条目列表
 */
public record TabServerSnapshot(
    String nodeId,
    String definitionId,
    long timestamp,
    List<TabRemoteEntry> entries
) {
}
