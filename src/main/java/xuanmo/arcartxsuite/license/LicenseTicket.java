package xuanmo.arcartxsuite.license;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record LicenseTicket(
    String raw,
    String ticketId,
    String kid,
    String licenseId,
    String activationId,
    String ownerQq,
    String subjectId,
    String product,
    String edition,
    String installId,
    String fingerprintHash,
    String localSaltHash,
    Set<String> modules,
    List<LicenseKeyResult> keyResults,
    Map<String, ResourceKeyEnvelope> resourceKeys,
    long issuedAt,
    long notBefore,
    long expiresAt,
    long refreshAfter,
    long offlineGraceUntil,
    long hardExpireAt,
    int revokeListVersion
) {

    public LicenseTicket {
        modules = modules == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(modules));
        keyResults = keyResults == null ? List.of() : List.copyOf(keyResults);
        resourceKeys = resourceKeys == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(resourceKeys));
    }

    public boolean hasModule(String moduleId) {
        return modules.contains(moduleId);
    }

    static LicenseTicket fromPayload(String raw, JsonObject payload) {
        Set<String> modules = new LinkedHashSet<>();
        if (payload.has("modules") && payload.get("modules").isJsonArray()) {
            payload.getAsJsonArray("modules").forEach(element -> modules.add(element.getAsString().toLowerCase()));
        }

        Map<String, ResourceKeyEnvelope> resourceKeys = new LinkedHashMap<>();
        if (payload.has("resourceKeys") && payload.get("resourceKeys").isJsonObject()) {
            JsonObject keys = payload.getAsJsonObject("resourceKeys");
            for (String moduleId : keys.keySet()) {
                JsonObject key = keys.getAsJsonObject(moduleId);
                resourceKeys.put(moduleId.toLowerCase(), new ResourceKeyEnvelope(
                    string(key, "alg", "AES-GCM"),
                    string(key, "kid", ""),
                    string(key, "wrapped", ""),
                    string(key, "iv", "")
                ));
            }
        }

        List<LicenseKeyResult> keyResults = new ArrayList<>();
        if (payload.has("keyResults") && payload.get("keyResults").isJsonArray()) {
            payload.getAsJsonArray("keyResults").forEach(element -> {
                if (element.isJsonObject()) {
                    keyResults.add(LicenseKeyResult.fromJson(element.getAsJsonObject()));
                }
            });
        }

        return new LicenseTicket(
            raw,
            string(payload, "ticketId", ""),
            string(payload, "kid", ""),
            string(payload, "licenseId", ""),
            string(payload, "activationId", ""),
            string(payload, "ownerQq", ""),
            string(payload, "subjectId", ""),
            string(payload, "product", ""),
            string(payload, "edition", ""),
            string(payload, "installId", ""),
            string(payload, "fingerprintHash", ""),
            string(payload, "localSaltHash", ""),
            modules,
            keyResults,
            resourceKeys,
            number(payload, "issuedAt"),
            number(payload, "notBefore"),
            number(payload, "expiresAt"),
            number(payload, "refreshAfter"),
            number(payload, "offlineGraceUntil"),
            number(payload, "hardExpireAt"),
            (int) number(payload, "revokeListVersion")
        );
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static long number(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : 0L;
    }

    public record ResourceKeyEnvelope(String alg, String kid, String wrapped, String iv) {
    }
}
