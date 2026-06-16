package xuanmo.arcartxsuite.module;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

/**
 * 模块数字签名验证器（Ed25519）。
 * <p>
 * 验证 module.yml 中的 {@code signature} 字段是否为作者私钥对以下 payload 的签名：
 * <pre>id + ":" + version + ":" + mainClass</pre>
 * <p>
 * 公钥通过 {@code config.yml} 的 {@code module-signature-public-key} 配置注入。
 * 如果公钥未配置，则跳过验证（向后兼容）。
 */
public final class ModuleSignatureVerifier {

    private final PublicKey publicKey;
    private final Logger logger;

    /**
     * @param publicKeyBase64 Base64 编码的 32 字节 Ed25519 公钥；null 或空表示不启用验证
     * @param logger          日志输出
     */
    public ModuleSignatureVerifier(String publicKeyBase64, Logger logger) {
        this.logger = logger;
        PublicKey pk = null;
        if (publicKeyBase64 != null && !publicKeyBase64.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(publicKeyBase64.trim());
                KeyFactory kf = KeyFactory.getInstance("Ed25519");
                pk = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(decoded));
            } catch (Exception e) {
                logger.warning("[ModuleSignature] 无法解析 Ed25519 公钥: " + e.getMessage());
            }
        }
        this.publicKey = pk;
    }

    /** 是否启用了签名验证 */
    public boolean isEnabled() {
        return publicKey != null;
    }

    /**
     * 验证模块描述符的签名。
     *
     * @return true = 验证通过（或无公钥不验证）；false = 签名缺失或验证失败
     */
    public boolean verify(ModuleDescriptor descriptor) {
        if (publicKey == null) {
            return true; // 未配置公钥，跳过验证（向后兼容）
        }
        String sigB64 = descriptor.signature();
        if (sigB64 == null || sigB64.isBlank()) {
            logger.warning("[ModuleSignature] 模块 " + descriptor.id() + " 缺少 signature，拒绝加载。");
            return false;
        }
        try {
            byte[] signature = Base64.getDecoder().decode(sigB64.trim());
            byte[] payload = buildPayload(descriptor).getBytes(StandardCharsets.UTF_8);
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(payload);
            boolean ok = sig.verify(signature);
            if (!ok) {
                logger.warning("[ModuleSignature] 模块 " + descriptor.id() + " 签名验证失败，拒绝加载。");
            }
            return ok;
        } catch (Exception e) {
            logger.warning("[ModuleSignature] 模块 " + descriptor.id() + " 签名验证异常: " + e.getMessage());
            return false;
        }
    }

    private static String buildPayload(ModuleDescriptor descriptor) {
        return descriptor.id() + ":" + descriptor.version() + ":" + descriptor.mainClass();
    }
}
