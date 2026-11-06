package xuanmo.arcartxsuite.auth.minecraft;

import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProxyProofsTest {
    private final long now=1_794_000_000_000L;
    @Test void verifiesOnlyThePinnedIssuerProviderAndExactConnectionChallenge() throws Exception {
        var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var identity=new ProxyProofs.Identity(UUID.randomUUID(),"玩家_123","microsoft",now-1000);
        var target=new ProxyProofs.Target("realm","node",UUID.randomUUID(),UUID.randomUUID());
        var trust=List.of(new ProxyProofs.Trust("proxy",keys.getPublic(),Set.of("microsoft")));
        String proof=ProxyProofs.sign("proxy",keys.getPrivate(),identity,target,now);
        assertEquals(identity,ProxyProofs.verify(proof,trust,identity.profile(),identity.name(),target,now));
        assertThrows(Exception.class,()->ProxyProofs.verify(proof,trust,identity.profile(),identity.name(),
            new ProxyProofs.Target("other","node",target.connection(),target.challenge()),now));
        assertThrows(Exception.class,()->ProxyProofs.verify(proof,trust,identity.profile(),identity.name(),
            new ProxyProofs.Target("realm","other",target.connection(),target.challenge()),now));
        assertThrows(Exception.class,()->ProxyProofs.verify(proof,trust,identity.profile(),identity.name(),
            new ProxyProofs.Target("realm","node",target.connection(),UUID.randomUUID()),now));
        assertThrows(Exception.class,()->ProxyProofs.verify(proof,trust,UUID.randomUUID(),identity.name(),target,now));
        assertThrows(Exception.class,()->ProxyProofs.verify(proof,trust,identity.profile(),identity.name(),target,now+40_000));
    }
    @Test void rejectsTamperingOtherKeysAndUnauthorizedProviders() throws Exception {
        var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var other=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var identity=new ProxyProofs.Identity(UUID.randomUUID(),"Alice","microsoft",now);
        var target=new ProxyProofs.Target("realm","node",UUID.randomUUID(),UUID.randomUUID());
        String proof=ProxyProofs.sign("proxy",keys.getPrivate(),identity,target,now);
        assertThrows(Exception.class,()->ProxyProofs.verify(proof,List.of(new ProxyProofs.Trust("proxy",other.getPublic(),Set.of("microsoft"))),identity.profile(),"Alice",target,now));
        assertThrows(Exception.class,()->ProxyProofs.verify(proof,List.of(new ProxyProofs.Trust("proxy",keys.getPublic(),Set.of("littleskin"))),identity.profile(),"Alice",target,now));
        String[] fields=proof.split("\\."); byte[] signature=Base64.getUrlDecoder().decode(fields[2]); signature[0]^=1;
        String tampered=fields[0]+"."+fields[1]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        assertThrows(Exception.class,()->ProxyProofs.verify(tampered,List.of(new ProxyProofs.Trust("proxy",keys.getPublic(),Set.of("microsoft"))),identity.profile(),"Alice",target,now));
    }
}
