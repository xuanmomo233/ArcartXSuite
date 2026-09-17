package xuanmo.arcartxsuite.api.capability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 公开 Capability 标记。
 * <p>
 * 打在该注解标记的 capability 接口，才能通过 {@code AxsCapabilities.get(Class)} 被外部
 * Bukkit 插件获取；未标记的类型对外一律返回 {@code null}（模块内部 getCapability 不受限）。
 * <p>
 * 公开 capability 的约定：入参用富对象（spec/record），阻塞操作返回
 * {@link java.util.concurrent.CompletableFuture}，并在 javadoc 注明线程期望。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PublicCapability {
}
