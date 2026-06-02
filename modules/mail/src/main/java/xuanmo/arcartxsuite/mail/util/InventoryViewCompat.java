package xuanmo.arcartxsuite.mail.util;

import java.lang.reflect.Method;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.Inventory;

/**
 * Paper 1.21+ 将 InventoryView 从 abstract class 改为 interface，
 * 导致编译对 spigot-api:1.20.1 的字节码使用 invokevirtual 调用时
 * 抛出 IncompatibleClassChangeError。此工具类通过反射绕过该问题。
 */
public final class InventoryViewCompat {

    private static final Method GET_VIEW;
    private static final Method GET_TOP_INVENTORY;

    static {
        try {
            GET_VIEW = InventoryEvent.class.getMethod("getView");
            GET_TOP_INVENTORY = Class.forName("org.bukkit.inventory.InventoryView")
                    .getMethod("getTopInventory");
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private InventoryViewCompat() {
    }

    public static Inventory getTopInventory(InventoryEvent event) {
        try {
            Object view = GET_VIEW.invoke(event);
            return (Inventory) GET_TOP_INVENTORY.invoke(view);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("InventoryView compat failure", e);
        }
    }
}
