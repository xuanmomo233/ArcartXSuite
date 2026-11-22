package xuanmo.arcartxsuite.essentials.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerHideEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import xuanmo.arcartxsuite.api.auth.AuthAccess;

/** 查看容器只展示背包副本。编辑时由目标背包的实时槽位和查看者光标完成交换。 */
public final class InventoryInspection implements Listener {

    public static final String VIEW_PERMISSION = "axs.essentials.inv";
    public static final String EDIT_PERMISSION = "axs.essentials.inv.edit";
    private final Map<UUID, Inspection> sessions = new HashMap<>();

    public void open(Player viewer, Player target, boolean editing) {
        if (!viewer.hasPermission(VIEW_PERMISSION) || !viewer.canSee(target)
            || (editing && (!viewer.hasPermission(EDIT_PERMISSION) || viewer.equals(target)))) {
            return;
        }
        Inspection session = new Inspection(viewer, target, editing);
        for (int slot = 0; slot < session.inventory.getSize(); slot++) {
            session.inventory.setItem(slot, copy(target.getInventory().getItem(targetSlot(slot))));
        }
        viewer.openInventory(session.inventory);
        if (viewer.getOpenInventory().getTopInventory().getHolder() == session) {
            sessions.put(viewer.getUniqueId(), session);
        }
    }

    public void closeAll() {
        for (Inspection session : new ArrayList<>(sessions.values())) {
            close(session);
        }
        sessions.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Inspection session)) return;
        boolean previouslyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (previouslyCancelled || !(event.getWhoClicked() instanceof Player viewer)
            || sessions.get(viewer.getUniqueId()) != session || !session.editing
            || !viewer.hasPermission(VIEW_PERMISSION) || !viewer.hasPermission(EDIT_PERMISSION)
            || !AuthAccess.admitted(viewer.getUniqueId())) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
        switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_SOME, PICKUP_ONE, PLACE_ALL, PLACE_SOME, PLACE_ONE,
                 SWAP_WITH_CURSOR -> {}
            default -> { return; }
        }

        Player target = Bukkit.getPlayer(session.target);
        if (target == null || !target.isOnline() || viewer.equals(target) || !viewer.canSee(target)) return;
        int slot = event.getRawSlot();
        if (slot >= top.getSize() && slot < event.getView().countSlots()) {
            // 普通下半区点击只修改查看者自己的背包。双击收集和 Shift 转移均在上方拦截。
            event.setCancelled(false);
            return;
        }
        if (slot < 0 || slot >= top.getSize()) return;

        PlayerInventory inventory = target.getInventory();
        int targetSlot = targetSlot(slot);
        ItemStack live = copy(inventory.getItem(targetSlot));
        Transfer result = transferIfUnchanged(copy(top.getItem(slot)), live, copy(event.getView().getCursor()),
            click == ClickType.RIGHT, inventory.getMaxStackSize());
        if (result == null) {
            top.setItem(slot, copy(live));
            viewer.sendMessage(ChatColor.YELLOW + "目标槽位已变化，已刷新，请重试。");
            return;
        }

        inventory.setItem(targetSlot, copy(result.slot()));
        top.setItem(slot, copy(result.slot()));
        event.getView().setCursor(copy(result.cursor()));
        viewer.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Inspection) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Inspection session) {
            sessions.remove(event.getPlayer().getUniqueId(), session);
        }
    }

    @EventHandler
    public void onTargetHidden(PlayerHideEntityEvent event) {
        Inspection session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.target.equals(event.getEntity().getUniqueId())) close(session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sessions.remove(id);
        for (Inspection session : new ArrayList<>(sessions.values())) {
            if (session.target.equals(id)) close(session);
        }
    }

    private void close(Inspection session) {
        Player viewer = Bukkit.getPlayer(session.viewer);
        if (viewer != null && viewer.getOpenInventory().getTopInventory().getHolder() == session) {
            viewer.closeInventory();
        }
    }

    static Transfer transferIfUnchanged(ItemStack snapshot, ItemStack live, ItemStack cursor,
                                        boolean rightClick, int maxSlotSize) {
        return Objects.equals(snapshot, live) ? transfer(live, cursor, rightClick, maxSlotSize) : null;
    }

    private static Transfer transfer(ItemStack slot, ItemStack cursor, boolean rightClick, int maxSlotSize) {
        if (cursor == null) {
            if (slot == null) return new Transfer(null, null);
            int taken = rightClick ? slot.getAmount() / 2 + slot.getAmount() % 2 : slot.getAmount();
            return new Transfer(withAmount(slot, slot.getAmount() - taken), withAmount(slot, taken));
        }
        int limit = Math.min(maxSlotSize, cursor.getMaxStackSize());
        if (slot != null && !slot.isSimilar(cursor)) {
            return !rightClick && cursor.getAmount() <= limit ? new Transfer(cursor, slot) : new Transfer(slot, cursor);
        }
        int previous = slot == null ? 0 : slot.getAmount();
        int moved = Math.min(rightClick ? 1 : cursor.getAmount(), Math.max(0, limit - previous));
        return new Transfer(withAmount(cursor, previous + moved), withAmount(cursor, cursor.getAmount() - moved));
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        if (amount <= 0) return null;
        ItemStack result = item.clone();
        result.setAmount(amount);
        return result;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item.clone();
    }

    private static int targetSlot(int slot) {
        return slot < 27 ? slot + 9 : slot - 27;
    }

    record Transfer(ItemStack slot, ItemStack cursor) {}

    private static final class Inspection implements InventoryHolder {
        private final UUID viewer;
        private final UUID target;
        private final boolean editing;
        private final Inventory inventory;

        private Inspection(Player viewer, Player target, boolean editing) {
            this.viewer = viewer.getUniqueId();
            this.target = target.getUniqueId();
            this.editing = editing;
            this.inventory = Bukkit.createInventory(this, 36,
                ChatColor.DARK_GREEN + target.getName() + (editing ? " 的背包（编辑）" : " 的背包（只读）"));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
