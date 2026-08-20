package xuanmo.arcartxsuite.fishing.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.fishing.model.FishingItemRef;

/**
 * 统一物品生成器，封装 {@link ItemSourceRegistry} 调用。
 * <p>
 * 生成优先级：JSON > source+itemId 外部物品 > 原版 Material 回退。
 * 支持的外部来源：mythic、neige、overture、mmo、minecraft。
 */
public final class FishingItemGenerator {

    private final ItemSourceRegistry itemSourceRegistry;
    private final ItemBridgeAPI itemBridgeAPI;

    public FishingItemGenerator(@Nullable ItemSourceRegistry itemSourceRegistry,
                                @Nullable ItemBridgeAPI itemBridgeAPI) {
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemBridgeAPI = itemBridgeAPI;
    }

    /**
     * 根据 FishingItemRef 生成 ItemStack 并序列化为 itemJson 字符串，供 UI Slot 图标渲染。
     *
     * @param ref    物品引用
     * @param player 玩家上下文（Overture 需要）
     * @return itemJson 字符串，失败或不可用时返回空字符串
     */
    public @NotNull String generateItemJson(@NotNull FishingItemRef ref, @Nullable Player player) {
        ItemStack stack = generate(ref, player);
        if (stack == null || itemBridgeAPI == null) {
            return "";
        }
        return itemBridgeAPI.itemToJson(stack).orElse("");
    }

    /**
     * 将 ItemStack 序列化为 itemJson 字符串，供 UI Slot 图标渲染。
     *
     * @param stack 物品堆，为 null 时返回空字符串
     * @return itemJson 字符串
     */
    public @NotNull String itemToJson(@Nullable ItemStack stack) {
        if (stack == null || itemBridgeAPI == null) {
            return "";
        }
        return itemBridgeAPI.itemToJson(stack).orElse("");
    }

    /**
     * 根据 FishingItemRef 生成 ItemStack。
     *
     * @param ref    物品引用
     * @param player 玩家上下文（Overture 需要）
     * @return 生成的 ItemStack，失败时返回 null
     */
    public @Nullable ItemStack generate(@NotNull FishingItemRef ref, @Nullable Player player) {
        if (ref.isEmpty()) return null;

        // 1. JSON 完整定义
        if (!ref.json().isBlank()) {
            return parseJson(ref.json());
        }

        // 2. 外部物品库
        ItemStack stack = generateFromSource(ref, player);
        if (stack != null) {
            stack.setAmount(ref.amount());
            stack = applyCustomNbt(stack, ref);
            return stack;
        }

        // 3. 原版 Material 回退
        if (!ref.itemId().isBlank()) {
            Material material = Material.matchMaterial(ref.itemId());
            if (material != null && !material.isAir()) {
                stack = new ItemStack(material, ref.amount());
                stack = applyCustomNbt(stack, ref);
                return stack;
            }
        }

        return null;
    }

    @Nullable
    private ItemStack generateFromSource(@NotNull FishingItemRef ref, @Nullable Player player) {
        if (itemSourceRegistry == null) return null;
        String source = ref.source().trim().toLowerCase();
        // mmoitems 支持mmoType/mmoId分离配置，需拼装为 "类型ID;物品ID" 格式
        if ("mmo".equals(source) || "mmoitems".equals(source)) {
            String id = (!ref.mmoType().isBlank() && !ref.mmoId().isBlank())
                ? ref.mmoType() + ";" + ref.mmoId()
                : ref.itemId().contains(":") ? ref.itemId().replace(":", ";") : ref.itemId();
            return itemSourceRegistry.generateItem("mmoitems", id, ref.amount(), null, player);
        }
        return itemSourceRegistry.generateItem(source, ref.itemId(), ref.amount(), null, player);
    }

    private ItemStack applyCustomNbt(@Nullable ItemStack stack, @NotNull FishingItemRef ref) {
        if (stack == null || itemBridgeAPI == null) return stack;
        if (!ref.texture().isBlank()) {
            stack = itemBridgeAPI.putStringTag(stack, "icon", ref.texture());
        }
        if (!ref.textureUrl().isBlank()) {
            stack = itemBridgeAPI.putStringTag(stack, "url", ref.textureUrl());
        }
        return stack;
    }

    @Nullable
    private ItemStack parseJson(@NotNull String json) {
        try {
            return Bukkit.getUnsafe().modifyItemStack(new ItemStack(Material.STONE), json);
        } catch (Exception e) {
            return null;
        }
    }
}