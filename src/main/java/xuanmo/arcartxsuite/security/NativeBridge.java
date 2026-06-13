package xuanmo.arcartxsuite.security;

/**
 * Native JNI 桥接类，提供加密/解密、签名验证等安全操作。
 */
public final class NativeBridge {

    static {
        try {
            System.loadLibrary("axs_native");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[AXS-Native] 无法加载 axs_native 库: " + e.getMessage());
        }
    }

    private NativeBridge() {}

    /** 获取 native 库版本号 */
    public static native int nativeVersion();

    /**
     * 解密受保护的资源（AES-256-GCM + GZIP）。
     *
     * @param encrypted   加密后的资源数据
     * @param keyMaterial 密钥材料
     * @return 解密并解压后的原始数据
     */
    public static native byte[] decryptResource(byte[] encrypted, byte[] keyMaterial);

    /**
     * 验证 Ed25519 签名。
     *
     * @param ticketJson 待验证的 JSON 数据
     * @param signature  签名字节
     * @return 是否验证通过
     */
    public static native boolean verifyTicketSignature(byte[] ticketJson, byte[] signature);

    /**
     * 解包资源密钥（AES-256-GCM unwrap）。
     *
     * @param wrappedKey 加密包装的密钥
     * @param iv         初始化向量
     * @param material   密钥材料
     * @return 解包后的原始密钥
     */
    public static native byte[] unwrapResourceKey(byte[] wrappedKey, byte[] iv, byte[] material);

    /**
     * 环境安全检查（反调试/反篡改）。
     *
     * @return 检查状态码
     */
    public static native int environmentCheck();

    /**
     * 解密云端模块 .axb 文件（AES-256-GCM + GZIP）。
     *
     * @param encryptedAxb 加密的 axb 数据
     * @param key          AES 密钥
     * @param iv           初始化向量
     * @return 解密解压后的 jar 字节
     */
    public static native byte[] decryptModule(byte[] encryptedAxb, byte[] key, byte[] iv);
}
