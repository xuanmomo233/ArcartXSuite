package xuanmo.arcartxsuite.api.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * OP 代理工厂接口。
 * <p>
 * 实现方（如 axs-core 的 ByteBuddy 实现）通过此接口创建代理 CommandSender，
 * 使其 {@code isOp()} 和 {@code hasPermission()} 始终返回 true，
 * 而不修改玩家真实的 OP 状态。
 *
 * @since 1.5.0
 */
public interface OpProxyFactory {

    /**
     * 为玩家创建一个临时 OP 代理 CommandSender。
     * <p>
     * 代理对象的 {@code isOp()} 返回 true，{@code hasPermission(String)} 返回 true，
     * 其他方法委托给原始玩家对象。
     *
     * @param player 原始玩家
     * @return 代理 CommandSender，如果创建失败返回 null
     */
    CommandSender createOpProxy(Player player);

    /**
     * 工厂是否可用（实现库已加载）。
     *
     * @return true 如果代理工厂可以正常工作
     */
    boolean available();
}
