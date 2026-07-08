import java.io.File;
import xuanmo.arcartxsuite.security.NativeBridge;

/**
 * CI 运行时自校验 harness（仅测试用）。
 *
 * 流程：System.load 真实构建 + 已注入自哈希的 native 库 → 调用
 * NativeBridge.n5()（initProtection，内部先执行 verify_native_self_integrity）。
 *   - initProtection 返回 -1  ⇔ 自校验判定库被篡改（fail-closed 生效）
 *   - 返回 !=-1（0 或 -2）      ⇔ 自校验通过（-2 只是后续密钥初始化未就绪，与自校验无关）
 *
 * 用法：java SelfCheckHarness <libpath> <expect_pass|expect_tamper>
 */
public class SelfCheckHarness {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: SelfCheckHarness <libpath> <expect_pass|expect_tamper>");
            System.exit(2);
        }
        String lib = new File(args[0]).getAbsolutePath();
        String mode = args[1];

        // 确保 NativeBridge 已被应用类加载器定义，供 JNI_OnLoad 的 FindClass 命中。
        try {
            Class.forName("xuanmo.arcartxsuite.security.NativeBridge");
        } catch (Throwable ignore) {
        }

        try {
            System.load(lib);
        } catch (Throwable t) {
            if ("expect_tamper".equals(mode)) {
                System.out.println("PASS(load-rejected): tampered library refused at load: " + t);
                System.exit(0);
            }
            System.err.println("FAIL: could not load library: " + t);
            System.exit(1);
            return;
        }

        int r = NativeBridge.n5();
        System.out.println("n5(initProtection) returned " + r);

        if ("expect_pass".equals(mode)) {
            if (r == -1) {
                System.err.println("FAIL: self-check REJECTED an untampered, correctly-injected library (r=-1)");
                System.exit(1);
            }
            System.out.println("PASS: untampered library accepted by self-check (r=" + r + "; -1 would mean tamper)");
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
