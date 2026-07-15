package xuanmo.arcartxsuite.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.PendingDelivery;

/**
 * 待发放队列消费者：玩家上线时补发其离线期间（或背包曾满时）累积的物品 / 货币。
 * <p>
 * 与拍卖结算的"安全发放"配合，保证 Market 交易在任何在线/离线/背包状态下都不丢钱、不丢物品。
 */
public class PendingDeliveryService implements Listener {

    private final JavaPlugin plugin;
    private final MarketRepository repository;
    private final CurrencyBridgeAPI currencyManager;
    private final AuctionItemSerializer itemSerializer;
    private final Logger logger;

    public PendingDeliveryService(JavaPlugin plugin, MarketRepository repository,
                                  CurrencyBridgeAPI currencyManager,
                                  AuctionItemSerializer itemSerializer, Logger logger) {
        this.plugin = plugin;
        this.repository = repository;
        this.currencyManager = currencyManager;
        this.itemSerializer = itemSerializer;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        // 异步读取队列，主线程发放（延迟确保玩家完全加载）
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            List<PendingDelivery> pending = repository.getPendingDeliveries(uuid);
            if (pending.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> flush(uuid, pending));
        }, 40L);
    }

    private void flush(UUID uuid, List<PendingDelivery> pending) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        int deliveredItems = 0;
        double deliveredCurrency = 0;
        for (PendingDelivery delivery : pending) {
            try {
                if (delivery.isItem()) {
                    ItemStack item = itemSerializer.deserialize(delivery.itemData());
                    if (item == null) {
                        logger.warning("[Market] 待发放物品反序列化失败，已丢弃 id=" + delivery.id());
                        continue;
                    }
                    Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                    if (!overflow.isEmpty()) {
                        // 背包仍装不下：保留记录，待下次上线 / 腾空后再补发
                        continue;
                    }
                    deliveredItems++;
                } else if (delivery.isCurrency()) {
                    CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(delivery.currency());
                    if (bridge == null || !bridge.available()) {
                        continue;
                    }
                    CurrencyTransactionResult result = bridge.deposit(player, BigDecimal.valueOf(delivery.amount()));
                    if (!result.success()) {
                        continue;
                    }
                    deliveredCurrency += delivery.amount();
                }
                repository.deletePendingDelivery(delivery.id());
            } catch (Exception e) {
                logger.warning("[Market] 补发记录处理异常 id=" + delivery.id() + ": " + e.getMessage());
            }
        }

        if (deliveredItems > 0 || deliveredCurrency > 0) {
            StringBuilder sb = new StringBuilder(ChatColor.GREEN + "[市场] 已补发离线期间的");
            if (deliveredItems > 0) {
                sb.append(' ').append(deliveredItems).append(" 件物品");
            }
            if (deliveredCurrency > 0) {
                sb.append(' ').append(deliveredCurrency);
            }
            player.sendMessage(sb.toString());
        }
    }
}
