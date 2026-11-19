package xuanmo.arcartxsuite.bridge.potion;

import java.lang.reflect.Method;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI.PotionDrink;

/** 从额外槽位取单瓶药水的副本，委托服务器原版完成效果与返还容器。 */
public final class NativePotionDrink {
    private NativePotionDrink() {}

    public static PotionDrink prepare(Player player, ItemStack item) throws ReflectiveOperationException {
        Class<?> server = player.getServer().getClass();
        Class<?> craftStack = Class.forName(server.getPackageName() + ".inventory.CraftItemStack",
            false, server.getClassLoader());
        Method asNative = craftStack.getMethod("asNMSCopy", ItemStack.class);
        Object stack = asNative.invoke(null, item);
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object level = player.getWorld().getClass().getMethod("getHandle").invoke(player.getWorld());
        Class<?> stackType = asNative.getReturnType();
        Method asBukkit = craftStack.getMethod("asBukkitCopy", stackType);
        Method finish = finishMethod(stackType, level, handle);
        return () -> {
            try {
                Object remaining = finish.invoke(stack, level, handle);
                return (ItemStack) asBukkit.invoke(null, remaining);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("原版药水饮用结束处理失败", exception);
            }
        };
    }

    private static Method finishMethod(Class<?> stackType, Object level, Object player) throws NoSuchMethodException {
        // Mojang 映射、Forge SRG 映射和 Spigot 混淆名。
        for (Method method : stackType.getMethods()) {
            String name = method.getName();
            if (!name.equals("finishUsingItem") && !name.equals("m_41671_") && !name.equals("a")) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getReturnType() == stackType && parameters.length == 2
                && parameters[0].isInstance(level) && parameters[1].isInstance(player)) {
                return method;
            }
        }
        throw new NoSuchMethodException(stackType.getName() + ".finishUsingItem");
    }
}
