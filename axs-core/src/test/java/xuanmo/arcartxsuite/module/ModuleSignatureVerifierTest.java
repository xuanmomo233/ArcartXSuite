package xuanmo.arcartxsuite.module;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleSignatureVerifierTest {
    @TempDir Path directory;

    @Test
    void localSignatureAuthenticatesAllArtifactBytes() throws Exception {
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var verifier = new ModuleSignatureVerifier(
            List.of(Base64.getEncoder().encodeToString(key.getPublic().getEncoded())), Logger.getAnonymousLogger());
        Path jar = directory.resolve("module.jar");
        byte[] original = "original module bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(jar, original);
        assertNull(verifier.verifiedLocalJar(jar));

        var signer = Signature.getInstance("Ed25519");
        signer.initSign(key.getPrivate());
        signer.update("AXS local module JAR v1\0".getBytes(StandardCharsets.UTF_8));
        signer.update(MessageDigest.getInstance("SHA-256").digest(original));
        Files.write(directory.resolve("module.jar.sig"), signer.sign());
        assertArrayEquals(original, verifier.verifiedLocalJar(jar));

        byte[] modified = original.clone();
        modified[0] ^= 1;
        Files.write(jar, modified);
        assertNull(verifier.verifiedLocalJar(jar));
    }
}
