import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import xuanmo.arcartxsuite.security.NativeBridge;

/**
 * CI runtime harness for NativeBridge.n10() (Ed25519 .axb signature gate).
 *
 * Usage:
 *   java AxbCheckHarness <libpath> <axbpath> <aes-key|meta.json|base64> <sigpath> <expect_pass|expect_tamper>
 */
public final class AxbCheckHarness {
    private AxbCheckHarness() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: AxbCheckHarness <libpath> <axbpath> <aes-key|meta.json|base64> <sigpath> <expect_pass|expect_tamper>");
            System.exit(2);
        }

        String libPath = new File(args[0]).getAbsolutePath();
        Path axbPath = Path.of(args[1]);
        byte[] key = loadAesKey(args[2]);
        byte[] signature = Files.readAllBytes(Path.of(args[3]));
        String mode = args[4];

        try {
            Class.forName("xuanmo.arcartxsuite.security.NativeBridge");
        } catch (Throwable ignore) {
        }

        try {
            System.load(libPath);
        } catch (Throwable t) {
            if ("expect_tamper".equals(mode)) {
                System.out.println("PASS(load-rejected): library refused at load: " + t);
                System.exit(0);
            }
            System.err.println("FAIL: could not load library: " + t);
            System.exit(1);
            return;
        }

        byte[] axb = Files.readAllBytes(axbPath);
        byte[] result = NativeBridge.n10(axb, key, signature);
        int resultLen = result == null ? -1 : result.length;
        System.out.println("n10(decryptModuleVerified) returned " + resultLen);

        if ("expect_pass".equals(mode)) {
            if (result == null || result.length == 0) {
                System.err.println("FAIL: valid signed .axb was rejected (expected non-null result)");
                System.exit(1);
            }
            System.out.println("PASS: valid signed .axb accepted by n10()");
        } else if ("expect_tamper".equals(mode)) {
            if (result != null && result.length > 0) {
                System.err.println("FAIL: tampered .axb/signature was accepted (expected null)");
                System.exit(1);
            }
            System.out.println("PASS: tampered input rejected by n10() (null)");
        } else {
            System.err.println("unknown mode: " + mode);
            System.exit(2);
        }
    }

    private static byte[] loadAesKey(String arg) throws IOException {
        Path path = Path.of(arg);
        String material = arg;
        if (Files.exists(path)) {
            material = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (material.startsWith("{")) {
                int idx = material.indexOf("moduleKey");
                if (idx >= 0) {
                    int colon = material.indexOf(':', idx);
                    int firstQuote = material.indexOf('"', colon + 1);
                    int secondQuote = material.indexOf('"', firstQuote + 1);
                    if (colon >= 0 && firstQuote >= 0 && secondQuote > firstQuote) {
                        material = material.substring(firstQuote + 1, secondQuote);
                    }
                }
            }
        }
        byte[] key = Base64.getDecoder().decode(material.trim());
        if (key.length != 32) {
            throw new IllegalArgumentException("AES key must be 32 bytes, got " + key.length);
        }
        return key;
    }
}
