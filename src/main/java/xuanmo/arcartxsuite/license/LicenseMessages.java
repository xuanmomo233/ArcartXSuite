package xuanmo.arcartxsuite.license;

public final class LicenseMessages {

    private LicenseMessages() {
    }

    public static String state(LicenseDecision.State state) {
        if (state == null) {
            return "未知状态";
        }
        return switch (state) {
            case VALID -> "在线票据有效（VALID）";
            case GRACE -> "离线宽限可用（GRACE）";
            case EMERGENCY_GRACE -> "应急宽限可用（EMERGENCY_GRACE）";
            case DISABLED -> "授权不可用（DISABLED）";
            case NETWORK_ERROR -> "授权网络错误（NETWORK_ERROR）";
            case AUTH_DENIED -> "授权被拒绝（AUTH_DENIED）";
            case NOT_CONFIGURED -> "授权未配置（NOT_CONFIGURED）";
        };
    }

    public static String authError(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String message = switch (code) {
            case "MISSING_QQ" -> "未填写 license.qq";
            case "MISSING_LICENSE_KEYS" -> "未填写 license.keys 授权码列表";
            case "MISSING_INSTALL_ID" -> "缺少 install_id";
            case "MISSING_FINGERPRINT_HASH" -> "缺少机器指纹";
            case "QQ_MISMATCH" -> "授权码不属于当前 QQ";
            case "PRODUCT_MISMATCH" -> "授权码不属于 ArcartXSuite";
            case "LICENSE_CODE_NOT_FOUND" -> "授权中心不存在该授权码";
            case "LICENSE_CODE_NOT_ACTIVE" -> "授权码已停用";
            case "LICENSE_CODE_EXPIRED" -> "授权码已过期";
            case "BINDING_NOT_FOUND" -> "授权码尚未绑定当前服务器，请执行 /axs license activate";
            case "BOUND_TO_OTHER_INSTALL" -> "授权码已绑定到其他服务器或旧机器指纹；如确认迁移，请执行 /axs license rebind";
            case "REBIND_QUOTA_EXHAUSTED" -> "自助换绑次数不足，请后台补次数或管理员删除旧绑定";
            case "REBIND_COOLDOWN_ACTIVE" -> "授权码仍在换绑冷却中，请等待冷却结束或后台重置冷却";
            case "LOGIN_REQUIRED" -> "云端账号未登录";
            case "ACCOUNT_OR_PASSWORD_INVALID" -> "QQ 或密码错误";
            case "ACCOUNT_LOCKED" -> "登录失败次数过多，账号暂时锁定";
            case "SETUP_TOKEN_INVALID_OR_EXPIRED" -> "账号设置链接无效或已过期";
            case "PASSWORD_TOO_SHORT" -> "密码太短，至少 8 位";
            case "MISSING_CHALLENGE_CODE" -> "缺少云端换绑挑战码";
            case "CHALLENGE_INVALID_OR_EXPIRED" -> "云端换绑挑战码无效、已过期或已使用";
            case "LICENSE_CODE_NOT_OWNED" -> "该授权码不属于当前登录 QQ";
            case "CLOUD_REBIND_MONTHLY_LIMIT_EXHAUSTED" -> "该授权码本月云端换绑免费次数已用完";
            case "NO_VALID_LICENSE_KEYS" -> "没有可用的授权码";
            case "NETWORK_ERROR" -> "授权入口不可达，请检查网络、Cloudflare 或代理配置";
            case "INVALID_JSON" -> "授权请求 JSON 无效";
            case "INVALID_MODULE_ID" -> "模块 ID 无效";
            case "UNAUTHORIZED" -> "管理令牌无效";
            case "ADMIN_TOKEN_NOT_CONFIGURED" -> "后台管理令牌未配置";
            case "NOT_FOUND" -> "请求的接口不存在";
            case "OK" -> "正常";
            default -> code;
        };
        return message.equals(code) ? code : message + "（" + code + "）";
    }

    public static String keyStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String message = switch (status) {
            case "OK" -> "通过";
            case "DENIED" -> "拒绝";
            default -> status;
        };
        return message.equals(status) ? status : message + "（" + status + "）";
    }
}
