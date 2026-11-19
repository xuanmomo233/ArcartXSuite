package xuanmo.arcartxsuite.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

/**
 * 模块数字签名验证器（Ed25519）。
 * <p>
 * 验证 module.yml 中的 {@code signature} 字段是否为作者私钥对以下 payload 的签名：
 * <pre>id + "\n" + version + "\n" + mainClass + "\n" + depends + "\n" + softDepends + "\n" + externalDepends</pre>
 * 其中 depends / softDepends / externalDepends 以逗号分隔。
 * <p>
 * <b>注意：payload v2 自本版本起包含依赖声明字段，旧版（仅 id:version:mainClass）的签名需重新生成。</b>
 * <p>
 * 公钥通过 {@code config.yml} 的 {@code module-signature-public-keys} 配置注入（支持多个公钥）。
 * 如果公钥未配置，则跳过验证（向后兼容）。
 */
public final class ModuleSignatureVerifier {

    private static final int MAX_LOCAL_JAR_BYTES = 128 * 1024 * 1024;
    private static final int ED25519_SIGNATURE_BYTES = 64;
    private static final byte[] LOCAL_JAR_DOMAIN = "AXS local module JAR v1\0".getBytes(StandardCharsets.UTF_8);

    private final List<PublicKey> publicKeys;
    private final boolean required;
    private final Logger logger;

    /**
     * @param publicKeyBase64List Base64 编码的 Ed25519 公钥列表；null 或空表示不启用验证
     * @param logger              日志输出
     */
    public ModuleSignatureVerifier(List<String> publicKeyBase64List, Logger logger) {
        this.logger = logger;
        this.required = publicKeyBase64List != null && !publicKeyBase64List.isEmpty();
        if (!required) {
            this.publicKeys = Collections.emptyList();
            return;
        }
        List<PublicKey> keys = new ArrayList<>();
        for (String base64 : publicKeyBase64List) {
            if (base64 == null || base64.isBlank()) continue;
            try {
                byte[] decoded = Base64.getDecoder().decode(base64.trim());
                KeyFactory kf = KeyFactory.getInstance("Ed25519");
                keys.add(kf.generatePublic(new java.security.spec.X509EncodedKeySpec(decoded)));
            } catch (Exception e) {
                logger.warning("[ModuleSignature] 无法解析 Ed25519 公钥: " + e.getMessage());
            }
        }
        this.publicKeys = Collections.unmodifiableList(keys);
        if (keys.isEmpty()) {
            logger.severe("[ModuleSignature] 已配置签名公钥，但没有可用的 Ed25519 公钥，拒绝加载模块。");
        }
    }

    /** 是否启用了签名验证 */
    public boolean isEnabled() {
        return required;
    }

    /** Returns the exact bytes verified against the detached signature, or null if untrusted. */
    byte[] verifiedLocalJar(Path jar) {
        if (publicKeys.isEmpty()) return null;
        Path sidecar = jar.resolveSibling(jar.getFileName() + ".sig");
        try {
            byte[] signature;
            try (var input = Files.newInputStream(sidecar)) {
                signature = input.readNBytes(ED25519_SIGNATURE_BYTES + 1);
            }
            if (signature.length != ED25519_SIGNATURE_BYTES) {
                throw new IOException("无效的 Ed25519 签名长度");
            }
            long size = Files.size(jar);
            if (size <= 0 || size > MAX_LOCAL_JAR_BYTES) throw new IOException("本地 JAR 大小超出验签范围");
            byte[] bytes;
            try (var input = Files.newInputStream(jar)) {
                bytes = input.readNBytes((int) size + 1);
            }
            if (bytes.length != size) throw new IOException("读取期间 JAR 大小发生变化");
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            for (PublicKey key : publicKeys) {
                Signature verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(key);
                verifier.update(LOCAL_JAR_DOMAIN);
                verifier.update(digest);
                if (verifier.verify(signature)) return bytes;
            }
            logger.warning("[ModuleSignature] 本地 JAR 签名不匹配: " + jar.getFileName());
        } catch (IOException | GeneralSecurityException e) {
            logger.warning("[ModuleSignature] 无法验签本地 JAR " + jar.getFileName() + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * 验证模块描述符的签名。
     * <p>
     * 遍历所有配置的公钥，只要有一个验证通过即算成功。
     *
     * @return true = 验证通过（或无公钥不验证）；false = 签名缺失或验证失败
     */
    public boolean verify(ModuleDescriptor descriptor) {
        if (!required) return true;
        if (publicKeys.isEmpty()) return false;
        String sigB64 = descriptor.signature();
        if (sigB64 == null || sigB64.isBlank()) {
            logger.warning("[ModuleSignature] 模块 " + descriptor.id() + " 缺少 signature，拒绝加载。");
            return false;
        }
        try {
            byte[] signature = Base64.getDecoder().decode(sigB64.trim());
            byte[] payload = buildPayload(descriptor).getBytes(StandardCharsets.UTF_8);
            for (PublicKey pk : publicKeys) {
                Signature sig = Signature.getInstance("Ed25519");
                sig.initVerify(pk);
                sig.update(payload);
                if (sig.verify(signature)) {
                    return true;
                }
            }
            logger.warning("[ModuleSignature] 模块 " + descriptor.id() + " 签名验证失败，拒绝加载。");
            return false;
        } catch (Exception e) {
            logger.warning("[ModuleSignature] 模块 " + descriptor.id() + " 签名验证异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 构建签名 payload（v2）。
     * <p>
     * 使用换行符分隔各字段，依赖列表以逗号连接。
     * 相比 v1（{@code id:version:mainClass}），v2 额外覆盖 depends / softDepends / externalDepends，
     * 防止攻击者篡改依赖声明影响加载顺序或导致加载失败。
     */
    private static String buildPayload(ModuleDescriptor descriptor) {
        return descriptor.id() + "\n"
            + descriptor.version() + "\n"
            + descriptor.mainClass() + "\n"
            + String.join(",", descriptor.depends()) + "\n"
            + String.join(",", descriptor.softDepends()) + "\n"
            + String.join(",", descriptor.externalDepends());
    }
}
