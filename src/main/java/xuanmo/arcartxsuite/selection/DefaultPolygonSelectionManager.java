package xuanmo.arcartxsuite.selection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.selection.PolygonSelection;
import xuanmo.arcartxsuite.api.selection.PolygonSelectionManager;

/**
 * 多边形选区管理器默认实现。
 * <p>
 * 通过 Bukkit 事件监听 wand 物品交互，管理玩家选区会话。
 * 交互约定：
 * <ul>
 *   <li>左键方块 → 添加顶点</li>
 *   <li>右键方块 → 闭合多边形（至少 3 点）</li>
 *   <li>Shift+右键 → 撤销上一个顶点</li>
 * </ul>
 */
public final class DefaultPolygonSelectionManager implements PolygonSelectionManager, Listener {

    private final Map<UUID, PolygonSelection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, Consumer<PolygonSelection>> callbacks = new ConcurrentHashMap<>();

    private volatile Material wandMaterial = Material.STICK;
    private volatile @Nullable String requiredPermission = null;

    @Override
    public void startSelection(@NotNull Player player, @Nullable Consumer<PolygonSelection> onClosed) {
        UUID uuid = player.getUniqueId();
        selections.put(uuid, new PolygonSelection());
        if (onClosed != null) {
            callbacks.put(uuid, onClosed);
        } else {
            callbacks.remove(uuid);
        }
    }

    @Override
    public @Nullable PolygonSelection getSelection(@NotNull Player player) {
        return selections.get(player.getUniqueId());
    }

    @Override
    public void stopSelection(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        selections.remove(uuid);
        callbacks.remove(uuid);
    }

    @Override
    public void setWandMaterial(@NotNull String material) {
        Material mat = Material.matchMaterial(material);
        if (mat != null) {
            this.wandMaterial = mat;
        }
    }

    @Override
    public void setRequiredPermission(@Nullable String permission) {
        this.requiredPermission = permission;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PolygonSelection sel = selections.get(player.getUniqueId());
        if (sel == null) return;

        // 权限检查
        if (requiredPermission != null && !player.hasPermission(requiredPermission)) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != wandMaterial) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();
            if (sel.addPoint(loc)) {
                player.sendMessage(ChatColor.GREEN + "[选区] 已添加点 #" + sel.pointCount()
                    + " (" + loc.getBlockX() + ", " + loc.getBlockZ() + ")");
                if (sel.canClose()) {
                    player.sendMessage(ChatColor.YELLOW + "[选区] 已有 " + sel.pointCount()
                        + " 个点，右键闭合多边形");
                }
            } else {
                player.sendMessage(ChatColor.RED + "[选区] 该点与已有顶点不在同一世界，已忽略");
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                // Shift+右键 → 撤销
                if (sel.undoLast()) {
                    player.sendMessage(ChatColor.GOLD + "[选区] 已撤销上一个点，当前 "
                        + sel.pointCount() + " 个点");
                } else {
                    player.sendMessage(ChatColor.RED + "[选区] 没有可撤销的点");
                }
            } else {
                // 右键 → 闭合
                if (sel.close()) {
                    player.sendMessage(ChatColor.GREEN + "[选区] 多边形已闭合！共 "
                        + sel.pointCount() + " 个顶点，世界: " + sel.worldName());
                    Consumer<PolygonSelection> cb = callbacks.get(player.getUniqueId());
                    if (cb != null) {
                        cb.accept(sel);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "[选区] 至少需要 3 个点才能闭合，当前 "
                        + sel.pointCount() + " 个点");
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        selections.remove(uuid);
        callbacks.remove(uuid);
    }

    /**
     * 清理所有玩家选区（插件卸载时调用）。
     */
    public void clearAll() {
        selections.clear();
        callbacks.clear();
    }
}
