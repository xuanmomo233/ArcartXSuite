package xuanmo.arcartxsuite.warehouse.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.WarehousePanelAccess;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SlotItemRecord;

/** Shared warehouse view for the backpack and native container screens. */
public final class WarehouseCursorPanel implements Listener, WarehousePanelAccess, AutoCloseable {
    private static final String PREFIX = "axs_wh_entry_";
    private static final int PAGE_SIZE = 24;
    private static final int BACKPACK_PAGE_SIZE = 36;
    private final WarehouseService service;
    private final PacketBridgeAPI bridge;
    private final Logger logger;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private boolean closed;

    public WarehouseCursorPanel(WarehouseService service, PacketBridgeAPI bridge, Logger logger) {
        this.service = service;
        this.bridge = bridge;
        this.logger = logger;
    }

    public void registerClientEvents(org.bukkit.plugin.java.JavaPlugin plugin) {
        org.bukkit.plugin.Plugin arcartx = org.bukkit.Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartx == null) throw new IllegalStateException("ArcartX 尚未加载");
        try {
            Class<? extends org.bukkit.event.Event> type = Class.forName(
                "priv.seventeen.artist.arcartx.event.client.ClientExtraSlotClickEvent", false,
                arcartx.getClass().getClassLoader()).asSubclass(org.bukkit.event.Event.class);
            if (!org.bukkit.event.Cancellable.class.isAssignableFrom(type)) {
                throw new IllegalStateException("ArcartX 槽位事件不支持取消操作");
            }
            var slot = type.getMethod("getSlotID");
            var player = type.getMethod("getPlayer");
            var button = type.getMethod("getButtonID");
            org.bukkit.Bukkit.getPluginManager().registerEvent(type, this, org.bukkit.event.EventPriority.NORMAL,
                (listener, event) -> {
                    try {
                        String clickId = (String) slot.invoke(event);
                        if (clickId == null || !clickId.startsWith(PREFIX)) return;
                        ((org.bukkit.event.Cancellable) event).setCancelled(true);
                        Player actor = (Player) player.invoke(event);
                        int buttonId = ((Number) button.invoke(event)).intValue();
                        Runnable action = () -> click(actor, clickId, buttonId);
                        if (org.bukkit.Bukkit.isPrimaryThread()) action.run();
                        else org.bukkit.Bukkit.getScheduler().runTask(plugin, action);
                    } catch (ReflectiveOperationException exception) {
                        throw new org.bukkit.event.EventException(exception);
                    }
                }, plugin);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法注册 ArcartX 仓库槽位事件", exception);
        }
    }

    @Override
    public List<String> categoryIds() {
        return List.copyOf(service.configuration.categories().keySet());
    }

    @Override
    public void close() {
        closed = true;
        sessions.clear();
    }

    @Override
    public String classify(ItemStack stack) {
        return service.resolveCategory(stack);
    }

    @Override
    public void handle(Player player, String uiId, List<String> data) {
        if (closed) return;
        String action = WarehouseTexts.value(data, 0, "");
        String value = WarehouseTexts.value(data, 1, "");
        UUID uuid = player.getUniqueId();
        Session session = sessions.get(uuid);
        try {
            if ("open".equals(action) || "refresh".equals(action) && session == null) {
                if (!bridge.isUiOpen(player, uiId)) return;
                service.ensureEntitlements(player);
                var owned = service.personalWarehouseMap(uuid);
                String warehouseId = service.configuration.warehouses().keySet().stream()
                    .filter(owned::containsKey).findFirst().orElse("");
                if (warehouseId.isBlank()) {
                    bridge.sendPacket(player, uiId, "warehouse_panel", Map.of("available", false));
                    return;
                }
                session = new Session(uiId, player.getOpenInventory(), warehouseId);
                sessions.put(uuid, session);
                render(player, session);
                return;
            }
            if (session == null || !session.uiId.equals(uiId)) return;
            if ("close".equals(action)) {
                if (session.token.equals(value)) sessions.remove(uuid);
                return;
            }
            if (!valid(player, session)) return;
            switch (action) {
                case "refresh" -> { }
                case "page" -> {
                    if ("prev".equals(value)) {
                        session.page = Math.max(0, session.page - 1);
                    } else if ("next".equals(value)) {
                        session.page++;
                    } else {
                        return;
                    }
                }
                case "sort" -> {
                    List<String> sorts = service.configuration.sortProfileIds();
                    if (sorts.isEmpty()) return;
                    if (value.isBlank()) {
                        session.sort = sorts.get((sorts.indexOf(session.sort) + 1) % sorts.size());
                    } else if (sorts.contains(value)) {
                        session.sort = value;
                    } else {
                        return;
                    }
                    session.page = 0;
                }
                case "category" -> {
                    List<String> ids = new ArrayList<>();
                    ids.add("all");
                    ids.addAll(service.configuration.categories().keySet());
                    if (value.isBlank()) {
                        session.category = ids.get((ids.indexOf(session.category) + 1) % ids.size());
                    } else if (ids.contains(value)) {
                        session.category = value;
                    } else {
                        return;
                    }
                    session.page = 0;
                }
                case "search" -> {
                    session.search = WarehouseTexts.normalizeToken(value.substring(0, Math.min(value.length(), 80)));
                    session.page = 0;
                }
                case "deposit" -> {
                    if (!session.token.equals(value) || !canWrite(player)) return;
                    deposit(player, session);
                }
                case "pin" -> {
                    if (!"default".equals(session.sort) || !empty(player.getItemOnCursor()) || !canWrite(player)
                        || !session.token.equals(WarehouseTexts.value(data, 2, ""))) return;
                    SlotItemRecord shown = session.entries.get(value);
                    if (shown == null) return;
                    SlotItemRecord current = service.repository.loadSlot(shown.ownerType(), shown.ownerId(),
                        shown.warehouseId(), shown.slot()).orElse(null);
                    if (current == null || !EntryKey.of(current).equals(EntryKey.of(shown))) {
                        render(player, session);
                        return;
                    }
                    Set<String> pins = service.repository.loadPins(uuid, session.warehouseId);
                    service.repository.setPin(uuid, session.warehouseId, current.itemHash(), !pins.contains(current.itemHash()));
                    session.page = 0;
                }
                default -> { return; }
            }
            render(player, session);
        } catch (Exception exception) {
            failed(player, exception);
        }
    }

    private void click(Player player, String clickId, int button) {
        if (closed) return;
        if (!clickId.startsWith(PREFIX)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !valid(player, session) || (button < 0 || button > 2)
            || !empty(player.getItemOnCursor()) || !canWrite(player)) return;
        SlotItemRecord shown = session.entries.get(clickId);
        if (shown == null) return;
        try {
            SlotItemRecord current = service.repository.loadSlot(shown.ownerType(), shown.ownerId(),
                shown.warehouseId(), shown.slot()).orElse(null);
            if (current == null || !EntryKey.of(current).equals(EntryKey.of(shown))) {
                render(player, session);
                return;
            }
            if (button == 2) {
                if (!"default".equals(session.sort)) return;
                Set<String> pins = service.repository.loadPins(player.getUniqueId(), session.warehouseId);
                service.repository.setPin(player.getUniqueId(), session.warehouseId, current.itemHash(),
                    !pins.contains(current.itemHash()));
                session.page = 0;
            } else {
                ItemStack item = service.deserializeSlotItem(current);
                if (empty(item)) {
                    int group=service.unavailableBatchSize(current);
                    if(service.discardUnavailable(player,current,button==0?group:(group+1)/2)>0)session.rotateToken();
                    render(player,session);
                    return;
                }
                int visible = visibleAmount(current, item);
                int requested = button == 0 ? visible : (visible + 1) / 2;
                var result = service.withdraw(player, current.slot(), requested, current);
                if (result.success()) {
                    player.setItemOnCursor(result.itemStack());
                    session.rotateToken();
                } else {
                    player.sendMessage(result.message());
                }
            }
            render(player, session);
        } catch (Exception exception) {
            failed(player, exception);
        }
    }

    private void deposit(Player player, Session session) throws Exception {
        ItemStack carried = player.getItemOnCursor();
        if (empty(carried)) return;
        ItemStack stack = carried.clone();
        var result = service.depositStack(player, WarehouseService.OWNER_PERSONAL,
            player.getUniqueId().toString(), session.warehouseId, stack);
        if (!result.success()) {
            player.sendMessage(result.message());
            return;
        }
        ItemStack remainder = stack.clone();
        remainder.setAmount(result.remainingAmount());
        player.setItemOnCursor(result.remainingAmount() > 0 ? remainder : null);
        session.rotateToken();
        // Closing the deposit overlay can expose the grid during the same native click.
        // Discard click IDs issued before the transfer so that click cannot take the item back.
        session.ids.clear();
        session.entries.clear();
        service.publishDepositEvent(player, stack, result.storedAmount());
    }

    private void render(Player player, Session session) throws Exception {
        UUID uuid = player.getUniqueId();
        List<SlotItemRecord> inventory = service.packetFactory.orderedSlots(
            WarehouseService.OWNER_PERSONAL, uuid.toString(), session.warehouseId, session.sort);
        Set<EntryKey> currentKeys = new HashSet<>();
        for (SlotItemRecord row : inventory) currentKeys.add(EntryKey.of(row));
        session.ids.keySet().retainAll(currentKeys);
        session.entries.values().removeIf(row -> !currentKeys.contains(EntryKey.of(row)));
        List<SlotItemRecord> filtered = inventory.stream()
            .filter(row -> service.slotMatches(row, session.category, session.search)).toList();
        int pageSize = session.backpack() ? BACKPACK_PAGE_SIZE : PAGE_SIZE;
        int pages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        session.page = Math.min(session.page, pages - 1);
        int start = session.page * pageSize;
        List<SlotItemRecord> page = filtered.subList(start, Math.min(start + pageSize, filtered.size()));
        Map<Integer, SlotItemRecord> pageItems = new HashMap<>();
        for (SlotItemRecord row : service.repository.loadSlots(WarehouseService.OWNER_PERSONAL,
            uuid.toString(), session.warehouseId, page.stream().map(SlotItemRecord::slot).toList())) {
            pageItems.put(row.slot(), row);
        }
        Set<String> pins = "default".equals(session.sort)
            ? service.repository.loadPins(uuid, session.warehouseId) : Set.of();
        Map<String, Object> rows = new LinkedHashMap<>();
        for (int i = 0; i < page.size(); i++) {
            SlotItemRecord summary = page.get(i);
            SlotItemRecord record = pageItems.get(summary.slot());
            if (record == null || !EntryKey.of(record).equals(EntryKey.of(summary))) continue;
            ItemStack item = service.deserializeSlotItem(record);
            if (empty(item)) item=service.unavailableItemIcon(record);
            String clickId = session.ids.computeIfAbsent(EntryKey.of(record), key -> PREFIX + UUID.randomUUID());
            session.entries.put(clickId, record);
            var meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.of());
                lore.add("§7仓库总量 §f" + record.amount());
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            // 展示图标固定为一个；总量由各面板的 Text 显示，取出数量仍由 visibleAmount 计算。
            item.setAmount(1);
            String icon = service.itemStackBridge == null ? ""
                : service.itemStackBridge.itemToJson(item).orElse("");
            rows.put(Integer.toString(i), Map.of("clickId", clickId, "icon", icon,
                "pinned", pins.contains(record.itemHash()), "amount", record.amount()));
        }
        var category = service.configuration.categories().get(session.category);
        Map<String, Object> packet = new LinkedHashMap<>(Map.of(
            "available", true, "token", session.token, "rows", rows,
            "page", (session.page + 1) + " / " + pages,
            "hasPreviousPage", session.page > 0,
            "hasNextPage", session.page + 1 < pages,
            "sort", service.packetFactory.sortText(session.sort),
            "category", category == null ? "全部" : category.displayName(),
            "count", inventory.size() + " / " + service.personalUnlockedSlots(uuid, session.warehouseId)));
        Map<String, Object> categories = new LinkedHashMap<>();
        categories.put("0", Map.of("id", "all", "name", "全部", "active", "all".equals(session.category)));
        service.configuration.categories().forEach((id, definition) -> {
            if (!"all".equals(id)) {
                categories.put(Integer.toString(categories.size()), Map.of(
                    "id", id, "name", definition.displayName(), "active", id.equals(session.category)));
            }
        });
        packet.put("categories", categories);
        packet.put("categoryCount", categories.size());
        Map<String, Object> sorts = new LinkedHashMap<>();
        for (String id : service.configuration.sortProfileIds()) {
            sorts.put(Integer.toString(sorts.size()), Map.of("id", id,
                "name", service.packetFactory.sortText(id), "active", id.equals(session.sort)));
        }
        packet.put("sorts", sorts);
        packet.put("sortCount", sorts.size());
        bridge.sendPacket(player, session.uiId, "warehouse_panel", packet);
    }

    private boolean valid(Player player, Session session) {
        return player.isOnline() && player.getOpenInventory() == session.inventory
            && bridge.isUiOpen(player, session.uiId);
    }

    private boolean canWrite(Player player) {
        if (service.isSecondaryUnlocked(player)) return true;
        player.sendMessage("仓库已锁定，请先解锁二级密码。");
        return false;
    }

    private void failed(Player player, Exception exception) {
        logger.log(Level.WARNING, "仓库面板操作失败", exception);
        player.sendMessage("仓库操作失败，请刷新后重试。");
    }

    @Override
    public void close(String uiId) {
        sessions.values().removeIf(session -> session.uiId.equals(uiId));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private static boolean empty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    private static int visibleAmount(SlotItemRecord row, ItemStack stack) {
        return (int) Math.min(row.amount(), Math.min(64, stack.getMaxStackSize()));
    }

    private record EntryKey(int slot, String itemHash, long createdAt) {
        static EntryKey of(SlotItemRecord row) {
            return new EntryKey(row.slot(), row.itemHash(), row.createdAt());
        }
    }

    private static final class Session {
        final String uiId;
        final InventoryView inventory;
        final String warehouseId;
        final Map<EntryKey, String> ids = new HashMap<>();
        final Map<String, SlotItemRecord> entries = new HashMap<>();
        String token = UUID.randomUUID().toString();
        String sort = "default";
        String category = "all";
        String search = "";
        int page;

        Session(String uiId, InventoryView inventory, String warehouseId) {
            this.uiId = uiId;
            this.inventory = inventory;
            this.warehouseId = warehouseId;
        }

        void rotateToken() {
            token = UUID.randomUUID().toString();
        }

        boolean backpack() {
            return "AXS:extrabackpack".equalsIgnoreCase(uiId);
        }
    }
}
