package xuanmo.arcartxsuite.vanilla.service;

import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import xuanmo.arcartxsuite.api.capability.WarehousePanelAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.*;

/** Shared screen dispatch; every request is checked against its exact native container. */
public final class VanillaService implements org.bukkit.event.Listener {
    private final PacketBridgeAPI bridge;
    private final VanillaConfiguration configuration;
    private final RecipeCollector collector;
    private final NativeOfferBook nativeOffers;
    private final EquipmentWorkbenchService equipment;
    private final ArtisanWorkbenchService artisan;
    private final UtilityWorkbenchService utility;
    private final org.bukkit.plugin.Plugin plugin;
    private final Map<UUID,org.bukkit.scheduler.BukkitTask> refreshTasks=new HashMap<>();
    private final Map<WorkshopType, String> uiIds = new EnumMap<>(WorkshopType.class);
    private final Map<UUID, Boolean> bookState = new HashMap<>();
    private final Map<UUID, Map<NamespacedKey, Long>> bookRevisions = new HashMap<>();
    private VanillaRecipeRegistry customRecipes;
    private RecipeEditor recipeEditor;
    private Supplier<WarehousePanelAccess> warehousePanel = () -> null;
    private boolean stopped;

    public VanillaService(org.bukkit.plugin.Plugin plugin,PacketBridgeAPI bridge, xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI items,VanillaItemNameBridge names, VanillaConfiguration configuration, Logger logger) {
        this.bridge = bridge; this.configuration = configuration; this.collector = new RecipeCollector(names);
        this.plugin=plugin;this.nativeOffers=new NativeOfferBook(items,names);
        for (WorkshopType type : WorkshopType.values()) uiIds.put(type, configuration.uiId(type));
        equipment=new EquipmentWorkbenchService(plugin,bridge,names,configuration,materialPlanner(),uiIds::get,player->materialPlanner().busy(player));
        artisan=new ArtisanWorkbenchService(plugin,bridge,items,names,configuration,materialPlanner(),uiIds::get,player->materialPlanner().busy(player));
        utility=new UtilityWorkbenchService(plugin,bridge,configuration,materialPlanner(),uiIds::get,player->materialPlanner().busy(player));
        utility.setWarehouseRefresh((player,id)->{var panel=warehousePanel.get();if(panel!=null)panel.handle(player,id,List.of("refresh"));});
    }
    public EquipmentWorkbenchService equipment(){return equipment;}
    public ArtisanWorkbenchService artisan(){return artisan;}
    public UtilityWorkbenchService utility(){return utility;}
    public void setWarehousePanel(Supplier<WarehousePanelAccess> supplier) { warehousePanel = supplier; }
    public RecipeMaterialPlanner materialPlanner() { return collector.materialPlanner(); }
    public void setRuntimeUiId(WorkshopType type, String id) { uiIds.put(type, id); }
    public void setCustomRecipes(VanillaRecipeRegistry recipes, RecipeEditor editor) {
        customRecipes = recipes;
        recipeEditor = editor;
    }

    public boolean ownsPacket(String id) {
        return uiIds.values().stream().anyMatch(ui -> ui.equalsIgnoreCase(id))
            || Arrays.stream(WorkshopType.values()).anyMatch(type -> type.uiId().equalsIgnoreCase(id));
    }
    public boolean notice(Player player,String message){
        if (bridge == null || message == null || message.isBlank()) return false;
        for (String ui : uiIds.values()) {
            if (bridge.isUiOpen(player, ui)
                && bridge.sendPacket(player, ui, "ui_notice", Map.of("message", org.bukkit.ChatColor.stripColor(message)))) {
                return true;
            }
        }
        return false;
    }
    @org.bukkit.event.EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        bookState.remove(event.getPlayer().getUniqueId());
        bookRevisions.remove(event.getPlayer().getUniqueId());
        if(event.getPlayer() instanceof Player player)nativeOffers.close(player);
        var task=refreshTasks.remove(event.getPlayer().getUniqueId());if(task!=null)task.cancel();
    }
    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        bookState.remove(event.getPlayer().getUniqueId());
        bookRevisions.remove(event.getPlayer().getUniqueId());
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR,ignoreCancelled=true)
    public void onInput(org.bukkit.event.inventory.InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        var view=event.getView();
        WorkshopType type=WorkshopType.MERCHANT.matches(view)?WorkshopType.MERCHANT:WorkshopType.BREWING.matches(view)?WorkshopType.BREWING:null;
        if(type==null||!configuration.enabled(type)||refreshTasks.containsKey(player.getUniqueId()))return;
        refreshTasks.put(player.getUniqueId(),org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,()->{
            refreshTasks.remove(player.getUniqueId());
            if(!stopped&&player.isOnline()&&player.getOpenInventory()==view&&!materialPlanner().busy(player)&&bridge.isUiOpen(player,uiIds.get(type)))sendBook(player,type,true);
        },3L));
    }
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (stopped || data == null || data.isEmpty()) return false;
        for (WorkshopType type : WorkshopType.values()) {
            String ui = uiIds.get(type);
            if (!ui.equalsIgnoreCase(packetId) && !type.uiId().equalsIgnoreCase(packetId)) continue;
            if (!configuration.enabled(type) || !type.matches(player.getOpenInventory())) return true;
            String action = data.get(0);
            if(type.equipment()&&!"warehouse_panel".equals(action))return equipment.handle(player,type,action,data);
            if(type.artisan()&&!"warehouse_panel".equals(action))return artisan.handle(player,type,action,data);
            if(type.utility()&&!"warehouse_panel".equals(action))return utility.handle(player,type,action,data);
            switch (action) {
                case "warehouse_panel" -> {
                    WarehousePanelAccess panel = warehousePanel.get();
                    if (panel != null) panel.handle(player, ui, data.subList(1, data.size()));
                    else bridge.sendPacket(player, ui, "warehouse_panel", Map.of("available", false));
                }
                case "show_recipe_book" -> sendBook(player, type, true);
                case "request_recipe_book" -> sendBook(player, type, bookState.getOrDefault(player.getUniqueId(), false));
                case "toggle_recipe_book" -> sendBook(player, type, !bookState.getOrDefault(player.getUniqueId(), false));
                case "recipe_admin_status" -> {
                    if (type == WorkshopType.CRAFTING) {
                        bridge.sendPacket(player, ui, "recipe_admin_access",
                            Map.of("enabled", recipeEditor != null && player.hasPermission(RecipeEditor.ADMIN)));
                    }
                }
                case "open_recipe_editor" -> {
                    if (type == WorkshopType.CRAFTING && recipeEditor != null && !materialPlanner().busy(player)) {
                        if (!recipeEditor.open(player)) notice(player, "请放下光标上的物品并确认配方管理权限");
                    }
                }
                case "place_ingredients" -> { if (data.size() > 1) place(player, type, data.get(1)); }
                case "select_recipe" -> {
                    if(data.size()>1 && type==WorkshopType.STONECUTTER)selectStonecutting(player,type,data.get(1));
                    else if(data.size()>1 && type==WorkshopType.MERCHANT && !materialPlanner().busy(player))nativeOffers.selectMerchant(player,data.get(1));
                }
                default -> { return false; }
            }
            return true;
        }
        return false;
    }
    private void sendBook(Player player, WorkshopType type, boolean open) {
        if (stopped) return;
        String ui = uiIds.get(type);
        bookState.put(player.getUniqueId(), open);
        if (!open) { bridge.sendPacket(player, ui, "recipe_book_toggle", Map.of("open", false)); return; }
        var view = player.getOpenInventory();
        materialPlanner().stockAsync(player, stock -> {
            if (stopped || !player.isOnline() || player.getOpenInventory() != view || !type.matches(view) || !bridge.isUiOpen(player, ui)) return;
            Map<String, Object> recipes = new LinkedHashMap<>(type==WorkshopType.BREWING||type==WorkshopType.MERCHANT
                ? nativeOffers.collect(player,type,stock) : collector.collectRecipes(player, type, stock));
            if (customRecipes != null && type != WorkshopType.BREWING && type != WorkshopType.MERCHANT) {
                Map<NamespacedKey, Long> revisions = new HashMap<>();
                recipes.keySet().removeIf(key -> {
                    NamespacedKey id = NamespacedKey.fromString(key);
                    if (id == null || !customRecipes.managed(id)) return false;
                    var entry = customRecipes.recipe(id).orElse(null);
                    if (entry == null || !customRecipes.allowed(player, entry)) return true;
                    revisions.put(id, entry.revision());
                    return false;
                });
                bookRevisions.put(player.getUniqueId(), Map.copyOf(revisions));
            }
            bridge.sendPacket(player, ui, "recipe_book_toggle", Map.of("open", true, "recipes", recipes, "recipeCount", recipes.size()));
        });
    }
    private void place(Player player, WorkshopType type, String key) {
        String ui = uiIds.get(type);
        var view = player.getOpenInventory();
        if (materialPlanner().busy(player)){notice(player,"上一笔材料或兑换仍在处理，请稍候");return;}
        if(type==WorkshopType.MERCHANT&&!nativeOffers.selectMerchant(player,key)){
            bridge.sendPacket(player,ui,"placement_state",Map.of("busy",false,"message","交易已变化、售罄或被其他插件取消，请重新选择"));sendBook(player,type,true);return;
        }
        if (type == WorkshopType.STONECUTTER && !StonecutterSelection.available(view)) {
            bridge.sendPacket(player, ui, "placement_state", Map.of("busy", false, "message", "当前服务端不支持切石配方选择，未取材料"));
            return;
        }
        bridge.sendPacket(player, ui, "placement_state", Map.of("busy", true, "message", "正在准备材料"));
        java.util.function.Consumer<String> done = message -> {
            if (!player.isOnline() || player.getOpenInventory() != view || !bridge.isUiOpen(player, ui)) return;
            if (message.isEmpty() && type == WorkshopType.STONECUTTER && !StonecutterSelection.select(player, key))
                message = "材料已填入，配方选择未完成；请重新选择配方";
            bridge.sendPacket(player, ui, "placement_state", Map.of("busy", false, "message", message.isEmpty()?"材料已填入":message));
            sendBook(player, type, true);
            WarehousePanelAccess panel = warehousePanel.get();
            if (panel != null) panel.handle(player, ui, List.of("refresh"));
        };
        if (type == WorkshopType.BREWING || type == WorkshopType.MERCHANT) {
            materialPlanner().fillCustom(player, type, stock -> nativeOffers.demand(player, key), done);
            return;
        }
        NamespacedKey id = NamespacedKey.fromString(key);
        if (id != null && customRecipes != null && customRecipes.managed(id)) {
            long revision = bookRevisions.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(id, -1L);
            customRecipes.fill(player, id, revision).thenAccept(receipt -> done.accept(receipt.message()));
        } else materialPlanner().fill(player,key,type,done);
    }
    private void selectStonecutting(Player player, WorkshopType type, String key) {
        if (materialPlanner().busy(player)) return;
        boolean selected = StonecutterSelection.select(player, key);
        bridge.sendPacket(player, uiIds.get(type), "placement_state", Map.of("busy", false, "message", selected ? "" : "先填入对应石材，再选择配方"));
    }
    public void shutdown() {
        stopped = true; materialPlanner().close();
        bookRevisions.clear();
        equipment.close();
        artisan.close();
        utility.close();
        refreshTasks.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);refreshTasks.clear();nativeOffers.clear();
        WarehousePanelAccess panel = warehousePanel.get();
        if (panel != null) for (String ui : uiIds.values()) panel.close(ui);
        bookState.clear();
    }
}
