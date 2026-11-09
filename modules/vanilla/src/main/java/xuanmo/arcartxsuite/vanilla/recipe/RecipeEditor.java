package xuanmo.arcartxsuite.vanilla.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Definition;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Entry;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Ingredient;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.SmithingData;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess.Station;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.ItemPresentation;
import xuanmo.arcartxsuite.api.inventory.ItemSnapshots;

public final class RecipeEditor implements Listener, AutoCloseable {
    public static final String UI = "AXS:vanilla_recipe_editor";
    public static final String ADMIN = "arcartxsuite.vanilla.recipe.admin";
    private static final int PAGE_SIZE = 9;

    private static final class Draft {
        final String token = UUID.randomUUID().toString();
        final List<Ingredient> grid = new ArrayList<>(Collections.nCopies(9, Ingredient.blank()));
        String selected = "";
        String permission = "";
        Station station = Station.SHAPED;
        ItemStack result;
        int page;
        long revision;
        int fieldVersion;
    }

    private final VanillaRecipeRegistry registry;
    private final PacketBridgeAPI bridge;
    private final ItemPresentation items;
    private final String ui;
    private final Map<UUID, Draft> drafts = new LinkedHashMap<>();

    public RecipeEditor(VanillaRecipeRegistry registry, PacketBridgeAPI bridge, ItemPresentation items, String ui) {
        this.registry = registry;
        this.bridge = bridge;
        this.items = items;
        this.ui = ui;
    }

    public boolean open(Player player) {
        if (!player.hasPermission(ADMIN) || !bridge.isAvailable() || !InventoryItems.empty(player.getItemOnCursor())) {
            return false;
        }
        Draft draft = new Draft();
        draft.revision = registry.editRevision();
        drafts.put(player.getUniqueId(), draft);
        if (!bridge.openUi(player, ui)) {
            drafts.remove(player.getUniqueId(), draft);
            return false;
        }
        return true;
    }

    public boolean ownsPacket(String packet) { return ui.equalsIgnoreCase(packet) || UI.equalsIgnoreCase(packet); }

    public void handle(Player player, List<String> data) {
        Draft draft = drafts.get(player.getUniqueId());
        if (draft == null || !player.hasPermission(ADMIN) || data == null || data.isEmpty()) return;
        String action = data.get(0);
        if ("close".equals(action)) {
            if (data.size() > 1 && draft.token.equals(data.get(1))) drafts.remove(player.getUniqueId(), draft);
            return;
        }
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
                case "sample" -> { sample(player, draft, data); yield ""; }
                case "clear" -> { set(draft, integer(data, 2), null, false); yield ""; }
                case "save" -> {
                    save(draft, field(data, 2), integer(data, 3), field(data, 4));
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
                case "refresh" -> "";
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

    private void select(Draft draft, String id) {
        Entry existing = id.isEmpty() ? null : craftingEntries().stream()
            .filter(entry -> entry.definition().id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("配方已不存在"));
        draft.revision = registry.editRevision();
        draft.fieldVersion++;
        draft.selected = id;
        Collections.fill(draft.grid, Ingredient.blank());
        draft.result = null;
        draft.station = Station.SHAPED;
        draft.permission = "";
        if (existing != null) {
            Definition definition = existing.definition();
            draft.station = definition.station();
            draft.result = definition.result();
            draft.permission = definition.permission();
            for (int i = 0; i < definition.ingredients().size(); i++) draft.grid.set(i, definition.ingredients().get(i));
        }
    }

    private void sample(Player player, Draft draft, List<String> data) {
        int target = integer(data, 2), slot = integer(data, 3);
        if (slot < 0 || slot >= 36 || target < 0 || target > 9) throw new IllegalArgumentException("样品格无效");
        ItemStack item = player.getInventory().getItem(slot);
        if (InventoryItems.empty(item) || !fingerprint(item).equals(field(data, 4))) {
            throw new IllegalArgumentException("背包中的样品已变化，请重新选择");
        }
        NativeRecipeFactory.validateSnapshot(item);
        set(draft, target, item.clone(), "material".equals(field(data, 5)));
    }

    private void set(Draft draft, int target, ItemStack item, boolean material) {
        if (target == 9) {
            draft.result = item;
            return;
        }
        if (target < 0 || target >= 9) throw new IllegalArgumentException("材料格无效");
        if (item == null) draft.grid.set(target, Ingredient.blank());
        else if (material) {
            var alternatives = new ArrayList<>(draft.grid.get(target).materials());
            if (!alternatives.contains(item.getType())) alternatives.add(item.getType());
            draft.grid.set(target, new Ingredient(alternatives, List.of()));
        } else draft.grid.set(target, new Ingredient(List.of(), List.of(item)));
    }

    private void save(Draft draft, String id, int amount, String permission) throws Exception {
        if (draft.result == null || amount < 1 || amount > draft.result.getMaxStackSize()) {
            throw new IllegalArgumentException("请选择产物并填写有效数量");
        }
        if (permission.length() > 120 || !permission.matches("[a-zA-Z0-9_.-]*")) {
            throw new IllegalArgumentException("权限节点格式无效");
        }
        if (!draft.selected.isEmpty() && !draft.selected.equals(id)) throw new IllegalArgumentException("已发布配方的 ID 不可修改");
        if (draft.selected.isEmpty() && registry.recipes().stream().filter(registry::configured)
            .anyMatch(entry -> entry.definition().id().equals(id))) throw new IllegalArgumentException("配方 ID 已存在");
        var grid = draft.station == Station.SHAPED ? draft.grid : draft.grid.stream().filter(i -> !i.empty()).toList();
        ItemStack result = draft.result.clone();
        result.setAmount(amount);
        registry.save(id, new Definition(id, draft.station, grid, result, permission, 200, 0, SmithingData.INHERIT_BASE),
            draft.revision);
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
        Map<String, Object> slots = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (!InventoryItems.empty(item)) slots.put(Integer.toString(i), Map.of("icon", items.icon(item), "id", fingerprint(item)));
        }
        Map<String, Object> cells = new LinkedHashMap<>();
        for (int i = 0; i < draft.grid.size(); i++) {
            Ingredient ingredient = draft.grid.get(i);
            if (ingredient.empty()) continue;
            List<Material> materials = ingredient.materials();
            ItemStack item = materials.isEmpty() ? ingredient.exact().get(0) : new ItemStack(materials.get(0));
            cells.put(Integer.toString(i), Map.of("icon", items.icon(item),
                "hint", materials.isEmpty() ? "精确" : "材质 ×" + materials.size()));
        }
        if (draft.result != null) cells.put("9", Map.of("icon", items.icon(draft.result), "hint", "产物"));
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("token", draft.token);
        packet.put("list", list);
        packet.put("page", (draft.page + 1) + " / " + pages);
        packet.put("inventory", slots);
        packet.put("cells", cells);
        packet.put("selected", draft.selected);
        packet.put("fieldVersion", draft.fieldVersion);
        packet.put("permission", draft.permission);
        packet.put("amount", draft.result == null ? "1" : Integer.toString(draft.result.getAmount()));
        packet.put("station", draft.station == Station.SHAPED ? "有序" : "无序");
        packet.put("message", message.length() > 240 ? message.substring(0, 240) : message);
        bridge.sendPacket(player, ui, "recipe_editor_state", packet);
    }

    private static String fingerprint(ItemStack item) { return ItemSnapshots.hash(ItemSnapshots.encode(item)); }

    private static String field(List<String> data, int index) {
        return data.size() > index ? data.get(index) : "";
    }

    private static int integer(List<String> data, int index) { return Integer.parseInt(field(data, index)); }

    @EventHandler public void quit(PlayerQuitEvent event) { drafts.remove(event.getPlayer().getUniqueId()); }

    @Override public void close() {
        drafts.clear();
    }
}
