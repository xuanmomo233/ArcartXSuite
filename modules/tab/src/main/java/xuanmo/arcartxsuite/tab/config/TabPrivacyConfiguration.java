package xuanmo.arcartxsuite.tab.config;

/**
 * 隐私合规配置（settings.privacy.*）。
 *
 * <p>当前仅在 PAPI 占位符层生效：
 * <ul>
 *   <li>{@code hideUuid}：{@code %AXStab_uuid%} 返回空串。</li>
 *   <li>{@code hideIp}：{@code %AXStab_ip%} 返回空串。</li>
 * </ul>
 * Pack 模板中若使用上述占位符替代 {@code %player_uuid%} / {@code %player_ip%}，
 * 即可在不修改业务 PAPI 的前提下集中开关脱敏。
 *
 * @param hideUuid 是否在占位符层屏蔽玩家 UUID
 * @param hideIp   是否在占位符层屏蔽玩家 IP
 */
public record TabPrivacyConfiguration(boolean hideUuid, boolean hideIp) {

    public static TabPrivacyConfiguration defaults() {
        return new TabPrivacyConfiguration(false, false);
    }
}
