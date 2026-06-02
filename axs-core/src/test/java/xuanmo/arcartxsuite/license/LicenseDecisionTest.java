package xuanmo.arcartxsuite.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LicenseDecision 授权决策")
class LicenseDecisionTest {

    @Test
    @DisplayName("VALID 状态允许已授权模块")
    void validAllowsLicensedModule() {
        LicenseDecision decision = new LicenseDecision(
            LicenseDecision.State.VALID, "ok", Set.of("warehouse", "mail"), null);
        assertTrue(decision.allowsModule("warehouse"));
        assertTrue(decision.allowsModule("mail"));
    }

    @Test
    @DisplayName("VALID 状态拒绝未授权模块")
    void validRejectsUnlicensedModule() {
        LicenseDecision decision = new LicenseDecision(
            LicenseDecision.State.VALID, "ok", Set.of("warehouse"), null);
        assertFalse(decision.allowsModule("title"));
    }

    @Test
    @DisplayName("GRACE 状态仍允许已授权模块")
    void graceAllowsModule() {
        LicenseDecision decision = new LicenseDecision(
            LicenseDecision.State.GRACE, "grace period", Set.of("warehouse"), null);
        assertTrue(decision.allowsModule("warehouse"));
    }

    @Test
    @DisplayName("EMERGENCY_GRACE 状态仍允许已授权模块")
    void emergencyGraceAllowsModule() {
        LicenseDecision decision = new LicenseDecision(
            LicenseDecision.State.EMERGENCY_GRACE, "emergency", Set.of("warehouse"), null);
        assertTrue(decision.allowsModule("warehouse"));
    }

    @Test
    @DisplayName("DISABLED 状态拒绝所有模块")
    void disabledRejectsAll() {
        LicenseDecision decision = new LicenseDecision(
            LicenseDecision.State.DISABLED, "disabled", Set.of("warehouse"), null);
        assertFalse(decision.allowsModule("warehouse"));
    }

    @Test
    @DisplayName("NETWORK_ERROR 状态拒绝所有模块")
    void networkErrorRejectsAll() {
        LicenseDecision decision = new LicenseDecision(
            LicenseDecision.State.NETWORK_ERROR, "net error", Set.of("warehouse"), null);
        assertFalse(decision.allowsModule("warehouse"));
    }

    @Test
    @DisplayName("allowsPaidModules 在有效状态返回 true")
    void allowsPaidInValidStates() {
        assertTrue(new LicenseDecision(LicenseDecision.State.VALID, "", Set.of(), null).allowsPaidModules());
        assertTrue(new LicenseDecision(LicenseDecision.State.GRACE, "", Set.of(), null).allowsPaidModules());
        assertTrue(new LicenseDecision(LicenseDecision.State.EMERGENCY_GRACE, "", Set.of(), null).allowsPaidModules());
    }

    @Test
    @DisplayName("allowsPaidModules 在无效状态返回 false")
    void deniesPaidInInvalidStates() {
        assertFalse(new LicenseDecision(LicenseDecision.State.DISABLED, "", Set.of(), null).allowsPaidModules());
        assertFalse(new LicenseDecision(LicenseDecision.State.NETWORK_ERROR, "", Set.of(), null).allowsPaidModules());
        assertFalse(new LicenseDecision(LicenseDecision.State.AUTH_DENIED, "", Set.of(), null).allowsPaidModules());
        assertFalse(new LicenseDecision(LicenseDecision.State.NOT_CONFIGURED, "", Set.of(), null).allowsPaidModules());
    }

    @Test
    @DisplayName("notConfigured 工厂方法产生 NOT_CONFIGURED 状态")
    void notConfiguredFactory() {
        LicenseDecision decision = LicenseDecision.notConfigured();
        assertEquals(LicenseDecision.State.NOT_CONFIGURED, decision.state());
        assertFalse(decision.allowsPaidModules());
    }

    @Test
    @DisplayName("disabled 工厂方法保留原因")
    void disabledFactoryKeepsReason() {
        LicenseDecision decision = LicenseDecision.disabled("被管理员关闭");
        assertEquals(LicenseDecision.State.DISABLED, decision.state());
        assertEquals("被管理员关闭", decision.reason());
    }

    @Test
    @DisplayName("null reason 规范化为空字符串")
    void nullReasonNormalized() {
        LicenseDecision decision = new LicenseDecision(LicenseDecision.State.VALID, null, Set.of(), null);
        assertEquals("", decision.reason());
    }

    @Test
    @DisplayName("null modules 规范化为空集合")
    void nullModulesNormalized() {
        LicenseDecision decision = new LicenseDecision(LicenseDecision.State.VALID, "ok", null, null);
        assertTrue(decision.modules().isEmpty());
    }

    @Test
    @DisplayName("modules 集合不可变")
    void modulesImmutable() {
        LicenseDecision decision = new LicenseDecision(
            LicenseDecision.State.VALID, "ok", Set.of("warehouse"), null);
        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> decision.modules().add("hacked"));
    }
}
