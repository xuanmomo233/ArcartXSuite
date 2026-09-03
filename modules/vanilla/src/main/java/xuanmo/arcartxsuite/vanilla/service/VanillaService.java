package xuanmo.arcartxsuite.vanilla.service;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.RecipeCollector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Vanilla UI 业务服务：配方书推送、一键填充材料、熔炉状态管理。
 */
public final class VanillaService {

    private final PacketBridgeAPI packetBridge;
    private final VanillaItemNameBridge nameBridge;
    private final VanillaConfiguration configuration;
    private final Logger logger;

    private final RecipeCollector recipeCollector;
    private final String craftingUiId;
    private final String furnaceUiId;

    private final Map<UUID, Boolean> craftingBookState = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> furnaceBookState = new ConcurrentHashMap<>();

    public VanillaService(PacketBridgeAPI packetBridge, VanillaItemNameBridge nameBridge,
                          VanillaConfiguration configuration, Logger logger) {
        this.packetBridge = packetBridge;
        this.nameBridge = nameBridge;
        this.configuration = configuration;
        this.logger = logger;
        this.recipeCollector = new RecipeCollector(nameBridge);
        this.craftingUiId = configuration.ui().craftingUiId();
        this.furnaceUiId = configuration.ui().furnaceUiId();
    }

    /**
     * 处理客户端发来的自定义包。
     *
     * @param packetId UI id（vanilla_crafting 或 vanilla_furnace）
     * @param data     数据负载，data.get(0) 为 action
     * @return true 表示已消费
     */
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        String action = (data == null || data.isEmpty()) ? "" : data.get(0);
        String extra = data.size() > 1 ? data.get(1) : "";

        if (craftingUiId.equalsIgnoreCase(packetId)) {
            return handleCraftingPacket(player, action, extra);
        } else if (furnaceUiId.equalsIgnoreCase(packetId)) {
            return handleFurnacePacket(player, action, extra);
        }
        return false;
    }

    private boolean handleCraftingPacket(Player player, String action, String extra) {
        switch (action) {
            case "toggle_recipe_book" -> {
                handleCraftingRecipeBook(player);
                return true;
            }
            case "request_recipe_book" -> {
                handleRequestCraftingRecipeBook(player);
                return true;
            }
            case "place_ingredients" -> {
                handlePlaceIngredients(player, extra);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleFurnacePacket(Player player, String action, String extra) {
        switch (action) {
            case "toggle_recipe_book" -> {
                handleFurnaceRecipeBook(player);
                return true;
            }
            case "request_recipe_book" -> {
                handleRequestFurnaceRecipeBook(player);
                return true;
            }
            case "place_ingredients" -> {
                handlePlaceFurnaceIngredient(player, extra);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ── 工作台配方书 ──────────────────────────────────────

    private void handleCraftingRecipeBook(Player player) {
        boolean open = !craftingBookState.getOrDefault(player.getUniqueId(), false);
        craftingBookState.put(player.getUniqueId(), open);
        sendCraftingRecipeBookPacket(player, open);
    }

    private void handleRequestCraftingRecipeBook(Player player) {
        boolean open = craftingBookState.getOrDefault(player.getUniqueId(), false);
        sendCraftingRecipeBookPacket(player, open);
    }

    private void sendCraftingRecipeBookPacket(Player player, boolean open) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("open", open);
        // catIcons 始终推送：配方书按钮(书本图标)在关闭状态下也需要图标
        packet.put("catIcons", recipeCollector.craftingCategoryIcons());
        if (open) {
            Map<String, Object> recipes = recipeCollector.collectCraftingRecipes(player);
            packet.put("recipes", recipes);
            packet.put("recipeCount", recipes.size());
        }
        packetBridge.sendPacket(player, craftingUiId, "recipe_book_toggle", packet);
    }

    // ── 熔炉配方书 ────────────────────────────────────────

    private void handleFurnaceRecipeBook(Player player) {
        boolean open = !furnaceBookState.getOrDefault(player.getUniqueId(), false);
        furnaceBookState.put(player.getUniqueId(), open);
        sendFurnaceRecipeBookPacket(player, open);
    }

    private void handleRequestFurnaceRecipeBook(Player player) {
        boolean open = furnaceBookState.getOrDefault(player.getUniqueId(), false);
        sendFurnaceRecipeBookPacket(player, open);
    }

    private void sendFurnaceRecipeBookPacket(Player player, boolean open) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("open", open);
        // catIcons 始终推送：配方书按钮(书本图标)在关闭状态下也需要图标
        packet.put("catIcons", recipeCollector.furnaceCategoryIcons());
        if (open) {
            Map<String, Object> recipes = recipeCollector.collectFurnaceRecipes(player);
            packet.put("recipes", recipes);
            packet.put("recipeCount", recipes.size());
        }
        packetBridge.sendPacket(player, furnaceUiId, "recipe_book_toggle", packet);
    }

    // ── 一键填充材料 ──────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void handlePlaceIngredients(Player player, String recipeKey) {
        Map<String, Object> recipes = recipeCollector.collectCraftingRecipes(player);
        Object entry = recipes.get(recipeKey);
        if (entry == null) return;
        Map<?, ?> recipe = (Map<?, ?>) entry;
        var topInv = player.getOpenInventory().getTopInventory();
        if (topInv.getSize() < 10) return;

        // 计算每个槽位需要的材料
        Material[] needed = new Material[10]; // 1~9 对应合成槽
        Object shapedObj = recipe.get("shaped");
        boolean shaped = shapedObj instanceof Boolean && (Boolean) shapedObj;
        if (shaped) {
            Object shapeObj = recipe.get("shape");
            Object ingredientsObj = recipe.get("ingredients");
            if (shapeObj instanceof List<?> shapeList && ingredientsObj instanceof Map<?, ?> ingredientMap) {
                for (int row = 0; row < shapeList.size(); row++) {
                    String rowStr = (String) shapeList.get(row);
                    for (int col = 0; col < rowStr.length(); col++) {
                        char c = rowStr.charAt(col);
                        if (c == ' ') continue;
                        Object json = ingredientMap.get(String.valueOf(c));
                        if (json == null) continue;
                        needed[row * 3 + col + 1] = parseMaterialFromJson(json.toString());
                    }
                }
            }
        } else {
            Object ingredientsObj = recipe.get("ingredients");
            if (ingredientsObj instanceof List<?> ingredientList) {
                for (int i = 0; i < Math.min(ingredientList.size(), 9); i++) {
                    needed[i + 1] = parseMaterialFromJson(ingredientList.get(i).toString());
                }
            }
        }

        // 退回材料不匹配的槽位物品，保留已正确的
        for (int i = 1; i <= 9; i++) {
            ItemStack existing = topInv.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) continue;
            if (needed[i] != null && existing.getType() == needed[i]) continue; // 已正确，保留
            // 材料不匹配，退回背包
            player.getInventory().addItem(existing);
            topInv.setItem(i, null);
        }

        // 填充或累加材料
        for (int i = 1; i <= 9; i++) {
            if (needed[i] == null || needed[i] == Material.AIR) continue;
            ItemStack existing = topInv.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) {
                placeFromInventory(player, topInv, i, needed[i]);
            } else if (existing.getType() == needed[i] && existing.getAmount() < existing.getMaxStackSize()) {
                // 已有正确材料且未满，尝试追加
                addToSlotFromInventory(player, topInv, i, needed[i], existing.getMaxStackSize() - existing.getAmount());
            }
        }
        player.updateInventory();
    }

    @SuppressWarnings("deprecation")
    private void handlePlaceFurnaceIngredient(Player player, String recipeKey) {
        Map<String, Object> recipes = recipeCollector.collectFurnaceRecipes(player);
        Object entry = recipes.get(recipeKey);
        if (entry == null) return;
        Map<?, ?> recipe = (Map<?, ?>) entry;
        Object inputJson = recipe.get("inputJson");
        if (inputJson == null) return;
        Material mat = parseMaterialFromJson(inputJson.toString());
        var topInv = player.getOpenInventory().getTopInventory();
        if (topInv.getSize() < 3) return;
        ItemStack existing = topInv.getItem(0);
        if (existing != null && existing.getType() == mat) {
            // 已有正确材料，尝试追加而不中断燃烧
            if (existing.getAmount() < existing.getMaxStackSize()) {
                addToSlotFromInventory(player, topInv, 0, mat, existing.getMaxStackSize() - existing.getAmount(), true);
            }
            return;
        }
        // 输入槽为空，直接放入
        if (existing == null || existing.getType() == Material.AIR) {
            placeFromInventory(player, topInv, 0, mat);
            player.updateInventory();
            return;
        }
        // 输入槽有其他物品，退回背包再填充
        player.getInventory().addItem(existing);
        topInv.setItem(0, null);
        placeFromInventory(player, topInv, 0, mat);
        player.updateInventory();
    }

    private void placeFromInventory(Player player, Inventory topInv, int slot, Material mat) {
        if (mat == Material.AIR) return;
        var playerInv = player.getInventory();
        for (int i = 0; i < playerInv.getSize(); i++) {
            ItemStack item = playerInv.getItem(i);
            if (item != null && item.getType() == mat) {
                topInv.setItem(slot, new ItemStack(mat, 1));
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) {
                    playerInv.setItem(i, null);
                } else {
                    playerInv.setItem(i, item);
                }
                return;
            }
        }
    }

    private void addToSlotFromInventory(Player player, Inventory topInv, int slot, Material mat, int maxAdd) {
        addToSlotFromInventory(player, topInv, slot, mat, maxAdd, false);
    }

    private void addToSlotFromInventory(Player player, Inventory topInv, int slot, Material mat, int maxAdd, boolean silent) {
        if (mat == Material.AIR || maxAdd <= 0) return;
        var playerInv = player.getInventory();
        for (int i = 0; i < playerInv.getSize(); i++) {
            ItemStack item = playerInv.getItem(i);
            if (item != null && item.getType() == mat) {
                ItemStack existing = topInv.getItem(slot);
                int toAdd = Math.min(maxAdd, item.getAmount());
                if (existing != null) {
                    if (silent) {
                        // 直接修改 amount 不调用 setItem，避免重置熔炉燃烧进度
                        existing.setAmount(existing.getAmount() + toAdd);
                    } else {
                        // 复制后修改再 setItem，确保 Bukkit 感知到变化
                        ItemStack copy = existing.clone();
                        copy.setAmount(existing.getAmount() + toAdd);
                        topInv.setItem(slot, copy);
                    }
                } else {
                    topInv.setItem(slot, new ItemStack(mat, toAdd));
                }
                item.setAmount(item.getAmount() - toAdd);
                if (item.getAmount() <= 0) {
                    playerInv.setItem(i, null);
                } else {
                    playerInv.setItem(i, item);
                }
                return;
            }
        }
    }

    private Material parseMaterialFromJson(String json) {
        if (json == null || json.isEmpty()) return Material.AIR;
        String id = json.replace("{\"id\":\"", "").replace("\"Count\":1b}", "");
        id = id.split("\"")[0];
        if (id.equals("minecraft:air")) return Material.AIR;
        try {
            return Material.valueOf(id.replace("minecraft:", "").toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.AIR;
        }
    }

    public void shutdown() {
        craftingBookState.clear();
        furnaceBookState.clear();
    }
}
