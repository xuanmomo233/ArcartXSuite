package xuanmo.arcartxsuite.tab.config;

/**
 * 过滤 / 置顶 / 置底通用规则。
 *
 * <p>同一规则只生效一种判定：
 * <ul>
 *   <li>papi 非空 → 比较 PAPI 渲染结果是否等于 equalsValue（忽略大小写）。equalsValue 为空时只判断 PAPI 结果非空。</li>
 *   <li>permission 非空 → 判断玩家是否持有该权限节点。</li>
 * </ul>
 *
 * <p>invert=true 时把上述判定结果反转（"不持有 / 不匹配"）。
 */
public record TabFilterRule(
    String papi,
    String equalsValue,
    String permission,
    boolean invert
) {
    public TabFilterRule {
        papi = papi == null ? "" : papi.trim();
        equalsValue = equalsValue == null ? "" : equalsValue;
        permission = permission == null ? "" : permission.trim();
    }

    public boolean isPapi() {
        return !papi.isBlank();
    }

    public boolean isPermission() {
        return !permission.isBlank();
    }

    public boolean isValid() {
        return isPapi() || isPermission();
    }
}
