package xuanmo.arcartxsuite.license;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import xuanmo.arcartxsuite.config.ProtectedResourceStore;
import xuanmo.arcartxsuite.security.MohistCompat;

public final class EncryptedResourceLoader {

    private static final byte[] MAGIC = new byte[] {0x41, 0x58, 0x4C, 0x31};
    private static final String RESOURCE_ROOT = "arcartx/internal/license/";
    private static final int GCM_IV_LENGTH = 12;
    private static final Set<String> PAID_MODULES = LicenseService.PAID_MODULES;

    private final ResourceKeyManager keyManager;

    public EncryptedResourceLoader(ResourceKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    public InputStream open(String moduleId, String resourcePath, ClassLoader loader) throws IOException {
        String normalizedModuleId = normalizeModule(moduleId);
        if (PAID_MODULES.contains(normalizedModuleId)) {
            byte[] key = keyManager.keyFor(normalizedModuleId);
            if (key != null) {
                byte[] encrypted = readLicenseResource(normalizedModuleId, resourcePath, loader);
                if (encrypted != null) {
                    return new ByteArrayInputStream(decrypt(resourcePath, encrypted, key));
                }
            }
        }
        return ProtectedResourceStore.open(resourcePath, loader);
    }

    public boolean hasLicenseResource(String moduleId, String resourcePath, ClassLoader loader) {
        return readLicenseResource(normalizeModule(moduleId), resourcePath, loader) != null;
    }

    private byte[] readLicenseResource(String moduleId, String resourcePath, ClassLoader loader) {
        String encodedPath = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(resourcePath.replace('\\', '/').getBytes(StandardCharsets.UTF_8));
        String path = RESOURCE_ROOT + moduleId + "/" + encodedPath + ".axl";
        ClassLoader effectiveLoader = loader == null ? EncryptedResourceLoader.class.getClassLoader() : loader;
        try (InputStream input = effectiveLoader.getResourceAsStream(path)) {
            if (input != null) return input.readAllBytes();
        } catch (IOException exception) {
            // fall through to MohistCompat
        }
        try (InputStream fallback = MohistCompat.getResourceSafe(path, effectiveLoader)) {
            return fallback == null ? null : fallback.readAllBytes();
        } catch (IOException exception) {
            return null;
        }
    }

    private byte[] decrypt(String resourcePath, byte[] encrypted, byte[] key) throws IOException {
        if (encrypted.length <= MAGIC.length + GCM_IV_LENGTH) {
            throw new IOException("授权资源长度异常: " + resourcePath);
        }
        if (!Arrays.equals(Arrays.copyOf(encrypted, MAGIC.length), MAGIC)) {
            throw new IOException("授权资源头不匹配: " + resourcePath);
        }
        byte[] iv = Arrays.copyOfRange(encrypted, MAGIC.length, MAGIC.length + GCM_IV_LENGTH);
        byte[] payload = Arrays.copyOfRange(encrypted, MAGIC.length + GCM_IV_LENGTH, encrypted.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return ungzip(cipher.doFinal(payload));
        } catch (GeneralSecurityException exception) {
            throw new IOException("授权资源解密失败: " + resourcePath, exception);
        }
    }

    private byte[] ungzip(byte[] compressed) throws IOException {
        try (
            GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed));
            ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private String normalizeModule(String moduleId) {
        return moduleId == null ? "" : moduleId.trim().toLowerCase();
    }
}
