package xuanmo.arcartxsuite.loginview.auth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * AuthMe 插件 API 桥接器，属于 LoginView 模块的 authme 兼容层。
 * <p>
 * 当 {@code auth.mode} 配置为 {@code authme} 时，LoginView 将登录/注册/改密等
 * 操作委托给 AuthMe；本类封装了对 {@code AuthMeApi} 的安全调用，
 * 在 AuthMe 缺失或调用抛出 {@link LinkageError} 时统一降级为不可用。
 */
public final class AuthMeBridge {

    private fr.xephi.authme.api.v3.AuthMeApi api;

    /**
     * 初始化桥接，探测 AuthMe 插件并获取其 API 实例。
     *
     * @return {@code true} 表示 AuthMe 可用且 API 已就绪
     */
    public boolean initialize() {
        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authMe == null || !authMe.isEnabled()) {
            return false;
        }
        try {
            api = fr.xephi.authme.api.v3.AuthMeApi.getInstance();
            return api != null;
        } catch (Exception | LinkageError exception) {
            api = null;
            return false;
        }
    }

    /**
     * 当前桥接是否可用（AuthMe API 已加载）。
     *
     * @return {@code true} 表示可用
     */
    public boolean available() {
        return api != null;
    }

    /**
     * 判断指定玩家是否已完成 AuthMe 登录验证。
     *
     * @param player 目标玩家
     * @return {@code true} 表示已登录；AuthMe 不可用时返回 {@code false}
     */
    public boolean isAuthenticated(Player player) {
        if (api == null) {
            return false;
        }
        try {
            return api.isAuthenticated(player);
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    /**
     * 判断指定玩家名是否已在 AuthMe 注册。
     *
     * @param playerName 玩家名
     * @return {@code true} 表示已注册；AuthMe 不可用时返回 {@code false}
     */
    public boolean isRegistered(String playerName) {
        if (api == null) {
            return false;
        }
        try {
            return api.isRegistered(playerName);
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    /**
     * 校验指定玩家名与明文密码是否匹配 AuthMe 存储的哈希。
     *
     * @param playerName 玩家名
     * @param password   明文密码
     * @return {@code true} 表示密码正确；AuthMe 不可用时返回 {@code false}
     */
    public boolean checkPassword(String playerName, String password) {
        if (api == null) {
            return false;
        }
        try {
            return api.checkPassword(playerName, password);
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    /**
     * 在 AuthMe 中注册新玩家。
     *
     * @param playerName 玩家名
     * @param password   明文密码
     * @return {@code true} 表示注册成功；AuthMe 不可用时返回 {@code false}
     */
    public boolean registerPlayer(String playerName, String password) {
        if (api == null) {
            return false;
        }
        try {
            return api.registerPlayer(playerName, password);
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    /**
     * 强制将玩家标记为已登录（跳过密码校验）。
     *
     * @param player 目标玩家
     * @return {@code true} 表示操作成功；AuthMe 不可用时返回 {@code false}
     */
    public boolean forceLogin(Player player) {
        if (api == null) {
            return false;
        }
        try {
            api.forceLogin(player);
            return true;
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    /**
     * 修改指定玩家在 AuthMe 中的密码。
     *
     * @param playerName 玩家名
     * @param password   新的明文密码
     * @return {@code true} 表示修改成功；AuthMe 不可用时返回 {@code false}
     */
    public boolean changePassword(String playerName, String password) {
        if (api == null) {
            return false;
        }
        try {
            api.changePassword(playerName, password);
            return true;
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }
}
