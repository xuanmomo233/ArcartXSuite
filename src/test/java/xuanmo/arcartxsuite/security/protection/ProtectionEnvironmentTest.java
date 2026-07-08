package xuanmo.arcartxsuite.security.protection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProtectionEnvironmentTest {

    @AfterEach
    void reset() {
        ProtectionEnvironment.resetForTests();
    }

    @Test
    void cleanEnvironmentAllowsDecryption() {
        assertTrue(ProtectionEnvironment.ensureCleanEnvironment());
    }

    @Test
    void compromisedEnvironmentBlocksDecryption() {
        ProtectionEnvironment.observeThreats(0, ProtectionEnvironment.NATIVE_FLAG_AGENT, true);
        assertFalse(ProtectionEnvironment.ensureCleanEnvironment());
    }

    @Test
    void javaAgentAloneDoesNotBecomeHardBlock() {
        assertFalse(ProtectionEnvironment.isHardThreatSignal(JvmAntiDebug.THREAT_AGENT, 0));
    }

    @Test
    void nativeAgentAndTamperAreHardThreats() {
        assertTrue(ProtectionEnvironment.isHardThreatSignal(0, ProtectionEnvironment.NATIVE_FLAG_AGENT));
        assertTrue(ProtectionEnvironment.isHardThreatSignal(0, ProtectionEnvironment.NATIVE_FLAG_TAMPER));
    }
}
