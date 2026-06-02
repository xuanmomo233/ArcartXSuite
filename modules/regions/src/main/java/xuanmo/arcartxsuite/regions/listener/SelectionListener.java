package xuanmo.arcartxsuite.regions.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.Selection;
import xuanmo.arcartxsuite.regions.service.RegionManager;

/**
 * 选区工具监听器 — 左键设点1，右键设点2。
 */
public final class SelectionListener implements Listener {

    private final RegionManager manager;
    private final RegionsConfiguration config;

    public SelectionListener(RegionManager manager, RegionsConfiguration config) {
        this.manager = manager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("axs.regions.select")) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != config.selection().wandItem()) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();
            Selection sel = manager.getSelection(player.getUniqueId());
            sel.setPos1(loc);
            String msg = config.messages().wandPos1()
                .replace("{x}", String.valueOf(loc.getBlockX()))
                .replace("{y}", String.valueOf(loc.getBlockY()))
                .replace("{z}", String.valueOf(loc.getBlockZ()));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().prefix() + msg));
            if (sel.isComplete()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    config.messages().prefix() + "&7选区体积: &f" + sel.volume() + " 方块"));
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();
            Selection sel = manager.getSelection(player.getUniqueId());
            sel.setPos2(loc);
            String msg = config.messages().wandPos2()
                .replace("{x}", String.valueOf(loc.getBlockX()))
                .replace("{y}", String.valueOf(loc.getBlockY()))
                .replace("{z}", String.valueOf(loc.getBlockZ()));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().prefix() + msg));
            if (sel.isComplete()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    config.messages().prefix() + "&7选区体积: &f" + sel.volume() + " 方块"));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.cleanup(event.getPlayer().getUniqueId());
    }
}
