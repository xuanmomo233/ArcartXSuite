package xuanmo.arcartxsuite.api;

import org.bukkit.command.TabExecutor;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderExpansionRegistry;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

/**
 * 注册上下文：提供事件监听器、命令、客户端包处理器、按键、PlaceholderAPI、Capability 等注册能力。
 *
 * @since 1.5.0
 */
public interface RegistrationContext {

    /** 基类启用模块时建立本轮定时任务集合。 */
    default void beginScheduledTasks() { }

    /** 取消本轮任务，返回已经开始的异步回调退出信号，不等待回调完成。 */
    default java.util.concurrent.CompletionStage<Void> cancelScheduledTasks() {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    void registerListener(Listener listener);

    void unregisterListeners();

    void registerCommand(String commandName, TabExecutor executor);

    /**
     * 动态注册模块命令。开启保护时保留已有的无前缀命令，否则优先使用模块命令。
     * 本插件的其他命令始终保留。
     */
    void registerCommand(String commandName, TabExecutor executor, boolean protectExisting);

    /** 为动态命令指定实际动作的权限。null 使用宿主元数据，空字符串清除该权限。 */
    void registerCommand(String commandName, TabExecutor executor, boolean protectExisting, @Nullable String permission);

    /** 注销通过带冲突保护选项的接口注册的命令，并恢复被覆盖的命令。 */
    void unregisterDynamicCommands();

    /** 查询当前注册表以及本模块覆盖的原始绑定。应在服务器主线程调用。 */
    CommandRegistrationInfo inspectCommand(String label);

    record CommandRegistrationInfo(String owner, String moduleAlias, String previousOwner) {}

    @ApiStability.Stable
    @NotNull PlaceholderResolverAPI placeholderResolver();

    @ApiStability.Stable
    @NotNull PlaceholderExpansionRegistry expansionRegistry();

    void registerClientPacketHandler(PacketHandlerSpec spec);

    void registerClientInitializedHandler(ClientInitializedHandler handler);

    @ApiStability.Stable
    void registerKeybindHandler(String keyName, int priority, KeybindHandler handler);

    @ApiStability.Stable
    <T> void registerCapability(Class<T> capabilityType, T implementation);

    @ApiStability.Stable
    @Nullable <T> T getCapability(Class<T> capabilityType);

    @ApiStability.Internal
    @Nullable PropBridgeAPI propBridge();
}
