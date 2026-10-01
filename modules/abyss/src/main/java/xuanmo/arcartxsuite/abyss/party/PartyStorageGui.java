package xuanmo.arcartxsuite.abyss.party;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.service.AbyssService;

/**
 * 队伍仓库 GUI：Bukkit 容器界面（45 格/页 + 底行翻页），全客户端通用。
 * <p>
 * 存储模型复用 {@link Party} 的 slot→ItemStack 映射；翻页只改视口，关闭时回写。
 */
public final class PartyStorageGui implements InventoryHolder {

    private static final int PAGE_SLOTS = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final AbyssService service;
    private final Party party;
    private final int page;
    private final Inventory inv;

    private PartyStorageGui(AbyssService service, Party party, int page, int pages) {
        this.service = service;
        this.party = party;
        this.page = page;
        this.inv = Bukkit.createInventory(this, 54,
            service.msg("party.storage-title",
                "party", party.name(),
                "page", String.valueOf(page + 1),
                "pages", String.valueOf(pages)));
        refresh();
    }

    public static void open(AbyssService service, Player player, Party party, int page) {
        int pages = Math.max(1, service.config().party().storagePages());
        int p = Math.max(0, Math.min(page, pages - 1));
        player.openInventory(new PartyStorageGui(service, party, p, pages).inv);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    /** 填充当前页内容 + 底行导航。 */
    private void refresh() {
        int pages = Math.max(1, service.config().party().storagePages());
        // 存储区
        for (int i = 0; i < PAGE_SLOTS; i++) {
            ItemStack it = party.getStorageItem(page * PAGE_SLOTS + i);
            inv.setItem(i, it);
        }
        // 导航
        inv.setItem(SLOT_PREV, navItem(Material.ARROW, "party.storage-prev"));
        inv.setItem(SLOT_INFO, navItem(Material.ENDER_CHEST, "party.storage-info"));
        inv.setItem(SLOT_NEXT, navItem(Material.ARROW, "party.storage-next"));
        for (int i = PAGE_SLOTS; i < 54; i++) {
            if (i != SLOT_PREV && i != SLOT_INFO && i != SLOT_NEXT) {
                inv.setItem(i, navItem(Material.BLACK_STAINED_GLASS_PANE, "party.storage-filler"));
            }
        }
    }

    private ItemStack navItem(Material m, String key) {
        ItemStack it = new ItemStack(m);
        var meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(service.msg(key));
            it.setItemMeta(meta);
        }
        return it;
    }

    /** 该 GUI 是否属于指定队伍（解散前置关窗判定用）。 */
    public boolean isFor(Party p) { return party == p; }

    /** 关闭时把当前页视口写回队伍仓库。 */
    private void writeBack() {
        for (int i = 0; i < PAGE_SLOTS; i++) {
            party.setStorageItem(page * PAGE_SLOTS + i, inv.getItem(i));
        }
    }

    /** 事件监听器（挂到 moduleListeners）。 */
    public static final class Events implements Listener {
        private final AbyssService service;
        private final Logger logger;

        public Events(AbyssService service, Logger logger) {
            this.service = service;
            this.logger = logger;
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder() instanceof PartyStorageGui gui)) return;
            // 点底行：导航/禁止；点存储区：放行
            if (e.getClickedInventory() == gui.inv && e.getRawSlot() >= PAGE_SLOTS) {
                e.setCancelled(true);
                if (!(e.getWhoClicked() instanceof Player p)) return;
                if (e.getRawSlot() == SLOT_PREV) {
                    open(service, p, gui.party, gui.page - 1);
                } else if (e.getRawSlot() == SLOT_NEXT) {
                    open(service, p, gui.party, gui.page + 1);
                }
                return;
            }
            // 数字键互换/收集类动作会把容器物品顶到底行导航位，直接禁掉
            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && e.getClickedInventory() != gui.inv) return; // 玩家背包 → 仓库正常进
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onDrag(InventoryDragEvent e) {
            if (!(e.getInventory().getHolder() instanceof PartyStorageGui gui)) return;
            for (int raw : e.getRawSlots()) {
                if (raw >= PAGE_SLOTS && raw < 54) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onClose(InventoryCloseEvent e) {
            if (!(e.getInventory().getHolder() instanceof PartyStorageGui gui)) return;
            gui.writeBack();
        }
    }
}
