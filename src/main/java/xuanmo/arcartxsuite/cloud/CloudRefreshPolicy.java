package xuanmo.arcartxsuite.cloud;

/** 仅识别云端明确的身份错误，不能将封禁、撤销或普通 404 当成可恢复的绑定。 */
final class CloudRefreshPolicy {
    private CloudRefreshPolicy() {}

    static boolean isMissingServer(int status, String body) {
        String error = errorCode(body);
        return (status == 404 && "SERVER_NOT_FOUND".equals(error))
            || (status == 403 && "SERVER_NOT_REGISTERED".equals(error));
    }

    static boolean isSignatureMismatch(int status, String body) {
        String error = errorCode(body);
        return status == 403 && ("SIGNATURE_VERIFICATION_ERROR".equals(error)
            || "SIGNATURE_VERIFICATION_FAILED".equals(error));
    }

    static boolean isStandbyDisabled(int status, String body) {
        return status == 503 && "MAINTENANCE".equals(errorCode(body));
    }

    private static String errorCode(String body) {
        if (body == null) return null;
        try {
            var json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            var error = json.get("error");
            return error != null && error.isJsonPrimitive() && error.getAsJsonPrimitive().isString()
                ? error.getAsString() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
