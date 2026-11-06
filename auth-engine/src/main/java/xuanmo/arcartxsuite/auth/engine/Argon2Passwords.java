package xuanmo.arcartxsuite.auth.engine;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** One bounded password format; historical hashes are intentionally not accepted. */
public final class Argon2Passwords {
    private static final String PREFIX = "$argon2id$v=19$m=19456,t=2,p=1$";
    private static final SecureRandom RANDOM = new SecureRandom();
    public String hash(char[] password) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt);
        try { return PREFIX + Base64.getEncoder().withoutPadding().encodeToString(salt) + "$"
            + Base64.getEncoder().withoutPadding().encodeToString(derived); }
        finally { Arrays.fill(derived, (byte) 0); }
    }
    public boolean verify(char[] password, String stored) {
        if (stored == null || !stored.startsWith(PREFIX) || stored.length() > 160) return false;
        try {
            String[] fields = stored.substring(PREFIX.length()).split("\\$", -1);
            if (fields.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(fields[0]);
            byte[] expected = Base64.getDecoder().decode(fields[1]);
            if (salt.length != 16 || expected.length != 32) return false;
            byte[] actual = derive(password, salt);
            try { return MessageDigest.isEqual(expected, actual); }
            finally { Arrays.fill(actual, (byte) 0); }
        } catch (IllegalArgumentException e) { return false; }
    }
    private byte[] derive(char[] password, byte[] salt) {
        var params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(19456)
            .withIterations(2).withParallelism(1).withSalt(salt).build();
        var generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] result = new byte[32];
        try { generator.generateBytes(password, result); return result; }
        finally { params.clear(); }
    }
}
