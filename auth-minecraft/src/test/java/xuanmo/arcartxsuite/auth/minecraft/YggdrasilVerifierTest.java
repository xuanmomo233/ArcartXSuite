package xuanmo.arcartxsuite.auth.minecraft;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YggdrasilVerifierTest {
    @Test void rejectsAnotherProfileNameAndMalformedUuid() {
        assertEquals(YggdrasilVerifier.Status.INVALID,YggdrasilVerifier.validateResponse("test","Alice","{\"id\":\"123\",\"name\":\"Alice\"}").status());
        assertEquals(YggdrasilVerifier.Status.INVALID,YggdrasilVerifier.validateResponse("test","Alice","{\"id\":\"00000000000000000000000000000001\",\"name\":\"Bob\"}").status());
    }
    @Test void configuredProvidersMustUseHttps() {
        assertThrows(IllegalArgumentException.class,()->new YggdrasilVerifier.Provider("test",URI.create("http://example.com/hasJoined")));
    }
    @Test void invalidInputIsRejectedWithoutNetwork() {
        var verifier=new YggdrasilVerifier(List.of());
        assertEquals(YggdrasilVerifier.Status.INVALID,verifier.verify("Alice&admin=true","123",null).status());
        assertEquals(YggdrasilVerifier.Status.INVALID,verifier.verify("Alice","not-a-server-hash",null).status());
        assertEquals(YggdrasilVerifier.Status.INVALID,verifier.verify("Alice","123","not-an-ip.example").status());
        assertEquals(YggdrasilVerifier.Status.INVALID,verifier.verify("Alice","123","dead.beef").status());
        assertEquals(YggdrasilVerifier.Status.INVALID,verifier.verify("Alice","123","256.0.0.1").status());
        assertEquals(YggdrasilVerifier.Status.NO_MATCH,verifier.verify("Alice","123","127.0.0.1").status());
        assertEquals(YggdrasilVerifier.Status.NO_MATCH,verifier.verify("玩家_123","123",null).status());
    }
}
