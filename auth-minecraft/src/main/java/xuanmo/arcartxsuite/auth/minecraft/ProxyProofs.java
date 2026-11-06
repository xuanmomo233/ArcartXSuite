package xuanmo.arcartxsuite.auth.minecraft;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Fixed Ed25519 protocol, not an OAuth token. No algorithm negotiation or remote key discovery. */
public final class ProxyProofs {
    public record Trust(String issuer,PublicKey key,Set<String> providers) {
        public Trust { providers=Set.copyOf(providers); }
    }
    public record Identity(UUID profile,String name,String provider,long authenticatedAt) {}
    public record Target(String realm,String audience,UUID connection,UUID challenge) {}
    private static final Base64.Encoder ENCODER=Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER=Base64.getUrlDecoder();
    private ProxyProofs() {}
    public static PublicKey publicKey(String encoded) throws Exception {
        if(encoded==null || encoded.length()>1024) throw new IllegalArgumentException("Invalid public key");
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
    }
    private static byte[] signedBytes(String header,String payload) {
        return ("AXS-MC-PROOF-V1\n"+header+"."+payload).getBytes(StandardCharsets.US_ASCII);
    }
    public static String sign(String issuer,PrivateKey key,Identity identity,Target target,long now) throws Exception {
        var header=new JsonObject(); header.addProperty("issuer",issuer); header.addProperty("version",1);
        var body=new JsonObject();
        body.addProperty("profile",identity.profile().toString()); body.addProperty("name",identity.name());
        body.addProperty("provider",identity.provider()); body.addProperty("authTime",identity.authenticatedAt());
        body.addProperty("realm",target.realm()); body.addProperty("audience",target.audience());
        body.addProperty("connection",target.connection().toString()); body.addProperty("challenge",target.challenge().toString());
        body.addProperty("issuedAt",now); body.addProperty("expiresAt",now+30_000); body.addProperty("id",UUID.randomUUID().toString());
        String h=ENCODER.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
        String p=ENCODER.encodeToString(body.toString().getBytes(StandardCharsets.UTF_8));
        Signature signature=Signature.getInstance("Ed25519"); signature.initSign(key); signature.update(signedBytes(h,p));
        return h+"."+p+"."+ENCODER.encodeToString(signature.sign());
    }
    public static Identity verify(String token,List<Trust> trusted,UUID profile,String name,Target expected,long now) throws Exception {
        if(token==null || token.length()>4096 || trusted.size()>8) throw new IllegalArgumentException("Invalid proxy proof");
        String[] parts=token.split("\\.",-1);
        if(parts.length!=3 || parts[0].length()>512 || parts[1].length()>3072) throw new IllegalArgumentException("Invalid envelope");
        var header=JsonParser.parseString(new String(DECODER.decode(parts[0]),StandardCharsets.UTF_8)).getAsJsonObject();
        if(header.get("version").getAsInt()!=1) throw new IllegalArgumentException("Unsupported proof protocol");
        String issuer=header.get("issuer").getAsString();
        byte[] signature=DECODER.decode(parts[2]);
        if(signature.length!=64) throw new IllegalArgumentException("Invalid signature length");
        Trust verified=null;
        for(Trust trust:trusted) {
            if(!trust.issuer().equals(issuer)) continue;
            Signature check=Signature.getInstance("Ed25519"); check.initVerify(trust.key()); check.update(signedBytes(parts[0],parts[1]));
            if(check.verify(signature)) { verified=trust; break; }
        }
        if(verified==null) throw new SignatureException("Untrusted proxy proof");
        var body=JsonParser.parseString(new String(DECODER.decode(parts[1]),StandardCharsets.UTF_8)).getAsJsonObject();
        String provider=body.get("provider").getAsString();
        long issued=body.get("issuedAt").getAsLong(),expires=body.get("expiresAt").getAsLong(),auth=body.get("authTime").getAsLong();
        if(!verified.providers().contains(provider) || auth<0 || issued<0 || expires<0 || issued>now+5000 || expires<=now-5000 || expires<=issued || expires-issued>30_000
                || auth>issued+5000 || issued-auth>43_200_000L || !UUID.fromString(body.get("profile").getAsString()).equals(profile)
                || !body.get("name").getAsString().equalsIgnoreCase(name) || !body.get("realm").getAsString().equals(expected.realm())
                || !body.get("audience").getAsString().equals(expected.audience())
                || !UUID.fromString(body.get("connection").getAsString()).equals(expected.connection())
                || !UUID.fromString(body.get("challenge").getAsString()).equals(expected.challenge()))
            throw new SignatureException("Proxy proof binding mismatch");
        UUID.fromString(body.get("id").getAsString());
        return new Identity(profile,name,provider,auth);
    }
}
