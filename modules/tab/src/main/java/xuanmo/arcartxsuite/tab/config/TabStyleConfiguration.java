package xuanmo.arcartxsuite.tab.config;

import java.util.List;

/**
 * 全局视觉风格配置（settings.style.*）。
 *
 * @param pvpEnabled   是否启用 pvp-highlight
 * @param pvpWindowMs  玩家最近一次参与 PVP 的有效窗口（毫秒）
 * @param pvpColor     PVP 高亮颜色
 * @param vanishEnabled 是否启用 vanish-grey
 * @param vanishColor   隐身灰色颜色
 * @param pingEnabled   是否启用 ping-icon
 * @param pingTiers     ping 图标档位（按 max-ms 升序）
 */
public record TabStyleConfiguration(
    boolean pvpEnabled,
    long pvpWindowMs,
    String pvpColor,
    boolean vanishEnabled,
    String vanishColor,
    boolean pingEnabled,
    List<TabPingTier> pingTiers
) {

    public static TabStyleConfiguration defaults() {
        return new TabStyleConfiguration(
            false, 5_000L, "&c",
            false, "&7",
            false, List.of(
                new TabPingTier(80, "&a▮▮▮▮"),
                new TabPingTier(160, "&e▮▮▮▯"),
                new TabPingTier(300, "&6▮▮▯▯"),
                new TabPingTier(9999, "&c▮▯▯▯")
            )
        );
    }

    public record TabPingTier(int maxMs, String icon) {}
}
