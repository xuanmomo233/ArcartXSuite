import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import xuanmo.arcartxsuite.security.NativeBridge;

public final class RotationCheckHarness {
    public static void main(String[] args) throws Exception {
        Class.forName(NativeBridge.class.getName());
        System.load(Path.of(args[0]).toAbsolutePath().toString());
        require(NativeBridge.n5() == 0, "native init must succeed before signature assertions");
        require(NativeBridge.responseVerifyActive(), "response verification must be active");
        Path fixture = Path.of(args[1]);
        byte[] axb = Files.readAllBytes(fixture.resolve("module.axb"));
        byte[] aes = Files.readAllBytes(fixture.resolve("module-aes.bin"));
        byte[] jar = Files.readAllBytes(fixture.resolve("module.jar"));
        require(Arrays.equals(jar, NativeBridge.n10(axb, aes, Files.readAllBytes(fixture.resolve("module-new.sig")))),
            "new AXB signature must recover the expected JAR");
        require(NativeBridge.n10(axb, aes, Files.readAllBytes(fixture.resolve("module-old.sig"))) == null,
            "old AXB signature must be rejected for the same ciphertext");
        long timestamp = Long.parseLong(Files.readString(fixture.resolve("timestamp.txt")));
        byte[] body = Files.readAllBytes(fixture.resolve("response.json"));
        byte[] responseSignature = Files.readAllBytes(fixture.resolve("response-new.sig"));
        require(NativeBridge.verifyResponseSig(timestamp, body, responseSignature), "new response signature must pass");
        require(!NativeBridge.verifyResponseSig(timestamp, body, Files.readAllBytes(fixture.resolve("response-old.sig"))),
            "old response signature must be rejected for the same timestamp and body");
        require(!NativeBridge.verifyResponseSig(timestamp + 1, body, responseSignature), "changed timestamp must be rejected");
        body[0] ^= 1;
        require(!NativeBridge.verifyResponseSig(timestamp, body, responseSignature), "changed body must be rejected");
        System.out.println("ROTATION_OK native=active axb=new-pass,old-reject response=new-pass,old-reject,tamper-reject");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
