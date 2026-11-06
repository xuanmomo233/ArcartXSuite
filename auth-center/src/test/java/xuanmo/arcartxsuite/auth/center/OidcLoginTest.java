package xuanmo.arcartxsuite.auth.center;

import com.google.gson.JsonObject;
import java.net.URI;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class OidcLoginTest {
    @Test void idTokenBindsSignatureIssuerAudienceNonceAndRecentAuthentication() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var key=generator.generateKeyPair();
        URI issuer=URI.create("https://idp.example");
        var provider=new ExternalConfiguration.Provider(issuer,issuer.resolve("/authorize"),issuer.resolve("/token"),
            "axs-center","test","pinned",(RSAPublicKey)key.getPublic());
        long now=1_800_000_000L;
        JsonObject claims=new JsonObject();
        claims.addProperty("iss",issuer.toString()); claims.addProperty("sub","stable-subject"); claims.addProperty("aud","axs-center");
        claims.addProperty("nonce","expected"); claims.addProperty("iat",now); claims.addProperty("exp",now+60); claims.addProperty("auth_time",now);
        String valid=token(claims,key.getPrivate());
        assertEquals("stable-subject",OidcLogin.validate(provider,valid,"expected",now).subject());
        assertThrows(SecurityException.class,()->OidcLogin.validate(provider,valid,"different",now));
        assertThrows(SecurityException.class,()->OidcLogin.validate(provider,valid,"expected",now+60));
        String foreignKey=token(claims,generator.generateKeyPair().getPrivate());
        assertThrows(SecurityException.class,()->OidcLogin.validate(provider,foreignKey,"expected",now));
        claims.addProperty("iss","https://other.example");
        String foreignIssuer=token(claims,key.getPrivate());
        assertThrows(SecurityException.class,()->OidcLogin.validate(provider,foreignIssuer,"expected",now));
        claims.addProperty("iss",issuer.toString()); claims.addProperty("aud","other-client");
        String foreignAudience=token(claims,key.getPrivate());
        assertThrows(SecurityException.class,()->OidcLogin.validate(provider,foreignAudience,"expected",now));
        claims.addProperty("aud","axs-center");
        claims.addProperty("auth_time",now-331);
        String staleLogin=token(claims,key.getPrivate());
        assertThrows(SecurityException.class,()->OidcLogin.validate(provider,staleLogin,"expected",now));
    }
    private static String token(JsonObject claims,PrivateKey key) throws Exception {
        var base64=Base64.getUrlEncoder().withoutPadding();
        String header=base64.encodeToString("{\"alg\":\"RS256\",\"kid\":\"pinned\"}".getBytes(UTF_8));
        String input=header+"."+base64.encodeToString(claims.toString().getBytes(UTF_8));
        var signature=Signature.getInstance("SHA256withRSA");
        signature.initSign(key); signature.update(input.getBytes(UTF_8));
        return input+"."+base64.encodeToString(signature.sign());
    }
}
