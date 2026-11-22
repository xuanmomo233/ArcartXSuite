import java.io.File;
import xuanmo.arcartxsuite.security.NativeBridge;

/**
 * CI 运行时自校验 harness（仅测试用）。
 *
 * 流程：System.load 真实构建 + 已注入自哈希的 native 库 → 调用
 * NativeBridge.n5()（initProtection，内部先执行 verify_native_self_integrity）。
 *   - initProtection 返回 -1  ⇔ 自校验判定库被篡改（fail-closed 生效）
 *   - 返回 -3                  ⇔ 自校验通过，发布库拒绝未签名的 harness 类目录
 *
 * 用法：java SelfCheckHarness <libpath> <expect_pass|expect_tamper>
 */
public class SelfCheckHarness {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: SelfCheckHarness <libpath> <expect_pass|expect_tamper>");
            System.exit(2);
        }
        File library = new File(args[0]).getAbsoluteFile();
        if (!library.isFile()) {
            System.err.println("Native library path does not identify a file");
            System.exit(2);
        }
        String lib = library.getPath();
        String mode = args[1];

        // 确保 NativeBridge 已被应用类加载器定义，供 JNI_OnLoad 的 FindClass 命中。
        try {
            Class.forName("xuanmo.arcartxsuite.security.NativeBridge");
        } catch (Throwable ignore) {
        }

        try {
            System.load(lib);
        } catch (Throwable t) {
            System.err.println("FAIL: could not load library: " + t);
            System.exit(1);
            return;
        }

        int r = NativeBridge.n5();
        System.out.println("n5(initProtection) returned " + r);

        if ("expect_pass".equals(mode)) {
            if (r != -3) {
                System.err.println("FAIL: expected a clean native library to reject the unsigned class directory (r=" + r + ")");
                System.exit(1);
            }
            System.out.println("PASS: native self-check passed and unsigned class directory was rejected (r=-3)");
        } else if ("expect_tamper".equals(mode)) {
            if (r != -1) {
                System.err.println("FAIL: self-check did NOT reject tampered library (r=" + r + ", expected -1)");
                System.exit(1);
            }
            System.out.println("PASS: tampered library rejected by self-check (r=-1)");
        } else {
            System.err.println("unknown mode: " + mode);
            System.exit(2);
        }
    }
}
