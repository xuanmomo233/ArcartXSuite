package xuanmo.arcartxsuite.license;

import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.Set;

public record LicenseKeyResult(
    String codeId,
    String bindingId,
    String keySuffix,
    String status,
    String reason,
    String type,
    String moduleId,
    Set<String> modules
) {

    static LicenseKeyResult fromJson(JsonObject object) {
        Set<String> modules = new LinkedHashSet<>();
        if (object.has("modules") && object.get("modules").isJsonArray()) {
            object.getAsJsonArray("modules").forEach(element -> modules.add(element.getAsString().toLowerCase()));
        }
        return new LicenseKeyResult(
            string(object, "codeId"),
            string(object, "bindingId"),
            string(object, "keySuffix"),
            string(object, "status"),
            string(object, "reason"),
            string(object, "type"),
            string(object, "moduleId"),
            Set.copyOf(modules)
        );
    }

    public String summary() {
        String target = moduleId == null || moduleId.isBlank() ? type : moduleId;
        return keySuffix + ":" + LicenseMessages.keyStatus(status) + ":" + LicenseMessages.authError(reason)
            + (target == null || target.isBlank() ? "" : "(" + target + ")");
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
