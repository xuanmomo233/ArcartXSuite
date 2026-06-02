package xuanmo.arcartxsuite.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LicenseVerifierTest {

    private static final String TICKET = "AXS-TICKET-v1.kid-dev-001.eyJwcm90b2NvbCI6IkFYUy1USUNLRVQtdjEiLCJraWQiOiJraWQtZGV2LTAwMSIsImxpY2Vuc2VJZCI6ImxpY190ZXN0XzAwMSIsImFjdGl2YXRpb25JZCI6ImFhMzNlY2Q4LWQxMTctNDk5Mi1iMzE0LWQ4NDJmOWYxZjlhMSIsInByb2R1Y3QiOiJBcmNhcnRYU3VpdGUiLCJlZGl0aW9uIjoiUHJlbWl1bSIsImluc3RhbGxJZCI6InJlbW90ZS1pbnN0YWxsLTAwMSIsImZpbmdlcnByaW50SGFzaCI6InNoYTI1NjpyZW1vdGUtbWFjaGluZS0wMDEiLCJtb2R1bGVzIjpbIndhcmVob3VzZSIsIm1haWwiLCJtYXAiXSwiaXNzdWVkQXQiOjE3Nzg3NTQ3MTY2NDgsImV4cGlyZXNBdCI6MTc3OTM1OTUxNjY0OCwicmVmcmVzaEFmdGVyIjoxNzc4Nzc2MzE2NjQ4LCJvZmZsaW5lR3JhY2VVbnRpbCI6MTc3OTYxODcxNjY0OH0.-_limgM7VWPI9Q2WdHgxfI5juNqE4RHhrv1OQyvbOhzzvTPlJ-mlrdDznANCJ77uw1EITWC7X3X8wSMoSCp4CA";

    @Test
    void verifiesSignedTicket() {
        LicenseVerifier verifier = new LicenseVerifier();

        LicenseDecision decision = verifier.decisionFor(
            TICKET,
            "remote-install-001",
            "sha256:remote-machine-001",
            1778754716648L
        );

        assertEquals(LicenseDecision.State.VALID, decision.state());
        assertTrue(decision.allowsModule("warehouse"));
        assertTrue(decision.allowsModule("mail"));
    }

    @Test
    void rejectsTamperedTicket() {
        LicenseVerifier verifier = new LicenseVerifier();
        String tampered = TICKET.substring(0, TICKET.length() - 2) + "AA";

        assertThrows(
            LicenseVerifier.LicenseVerificationException.class,
            () -> verifier.verifyTicket(tampered, "remote-install-001", "sha256:remote-machine-001", 1778754716648L)
        );
    }

    @Test
    void rejectsFingerprintMismatch() {
        LicenseVerifier verifier = new LicenseVerifier();

        assertThrows(
            LicenseVerifier.LicenseVerificationException.class,
            () -> verifier.verifyTicket(TICKET, "remote-install-001", "sha256:other", 1778754716648L)
        );
    }
}
