package xuanmo.arcartxsuite.abyss.design;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * 设计模式监听器：标记杖交互 + 设计实例内创造编辑放行。
 * <p>
 * 标记杖 = BLAZE_ROD + PDC 标记 {@code abyss_design_wand}。
 * 交互约定：
 * <ul>
 *   <li>右键方块（无 pending）→ 命中已有对象开编辑；未命中提示</li>
 *   <li>右键方块（POINT pending）→ 落标记</li>
 *   <li>REGION pending：左键=A点、右键=B点完成</li>
 *   <li>Shift+右键 → 取消 pending</li>
 * </ul>
 * 设计实例内不拦截 blockBreak/blockPlace（创造搭场景），但限制配置仍拦普通副本。
 */
public final class DesignListener implements Listener {

    private final DesignManager manager;
    private final NamespacedKey wandKey;

    public DesignListener(Plugin plugin, DesignManager manager) {
        this.manager = manager;
        this.wandKey = new NamespacedKey(plugin, "abyss_design_wand");
    }

    /** 标记杖物品实例。 */
    public ItemStack wandItem() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l设计标记杖");
            meta.setLore(List.of(
                "§7右键方块: 选中/标记对象",
                "§7选区模式: 左键A点·右键B点",
                "§7左键空气: 重开设计面板",
                "§7Shift+右键: 取消标记"));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
            .has(wandKey, PersistentDataType.BYTE);
    }

    /** 收回玩家背包里的标记杖（退出设计时）。 */
    public void removeWand(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isWand(it)) it.setAmount(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        DesignSession s = manager.session(p);
        if (s == null) return;
        if (!isWand(e.getItem())) return;
        if (e.getClickedBlock() == null) {
            // 左键空气 = 重开主面板（无 pending 时的重开入口）
            if (e.getAction() == Action.LEFT_CLICK_AIR) {
                e.setCancelled(true);
                manager.openMain(p);
            }
            return;
        }
        e.setCancelled(true); // 设计模式下标记杖永不触发方块自身行为
        boolean right = e.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean left = e.getAction() == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return;
        if (p.isSneaking()) {
            if (s.pending() != null) {
                s.pending(null);
                p.sendMessage(manager.msgText("design.mark-cancelled"));
            }
            return;
        }
        DesignSession.PendingMark pending = s.pending();
        if (pending != null
            && pending.kind() == DesignSession.PendingMark.Kind.REGION) {
            // 选区：左键=A，右键=B
            manager.handleRegionClick(p, e.getClickedBlock().getLocation(), !left);
        } else if (right) {
            manager.handleMarkClick(p, e.getClickedBlock().getLocation(), true);
        }
        // REGION pending 下左键已被上面分支消费；无 pending 的左键不响应（防误触）
    }
}
