package xuanmo.arcartxsuite.abyss.listener;

import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.InteractConfig.InteractType;

/**
 * 交互点触发监听器：方块点击/破坏/放置 + 实体右键 + NPC 点击。
 * 区域类（STEP_ON/ENTER_REGION）由 service.proximityTick 驱动。
 */
public final class AbyssInteractListener implements Listener {

    private final AbyssService service;
    private final Logger logger;

    public AbyssInteractListener(AbyssService service, Logger logger) {
        this.service = service;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        // 右键类交互主副手各发一次（PHYSICAL 踩踏无手），只认主手防双触发
        if (event.getHand() != null && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        AbyssInstance instance = service.instances().getInstance(player);
        if (instance == null) {
            // 实例外：世界入口点（右键登记的方块/区域进本）；命中则取消交互防门/箱子打开
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && service.tryEntranceEnter(player, event.getClickedBlock())) {
                event.setCancelled(true);
            }
            return;
        }
        if (instance.getStatus() != AbyssStatus.ACTIVE) return;
        if (service.isDesignInstance(instance)) return; // 设计实例交互交给 DesignListener
        if (event.getClickedBlock() == null) return;
        InteractType type = switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK -> InteractType.RIGHT_CLICK_BLOCK;
            case LEFT_CLICK_BLOCK -> InteractType.LEFT_CLICK_BLOCK;
            case PHYSICAL -> InteractType.STEP_ON;
            default -> null;
        };
        if (type == null) return;
        ItemStack hand = event.getItem();
        service.interacts().onBlockTrigger(instance, player, type, event.getClickedBlock(), hand);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        AbyssInstance instance = service.instances().getInstance(player);
        if (instance == null || instance.getStatus() != AbyssStatus.ACTIVE) return;
        // 设计实例：创造编辑放行（限制配置只管游玩副本）
        if (service.isDesignInstance(instance)) return;
        // 限制配置：禁止破坏方块
        if (service.config().restrictions().blockBreak()) {
            event.setCancelled(true);
            return;
        }
        service.interacts().onBlockTrigger(instance, player, InteractType.BREAK_BLOCK,
            event.getBlock(), player.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        AbyssInstance instance = service.instances().getInstance(player);
        if (instance == null || instance.getStatus() != AbyssStatus.ACTIVE) return;
        if (service.isDesignInstance(instance)) return;
        if (service.config().restrictions().blockPlace()) {
            event.setCancelled(true);
            return;
        }
        service.interacts().onBlockTrigger(instance, player, InteractType.PLACE_BLOCK,
            event.getBlock(), event.getItemInHand());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        // 主副手各触发一次是 Bukkit 实体交互的经典行为——只认主手，否则对话/脚本双跑
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        AbyssInstance instance = service.instances().getInstance(player);
        if (instance == null || instance.getStatus() != AbyssStatus.ACTIVE) return;
        // NPC 优先（NPC 点击含对话+脚本）
        if (service.npcs().handleClick(instance, player, event.getRightClicked())) {
            return;
        }
        service.interacts().onEntityTrigger(instance, player, event.getRightClicked());
    }

    /** 禁用物品（紫颂果等消耗类——传送拦截管位移，这里管吃这个动作本身）。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        AbyssInstance instance = service.instances().getInstance(player);
        if (instance == null || instance.isFinished()) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        java.util.List<String> banned = new java.util.ArrayList<>(
            service.config().restrictions().blockedItems());
        banned.addAll(instance.getTemplate().requirements().disabledItems());
        for (String b : banned) {
            Material m = Material.matchMaterial(b.toUpperCase());
            if (m != null && item.getType() == m) {
                event.setCancelled(true);
                player.sendMessage(service.msg("dungeon.item-blocked"));
                return;
            }
        }
    }
}
