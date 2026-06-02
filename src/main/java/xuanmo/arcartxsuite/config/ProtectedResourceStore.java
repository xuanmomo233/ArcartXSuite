package xuanmo.arcartxsuite.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import xuanmo.arcartxsuite.security.MohistCompat;

public final class ProtectedResourceStore {

    private static final String RESOURCE_ROOT = "arcartx/internal/protected/";
    private static final byte[] MAGIC = new byte[] {0x41, 0x58, 0x52, 0x31};
    private static final int GCM_IV_LENGTH = 12;
    private static final int[] SEED_LEFT = {
        175, 119, 140, 14, 90, 244, 58, 154,
        22, 234, 29, 141, 21, 229, 61, 74,
        181, 98, 76, 164, 206, 128, 205, 200
    };
    private static final int[] SEED_RIGHT = {
        142, 91, 87, 25, 42, 3, 205, 96,
        67, 146, 110, 46, 153, 21, 165, 229,
        114, 152, 36, 68, 209, 105, 189, 51
    };
    private static final byte[] DIGEST_SALT = new byte[] {19, 51, 87, 123, 9, 44, 62, 108};
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

    private ProtectedResourceStore() {
    }

    public static InputStream open(String resourcePath) throws IOException {
        return new ByteArrayInputStream(readBytes(resourcePath));
    }

    public static InputStream open(String resourcePath, ClassLoader loader) throws IOException {
        return new ByteArrayInputStream(readBytes(resourcePath, loader));
    }

    public static String readText(String resourcePath) throws IOException {
        return new String(readBytes(resourcePath), StandardCharsets.UTF_8);
    }

    public static byte[] readBytes(String resourcePath) throws IOException {
        return readBytes(resourcePath, null);
    }

    public static byte[] readBytes(String resourcePath, ClassLoader loader) throws IOException {
        String normalizedPath = normalize(resourcePath);
        String cacheKey = cacheKey(normalizedPath, loader);
        byte[] cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached.clone();
        }

        byte[] decrypted = decrypt(normalizedPath, loader);
        CACHE.put(cacheKey, decrypted.clone());
        return decrypted;
    }

    public static boolean exists(String resourcePath) {
        return exists(resourcePath, null);
    }

    public static boolean exists(String resourcePath, ClassLoader loader) {
        String protectedPath = protectedPath(normalize(resourcePath));
        try (InputStream input = openRawResource(protectedPath, loader)) {
            return input != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static byte[] decrypt(String resourcePath, ClassLoader loader) throws IOException {
        String protectedPath = protectedPath(resourcePath);
        byte[] encrypted;
        try (InputStream protectedInput = openRawResource(protectedPath, loader)) {
            if (protectedInput != null) {
                encrypted = protectedInput.readAllBytes();
            } else {
                encrypted = null;
            }
        }

        if (encrypted == null) {
            try (InputStream fallbackInput = openRawResource(resourcePath, loader)) {
                if (fallbackInput != null) {
                    return fallbackInput.readAllBytes();
                }
            }
            throw new IOException("未找到受保护资源: " + protectedPath);
        }
        if (encrypted.length <= MAGIC.length + GCM_IV_LENGTH) {
            throw new IOException("受保护资源长度异常: " + resourcePath);
        }
        if (!Arrays.equals(Arrays.copyOf(encrypted, MAGIC.length), MAGIC)) {
            throw new IOException("受保护资源头不匹配: " + resourcePath);
        }

        byte[] iv = Arrays.copyOfRange(encrypted, MAGIC.length, MAGIC.length + GCM_IV_LENGTH);
        byte[] payload = Arrays.copyOfRange(encrypted, MAGIC.length + GCM_IV_LENGTH, encrypted.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(deriveKey(), "AES"),
                new GCMParameterSpec(128, iv)
            );
            return ungzip(cipher.doFinal(payload));
        } catch (GeneralSecurityException exception) {
            throw new IOException("受保护资源解密失败: " + resourcePath, exception);
        }
    }

    private static String protectedPath(String resourcePath) {
        String encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(resourcePath.getBytes(StandardCharsets.UTF_8));
        return RESOURCE_ROOT + encoded + ".axb";
    }

    private static InputStream openRawResource(String path, ClassLoader loader) {
        ClassLoader effectiveLoader = loader != null ? loader : ProtectedResourceStore.class.getClassLoader();
        InputStream input = effectiveLoader.getResourceAsStream(path);
        if (input != null) return input;
        return MohistCompat.getResourceSafe(path, effectiveLoader);
    }

    private static byte[] deriveKey() throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(decodeSeed());
            digest.update(DIGEST_SALT);
            return digest.digest();
        } catch (GeneralSecurityException exception) {
            throw new IOException("无法构建资源解密密钥。", exception);
        }
    }

    private static byte[] decodeSeed() {
        byte[] seed = new byte[SEED_LEFT.length];
        for (int index = 0; index < SEED_LEFT.length; index++) {
            int offset = (index * 29 + 17) & 0xFF;
            seed[index] = (byte) (SEED_LEFT[index] ^ SEED_RIGHT[index] ^ offset);
        }
        return seed;
    }

    private static byte[] ungzip(byte[] compressed) throws IOException {
        try (
            GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed));
            ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private static String cacheKey(String normalizedPath, ClassLoader loader) {
        if (loader == null) {
            return normalizedPath;
        }
        return System.identityHashCode(loader) + ":" + normalizedPath;
    }

    private static String normalize(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("resourcePath cannot be blank");
        }
        return resourcePath.replace('\\', '/');
    }
}
