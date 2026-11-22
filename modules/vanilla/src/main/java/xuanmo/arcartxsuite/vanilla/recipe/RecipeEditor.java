package xuanmo.arcartxsuite.vanilla.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Definition;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Entry;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Ingredient;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.SmithingData;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Station;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.ItemPresentation;

public final class RecipeEditor implements Listener, AutoCloseable {
    public static final String UI = "AXS:vanilla_recipe_editor";
    public static final String ADMIN = "arcartxsuite.vanilla.recipe.admin";
    private static final int PAGE_SIZE = 8;
    private static final String SLOT_PREFIX = "axs_vanilla_recipe_";

    private static final class Draft {
        final String token = UUID.randomUUID().toString();
        final List<Ingredient> grid = new ArrayList<>(Collections.nCopies(9, Ingredient.blank()));
        final RecipeEditorSlots slots = new RecipeEditorSlots();
        String selected = "";
        String permission = "";
        Station station = Station.SHAPED;
        int page;
        long revision;
        int fieldVersion;
        boolean materialMode;
        InventoryView view;
        final Map<Integer, Map<String, Object>> references = new LinkedHashMap<>();
    }

    private final VanillaRecipeRegistry registry;
    private final PacketBridgeAPI bridge;
    private final ItemPresentation items;
    private final ItemBridgeAPI slotDisplay;
    private final String ui;
    private final Plugin plugin;
    private final Map<UUID, Draft> drafts = new LinkedHashMap<>();

    public RecipeEditor(Plugin plugin, VanillaRecipeRegistry registry, PacketBridgeAPI bridge, ItemPresentation items,
                        ItemBridgeAPI slotDisplay, String ui) {
        this.plugin = plugin;
        this.registry = registry;
        this.bridge = bridge;
        this.items = items;
        this.slotDisplay = slotDisplay;
        this.ui = ui;
    }

    public boolean open(Player player) {
        return open(player, "");
    }

    public boolean open(Player player, String parentUi) {
        if (!player.hasPermission(ADMIN) || !bridge.isAvailable() || !InventoryItems.empty(player.getItemOnCursor())
            || slotDisplay == null || !slotDisplay.isAvailable()
            || !slotDisplay.showExtraSlot(player, SLOT_PREFIX + "ready", null)) {
            return false;
        }
        if (xuanmo.arcartxsuite.api.inventory.InventoryOperation.busy(player)) return false;
        // Finish the real workstation window before opening a standalone editor.
        // Otherwise closing the editor leaves the client's native menu behind.
        Draft previous = drafts.get(player.getUniqueId());
        if (previous != null) end(player, previous, true);
        player.closeInventory();
        if (!parentUi.isEmpty()) bridge.closeUi(player, parentUi);
        Draft draft = new Draft();
        draft.view = player.getOpenInventory();
        draft.revision = registry.editRevision();
        drafts.put(player.getUniqueId(), draft);
        if (!bridge.openUi(player, ui)) {
            drafts.remove(player.getUniqueId(), draft);
            return false;
        }
        return true;
    }

    public List<String> packetIds() { return List.of(ui, UI); }

    public boolean ownsPacket(String packet) { return ui.equalsIgnoreCase(packet) || UI.equalsIgnoreCase(packet); }

    public void handle(Player player, List<String> data) {
        Draft draft = drafts.get(player.getUniqueId());
        if (draft == null || data == null || data.isEmpty()) return;
        String action = data.get(0);
        if ("close".equals(action)) {
            if (data.size() > 1 && draft.token.equals(data.get(1))) end(player, draft, true);
            return;
        }
        if (!player.hasPermission(ADMIN) || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
            || player.getOpenInventory() != draft.view) return;
        if (!bridge.isUiOpen(player, ui)) return;
        if ("ready".equals(action)) {
            render(player, draft, "");
            return;
        }
        if (data.size() < 2 || !draft.token.equals(data.get(1))) return;
        try {
            String message = switch (action) {
                case "new" -> { select(draft, ""); yield ""; }
                case "select" -> { select(draft, field(data, 2)); yield ""; }
                case "page" -> { draft.page = Math.max(0, draft.page + Integer.signum(integer(data, 2))); yield ""; }
                case "mode" -> {
                    draft.station = draft.station == Station.SHAPED ? Station.SHAPELESS : Station.SHAPED;
                    yield "";
                }
                case "match_mode" -> {
                    draft.materialMode = matching(draft).equals("精确匹配");
                    for (int i = 0; i < 9; i++) {
                        ItemStack item = draft.slots.get(i);
                        var reference = draft.materialMode ? null : draft.references.get(i);
                        set(draft, i, item, draft.materialMode);
                        if (reference != null) draft.references.put(i, reference);
                    }
                    yield "";
                }
                case "save" -> {
                    if (data.size() != 4) throw new IllegalArgumentException("编辑器已更新，请重新打开后保存");
                    save(draft, field(data, 2), field(data, 3));
                    yield "配方已发布";
                }
                case "delete" -> {
                    if (draft.selected.isEmpty() || !draft.selected.equals(field(data, 2))) {
                        throw new IllegalArgumentException("请确认要删除的配方");
                    }
                    registry.save(draft.selected, null, draft.revision);
                    select(draft, "");
                    yield "配方已删除";
                }
                default -> throw new IllegalArgumentException("未知操作");
            };
            render(player, draft, message);
        } catch (Exception exception) {
            render(player, draft, exception.getMessage() == null ? "配方操作失败" : exception.getMessage());
        }
    }

    private List<Entry> craftingEntries() {
        return registry.recipes().stream().filter(registry::configured)
            .filter(entry -> NativeRecipeFactory.crafting(entry.definition().station())).toList();
    }

    private void select(Draft draft, String id) throws Exception {
        Entry existing = id.isEmpty() ? null : craftingEntries().stream()
            .filter(entry -> entry.definition().id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("配方已不存在"));
        draft.revision = registry.editRevision();
        draft.fieldVersion++;
        draft.selected = id;
        Collections.fill(draft.grid, Ingredient.blank());
        draft.slots.clear();
        draft.station = Station.SHAPED;
        draft.materialMode = false;
        draft.permission = "";
        draft.references.clear();
        if (existing != null) {
            Definition definition = existing.definition();
            draft.station = definition.station();
            draft.slots.set(9, definition.result());
            draft.permission = definition.permission();
            for (int i = 0; i < definition.ingredients().size(); i++) {
                Ingredient ingredient = definition.ingredients().get(i);
                draft.grid.set(i, ingredient);
                if (!ingredient.empty()) draft.slots.set(i, ingredient.materials().isEmpty()
                    ? ingredient.exact().get(0) : new ItemStack(ingredient.materials().get(0)));
            }
            draft.materialMode = matching(draft).equals("材质匹配");
            draft.references.putAll(registry.references(id));
        }
    }

    private String matching(Draft draft) {
        boolean material = false, exact = false;
        for (Ingredient ingredient : draft.grid) {
            if (ingredient.empty()) continue;
            if (ingredient.materials().isEmpty()) exact = true;
            else material = true;
        }
        if (material && exact) return "按格配置";
        if (material) return "材质匹配";
        if (exact) return "精确匹配";
        return draft.materialMode ? "材质匹配" : "精确匹配";
    }

    public void registerExtraEvents() {
        Plugin arcartx = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartx == null) throw new IllegalStateException("ArcartX 尚未加载");
        try {
            Class<? extends Event> type = Class.forName(
                "priv.seventeen.artist.arcartx.event.client.ClientExtraSlotClickEvent", false,
                arcartx.getClass().getClassLoader()).asSubclass(Event.class);
            if (!Cancellable.class.isAssignableFrom(type)) throw new IllegalStateException("额外槽位事件不支持取消");
            var slot = type.getMethod("getSlotID");
            var actor = type.getMethod("getPlayer");
            var button = type.getMethod("getButtonID");
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.NORMAL, (listener, event) -> {
                try {
                    String id = (String) slot.invoke(event);
                    if (id == null || !id.startsWith(SLOT_PREFIX)) return;
                    ((Cancellable) event).setCancelled(true);
                    Player player = (Player) actor.invoke(event);
                    int click = ((Number) button.invoke(event)).intValue();
                    Runnable action = () -> clickExtra(player, id, click);
                    if (Bukkit.isPrimaryThread()) action.run();
                    else Bukkit.getScheduler().runTask(plugin, action);
                } catch (ReflectiveOperationException failure) {
                    throw new org.bukkit.event.EventException(failure);
                }
            }, plugin);
            Class<? extends Event> request = Class.forName(
                "priv.seventeen.artist.arcartx.event.client.ClientExtraSlotRequestEvent", false,
                arcartx.getClass().getClassLoader()).asSubclass(Event.class);
            var requestedSlot = request.getMethod("getSlotID");
            var requester = request.getMethod("getPlayer");
            Bukkit.getPluginManager().registerEvent(request, this, EventPriority.NORMAL, (listener, event) -> {
                try {
                    String id = (String) requestedSlot.invoke(event);
                    if (id == null || !id.startsWith(SLOT_PREFIX)) return;
                    ((Cancellable) event).setCancelled(true);
                    Player player = (Player) requester.invoke(event);
                    Runnable reply = () -> showSlot(player, id);
                    if (Bukkit.isPrimaryThread()) reply.run();
                    else Bukkit.getScheduler().runTask(plugin, reply);
                } catch (ReflectiveOperationException failure) {
                    throw new org.bukkit.event.EventException(failure);
                }
            }, plugin);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("无法注册配方录入槽位", failure);
        }
    }

    void clickExtra(Player player, String id, int button) {
        Draft draft = drafts.get(player.getUniqueId());
        if (draft == null || !player.hasPermission(ADMIN) || !bridge.isUiOpen(player, ui)
            || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
            || xuanmo.arcartxsuite.api.inventory.InventoryOperation.busy(player)
            || player.getOpenInventory() != draft.view || (button != 0 && button != 1)) return;
        String prefix = SLOT_PREFIX + draft.token + "_";
        if (!id.startsWith(prefix)) return;
        try {
            int target = Integer.parseInt(id.substring(prefix.length()));
            if (target < 0 || target > 9) return;
            try (var operation = xuanmo.arcartxsuite.api.inventory.InventoryOperation.tryAcquire(player)) {
                if (operation == null) return;
                ItemStack cursor = InventoryItems.copy(player.getItemOnCursor()), before = draft.slots.get(target);
                if (cursor != null) NativeRecipeFactory.validateSnapshot(cursor);
                try {
                    ItemStack next = draft.slots.click(target, button, cursor);
                    player.setItemOnCursor(next);
                    player.updateInventory();
                } catch (RuntimeException failure) {
                    draft.slots.set(target, before);
                    player.setItemOnCursor(cursor);
                    throw failure;
                }
                ItemStack after = draft.slots.get(target);
                boolean same = before != null && after != null && before.isSimilar(after);
                if (!java.util.Objects.equals(before, after)) {
                    var reference = same ? draft.references.get(target) : null;
                    set(draft, target, after, draft.materialMode);
                    if (reference != null) draft.references.put(target, reference);
                }
            }
            render(player, draft, "");
        } catch (Exception failure) {
            render(player, draft, failure.getMessage() == null ? "样品录入失败" : failure.getMessage());
        }
    }

    private void showSlot(Player player, String id) {
        Draft draft = drafts.get(player.getUniqueId());
        if (draft == null || !player.hasPermission(ADMIN) || !bridge.isUiOpen(player, ui)) return;
        String prefix = SLOT_PREFIX + draft.token + "_";
        if (!id.startsWith(prefix)) return;
        try {
            int slot = Integer.parseInt(id.substring(prefix.length()));
            if (slot >= 0 && slot < 10) slotDisplay.showExtraSlot(player, id, draft.slots.get(slot));
        } catch (NumberFormatException ignored) { }
    }

    private void set(Draft draft, int target, ItemStack item, boolean material) {
        draft.references.remove(target);
        if (target == 9) return;
        if (target < 0 || target >= 9) throw new IllegalArgumentException("材料格无效");
        if (item == null) draft.grid.set(target, Ingredient.blank());
        else if (material) {
            var alternatives = new ArrayList<>(draft.grid.get(target).materials());
            if (!alternatives.contains(item.getType())) alternatives.add(item.getType());
            draft.grid.set(target, new Ingredient(alternatives, List.of()));
        } else draft.grid.set(target, new Ingredient(List.of(), List.of(item)));
    }

    private void save(Draft draft, String id, String permission) throws Exception {
        ItemStack result = draft.slots.get(9);
        if (InventoryItems.empty(result) || result.getAmount() > result.getMaxStackSize()) {
            throw new IllegalArgumentException("请将有效产物放入产物格");
        }
        if (permission.length() > 120 || !permission.matches("[a-zA-Z0-9_.-]*")) {
            throw new IllegalArgumentException("权限节点格式无效");
        }
        if (!draft.selected.isEmpty() && !draft.selected.equals(id)) throw new IllegalArgumentException("已发布配方的 ID 不可修改");
        if (draft.selected.isEmpty() && registry.recipes().stream().filter(registry::configured)
            .anyMatch(entry -> entry.definition().id().equals(id))) throw new IllegalArgumentException("配方 ID 已存在");
        var grid = draft.station == Station.SHAPED ? draft.grid : draft.grid.stream().filter(i -> !i.empty()).toList();
        Map<Integer, Map<String, Object>> references = new LinkedHashMap<>();
        int compact = 0;
        for (int i = 0; i < 9; i++) {
            if (draft.grid.get(i).empty()) continue;
            if (draft.references.containsKey(i)) references.put(draft.station == Station.SHAPED ? i : compact, draft.references.get(i));
            compact++;
        }
        if (draft.references.containsKey(9)) references.put(9, draft.references.get(9));
        registry.save(id, new Definition(id, draft.station, grid, result, permission, 200, 0, SmithingData.INHERIT_BASE),
            draft.revision, references);
        select(draft, id);
    }

    private void render(Player player, Draft draft, String message) {
        var recipes = craftingEntries();
        int pages = Math.max(1, (recipes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        draft.page = Math.min(draft.page, pages - 1);
        Map<String, Object> list = new LinkedHashMap<>();
        int start = draft.page * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, recipes.size()); i++) {
            Entry entry = recipes.get(i);
            list.put(Integer.toString(i - start), Map.of("id", entry.definition().id(),
                "name", items.name(entry.definition().result())));
        }
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("token", draft.token);
        packet.put("list", list);
        packet.put("page", (draft.page + 1) + " / " + pages);
        packet.put("hasPrevious", draft.page > 0);
        packet.put("hasNext", draft.page + 1 < pages);
        packet.put("slotPrefix", SLOT_PREFIX + draft.token + "_");
        packet.put("matching", matching(draft));
        packet.put("selected", draft.selected);
        packet.put("fieldVersion", draft.fieldVersion);
        packet.put("permission", draft.permission);
        packet.put("station", draft.station == Station.SHAPED ? "有序" : "无序");
        packet.put("message", message.length() > 240 ? message.substring(0, 240) : message);
        bridge.sendPacket(player, ui, "recipe_editor_state", packet);
        for (int i = 0; i < 10; i++) slotDisplay.showExtraSlot(player,
            SLOT_PREFIX + draft.token + "_" + i, draft.slots.get(i));
    }

    private static String field(List<String> data, int index) {
        return data.size() > index ? data.get(index) : "";
    }

    private static int integer(List<String> data, int index) { return Integer.parseInt(field(data, index)); }

    @EventHandler public void quit(PlayerQuitEvent event) { drafts.remove(event.getPlayer().getUniqueId()); }

    @EventHandler public void inventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Draft draft = drafts.get(player.getUniqueId());
        if (draft != null && draft.view == event.getView()) end(player, draft, false);
    }

    private void end(Player player, Draft draft, boolean closeNative) {
        if (!drafts.remove(player.getUniqueId(), draft)) return;
        for (int i = 0; i < 10; i++) slotDisplay.showExtraSlot(player, SLOT_PREFIX + draft.token + "_" + i, null);
        bridge.closeUi(player, ui);
        if (closeNative && player.isOnline() && player.getOpenInventory() == draft.view) player.closeInventory();
    }

    @Override public void close() {
        for (UUID owner : List.copyOf(drafts.keySet())) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null) end(player, drafts.get(owner), true);
        }
        drafts.clear();
    }
}
