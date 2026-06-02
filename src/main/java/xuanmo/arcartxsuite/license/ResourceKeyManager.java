package xuanmo.arcartxsuite.license;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class ResourceKeyManager {

    private final Map<String, byte[]> moduleKeys = new ConcurrentHashMap<>();

    public void update(LicenseTicket ticket) {
        moduleKeys.clear();
        if (ticket == null) {
            return;
        }
        for (Map.Entry<String, LicenseTicket.ResourceKeyEnvelope> entry : ticket.resourceKeys().entrySet()) {
            try {
                byte[] key = unwrap(ticket, entry.getKey(), entry.getValue());
                moduleKeys.put(entry.getKey(), key);
            } catch (Exception ignored) {
            }
        }
    }

    public byte[] keyFor(String moduleId) {
        byte[] key = moduleKeys.get(moduleId);
        return key == null ? null : key.clone();
    }

    public boolean hasKey(String moduleId) {
        return moduleKeys.containsKey(moduleId);
    }

    private byte[] unwrap(LicenseTicket ticket, String moduleId, LicenseTicket.ResourceKeyEnvelope envelope) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.DECRYPT_MODE,
            new SecretKeySpec(wrappingKey(ticket, moduleId), "AES"),
            new GCMParameterSpec(128, LicenseVerifier.base64UrlDecode(envelope.iv()))
        );
        return cipher.doFinal(LicenseVerifier.base64UrlDecode(envelope.wrapped()));
    }

    private byte[] wrappingKey(LicenseTicket ticket, String moduleId) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String subject = ticket.subjectId() == null || ticket.subjectId().isBlank() ? ticket.licenseId() : ticket.subjectId();
        String material = "AXS-RK|" + subject + "|" + ticket.installId() + "|"
            + ticket.fingerprintHash() + "|" + moduleId + "|" + ticket.localSaltHash();
        return digest.digest(material.getBytes(StandardCharsets.UTF_8));
    }

    static boolean constantTimeEquals(byte[] left, byte[] right) {
        return left != null && right != null && MessageDigest.isEqual(left, right);
    }

    static byte[] copyOf(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
