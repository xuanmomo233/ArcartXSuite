package xuanmo.arcartxsuite.market.listener;

import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.market.MarketService;
import xuanmo.arcartxsuite.market.recycle.RecycleService;

/**
 * 市场事件监听器（自动回收 + 玩家退出清理）。
 */
public class MarketEventListener implements Listener {

    private final Supplier<RecycleService> recycleSupplier;
    private final boolean autoRecycleEnabled;
    private final Supplier<MarketService> serviceSupplier;

    public MarketEventListener(Supplier<RecycleService> recycleSupplier, boolean autoRecycleEnabled,
                                Supplier<MarketService> serviceSupplier) {
        this.recycleSupplier = recycleSupplier;
        this.autoRecycleEnabled = autoRecycleEnabled;
        this.serviceSupplier = serviceSupplier;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!autoRecycleEnabled) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.hasPermission("axsmarket.autorecycle")) return;

        RecycleService service = recycleSupplier.get();
        if (service == null) return;

        ItemStack item = event.getItem().getItemStack();
        if(service.canAutoRecycle(player,item)){
            ItemStack snapshot=item.clone();
            try{
                var result=service.recycleAuto(player,snapshot,()->{
                    ItemStack current=event.getItem().getItemStack();
                    if(!event.getItem().isValid() || !current.isSimilar(snapshot) || current.getAmount()!=snapshot.getAmount())throw new IllegalStateException("拾取物品已变化");
                    event.getItem().remove();
                });
                if(result.success())event.setCancelled(true);
            }catch(RuntimeException exception){
                if(!event.getItem().isValid())event.setCancelled(true);
                player.sendMessage("§c自动回收已中断，已保留流水，请联系管理员核对。");
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event){MarketService service=serviceSupplier.get();if(service!=null)service.warmMarketPlayer(event.getPlayer());}

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        MarketService service = serviceSupplier.get();
        if (service != null) {
            service.clearPlayerState(event.getPlayer().getUniqueId());
        }
    }
}
