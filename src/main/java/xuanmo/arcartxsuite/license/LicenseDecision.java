package xuanmo.arcartxsuite.license;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record LicenseDecision(State state, String reason, Set<String> modules, LicenseTicket ticket) {

    public enum State {
        VALID,
        GRACE,
        EMERGENCY_GRACE,
        DISABLED,
        NETWORK_ERROR,
        AUTH_DENIED,
        NOT_CONFIGURED
    }

    public LicenseDecision {
        reason = reason == null ? "" : reason;
        modules = modules == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(modules));
    }

    public boolean allowsModule(String moduleId) {
        return switch (state) {
            case VALID, GRACE, EMERGENCY_GRACE -> modules.contains(moduleId);
            default -> false;
        };
    }

    public boolean allowsPaidModules() {
        return state == State.VALID || state == State.GRACE || state == State.EMERGENCY_GRACE;
    }

    public static LicenseDecision notConfigured() {
        return new LicenseDecision(State.NOT_CONFIGURED, "license.qq 或 license.keys 未配置", Set.of(), null);
    }

    public static LicenseDecision disabled(String reason) {
        return new LicenseDecision(State.DISABLED, reason, Set.of(), null);
    }
}
