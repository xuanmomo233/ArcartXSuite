package xuanmo.arcartxsuite.proxy.common.model;

import java.util.UUID;

/**
 * 认证账号类型。
 */
public enum AccountType {
    MICROSOFT("microsoft", "微软正版"),
    LITTLESKIN("littleskin", "LittleSkin"),
    OFFLINE("offline", "离线"),
    UNKNOWN("unknown", "未知");

    private final String id;
    private final String displayName;

    AccountType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean premium() {
        return this == MICROSOFT || this == LITTLESKIN;
    }

    public static AccountType fromId(String id) {
        for (AccountType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * 根据 UUID 版本做本地启发式判定（代理端粗略拦截用）。
     * <p>
     * 规则：
     * <ul>
     *   <li>UUID v4 → {@link #LITTLESKIN}（v4 也可能是离线服 {@code UUID.randomUUID()}，
     *       但代理端不踢 LittleSkin，误判只影响放行，后端会重新精确判定）</li>
     *   <li>UUID v3 → {@link #UNKNOWN}（v3 既可能是旧版微软正版也可能是离线，
     *       代理端不直接判 OFFLINE 以避免误踢旧版正版玩家，交给后端精确判定）</li>
     *   <li>UUID v1 → {@link #MICROSOFT}</li>
     *   <li>其他 → {@link #UNKNOWN}</li>
     * </ul>
     * <p>
     * 此方法仅做本地判定，不发起网络请求。精确判定由后端
     * {@code AccountTypeServiceImpl} 通过 Mojang API / 本地混合代理完成。
     */
    public static AccountType fromUuid(UUID uuid) {
        int version = (uuid.version() & 0xF);
        if (version == 4) return LITTLESKIN;
        if (version == 3) return UNKNOWN;
        if (version == 1) return MICROSOFT;
        return UNKNOWN;
    }
}
