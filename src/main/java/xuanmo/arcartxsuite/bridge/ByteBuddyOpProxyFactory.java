package xuanmo.arcartxsuite.bridge;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.util.OpProxyFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * 基于 ByteBuddy 的 OP 代理工厂实现。
 * <p>
 * 使用 ByteBuddy 动态生成 CraftPlayer 子类，拦截 isOp() 和 hasPermission() 返回 true，
 * 其他方法委托给原始玩家对象。代理对象仅用于 dispatchCommand，不修改玩家真实 OP 状态。
 */
public final class ByteBuddyOpProxyFactory implements OpProxyFactory {

    private final Logger logger;
    private volatile boolean available;
    private volatile Class<? extends Player> proxyClass;
    private final ConcurrentMap<Player, CommandSender> proxyCache = new ConcurrentHashMap<>();

    public ByteBuddyOpProxyFactory(Logger logger) {
        this.logger = logger;
        tryInitialize();
    }

    private void tryInitialize() {
        try {
            // 延迟初始化：首次调用时创建代理类
            // 不在构造函数中创建，因为 CraftPlayer 可能在此时还未加载
            available = true;
            logger.info("ByteBuddyOpProxyFactory: 已就绪（延迟初始化模式）。");
        } catch (RuntimeException exception) {
            available = false;
            logger.warning("ByteBuddyOpProxyFactory 初始化失败: " + exception.getMessage());
        }
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public CommandSender createOpProxy(Player player) {
        if (!available || player == null) {
            return null;
        }
        try {
            return createProxyInstance(player);
        } catch (RuntimeException exception) {
            logger.warning("ByteBuddyOpProxyFactory: 创建代理失败 - " + exception.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private CommandSender createProxyInstance(Player player) {
        try {
            Class<? extends Player> clazz = ensureProxyClass(player);
            // 通过反射创建代理实例，传入原始 player 作为委托目标
            Object proxy = clazz.getConstructor(Player.class).newInstance(player);
            return (CommandSender) proxy;
        } catch (InstantiationException | IllegalAccessException |
                 InvocationTargetException | NoSuchMethodException exception) {
            throw new RuntimeException("无法实例化代理类", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized Class<? extends Player> ensureProxyClass(Player player) {
        if (proxyClass != null) {
            return proxyClass;
        }
        Class<? extends Player> playerClass = player.getClass();
        try {
            proxyClass = (Class<? extends Player>) new ByteBuddy()
                .subclass(playerClass)
                .method(ElementMatchers.named("isOp"))
                .intercept(FixedValue.value(true))
                .method(ElementMatchers.named("hasPermission").and(ElementMatchers.takesArguments(String.class)))
                .intercept(FixedValue.value(true))
                .method(ElementMatchers.named("hasPermission").and(ElementMatchers.takesArguments(String.class, String.class)))
                .intercept(FixedValue.value(true))
                .method(ElementMatchers.not(ElementMatchers.named("isOp").or(ElementMatchers.named("hasPermission"))))
                .intercept(MethodDelegation.toField("delegate"))
                .defineField("delegate", Player.class, net.bytebuddy.description.modifier.Visibility.PRIVATE)
                .make()
                .load(playerClass.getClassLoader())
                .getLoaded();
            logger.info("ByteBuddyOpProxyFactory: 已生成代理类 " + proxyClass.getName());
            return proxyClass;
        } catch (RuntimeException exception) {
            available = false;
            throw new RuntimeException("无法生成 ByteBuddy 代理类: " + exception.getMessage(), exception);
        }
    }
}
