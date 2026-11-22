package xuanmo.arcartxsuite.vanilla.service;

import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.Recipe;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.capability.WarehousePanelAccess;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.inventory.ItemPresentation;
import xuanmo.arcartxsuite.api.inventory.MaterialStock;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.*;

/** Shared screen dispatch; every request is checked against its exact native container. */
public final class VanillaService implements org.bukkit.event.Listener {
    private final PacketBridgeAPI bridge;
    private final VanillaConfiguration configuration;
    private final RecipeCollector collector;
    private final ItemPresentation presentation;
    private final NativeOfferBook nativeOffers;
    private final EquipmentWorkbenchService equipment;
    private final ArtisanWorkbenchService artisan;
    private final UtilityWorkbenchService utility;
    private final org.bukkit.plugin.Plugin plugin;
    private final Map<UUID,org.bukkit.scheduler.BukkitTask> refreshTasks=new HashMap<>();
    private final Map<WorkshopType, String> uiIds = new EnumMap<>(WorkshopType.class);
    private final Map<UUID, Boolean> bookState = new HashMap<>();
    private final Map<UUID, PendingStock> stockReads = new HashMap<>();
    private final Map<UUID, Map<NamespacedKey, Long>> bookRevisions = new HashMap<>();
    private VanillaRecipeRegistry customRecipes;
    private RecipeEditor recipeEditor;
    private Supplier<WarehousePanelAccess> warehousePanel = () -> null;
    private boolean stopped;

    private record RecipeDetails(String key, NamespacedKey id, int quantity) {}

    private static final class PendingStock {
        private final InventoryView view;
        private final WorkshopType type;
        private final WarehouseMaterialAccess warehouse;
        private boolean bookRequested;
        private RecipeDetails details;

        private PendingStock(InventoryView view, WorkshopType type, WarehouseMaterialAccess warehouse) {
            this.view = view;
            this.type = type;
            this.warehouse = warehouse;
        }

        private void request(RecipeDetails detail) {
            if (detail == null) bookRequested = true;
            else details = detail;
        }
    }

    public VanillaService(org.bukkit.plugin.Plugin plugin,PacketBridgeAPI bridge, xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI items,VanillaItemNameBridge names, VanillaConfiguration configuration, Logger logger) {
        this.bridge = bridge; this.configuration = configuration; this.collector = new RecipeCollector(names);
        presentation = new ItemPresentation(items, names);
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

    public List<String> packetIds() {
        var ids = new ArrayList<>(uiIds.values());
        for (WorkshopType type : WorkshopType.values()) ids.add(type.uiId());
        return List.copyOf(ids);
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
        stockReads.remove(event.getPlayer().getUniqueId());
        bookRevisions.remove(event.getPlayer().getUniqueId());
        if(event.getPlayer() instanceof Player player)nativeOffers.close(player);
        var task=refreshTasks.remove(event.getPlayer().getUniqueId());if(task!=null)task.cancel();
    }
    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        bookState.remove(event.getPlayer().getUniqueId());
        stockReads.remove(event.getPlayer().getUniqueId());
        bookRevisions.remove(event.getPlayer().getUniqueId());
        var task = refreshTasks.remove(event.getPlayer().getUniqueId());
        if (task != null) task.cancel();
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR,ignoreCancelled=true)
    public void onInput(org.bukkit.event.inventory.InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        var view=event.getView();
        WorkshopType type=WorkshopType.MERCHANT.matches(view)?WorkshopType.MERCHANT:WorkshopType.BREWING.matches(view)?WorkshopType.BREWING:null;
        if(type==null||!configuration.enabled(type)||!bookState.getOrDefault(player.getUniqueId(),false)
            ||refreshTasks.containsKey(player.getUniqueId()))return;
        refreshTasks.put(player.getUniqueId(),org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,()->{
            refreshTasks.remove(player.getUniqueId());
            if(!stopped&&player.isOnline()&&player.getOpenInventory()==view&&bookState.getOrDefault(player.getUniqueId(),false)
                &&!materialPlanner().busy(player)&&bridge.isUiOpen(player,uiIds.get(type)))sendBook(player,type,true);
        },3L));
    }
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (stopped || data == null || data.isEmpty()) return false;
        for (WorkshopType type : WorkshopType.values()) {
            String ui = uiIds.get(type);
            if (!ui.equalsIgnoreCase(packetId) && !type.uiId().equalsIgnoreCase(packetId)) continue;
            if (!configuration.enabled(type) || !type.matches(player.getOpenInventory())
                || bridge == null || !bridge.isUiOpen(player, ui)) return true;
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
                        if (!recipeEditor.open(player, ui)) notice(player, "请放下光标上的物品并确认配方管理权限");
                    }
                }
                case "inspect_recipe" -> {
                    if (type == WorkshopType.CRAFTING && data.size() > 1) inspectRecipe(player, data.get(1), quantity(data));
                }
                case "place_ingredients" -> {
                    if (data.size() > 1) place(player, type, data.get(1), quantity(data));
                }
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
        UUID playerId = player.getUniqueId();
        bookState.put(playerId, open);
        if (!open) {
            stockReads.remove(playerId);
            bookRevisions.remove(playerId);
            bridge.sendPacket(player, ui, "recipe_book_toggle", Map.of("open", false));
            return;
        }
        requestStock(player, type, null);
    }

    private void requestStock(Player player, WorkshopType type, RecipeDetails details) {
        UUID playerId = player.getUniqueId();
        var view = player.getOpenInventory();
        WarehouseMaterialAccess warehouse = materialPlanner().warehouseAccess();
        var pending = stockReads.get(playerId);
        if (pending != null && pending.view == view && pending.type == type && pending.warehouse == warehouse) {
            pending.request(details);
            return;
        }
        var request = new PendingStock(view, type, warehouse);
        request.request(details);
        stockReads.put(playerId, request);
        try {
            MaterialStock.readAsync(player, warehouse, stock -> publishStock(player, request, stock));
        } catch (RuntimeException failure) {
            stockReads.remove(playerId, request);
            throw failure;
        }
    }

    private void publishStock(Player player, PendingStock request, MaterialStock stock) {
        UUID playerId = player.getUniqueId();
        if (!stockReads.remove(playerId, request)) return;
        String ui = uiIds.get(request.type);
        if (!canPublishStock(player, request, ui)) return;
        boolean publishList = request.bookRequested && bookState.getOrDefault(playerId, false);
        try {
            if (publishList) publishBook(player, ui, request.type, stock);
        } finally {
            if (request.details != null && (!publishList || canPublishStock(player, request, ui))) {
                publishRecipeDetails(player, ui, request.details, stock);
            }
        }
    }

    private boolean canPublishStock(Player player, PendingStock request, String ui) {
        return currentWorkshopView(player, request.view, request.type, ui)
            && materialPlanner().warehouseAccess() == request.warehouse;
    }

    private boolean currentWorkshopView(Player player, InventoryView view, WorkshopType type, String ui) {
        UUID id = player.getUniqueId();
        return !stopped && player.isOnline() && Bukkit.getPlayer(id) == player && AuthAccess.admitted(id)
            && player.getOpenInventory() == view && type.matches(view) && bridge.isUiOpen(player, ui);
    }

    private void publishBook(Player player, String ui, WorkshopType type, MaterialStock stock) {
        UUID playerId = player.getUniqueId();
        boolean nativeOffersOnly = type == WorkshopType.BREWING || type == WorkshopType.MERCHANT;
        Map<String, Object> recipes = new LinkedHashMap<>(nativeOffersOnly
            ? nativeOffers.collect(player, type, stock) : collector.collectRecipes(player, type, stock));
        Map<NamespacedKey, Long> revisions = new HashMap<>();
        if (customRecipes != null && !nativeOffersOnly) {
            recipes.keySet().removeIf(key -> {
                NamespacedKey id = NamespacedKey.fromString(key);
                if (id == null || !customRecipes.managed(id)) return false;
                var entry = customRecipes.recipe(id).orElse(null);
                if (entry == null || !customRecipes.allowed(player, entry)) return true;
                revisions.put(id, entry.revision());
                return false;
            });
        }
        if (bridge.sendPacket(player, ui, "recipe_book_toggle", Map.of("open", true, "recipes", recipes, "recipeCount", recipes.size()))) {
            bookRevisions.put(playerId, Map.copyOf(revisions));
        }
    }
    private int quantity(List<String> data) {
        if (data.size() < 3) return 1;
        if ("max".equals(data.get(2))) return 0;
        try { return Integer.parseInt(data.get(2).trim()); }
        catch (NumberFormatException exception) { return -1; }
    }

    private void inspectRecipe(Player player, String key, int quantity) {
        String ui = uiIds.get(WorkshopType.CRAFTING);
        NamespacedKey id = NamespacedKey.fromString(key);
        Recipe recipe = id == null ? null : Bukkit.getRecipe(id);
        if (!bridge.isUiOpen(player, ui) || !WorkshopType.CRAFTING.visibleTo(player, recipe) || !recipeAllowed(player, id)) return;
        if (quantity < 0 || quantity > RecipeMaterialPlanner.MAX_BATCH) {
            notice(player, "请输入 1～64 份，或选择最多");
            return;
        }
        requestStock(player, WorkshopType.CRAFTING, new RecipeDetails(key, id, quantity));
    }

    private void publishRecipeDetails(Player player, String ui, RecipeDetails request, MaterialStock stock) {
        Recipe current = Bukkit.getRecipe(request.id());
        if (!WorkshopType.CRAFTING.visibleTo(player, current) || !recipeAllowed(player, request.id())) return;
        var preview = materialPlanner().previewCrafting(player, request.id(), current, stock);
        int shown = request.quantity() == 0 ? Math.max(1, preview.maximum()) : request.quantity();
        Map<String, Object> rows = new LinkedHashMap<>();
        for (var row : preview.rows()) {
            rows.put(Integer.toString(rows.size()), Map.of("name", presentation.name(row.sample()) + (row.alternatives() ? "等" : ""),
                "need", Integer.toString(row.perCraft() * shown), "workbench", Long.toString(row.workbench()),
                "backpack", Long.toString(row.backpack()), "warehouse", Long.toString(row.warehouse()),
                "missing", Long.toString(row.missing(shown))));
        }
        bridge.sendPacket(player, ui, "recipe_details", Map.of("key", request.key(), "rows", rows,
            "maximum", preview.maximum(), "quantity", shown, "warehouseAvailable", stock.warehouse() != null && stock.warehouse().available()));
    }

    private boolean recipeAllowed(Player player, NamespacedKey id) {
        return id != null && (customRecipes == null || !customRecipes.managed(id)
            || customRecipes.recipe(id).filter(entry -> customRecipes.allowed(player, entry)).isPresent());
    }

    private void place(Player player, WorkshopType type, String key, int quantity) {
        String ui = uiIds.get(type);
        var view = player.getOpenInventory();
        if (quantity < 0 || quantity > RecipeMaterialPlanner.MAX_BATCH || (type != WorkshopType.CRAFTING && quantity != 1)) {
            notice(player, "当前配方的填料份数无效");
            return;
        }
        if (materialPlanner().busy(player)){notice(player,"上一笔材料或兑换仍在处理，请稍候");return;}
        if(type==WorkshopType.MERCHANT&&!nativeOffers.selectMerchant(player,key)){
            bridge.sendPacket(player,ui,"placement_state",Map.of("busy",false,"message","交易已变化、售罄或被其他插件取消，请重新选择"));sendBook(player,type,true);return;
        }
        if (type == WorkshopType.STONECUTTER && !StonecutterSelection.available(view)) {
            bridge.sendPacket(player, ui, "placement_state", Map.of("busy", false, "message", "当前服务端不支持切石配方选择，未取材料"));
            return;
        }
        bridge.sendPacket(player, ui, "placement_state", Map.of("busy", true, "message", "正在准备材料"));
        java.util.function.BooleanSupplier currentView =
            () -> currentWorkshopView(player, view, type, ui);
        java.util.function.Consumer<String> done = message -> {
            if (!currentView.getAsBoolean()) return;
            if (message.isEmpty() && type == WorkshopType.STONECUTTER && !StonecutterSelection.select(player, key))
                message = "材料已填入，配方选择未完成；请重新选择配方";
            bridge.sendPacket(player, ui, "placement_state", Map.of("busy", false, "message", message.isEmpty()?"材料已填入":message));
            sendBook(player, type, true);
            WarehousePanelAccess panel = warehousePanel.get();
            if (panel != null) panel.handle(player, ui, List.of("refresh"));
        };
        if (type == WorkshopType.BREWING || type == WorkshopType.MERCHANT) {
            materialPlanner().fillCustom(player, type, stock -> nativeOffers.demand(player, key), currentView, done);
            return;
        }
        NamespacedKey id = NamespacedKey.fromString(key);
        if (id != null && customRecipes != null && customRecipes.managed(id)) {
            long revision = bookRevisions.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(id, -1L);
            if (quantity == 1) customRecipes.fill(player, id, revision, currentView).thenAccept(receipt -> done.accept(receipt.message()));
            else materialPlanner().fill(player, key, type, quantity,
                () -> currentView.getAsBoolean() && customRecipes.valid(player, id, revision), done);
        } else materialPlanner().fill(player, key, type, quantity, currentView, done);
    }
    private void selectStonecutting(Player player, WorkshopType type, String key) {
        if (materialPlanner().busy(player)) return;
        boolean selected = StonecutterSelection.select(player, key);
        bridge.sendPacket(player, uiIds.get(type), "placement_state", Map.of("busy", false, "message", selected ? "" : "先填入对应石材，再选择配方"));
    }
    public void shutdown() {
        stopped = true; materialPlanner().close();
        stockReads.clear();
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
