package xuanmo.arcartxsuite.auth.protocol;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RpcAuthenticationTest {
    @Test void requestMacBindsNodeTimestampNonceAndExactBody() {
        byte[] key=new byte[32]; byte[] body="原始请求".getBytes(StandardCharsets.UTF_8); UUID nonce=UUID.randomUUID();
        String signed=RpcAuthentication.request(key,"lobby",100,nonce,body);
        assertTrue(RpcAuthentication.matches(signed,RpcAuthentication.request(key,"lobby",100,nonce,body)));
        assertFalse(RpcAuthentication.matches(signed,RpcAuthentication.request(key,"other",100,nonce,body)));
        assertFalse(RpcAuthentication.matches(signed,RpcAuthentication.request(key,"lobby",101,nonce,body)));
        assertFalse(RpcAuthentication.matches(signed,RpcAuthentication.request(key,"lobby",100,UUID.randomUUID(),body)));
        assertFalse(RpcAuthentication.matches(signed,RpcAuthentication.request(key,"lobby",100,nonce,new byte[]{1})));
        assertFalse(RpcAuthentication.matches(signed,RpcAuthentication.response(key,nonce,body)));
    }
    @Test void insecureRemoteHttpAndEmbeddedCredentialsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new CenterClient("http://example.com:25600","node",new byte[32]));
        assertThrows(IllegalArgumentException.class,()->new CenterClient("https://user:password@example.com","node",new byte[32]));
        assertThrows(IllegalArgumentException.class,()->new CenterClient("https://example.com/redirect","node",new byte[32]));
        try(var client=new CenterClient("http://127.0.0.1:25600","node",new byte[32])) { assertNotNull(client); }
    }
}
