package xuanmo.arcartxsuite.bridge.potion;

import org.bukkit.Bukkit;

/**
 * 按服务端版本选择 {@link PotionAccessor} 实现。
 * <p>
 * 1.20.2+ 使用 {@link PotionAccessorModern}（反射加载，避免主源集编译时引用 1.21 API），
 * 1.20.1 及以下使用 {@link PotionAccessorLegacy}。
 * <p>
 * 选择策略：解析 Bukkit.getBukkitVersion() 的主次版本号，
 * 1.20.2 及以上 → Modern，否则 → Legacy。
 */
public final class PotionAccessorFactory {

    private static final PotionAccessor INSTANCE = select();

    private PotionAccessorFactory() {
    }

    /** 获取当前服务端版本对应的 PotionAccessor 实例 */
    public static PotionAccessor get() {
        return INSTANCE;
    }

    private static PotionAccessor select() {
        if (isModernVersion()) {
            // 反射加载 Modern 实现（主源集用 1.20.1 编译，无法直接引用）
            try {
                Class<?> clazz = Class.forName(
                    "xuanmo.arcartxsuite.bridge.potion.PotionAccessorModern");
                return (PotionAccessor) clazz.getDeclaredConstructor().newInstance();
            } catch (Throwable ignored) {
                // Modern 类加载失败（理论上不应发生，回退到 Legacy）
            }
        }
        return new PotionAccessorLegacy();
    }

    /** 判断服务端版本是否 >= 1.20.2（getBasePotionType 存在的最低版本） */
    private static boolean isModernVersion() {
        String version;
        try {
            version = Bukkit.getBukkitVersion();
        } catch (Throwable ignored) {
            // Bukkit 尚未初始化（理论上不应在 onEnable 阶段发生），保守使用 Legacy
            return false;
        }
        if (version == null || version.isBlank()) {
            return false;
        }
        // version 形如 "1.20.1-R0.1-SNAPSHOT" 或 "1.21-R0.1-SNAPSHOT"
        int[] parsed = parseVersion(version);
        if (parsed.length < 2) {
            // 无法解析，保守使用 Legacy
            return false;
        }
        int major = parsed[0];
        int minor = parsed[1];
        // 1.20.2+ 或 1.21+
        return (major == 1 && minor >= 21) || (major == 1 && minor == 20 && getPatch(version) >= 2);
    }

    /** 解析版本号的主.次部分，如 "1.20.1-R0.1" → [1, 20, 1] */
    private static int[] parseVersion(String version) {
        String clean = version.split("-")[0].trim();
        String[] parts = clean.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return new int[0];
            }
        }
        return result;
    }

    /** 提取版本号的 patch 部分（第三段），如 "1.20.1" → 1 */
    private static int getPatch(String version) {
        String clean = version.split("-")[0].trim();
        String[] parts = clean.split("\\.");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
