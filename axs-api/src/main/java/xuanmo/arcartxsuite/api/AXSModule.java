package xuanmo.arcartxsuite.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;

/**
 * 可插拔模块的生命周期接口。
 * <p>
 * 每个独立模块 Jar 需提供一个实现此接口的主类，
 * 并在 {@code module.yml} 的 {@code main} 字段中声明。
 */
public interface AXSModule {

    /**
     * 模块描述符，提供元数据信息。
     */
    ModuleDescriptor descriptor();

    /**
     * 模块声明的配置同步与诊断规约列表。
     * <p>
     * 默认返回空列表，表示模块不参与智能配置体检系统。
     * 模块可返回多个 {@link ModuleConfigSpec}，用于覆盖主 yml 与附属 yml。
     * 引擎会在 {@link #onEnable} 之前以 dry-run 方式跑结构 / 类型 / 迁移 / 校验诊断，
     * 报告写入 {@code plugins/ArcartXSuite/diagnosis/}，由玩家通过命令显式应用。
     */
    default List<ModuleConfigSpec> configSpecs() {
        return List.of();
    }

    /**
     * 启用模块。宿主在密码验证和依赖检查通过后调用此方法。
     *
     * @param context 模块上下文，提供基础设施访问
     * @return 启动完成信号。{@code true} 表示服务已就绪，异常完成表示启动失败
     */
    CompletionStage<Boolean> onEnable(ModuleContext context) throws Exception;

    /**
     * 禁用模块，释放所有资源（事件、任务、连接池等）。
     */
    void onDisable();

    /**
     * 停用后后台资源退出的完成信号。宿主等待此信号后才关闭模块 ClassLoader，
     * 不在调用 onDisable 的线程上等待。完成前不得再向主线程提交业务回调。
     */
    default CompletionStage<Void> shutdownCompletion() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 热重载模块配置，不卸载 ClassLoader。
     *
     * @return 重载完成信号；失败通过异常完成返回
     */
    CompletionStage<Void> onReload();

    /**
     * Opt in to reloading without removing registrations. onReload must validate the
     * replacement before publishing it and leave the active module usable on failure.
     */
    default boolean reloadsInPlace() {
        return false;
    }

    /**
     * 当前模块是否处于正常运行状态。
     */
    boolean isReady();
}
