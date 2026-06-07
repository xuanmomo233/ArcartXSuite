package xuanmo.arcartxsuite.menu.service;

import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.menu.config.MenuIconDefinition;

public final class MenuIconResolver {

    private final ItemBridgeAPI itemStackBridge;
    private final ItemSourceRegistry itemSourceRegistry;

    public MenuIconResolver(ItemBridgeAPI itemStackBridge, ItemSourceRegistry itemSourceRegistry) {
        this.itemStackBridge = itemStackBridge;
        this.itemSourceRegistry = itemSourceRegistry;
    }

    public String resolveItemJson(Player player, @Nullable MenuIconDefinition icon) {
        if (icon == null || !icon.hasIcon()) {
            return "";
        }
        if (icon.json() != null && !icon.json().isBlank()) {
            return icon.json();
        }
        ItemStack stack = resolveItemStack(player, icon);
        if (stack == null || itemStackBridge == null) {
            return "";
        }
        return itemStackBridge.itemToJson(stack).orElse("");
    }

    @Nullable
    private ItemStack resolveItemStack(Player player, MenuIconDefinition icon) {
        ItemStack generated = generateExternalItem(player, icon);
        if (generated != null) {
            return generated;
        }
        Material material = Material.matchMaterial(icon.material());
        if (material == null || material.isAir()) {
            return null;
        }
        ItemStack stack = new ItemStack(material, Math.max(1, icon.amount()));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (icon.name() != null && !icon.name().isBlank()) {
                meta.setDisplayName(colorize(MenuConditionEvaluator.applyPlaceholders(player, icon.name())));
            }
            if (icon.lore() != null && !icon.lore().isEmpty()) {
                meta.setLore(icon.lore().stream()
                    .map(line -> colorize(MenuConditionEvaluator.applyPlaceholders(player, line)))
                    .toList());
            }
            if (icon.customModelData() > 0) {
                meta.setCustomModelData(icon.customModelData());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Nullable
    private ItemStack generateExternalItem(Player player, MenuIconDefinition icon) {
        if (itemSourceRegistry == null) {
            return null;
        }
        String source = icon.source() == null ? "" : icon.source().trim().toLowerCase(Locale.ROOT);
        if (!source.isBlank() && icon.sourceId() != null && !icon.sourceId().isBlank()) {
            return switch (source) {
                case "mythic", "mythicmobs" -> itemSourceRegistry.generateMythicItem(icon.sourceId(), icon.amount());
                case "neige", "neigeitems" -> itemSourceRegistry.generateNeigeItem(icon.sourceId(), icon.amount());
                case "overture" -> itemSourceRegistry.generateOvertureItem(icon.sourceId(), player, icon.amount());
                case "mmo", "mmoitems" -> {
                    if (icon.mmoType() != null && !icon.mmoType().isBlank()
                        && icon.mmoId() != null && !icon.mmoId().isBlank()) {
                        yield itemSourceRegistry.generateMmoItem(icon.mmoType(), icon.mmoId(), icon.amount());
                    }
                    String[] parts = icon.sourceId().split("[:;]", 2);
                    if (parts.length == 2) {
                        yield itemSourceRegistry.generateMmoItem(parts[0], parts[1], icon.amount());
                    }
                    yield null;
                }
                default -> null;
            };
        }
        if (icon.mmoType() != null && !icon.mmoType().isBlank()
            && icon.mmoId() != null && !icon.mmoId().isBlank()) {
            return itemSourceRegistry.generateMmoItem(icon.mmoType(), icon.mmoId(), icon.amount());
        }
        return null;
    }

    private static String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
