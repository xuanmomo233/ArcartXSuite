package xuanmo.arcartxsuite.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.InvocationTargetException;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class NativeLibraryCompressionTest {
    @Test
    void loadsCompressedNativeLibraries() throws Exception {
        byte[] library = "DLL/SO 原始字节\u0000".repeat(1000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var compressed = new ByteArrayOutputStream();
        try (var xz = new XZOutputStream(compressed, new LZMA2Options(6))) { xz.write(library); }
        assertArrayEquals(library, decrypt(encrypt(3, compressed.toByteArray())));
    }

    @Test
    void rejectsTamperingUnsupportedVersionsAndInvalidCompressedData() throws Exception {
        var compressed = new ByteArrayOutputStream();
        try (var xz = new XZOutputStream(compressed, new LZMA2Options(6))) { xz.write(new byte[]{0, 1, 2}); }
        byte[] encrypted = encrypt(3, compressed.toByteArray());
        encrypted[encrypted.length - 1] ^= 1;
        var tampered = assertThrows(InvocationTargetException.class, () -> decrypt(encrypted));
        assertInstanceOf(javax.crypto.AEADBadTagException.class, tampered.getCause());
        assertThrows(InvocationTargetException.class, () -> decrypt(encrypt(1, new byte[]{1})));
        assertThrows(InvocationTargetException.class, () -> decrypt(encrypt(3, new byte[]{1})));
        assertThrows(InvocationTargetException.class, () -> decrypt(encrypt(2, new byte[]{1})));
        assertThrows(InvocationTargetException.class, () -> decrypt(new byte[4]));
    }

    private static byte[] encrypt(int version, byte[] payload) throws Exception {
        var reconstruct = NativeBridge.class.getDeclaredMethod("reconstructKey");
        reconstruct.setAccessible(true);
        byte[] key = (byte[]) reconstruct.invoke(null);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        var output = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(output)) {
            data.writeBytes("AXNE");
            data.writeInt(version);
            data.write(iv);
            data.write(cipher.doFinal(payload));
        }
        return output.toByteArray();
    }

    private static byte[] decrypt(byte[] encrypted) throws Exception {
        var decrypt = NativeBridge.class.getDeclaredMethod("decryptNativeLib", byte[].class);
        decrypt.setAccessible(true);
        return (byte[]) decrypt.invoke(null, (Object) encrypted);
    }
}
