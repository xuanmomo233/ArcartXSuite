import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;

import xuanmo.arcartxsuite.security.NativeBridge;

/**
 * RoundTripTest - 加密/解密往返测试（Tier 2：Python 加密 → C++ native 解密）
 *
 * 验证 native 库能正确解密 encrypt-jar.py / selftest-crypto.py 生成的 .enc，
 * 且解密结果与原始 .class 字节完全一致。
 *
 * 前提:
 *   - native 库已编译并位于 classpath 的 /native/axs-native.dll（或 .so）
 *     （CMake 已配置输出到 src/main/resources/native/）
 *   - 已用 embed-keys.py 把真实 root_seed 嵌入 native
 *   - .enc 由【相同 root_seed】的 selftest-crypto.py 生成
 *
 * 编译:
 *   javac -cp axs-core/build/classes/java/main -d build/roundtrip scripts/RoundTripTest.java
 * 运行（注意把 resources 放进 classpath 以便 NativeBridge 找到 dll）:
 *   java -cp "axs-core/build/classes/java/main;src/main/resources;build/roundtrip" \
 *        RoundTripTest test.enc xuanmo.arcartxsuite.security.NativeBridge \
 *        axs-core/build/classes/java/main/xuanmo/arcartxsuite/security/NativeBridge.class
 *   (Linux 把 ';' 换成 ':')
 */
public class RoundTripTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("用法: RoundTripTest <enc文件> <类全名> <原始.class文件>");
            System.exit(2);
        }
        String encFile = args[0];
        String className = args[1];
        String origClassFile = args[2];

        // 1. 触发 NativeBridge 加载 native 库
        if (!NativeBridge.isAvailable()) {
            System.err.println("[!] native 库未加载: " + NativeBridge.getLoadError());
            System.err.println("    请确认 axs-native.dll 在 classpath 的 /native/ 下");
            System.exit(3);
        }
        System.out.println("[*] native 库已加载，版本 n0=" + NativeBridge.n0());

        // 2. 初始化保护（密钥派生 + 自校验）
        int initRc = NativeBridge.n5();
        System.out.println("[*] initProtection (n5) = " + initRc + (initRc == 0 ? " (OK)" : " (FAIL)"));
        if (initRc != 0) {
            System.err.println("[!] 初始化失败：-1=native自校验失败  -2=密钥派生失败");
            System.exit(4);
        }

        // 3. 读取 .enc 与原始字节
        byte[] enc = Files.readAllBytes(Path.of(encFile));
        byte[] original = Files.readAllBytes(Path.of(origClassFile));
        byte[] classNameHash = MessageDigest.getInstance("SHA-256")
                .digest(className.getBytes("UTF-8"));

        // 4. native 解密
        byte[] decrypted = NativeBridge.n6(classNameHash, enc);
        if (decrypted == null) {
            System.err.println("[!] decryptClass (n6) 返回 null —— 解密失败");
            System.err.println("    可能原因: root_seed 不一致 / .enc 由不同密钥加密 / 格式损坏");
            System.exit(5);
        }

        // 5. 比对
        boolean match = Arrays.equals(decrypted, original);
        System.out.println("[*] 原始 " + original.length + "B  解密 " + decrypted.length + "B");
        System.out.println("[" + (match ? "+" : "!") + "] 往返一致: " + (match ? "OK ✓" : "MISMATCH ✗"));
        System.exit(match ? 0 : 6);
    }
}
