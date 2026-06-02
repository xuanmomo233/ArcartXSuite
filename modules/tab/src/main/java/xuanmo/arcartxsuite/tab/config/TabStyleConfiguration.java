package xuanmo.arcartxsuite.tab.config;

import java.util.List;

/**
 * 全局视觉风格配置（settings.style.*）。
 *
 * <p>这些项不直接修改 pack 渲染流程，而是通过 {@code TabPlaceholderExpansion} 暴露的
 * PAPI 占位符供用户在 pack 模板内主动使用。
 *
 * <ul>
 *   <li>{@code %AXStab_pvp%} / {@code %AXStab_pvp_color%}：PVP 高亮。</li>
 *   <li>{@code %AXStab_vanished%} / {@code %AXStab_vanish_color%}：隐身灰显。</li>
 *   <li>{@code %AXStab_ping%} / {@code %AXStab_ping_icon%}：网络延迟图标。</li>
 * </ul>
 *
 * @param pvpEnabled        是否启用 pvp-highlight（仅影响占位符输出）
 * @param pvpWindowMs       玩家最近一次参与 PVP 的有效窗口（毫秒）
 * @param pvpColor          PVP 期间返回的颜色字符串（如 {@code &c}）
 * @param vanishGreyEnabled 是否启用 vanish 灰显
 * @param vanishColor       vanish 期间返回的颜色字符串（如 {@code &7}）
 * @param pingIconEnabled   是否启用 ping 图标分级
 * @param pingTiers         {@link PingTier} 阶梯列表（升序匹配，第一个 {@code maxMs >= ping} 的 icon 生效）
 */
public record TabStyleConfiguration(
    boolean pvpEnabled,
    long pvpWindowMs,
    String pvpColor,
    boolean vanishGreyEnabled,
    String vanishColor,
    boolean pingIconEnabled,
    List<PingTier> pingTiers
) {
    /** 单个 ping 阶梯：{@code ping <= maxMs} 时使用对应 {@code icon}。 */
    public record PingTier(int maxMs, String icon) {
    }

    public static TabStyleConfiguration defaults() {
        return new TabStyleConfiguration(
            false,
            5_000L,
            "&c",
            false,
            "&7",
            false,
            List.of()
        );
    }
}
