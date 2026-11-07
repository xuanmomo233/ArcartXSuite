package xuanmo.arcartxsuite.market.auction;

import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/** Coalesced asynchronous lot subscriptions; stale responses never reopen or replace another lot. */
public final class AuctionRoomService implements AutoCloseable {
    private record Selection(long listingId,long generation) {}
    private final JavaPlugin plugin;
    private final MarketRepository repository;
    private final PacketBridgeAPI packets;
    private final String uiId;
    private final BiConsumer<Player,AuctionRoomSnapshot> renderer;
    private final BiConsumer<Player,String> error;
    private final Logger logger;
    private final Map<UUID,Selection> selections=new ConcurrentHashMap<>();
    private final Set<UUID> inFlight=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Long> lastReads=new ConcurrentHashMap<>();
    private final AtomicLong generations=new AtomicLong();
    private volatile boolean closed;
    private BukkitTask timer;

    public AuctionRoomService(JavaPlugin plugin,MarketRepository repository,PacketBridgeAPI packets,String uiId,
                              BiConsumer<Player,AuctionRoomSnapshot> renderer,BiConsumer<Player,String> error,Logger logger) {
        this.plugin=plugin;this.repository=repository;this.packets=packets;this.uiId=uiId;
        this.renderer=renderer;this.error=error;this.logger=logger;
    }

    public void start() {
        timer=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            for(UUID id:selections.keySet()) {
                Player player=Bukkit.getPlayer(id);
                if(player==null || !player.isOnline() || !packets.isUiOpen(player,uiId)) {leave(id);continue;}
                request(player);
            }
        },40L,40L);
    }

    public void select(Player player,long listingId) {selections.put(player.getUniqueId(),new Selection(listingId,generations.incrementAndGet()));lastReads.remove(player.getUniqueId());}
    public long selected(Player player) {Selection value=selections.get(player.getUniqueId());return value==null?0:value.listingId();}
    public void leave(UUID player) {selections.remove(player);lastReads.remove(player);}

    public void changed(long listingId) {
        Runnable update=()->{
            if(closed)return;
            selections.forEach((id,value)->{
                if(value.listingId()!=listingId)return;
                Player player=Bukkit.getPlayer(id);
                if(player!=null && player.isOnline())request(player);
            });
        };
        if(Bukkit.isPrimaryThread())update.run();else Bukkit.getScheduler().runTask(plugin,update);
    }

    public void request(Player player) {
        UUID viewer=player.getUniqueId();Selection selection=selections.get(viewer);
        long now=System.currentTimeMillis();
        if(now-lastReads.getOrDefault(viewer,0L)<500)return;
        if(closed || selection==null || !packets.isUiOpen(player,uiId) || !inFlight.add(viewer))return;
        lastReads.put(viewer,now);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            AuctionRoomSnapshot snapshot=null;String failure=null;
            try {snapshot=repository.readAuctionRoom(selection.listingId(),viewer);}
            catch(RuntimeException exception) {failure="拍品状态暂时无法同步，请稍后刷新";logger.warning("[AuctionRoom] "+exception.getMessage());}
            if(closed){inFlight.remove(viewer);return;}
            final AuctionRoomSnapshot result=snapshot;final String message=failure;
            Bukkit.getScheduler().runTask(plugin,()->{
                try {
                    Selection current=selections.get(viewer);Player online=Bukkit.getPlayer(viewer);
                    if(closed || !selection.equals(current) || online==null || !online.isOnline() || !packets.isUiOpen(online,uiId))return;
                    if(message==null)renderer.accept(online,result);else error.accept(online,message);
                } finally {inFlight.remove(viewer);}
            });
        });
    }

    @Override public void close() {closed=true;if(timer!=null)timer.cancel();selections.clear();inFlight.clear();lastReads.clear();}
}
