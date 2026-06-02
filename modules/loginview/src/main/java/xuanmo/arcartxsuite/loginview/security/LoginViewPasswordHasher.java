package xuanmo.arcartxsuite.loginview.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class LoginViewPasswordHasher {

    public static final String AXS_ALGORITHM = "AXS_PBKDF2_SHA256";
    private static final int ITERATIONS = 180000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
    }

    public boolean verify(String password, String storedHash, String algorithm) {
        String normalized = algorithm == null ? "" : algorithm.trim().toUpperCase();
        return switch (normalized) {
            case AXS_ALGORITHM -> verifyAXS(password, storedHash);
            case "AUTHME_BCRYPT", "AUTHME_BCRYPT2Y" -> verifyBcrypt(password, storedHash);
            case "AUTHME_SHA256", "SHA256" -> verifyAuthMeSha256(password, storedHash);
            default -> false;
        };
    }

    private boolean verifyAXS(String password, String storedHash) {
        String[] parts = storedHash == null ? new String[0] : storedHash.split("\\$");
        if (parts.length != 3) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[0]);
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[1]);
            byte[] expected = decoder.decode(parts[2]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean verifyBcrypt(String password, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            BCrypt.Result result = BCrypt.verifyer().verify(
                password.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
            );
            return result.verified;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean verifyAuthMeSha256(String password, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !"SHA".equalsIgnoreCase(parts[1])) {
            return false;
        }
        String salt = parts[2];
        String computed = "$SHA$" + salt + "$" + sha256(sha256(password) + salt);
        return MessageDigest.isEqual(
            computed.getBytes(StandardCharsets.UTF_8),
            storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成 LoginView 密码哈希。", exception);
        } finally {
            keySpec.clearPassword();
        }
    }

    private String sha256(String message) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(message.getBytes(StandardCharsets.UTF_8));
            return String.format("%064x", new BigInteger(1, digest));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 不可用。", exception);
        }
    }
}
