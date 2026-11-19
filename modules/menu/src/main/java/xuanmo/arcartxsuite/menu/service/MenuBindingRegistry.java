package xuanmo.arcartxsuite.menu.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xuanmo.arcartxsuite.menu.config.MenuCommandBinding;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.config.MenuItemBinding;

/**
 * 菜单绑定注册表，维护命令绑定和物品绑定，支持按命令行匹配对应菜单。
 * <p>
 * 在菜单重载时由 {@link MenuService} 调用 {@link #rebuild} 重建全部绑定关系。
 */
public final class MenuBindingRegistry {

    private int commandCount;
    private final Map<String, List<CommandEntry>> commandPrefixes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final List<CommandEntry> regexBindings = new ArrayList<>();
    private List<MenuItemBinding> itemBindings = List.of();
    private final List<ItemBinding> compiledItems = new ArrayList<>();

    private record CommandEntry(int position, MenuCommandBinding binding) {}

    /**
     * 从菜单定义集合和全局物品绑定重建全部绑定关系。
     *
     * @param menus               菜单定义集合
     * @param globalItemBindings  全局物品绑定列表
     * @param logger              日志记录器
     */
    public void rebuild(Iterable<MenuDefinition> menus, List<MenuItemBinding> globalItemBindings, Logger logger) {
        commandCount = 0;
        commandPrefixes.clear();
        regexBindings.clear();
        compiledItems.clear();
        List<MenuItemBinding> loadedItems = new ArrayList<>();
        if (globalItemBindings != null) {
            loadedItems.addAll(globalItemBindings);
        }
        for (MenuDefinition definition : menus) {
            for (String command : definition.commands()) {
                if (command == null || command.isBlank()) {
                    continue;
                }
                addCommand(new MenuCommandBinding(
                    definition.id(),
                    command.trim(),
                    false,
                    null,
                    definition.permission()
                ));
            }
            for (String regex : definition.commandRegex()) {
                if (regex == null || regex.isBlank()) {
                    continue;
                }
                Pattern compiled = compilePattern(regex, definition.id(), logger);
                if (compiled != null) {
                    addCommand(new MenuCommandBinding(
                        definition.id(),
                        regex.trim(),
                        true,
                        compiled,
                        definition.permission()
                    ));
                }
            }
            loadedItems.addAll(definition.itemBinds());
        }
        itemBindings = List.copyOf(loadedItems);
        for (MenuItemBinding binding : itemBindings) {
            ItemBinding compiled = compileItem(binding, logger);
            if (compiled != null) compiledItems.add(compiled);
        }
    }

    /**
     * 匹配命令行对应的命令绑定。
     *
     * @param commandLine 命令行文本（可含前导 {@code /}）
     * @return 匹配的命令绑定，无匹配返回 {@code null}
     */
    public MenuCommandBinding matchCommand(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return null;
        }
        String normalized = commandLine.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        CommandEntry literal = null;
        for (CommandEntry entry : commandPrefixes.getOrDefault(commandRoot(normalized), List.of())) {
            if (entry.binding().matches(normalized)) {
                literal = entry;
                break;
            }
        }
        for (CommandEntry entry : regexBindings) {
            if (literal != null && entry.position() > literal.position()) break;
            if (entry.binding().matches(normalized)) return entry.binding();
        }
        return literal == null ? null : literal.binding();
    }

    private void addCommand(MenuCommandBinding binding) {
        CommandEntry entry = new CommandEntry(commandCount++, binding);
        if (binding.regex()) regexBindings.add(entry);
        else commandPrefixes.computeIfAbsent(commandRoot(binding.pattern()), ignored -> new ArrayList<>()).add(entry);
    }

    private static String commandRoot(String command) {
        int space = command.indexOf(' ');
        return space < 0 ? command : command.substring(0, space);
    }

    /**
     * 返回所有物品绑定的不可变副本。
     *
     * @return 物品绑定列表
     */
    public List<MenuItemBinding> itemBindings() {
        return itemBindings;
    }

    /**
     * 返回当前命令绑定的数量。
     *
     * @return 命令绑定数量
     */
    public int commandBindingCount() {
        return commandCount;
    }

    public MenuItemBinding matchItem(Player player, ItemStack stack, Action action, boolean mainHand) {
        if (compiledItems.isEmpty()) return null;
        ItemView item = new ItemView(stack);
        for (ItemBinding entry : compiledItems) {
            MenuItemBinding binding = entry.definition();
            if (!(mainHand ? binding.mainHand() : binding.offHand())
                || !matchesClick(binding, action, player)) continue;
            if (entry.material() != null && entry.material() != stack.getType()) continue;
            if (!MenuConditionEvaluator.hasPermission(player, binding.permission())) continue;
            if (entry.matches(item)) return binding;
        }
        return null;
    }

    private static boolean matchesClick(MenuItemBinding binding, Action action, Player player) {
        if (binding.requireSneak() != null && binding.requireSneak() != player.isSneaking()) return false;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean wantsRight = binding.clickAction() == Action.RIGHT_CLICK_AIR
            || binding.clickAction() == Action.RIGHT_CLICK_BLOCK;
        boolean wantsLeft = binding.clickAction() == Action.LEFT_CLICK_AIR
            || binding.clickAction() == Action.LEFT_CLICK_BLOCK;
        return wantsRight && right || wantsLeft && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
    }

    private static ItemBinding compileItem(MenuItemBinding binding, Logger logger) {
        Material material = hasText(binding.material()) ? Material.matchMaterial(binding.material()) : null;
        if (hasText(binding.material()) && material == null) {
            if (logger != null) logger.warning("Menu 物品绑定材质无效: menu=" + binding.menuId() + " material=" + binding.material());
            return null;
        }
        Pattern regex = hasText(binding.nameRegex()) ? compilePattern(binding.nameRegex(), binding.menuId(), logger) : null;
        if (hasText(binding.nameRegex()) && regex == null) return null;
        return new ItemBinding(binding, material, normalized(binding.nameContains()), regex, normalized(binding.loreContains()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalized(String value) {
        return hasText(value) ? ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value)).toLowerCase(Locale.ROOT) : null;
    }

    private record ItemBinding(MenuItemBinding definition, Material material, String name, Pattern regex, String lore) {
        boolean matches(ItemView item) {
            if (name != null && !item.lowerName().contains(name)) return false;
            if (regex != null && !regex.matcher(item.name()).find()) return false;
            if (lore != null && !item.loreContains(lore)) return false;
            if (definition.customModelData() < 0) return true;
            ItemMeta meta = item.meta();
            return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == definition.customModelData();
        }
    }

    private static final class ItemView {
        private final ItemStack stack;
        private boolean metaRead;
        private ItemMeta meta;
        private String name;
        private String lowerName;
        private List<String> lore;

        ItemView(ItemStack stack) { this.stack = stack; }

        ItemMeta meta() {
            if (!metaRead) {
                meta = stack.getItemMeta();
                metaRead = true;
            }
            return meta;
        }

        String name() {
            if (name == null) {
                ItemMeta value = meta();
                name = value != null && value.hasDisplayName() ? ChatColor.stripColor(value.getDisplayName()) : "";
            }
            return name;
        }

        String lowerName() {
            if (lowerName == null) lowerName = name().toLowerCase(Locale.ROOT);
            return lowerName;
        }

        boolean loreContains(String needle) {
            for (String line : lore()) {
                if (line.contains(needle)) return true;
            }
            return false;
        }

        List<String> lore() {
            if (lore == null) {
                ItemMeta value = meta();
                lore = value != null && value.hasLore()
                    ? value.getLore().stream().map(line -> ChatColor.stripColor(line).toLowerCase(Locale.ROOT)).toList()
                    : List.of();
            }
            return lore;
        }
    }

    private static Pattern compilePattern(String regex, String menuId, Logger logger) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException exception) {
            if (logger != null) {
                logger.warning("Menu 绑定正则无效: menu=" + menuId + " pattern=" + regex
                    + " -> " + exception.getMessage());
            }
            return null;
        }
    }
}
