package xuanmo.arcartxsuite.menu.listener;

import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.menu.config.MenuCommandBinding;
import xuanmo.arcartxsuite.menu.config.MenuItemBinding;
import xuanmo.arcartxsuite.menu.service.MenuConditionEvaluator;
import xuanmo.arcartxsuite.menu.service.MenuService;

/**
 * 菜单绑定监听器，监听命令预处理和物品交互事件以触发菜单打开。
 * <p>
 * 命令绑定：拦截匹配的命令并打开对应菜单；物品绑定：玩家手持匹配物品交互时打开菜单。
 * 通过 {@link java.util.function.Supplier} 延迟获取 {@link MenuService} 以兼容热重载。
 */
public final class MenuBindingListener implements Listener {

    private final Supplier<MenuService> serviceSupplier;

    public MenuBindingListener(Supplier<MenuService> serviceSupplier) {
        this.serviceSupplier = serviceSupplier;
    }

    /**
     * 处理命令预处理事件，匹配命令绑定并打开对应菜单。
     *
     * @param event 命令预处理事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        MenuService service = serviceSupplier.get();
        if (service == null) {
            return;
        }
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        MenuCommandBinding binding = service.bindingRegistry().matchCommand(message);
        if (binding == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!AuthAccess.admitted(player.getUniqueId()) || !MenuConditionEvaluator.hasPermission(player, binding.permission())) {
            return;
        }
        event.setCancelled(true);
        service.openMenu(player, binding.menuId());
    }

    /**
     * 处理物品交互事件，匹配物品绑定并打开对应菜单。
     *
     * @param event 玩家交互事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY
            || event.getClickedBlock() != null && event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        MenuService service = serviceSupplier.get();
        if (service == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
            && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!AuthAccess.admitted(player.getUniqueId())) return;
        ItemStack itemStack = event.getItem();
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        boolean mainHand = event.getHand() == EquipmentSlot.HAND;
        MenuItemBinding binding = service.bindingRegistry().matchItem(player, itemStack, action, mainHand);
        if (binding != null) {
            event.setCancelled(true);
            service.openMenu(player, binding.menuId());
        }
    }
}
