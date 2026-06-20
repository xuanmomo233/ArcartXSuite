package xuanmo.arcartxsuite.tab.config;

/**
 * 隐私脱敏配置（settings.privacy.*）。
 *
 * @param hideUuid 是否隐藏 UUID
 * @param hideIp   是否隐藏 IP
 */
public record TabPrivacyConfiguration(
    boolean hideUuid,
    boolean hideIp
) {

    public static TabPrivacyConfiguration defaults() {
        return new TabPrivacyConfiguration(false, false);
    }
}
