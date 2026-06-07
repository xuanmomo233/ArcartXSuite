package xuanmo.arcartxsuite.menu.listener;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xuanmo.arcartxsuite.menu.config.MenuCommandBinding;
import xuanmo.arcartxsuite.menu.config.MenuItemBinding;
import xuanmo.arcartxsuite.menu.service.MenuBindingRegistry;
import xuanmo.arcartxsuite.menu.service.MenuConditionEvaluator;
import xuanmo.arcartxsuite.menu.service.MenuService;

public final class MenuBindingListener implements Listener {

    private final MenuService service;
    private final MenuBindingRegistry bindingRegistry;

    public MenuBindingListener(MenuService service, MenuBindingRegistry bindingRegistry) {
        this.service = service;
        this.bindingRegistry = bindingRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        MenuCommandBinding binding = bindingRegistry.matchCommand(message);
        if (binding == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!MenuConditionEvaluator.hasPermission(player, binding.permission())) {
            return;
        }
        event.setCancelled(true);
        service.openMenu(player, binding.menuId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
            && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack itemStack = event.getItem();
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        boolean mainHand = event.getHand() == EquipmentSlot.HAND;
        for (MenuItemBinding binding : bindingRegistry.itemBindings()) {
            if (!matchesHand(binding, mainHand)) {
                continue;
            }
            if (!matchesClick(binding, action)) {
                continue;
            }
            if (!MenuConditionEvaluator.hasPermission(player, binding.permission())) {
                continue;
            }
            if (!matchesItem(binding, itemStack)) {
                continue;
            }
            event.setCancelled(true);
            service.openMenu(player, binding.menuId());
            return;
        }
    }

    private static boolean matchesHand(MenuItemBinding binding, boolean mainHand) {
        return mainHand ? binding.mainHand() : binding.offHand();
    }

    private static boolean matchesClick(MenuItemBinding binding, Action action) {
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean wantsRight = binding.clickAction() == Action.RIGHT_CLICK_AIR
            || binding.clickAction() == Action.RIGHT_CLICK_BLOCK;
        boolean wantsLeft = binding.clickAction() == Action.LEFT_CLICK_AIR
            || binding.clickAction() == Action.LEFT_CLICK_BLOCK;
        if (wantsRight && rightClick) {
            return true;
        }
        return wantsLeft && leftClick;
    }

    private static boolean matchesItem(MenuItemBinding binding, ItemStack itemStack) {
        if (binding.material() != null && !binding.material().isBlank()) {
            Material expected = Material.matchMaterial(binding.material());
            if (expected == null || itemStack.getType() != expected) {
                return false;
            }
        }
        ItemMeta meta = itemStack.getItemMeta();
        String displayName = meta != null && meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : "";
        if (binding.nameContains() != null && !binding.nameContains().isBlank()) {
            String needle = ChatColor.stripColor(colorize(binding.nameContains()));
            if (!displayName.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (binding.nameRegex() != null && !binding.nameRegex().isBlank()) {
            if (!Pattern.compile(binding.nameRegex(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(displayName).find()) {
                return false;
            }
        }
        if (binding.loreContains() != null && !binding.loreContains().isBlank()) {
            if (meta == null || !meta.hasLore()) {
                return false;
            }
            String needle = ChatColor.stripColor(colorize(binding.loreContains())).toLowerCase(Locale.ROOT);
            boolean found = false;
            for (String line : meta.getLore()) {
                if (ChatColor.stripColor(line).toLowerCase(Locale.ROOT).contains(needle)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        if (binding.customModelData() >= 0) {
            if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != binding.customModelData()) {
                return false;
            }
        }
        return true;
    }

    private static String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
