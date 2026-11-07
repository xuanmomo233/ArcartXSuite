package xuanmo.arcartxsuite.market;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/** Persistent READY tasks resume automatically; uncertain tasks require reconciliation. */
public final class PendingDeliveryService implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final MarketRepository repository;
    private final CurrencyBridgeAPI currencies;
    private final AuctionItemSerializer serializer;
    private final ItemRewardDispatcher dispatcher;
    private final MarketLedger ledger;
    private final Logger logger;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean beating = new AtomicBoolean();
    private volatile boolean closed;
    private BukkitTask timer;

    public PendingDeliveryService(JavaPlugin plugin, MarketRepository repository, CurrencyBridgeAPI currencies,
                                  AuctionItemSerializer serializer, ItemRewardDispatcher dispatcher, Logger logger, MarketLedger ledger) {
        this.plugin=plugin; this.repository=repository; this.currencies=currencies;
        this.serializer=serializer; this.dispatcher=dispatcher; this.logger=logger; this.ledger=ledger;
    }

    public void start() {
        timer=Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (closed) return;
            if (beating.compareAndSet(false,true)) Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try { if (!closed) ledger.heartbeat(); }
                catch(RuntimeException exception) {logger.warning("[Market] 恢复扫描失败: "+exception.getMessage());}
                finally {beating.set(false);}
            });
            for(Player player:Bukkit.getOnlinePlayers()) request(player.getUniqueId());
        },40L,40L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { request(event.getPlayer().getUniqueId()); }

    private void request(UUID playerId) {
        if(closed || !inFlight.add(playerId)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var tasks=repository.operations("READY",playerId,0,50);
                if(closed || tasks.isEmpty()) {inFlight.remove(playerId);return;}
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Player player=Bukkit.getPlayer(playerId);
                        if(!closed && player!=null && player.isOnline()) ledger.deliver(player,tasks,currencies,serializer,dispatcher);
                    } finally {inFlight.remove(playerId);}
                });
            } catch(RuntimeException exception) {
                inFlight.remove(playerId);
                logger.warning("[Market] 无法读取交付队列: "+exception.getMessage());
            }
        });
    }

    @Override
    public void close() {closed=true;if(timer!=null)timer.cancel();inFlight.clear();}
}
