package xuanmo.arcartxsuite.util;

import java.util.List;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import xuanmo.arcartxsuite.module.AxsLog;

/**
 * 安全注销 Bukkit 监听器的工具。
 * <p>
 * Bukkit 的 {@link HandlerList#unregisterAll(Listener)} 实现会扫描所有 handler list
 * 中已注册的 {@code RegisteredListener}，对每个调用 {@code getListener().equals(...)}。
 * 若服务器上存在第三方插件通过 {@link java.lang.reflect.Proxy} 注册了监听器，且其
 * {@code InvocationHandler} 对 {@code equals} 返回 {@code null}（未正确处理 Object 方法），
 * Bukkit 在拆箱 {@code boolean} 返回值时会抛出 {@link NullPointerException}，从而中断
 * 整个注销流程，导致后续监听器无法注销、发生监听器泄漏。
 * <p>
 * 本工具逐个监听器捕获异常并记录警告，确保单个坏监听器不影响其余监听器的注销。
 */
public final class ListenerUnregister {

    private ListenerUnregister() {
    }

    /**
     * 安全注销单个监听器。捕获并记录 Bukkit 注销过程中因第三方坏代理监听器
     * 抛出的异常，避免中断同批次其余监听器的注销。
     *
     * @param listener 待注销的监听器
     * @param tag      日志标签（通常是调用方类名），用于定位来源
     */
    public static void safeUnregisterAll(Listener listener, String tag) {
        if (listener == null) {
            return;
        }
        try {
            HandlerList.unregisterAll(listener);
        } catch (RuntimeException e) {
            AxsLog.logger().warning("[" + tag + "] 注销监听器 " + listener.getClass().getName()
                + " 时被第三方监听器干扰（疑似动态代理 equals 返回 null）: " + e.getMessage()
                + "；该监听器可能未完全注销，其余监听器继续处理。");
        }
    }

    /**
     * 安全注销一批监听器。逐个调用 {@link #safeUnregisterAll(Listener, String)}，
     * 单个失败不影响其余。完成后清空列表。
     *
     * @param listeners 待注销的监听器集合（将被清空）
     * @param tag       日志标签
     */
    public static void safeUnregisterAll(List<Listener> listeners, String tag) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (Listener listener : listeners) {
            safeUnregisterAll(listener, tag);
        }
        listeners.clear();
    }
}
