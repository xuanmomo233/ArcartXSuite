package xuanmo.arcartxsuite.onlinerewards.config;

import java.util.List;

public record OnlineRewardsUiConfiguration(
    String packetId,
    List<String> menuUiIds,
    boolean registerUiOnEnable,
    boolean overwriteUiFiles
) {
    /** 向后兼容 */
    public String menuUiId() { return menuUiIds.isEmpty() ? "" : menuUiIds.get(0); }
}
