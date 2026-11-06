package xuanmo.arcartxsuite.auth.protocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Distinct request/response domains, exact body binding, no algorithm negotiation. */
public final class RpcAuthentication {
    private RpcAuthentication() {}
    public static byte[] key(String encoded) {
        if(encoded==null || encoded.length()>128) throw new IllegalArgumentException("Missing node key environment variable");
        byte[] key=Base64.getDecoder().decode(encoded);
        if(key.length!=32) { Arrays.fill(key,(byte)0); throw new IllegalArgumentException("Node key must contain 32 bytes"); }
        return key;
    }
    public static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static String request(byte[] key,String node,long time,UUID nonce,byte[] body) {
        return sign(key,"AXS-NODE-REQUEST-V1\nPOST\n"+AuthWire.PATH+"\n"+node+"\n"+time+"\n"+nonce+"\n"+digest(body));
    }
    public static String response(byte[] key,UUID nonce,byte[] body) { return sign(key,"AXS-NODE-RESPONSE-V1\n"+nonce+"\n"+digest(body)); }
    public static String sign(byte[] key,String data) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static boolean matches(String expected,String actual) {
        return actual!=null && actual.length()==43 && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),actual.getBytes(StandardCharsets.US_ASCII));
    }
}
