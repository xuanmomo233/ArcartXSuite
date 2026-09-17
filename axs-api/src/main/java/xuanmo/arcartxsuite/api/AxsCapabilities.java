package xuanmo.arcartxsuite.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;
import xuanmo.arcartxsuite.api.capability.PublicCapability;

/**
 * AXS 公开能力门面：外部 Bukkit 插件访问所有模块公开 API 的统一入口。
 * <p>
 * 使用方式：
 * <pre>{@code
 * MapNavigable map = AxsCapabilities.get(MapNavigable.class);
 * if (map != null) {
 *     map.openMenuFor(player, targetId);
 * }
 * }</pre>
 * <p>
 * 只有接口标注 {@link PublicCapability} 的类型对外可见；插件需
 * {@code depend: [ArcartXSuite]}（或 softdepend）+ compileOnly axs-api。
 * 模块热卸载后对应 capability 自动返回 {@code null}。
 */
public final class AxsCapabilities {

    private AxsCapabilities() {
    }

    /** 本体注入的能力查找器。 */
    @FunctionalInterface
    public interface Lookup {
        <T> @Nullable T get(@NotNull Class<T> capabilityType);
    }

    private static volatile @Nullable Lookup lookup;

    /**
     * 获取公开 capability 实例。
     *
     * @param capabilityType capability 接口类型（必须标注 {@link PublicCapability}）
     * @return 实现实例；类型未公开、未注册或本体未就绪时返回 {@code null}
     */
    public static <T> @Nullable T get(@NotNull Class<T> capabilityType) {
        if (capabilityType == null
            || !capabilityType.isAnnotationPresent(PublicCapability.class)) {
            return null;
        }
        Lookup current = lookup;
        return current == null ? null : current.get(capabilityType);
    }

    /**
     * 判断指定公开 capability 当前是否可用。
     *
     * @param capabilityType capability 接口类型
     * @return {@code true} 表示已注册且对外可见
     */
    public static boolean has(@NotNull Class<?> capabilityType) {
        return get(capabilityType) != null;
    }

    /**
     * 内部方法：由本体注入能力查找器（模块注册表）。
     *
     * @param value 查找器，{@code null} 表示本体关闭、清空门面
     */
    @ApiStability.Internal
    public static void setLookup(@Nullable Lookup value) {
        lookup = value;
    }
}
