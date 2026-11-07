package xuanmo.arcartxsuite.vanilla.service;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.capability.WarehousePanelAccess;
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
    private boolean stopped;
    private String craftingUiId;
    private String furnaceUiId;
    private java.util.function.Supplier<WarehousePanelAccess> warehousePanel = () -> null;

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

    public void setWarehousePanel(java.util.function.Supplier<WarehousePanelAccess> supplier) {
        warehousePanel = supplier;
    }
    public xuanmo.arcartxsuite.vanilla.recipe.RecipeMaterialPlanner materialPlanner() {
        return recipeCollector.materialPlanner();
    }

    public void setCraftingRuntimeUiId(String uiId) {
        craftingUiId = uiId;
    }

    public void setFurnaceRuntimeUiId(String uiId) {
        furnaceUiId = uiId;
    }

    /**
     * 处理客户端发来的自定义包。
     *
     * @param packetId UI id（vanilla_crafting 或 vanilla_furnace）
     * @param data     数据负载，data.get(0) 为 action
     * @return true 表示已消费
     */
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (data == null || data.isEmpty()) return false;
        String action = data.get(0);
        String extra = data.size() > 1 ? data.get(1) : "";

        if (craftingUiId.equalsIgnoreCase(packetId) || "AXS:vanilla_crafting".equalsIgnoreCase(packetId)) {
            if (!configuration.crafting().enabled()
                || !(player.getOpenInventory().getTopInventory() instanceof CraftingInventory crafting)
                || crafting.getSize() != 10) return true;
            if ("warehouse_panel".equals(action)) return handleWarehousePanel(player, craftingUiId, data);
            return handleCraftingPacket(player, action, extra);
        } else if (furnaceUiId.equalsIgnoreCase(packetId) || "AXS:vanilla_furnace".equalsIgnoreCase(packetId)) {
            if (!configuration.furnace().enabled()
                || !(player.getOpenInventory().getTopInventory() instanceof FurnaceInventory)) return true;
            if ("warehouse_panel".equals(action)) return handleWarehousePanel(player, furnaceUiId, data);
            return handleFurnacePacket(player, action, extra);
        }
        return false;
    }

    private boolean handleWarehousePanel(Player player, String uiId, List<String> data) {
        WarehousePanelAccess panel = warehousePanel.get();
        if (panel != null) {
            panel.handle(player, uiId, data.subList(1, data.size()));
        } else {
            packetBridge.sendPacket(player, uiId, "warehouse_panel", Map.of("available", false));
        }
        return true;
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
            case "show_recipe_book" -> {
                sendCraftingRecipeBookPacket(player, true);
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
            case "show_recipe_book" -> {
                sendFurnaceRecipeBookPacket(player, true);
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

    private void sendCraftingRecipeBookPacket(Player player,boolean open){
        if(stopped)return;
        var view=player.getOpenInventory();
        if(!open){packetBridge.sendPacket(player,craftingUiId,"recipe_book_toggle",Map.of("open",false,"catIcons",recipeCollector.craftingCategoryIcons()));return;}
        materialPlanner().stockAsync(player,stock->{
            if(stopped || !player.isOnline() || player.getOpenInventory()!=view || !packetBridge.isUiOpen(player,craftingUiId))return;
            Map<String,Object> packet=new LinkedHashMap<>();
            packet.put("open",true);packet.put("catIcons",recipeCollector.craftingCategoryIcons());
            Map<String,Object> recipes=recipeCollector.collectCraftingRecipes(player,stock);
            packet.put("recipes",recipes);packet.put("recipeCount",recipes.size());
            packetBridge.sendPacket(player,craftingUiId,"recipe_book_toggle",packet);
        });
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

    private void sendFurnaceRecipeBookPacket(Player player,boolean open){
        if(stopped)return;
        var view=player.getOpenInventory();
        if(!open){packetBridge.sendPacket(player,furnaceUiId,"recipe_book_toggle",Map.of("open",false,"catIcons",recipeCollector.furnaceCategoryIcons()));return;}
        materialPlanner().stockAsync(player,stock->{
            if(stopped || !player.isOnline() || player.getOpenInventory()!=view || !packetBridge.isUiOpen(player,furnaceUiId))return;
            Map<String,Object> packet=new LinkedHashMap<>();
            packet.put("open",true);packet.put("catIcons",recipeCollector.furnaceCategoryIcons());
            Map<String,Object> recipes=recipeCollector.collectFurnaceRecipes(player,stock);
            packet.put("recipes",recipes);packet.put("recipeCount",recipes.size());
            packetBridge.sendPacket(player,furnaceUiId,"recipe_book_toggle",packet);
        });
    }

    // ── 一键填充材料 ──────────────────────────────────────

    private void handlePlaceIngredients(Player player,String recipeKey) { placeMaterials(player,recipeKey,false); }
    private void handlePlaceFurnaceIngredient(Player player,String recipeKey) { placeMaterials(player,recipeKey,true); }

    private void placeMaterials(Player player,String recipeKey,boolean furnace) {
        String ui=furnace?furnaceUiId:craftingUiId;
        var view=player.getOpenInventory();
        if(materialPlanner().busy(player)) return;
        packetBridge.sendPacket(player,ui,"placement_state",Map.of("busy",true,"message","正在准备材料"));
        materialPlanner().fill(player,recipeKey,furnace,message->{
            if(!player.isOnline() || player.getOpenInventory()!=view || !packetBridge.isUiOpen(player,ui))return;
            packetBridge.sendPacket(player,ui,"placement_state",Map.of("busy",false,"message",message));
            if(!message.isEmpty())player.sendMessage("§e"+message);
            if(furnace)sendFurnaceRecipeBookPacket(player,true);else sendCraftingRecipeBookPacket(player,true);
            WarehousePanelAccess panel=warehousePanel.get();
            if(panel!=null)panel.handle(player,ui,List.of("refresh"));
        });
    }

    private void placeFromInventory(Player player, Inventory topInv, int slot, Material mat) {
        if (mat == Material.AIR) return;
        var playerInv = player.getInventory();
        for (int i = 0; i < playerInv.getStorageContents().length; i++) {
            ItemStack item = playerInv.getItem(i);
            if (item != null && item.getType() == mat) {
                ItemStack placed = item.clone();
                placed.setAmount(1);
                topInv.setItem(slot, placed);
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
        ItemStack existing = topInv.getItem(slot);
        for (int i = 0; i < playerInv.getStorageContents().length; i++) {
            ItemStack item = playerInv.getItem(i);
            if (item != null && item.getType() == mat && (existing == null || existing.isSimilar(item))) {
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
                    ItemStack copy = item.clone();
                    copy.setAmount(toAdd);
                    topInv.setItem(slot, copy);
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

    private boolean returnToBackpack(Player player, Inventory inventory, int slot, ItemStack item) {
        ItemStack remainder = player.getInventory().addItem(item.clone()).get(0);
        inventory.setItem(slot, remainder);
        return remainder == null;
    }

    private Material parseMaterialFromJson(String json) {
        if (json == null || json.isEmpty()) return Material.AIR;
        String id = json.replace("{\"id\":\"", "").replace("\"Count\":1b}", "");
        id = id.split("\"")[0];
        if (id.equals("minecraft:air")) return Material.AIR;
        try {
            return Material.valueOf(id.replace("minecraft:", "").toUpperCase());
        } catch (IllegalArgumentException e) {
            warnMaterialParseOnce(id);
            return Material.AIR;
        }
    }

    /** 解析失败的材料 id 一次性告警集合（避免刷屏）。 */
    private final java.util.Set<String> materialParseWarned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void warnMaterialParseOnce(String id) {
        if (materialParseWarned.add(id)) {
            logger.warning("[Vanilla] 无法解析物品材料 id '" + id + "'（可能为新版/Mod 物品），按 AIR 处理");
        }
    }

    public void shutdown() {
        stopped=true;
        materialPlanner().close();
        WarehousePanelAccess panel = warehousePanel.get();
        if (panel != null) {
            panel.close(craftingUiId);
            panel.close(furnaceUiId);
        }
        craftingBookState.clear();
        furnaceBookState.clear();
    }
}
