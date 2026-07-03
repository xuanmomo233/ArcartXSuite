package xuanmo.arcartxsuite.menu.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.menu.config.MenuIconDefinition;

public final class MenuIconResolver {

    private static final Pattern SKIN_URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"(.*?)\"");

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
        stack = applyCustomNbt(stack, icon);
        return itemStackBridge.itemToJson(stack).orElse("");
    }

    private ItemStack applyCustomNbt(ItemStack stack, MenuIconDefinition icon) {
        if (stack == null || itemStackBridge == null) {
            return stack;
        }
        if (icon.texture() != null && !icon.texture().isBlank()) {
            stack = itemStackBridge.putStringTag(stack, "icon", icon.texture());
        }
        if (icon.textureUrl() != null && !icon.textureUrl().isBlank()) {
            stack = itemStackBridge.putStringTag(stack, "url", icon.textureUrl());
        }
        if (icon.nbt() != null) {
            for (java.util.Map.Entry<String, String> entry : icon.nbt().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && !key.isBlank() && value != null) {
                    stack = itemStackBridge.putStringTag(stack, key, value);
                }
            }
        }
        return stack;
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
            applyGlow(meta, icon);
            applySkullTexture(meta, icon);
            applyColor(meta, icon);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void applyGlow(ItemMeta meta, MenuIconDefinition icon) {
        if (!icon.glow()) {
            return;
        }
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (enchantment == null) {
            enchantment = Enchantment.getByKey(NamespacedKey.minecraft("infinity"));
        }
        if (enchantment != null) {
            meta.addEnchant(enchantment, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }

    private void applySkullTexture(ItemMeta meta, MenuIconDefinition icon) {
        if (icon.skullTexture() == null || icon.skullTexture().isBlank() || !(meta instanceof SkullMeta skullMeta)) {
            return;
        }
        String url = extractSkinUrl(icon.skullTexture().trim());
        if (url == null) {
            return;
        }
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new java.net.URI(url).toURL());
            profile.setTextures(textures);
            skullMeta.setOwnerProfile(profile);
        } catch (java.net.URISyntaxException | java.net.MalformedURLException | RuntimeException exception) {
            // malformed texture: leave the head as-is
        }
    }

    @Nullable
    private static String extractSkinUrl(String raw) {
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        Matcher matcher = SKIN_URL_PATTERN.matcher(decoded);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void applyColor(ItemMeta meta, MenuIconDefinition icon) {
        if (icon.color() == null || icon.color().isBlank() || !(meta instanceof LeatherArmorMeta leatherMeta)) {
            return;
        }
        Color parsed = parseColor(icon.color().trim());
        if (parsed != null) {
            leatherMeta.setColor(parsed);
        }
    }

    @Nullable
    private static Color parseColor(String raw) {
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() != 6) {
            return null;
        }
        try {
            return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (NumberFormatException exception) {
            return null;
        }
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
