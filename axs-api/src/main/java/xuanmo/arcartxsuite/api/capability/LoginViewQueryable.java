package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 登录界面查询能力接口（公开 capability）。
 * <p>
 * 由 LoginView 模块实现，供外部插件查询玩家认证状态或要求其重新打开登录界面。
 * 登录成功可订阅 EventBus 主题 {@code axs.loginview.login_success}。
 * <p>
 * 外部插件经 {@code AxsCapabilities.get(LoginViewQueryable.class)} 获取。
 */
@PublicCapability
public interface LoginViewQueryable {

    /**
     * 查询玩家当前是否已通过认证（含 session 恢复判定）。
     * <p>
     * 可从任意线程调用。注意：内存缓存未命中时会同步执行一次数据库
     * session 查询（重启/重连恢复判定），高频路径请缓存结果或异步调用。
     */
    boolean isAuthenticated(@NotNull Player player);

    /**
     * 查询玩家是否已有注册账号。
     * <p>
     * 可从任意线程调用。自建认证模式下每次调用都会执行一次数据库查询
     * （AuthMe 模式走其本地缓存），高频路径请缓存结果或异步调用。
     */
    boolean isRegistered(@NotNull Player player);

    /**
     * 为玩家打开登录界面（已认证且无 force 时为空操作）。
     * <p>
     * 涉及 UI 打开，请在 Bukkit 主线程调用。
     */
    void openFor(@NotNull Player player);

    /**
     * 为玩家打开登录界面。
     * <p>
     * 涉及 UI 打开，请在 Bukkit 主线程调用。
     *
     * @param force true 时即使已认证也强制打开（用于改密等场景）
     */
    void openFor(@NotNull Player player, boolean force);
}
