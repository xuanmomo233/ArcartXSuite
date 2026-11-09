package xuanmo.arcartxsuite.vanilla.recipe;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

/**
 * Paper 1.20.1 has no Bukkit stonecutter-selection setter. Keep this small native
 * button adapter isolated; never write a synthetic result or consume materials.
 */
public final class StonecutterSelection {
    private StonecutterSelection() { }
    private record Access(Method handle, Method player, Method recipes, Method select) { }
    private static final Map<Class<?>, Access> CACHE = new ConcurrentHashMap<>();
    private static Access access(InventoryView view) throws ReflectiveOperationException {
        Access cached = CACHE.get(view.getClass());
        if (cached != null) return cached;
        Method handle = view.getClass().getMethod("getHandle");
        Method player = view.getPlayer().getClass().getMethod("getHandle");
        Class<?> menu = handle.invoke(view).getClass();
        Method recipes = null, select = null;
        for (Method method : menu.getMethods()) {
            if ((method.getName().equals("getRecipes") || method.getName().equals("m"))
                && method.getParameterCount() == 0 && List.class.isAssignableFrom(method.getReturnType())) recipes = method;
            if ((method.getName().equals("clickMenuButton") || method.getName().equals("b"))
                && method.getReturnType() == boolean.class && method.getParameterCount() == 2
                && method.getParameterTypes()[1] == int.class
                && method.getParameterTypes()[0].isAssignableFrom(player.getReturnType())) select = method;
        }
        if (recipes == null || select == null) throw new NoSuchMethodException("stonecutter native selection");
        Access result = new Access(handle, player, recipes, select);
        CACHE.put(view.getClass(), result);
        return result;
    }
    public static boolean available(InventoryView view) {
        if (!WorkshopType.STONECUTTER.matches(view)) return false;
        try { access(view); return true; } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    public static boolean select(Player player, String key) {
        InventoryView view = player.getOpenInventory();
        if (!WorkshopType.STONECUTTER.matches(view)) return false;
        try {
            Access a = access(view);
            Object menu = a.handle().invoke(view);
            List<?> recipes = (List<?>) a.recipes().invoke(menu);
            for (int i = 0; i < recipes.size(); i++) {
                Object recipe = recipes.get(i).getClass().getMethod("toBukkitRecipe").invoke(recipes.get(i));
                if (recipe instanceof Keyed keyed && keyed.getKey().toString().equals(key)) {
                    boolean selected = (boolean) a.select().invoke(menu, a.player().invoke(player), i);
                    player.updateInventory();
                    return selected;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) { return false; }
        return false;
    }
}
