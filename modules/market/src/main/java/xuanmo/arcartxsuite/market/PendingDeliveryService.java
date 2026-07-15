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
 * å¾åæ¾éåæ¶è´¹èï¼ç©å®¶ä¸çº¿æ¶è¡¥åå¶ç¦»çº¿æé´ï¼æèåæ¾æ»¡æ¶ï¼ç´¯ç§¯çç©å / è´§å¸ã
 * <p>
 * ä¸æåç»ç®ç"å®å¨åæ¾"éåï¼ä¿è¯ Market äº¤æå¨ä»»ä½å¨çº¿/ç¦»çº¿/èåç¶æä¸é½ä¸ä¸¢é±ãä¸ä¸¢ç©åã
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
        // å¼æ­¥è¯»åéåï¼ä¸»çº¿ç¨åæ¾ï¼å»¶è¿ç¡®ä¿ç©å®¶å®å¨å è½½ï¼
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
                        logger.warning("[Market] å¾åæ¾ç©åååºååå¤±è´¥ï¼å·²ä¸¢å¼ id=" + delivery.id());
                        continue;
                    }
                    Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                    if (!overflow.isEmpty()) {
                        // èåä»è£ä¸ä¸ï¼ä¿çè®°å½ï¼å¾ä¸æ¬¡ä¸çº¿ / è¾ç©ºååè¡¥å
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
                logger.warning("[Market] è¡¥åè®°å½å¤çå¼å¸¸ id=" + delivery.id() + ": " + e.getMessage());
            }
        }

        if (deliveredItems > 0 || deliveredCurrency > 0) {
            StringBuilder sb = new StringBuilder(ChatColor.GREEN + "[å¸åº] å·²è¡¥åç¦»çº¿æé´ç");
            if (deliveredItems > 0) {
                sb.append(' ').append(deliveredItems).append(" ä»¶ç©å");
            }
            if (deliveredCurrency > 0) {
                sb.append(' ').append(deliveredCurrency);
            }
            player.sendMessage(sb.toString());
        }
    }
}
