package xuanmo.arcartxsuite.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 哈希工具类，提供 SHA-256 计算的公共方法。
 */
public final class HashUtils {

    private HashUtils() {}

    /**
     * 计算字符串的 SHA-256 哈希（UTF-8 编码）。
     *
     * @param input 输入字符串
     * @return 32 字节哈希值
     */
    public static byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
