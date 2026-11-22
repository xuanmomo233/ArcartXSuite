package xuanmo.arcartxsuite.market.auction;

import xuanmo.arcartxsuite.market.MarketWork;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/** Coalesced asynchronous lot subscriptions; stale responses never reopen or replace another lot. */
public final class AuctionRoomService implements AutoCloseable {
    private static final long READ_INTERVAL_MS = 500;
    private record Selection(long listingId,long generation) {}
    private record InitialSnapshot(AuctionRoomSnapshot snapshot, long readAt) {}
    private final MarketWork work;
    private final MarketRepository repository;
    private final PacketBridgeAPI packets;
    private final String uiId;
    private final CurrencyBridgeAPI currencies;
    private final Renderer renderer;
    private final BiConsumer<Player,String> error;
    private final Logger logger;
    private final Map<UUID,Selection> selections=new ConcurrentHashMap<>();
    private final Map<UUID,InitialSnapshot> initialSnapshots=new ConcurrentHashMap<>();
    private final Set<UUID> inFlight=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Long> lastReads=new ConcurrentHashMap<>();
    private final AtomicLong generations=new AtomicLong();
    private volatile boolean closed;
    private BukkitTask timer;

    @FunctionalInterface
    public interface Renderer {
        void render(Player viewer, @Nullable AuctionRoomSnapshot snapshot, @Nullable BigDecimal balance);
    }

    public AuctionRoomService(MarketWork work, MarketRepository repository, PacketBridgeAPI packets, String uiId,
                              CurrencyBridgeAPI currencies, Renderer renderer, BiConsumer<Player,String> error, Logger logger) {
        this.work = work;this.repository=repository;this.packets=packets;this.uiId=uiId;this.currencies=currencies;
        this.renderer=renderer;this.error=error;this.logger=logger;
    }

    public void start() {
        timer=work.repeat(()->{
            for(UUID id:selections.keySet()) {
                Player player=Bukkit.getPlayer(id);
                if(player==null || !player.isOnline() || !packets.isUiOpen(player,uiId)) {leave(id);continue;}
                request(player);
            }
        },40L,40L);
    }

    public void select(Player player, long listingId, AuctionRoomSnapshot initial, long readAt) {
        UUID viewer = player.getUniqueId();
        selections.put(viewer,new Selection(listingId,generations.incrementAndGet()));
        lastReads.remove(viewer);
        if (initial == null) initialSnapshots.remove(viewer);
        else initialSnapshots.put(viewer, new InitialSnapshot(initial, readAt));
    }
    public long selected(Player player) {Selection value=selections.get(player.getUniqueId());return value==null?0:value.listingId();}
    public void leave(UUID player) {selections.remove(player);lastReads.remove(player);initialSnapshots.remove(player);}

    public void changed(long listingId) {
        Runnable update=()->{
            if(closed)return;
            selections.forEach((id,value)->{
                if(value.listingId()!=listingId)return;
                initialSnapshots.remove(id);
                Player player=Bukkit.getPlayer(id);
                if(player!=null && player.isOnline())request(player);
            });
        };
        if(Bukkit.isPrimaryThread())update.run();else work.main(update);
    }

    public void requestInitial(Player player) {
        UUID viewer = player.getUniqueId();
        Selection selection = selections.get(viewer);
        InitialSnapshot initial = initialSnapshots.remove(viewer);
        if (selection != null && initial != null && currentRoom(player, selection)
                && System.nanoTime() - initial.readAt() <= TimeUnit.MILLISECONDS.toNanos(READ_INTERVAL_MS)
                && initial.snapshot().listing().isActive() && inFlight.add(viewer)) {
            lastReads.put(viewer, System.currentTimeMillis());
            loadBalance(player, selection, initial.snapshot());
            return;
        }
        request(player);
    }

    /** Returns false when an open room needs a later attempt. Called on the server thread. */
    public boolean request(Player player) {
        UUID viewer=player.getUniqueId();Selection selection=selections.get(viewer);
        long now=System.currentTimeMillis();
        if(closed || selection==null || !packets.isUiOpen(player,uiId))return true;
        if(now-lastReads.getOrDefault(viewer,0L)<READ_INTERVAL_MS || !inFlight.add(viewer))return false;
        initialSnapshots.remove(viewer);
        lastReads.put(viewer,now);
        work.async(()->{
            AuctionRoomSnapshot snapshot=null;String failure=null;
            try {snapshot=repository.readAuctionRoom(selection.listingId(),viewer);}
            catch(RuntimeException exception) {failure="拍品状态暂时无法同步，请稍后刷新";logger.warning("[AuctionRoom] "+exception.getMessage());}
            if(closed){inFlight.remove(viewer);return;}
            final AuctionRoomSnapshot result=snapshot;final String message=failure;
            work.main(()->{
                if (!currentRoom(player, selection)) {
                    inFlight.remove(viewer);
                } else if (message != null) {
                    try { error.accept(player, message); }
                    finally { inFlight.remove(viewer); }
                } else {
                    loadBalance(player, selection, result);
                }
            });
        });
        return true;
    }

    private boolean currentRoom(Player player, Selection selection) {
        UUID viewer = player.getUniqueId();
        return !closed && selection.equals(selections.get(viewer)) && Bukkit.getPlayer(viewer) == player
            && player.isOnline() && packets.isUiOpen(player, uiId);
    }

    private void loadBalance(Player player, Selection selection, @Nullable AuctionRoomSnapshot snapshot) {
        BigDecimal immediate = null;
        Supplier<BigDecimal> background = null;
        try {
            var currency = snapshot == null ? null : currencies.bridge(snapshot.listing().getCurrency());
            if (currency != null && currency.available()) {
                background = currency.prepareBackgroundBalance(player);
                if (background == null) immediate = currency.balance(player);
            }
        } catch (RuntimeException | LinkageError failure) {
            logger.fine("[AuctionRoom] 查询余额失败: " + failure.getMessage());
        }
        if (background == null) {
            finishRoom(player, selection, snapshot, immediate);
            return;
        }
        Supplier<BigDecimal> query = background;
        try {
            work.async(() -> {
                BigDecimal balance = null;
                try { if (!closed) balance = query.get(); }
                catch (RuntimeException | LinkageError failure) {
                    logger.fine("[AuctionRoom] 后台查询余额失败: " + failure.getMessage());
                }
                BigDecimal loaded = balance;
                work.main(() -> finishRoom(player, selection, snapshot, loaded));
            });
        } catch (java.util.concurrent.RejectedExecutionException closing) {
            inFlight.remove(player.getUniqueId());
        }
    }

    private void finishRoom(Player player, Selection selection, @Nullable AuctionRoomSnapshot snapshot, @Nullable BigDecimal balance) {
        try {
            if (currentRoom(player, selection)) renderer.render(player, snapshot, balance);
        } finally {
            inFlight.remove(player.getUniqueId());
        }
    }

    @Override public void close() {closed=true;if(timer!=null)timer.cancel();selections.clear();initialSnapshots.clear();inFlight.clear();lastReads.clear();}
}
