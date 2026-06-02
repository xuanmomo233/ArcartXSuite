package xuanmo.arcartxsuite.tab.transport;

import java.util.List;

public record TabServerSnapshot(
    String nodeId,
    String definitionId,
    long timestamp,
    List<TabRemoteEntry> entries
) {
}
