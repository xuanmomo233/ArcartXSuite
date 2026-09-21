package xuanmo.arcartxsuite.market;

import java.math.BigDecimal;
import java.util.List;
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
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.PendingDelivery;

/**
 * 待发放队列消费者：玩家上线时补发其离线期间（或背包曾满时）累积的物品 / 货币。
 * <p>
 * 物品补发统一通过 {@link ItemRewardDispatcher}（放入背包→背包满转邮件→无邮件则掉落），
 * 背包仍满时保留记录待下次补发。与拍卖结算的"安全发放"配合，
 * 保证 Market 交易在任何在线/离线/背包状态下都不丢钱、不丢物品。
 */
public class PendingDeliveryService implements Listener {

    private final JavaPlugin plugin;
    private final MarketRepository repository;
    private final CurrencyBridgeAPI currencyManager;
    private final AuctionItemSerializer itemSerializer;
    private final ItemRewardDispatcher itemRewardDispatcher;
    private final Logger logger;

    public PendingDeliveryService(JavaPlugin plugin, MarketRepository repository,
                                  CurrencyBridgeAPI currencyManager,
                                  AuctionItemSerializer itemSerializer,
                                  ItemRewardDispatcher itemRewardDispatcher,
                                  Logger logger) {
        this.plugin = plugin;
        this.repository = repository;
        this.currencyManager = currencyManager;
        this.itemSerializer = itemSerializer;
        this.itemRewardDispatcher = itemRewardDispatcher;
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
        int failedCount = 0;
        for (PendingDelivery delivery : pending) {
            try {
                boolean success = false;
                if (delivery.isItem()) {
                    ItemStack item = itemSerializer.deserialize(delivery.itemData());
                    if (item == null) {
                        logger.warning("待发放物品反序列化失败，已丢弃 id=" + delivery.id());
                        // 反序列化失败=数据损坏，直接删除避免永久阻塞队列
                        safeDelete(repository, delivery.id(), logger);
                        continue;
                    }
                    xuanmo.arcartxsuite.api.item.ItemRewardDispatcher.DispatchOptions options =
                        xuanmo.arcartxsuite.api.item.ItemRewardDispatcher.DispatchOptions.builder("market")
                            .sourceDetail("pending-delivery:" + delivery.id())
                            .overflowMailSubject("市场待发放物品")
                            .overflowMailBody("您的背包已满，市场待发放物品通过邮件补发。")
                            .overflowMailSenderName("全球市场")
                            .build();
                    xuanmo.arcartxsuite.api.item.ItemRewardDispatcher.DispatchResult result =
                        itemRewardDispatcher.dispatchItem(player, item, options);
                    if (!result.success()) {
                        // 发放失败（含邮件失败且掉落失败）：保留记录待下次补发
                        logger.warning("待发放物品发放失败，保留记录待下次补发 id=" + delivery.id()
                            + " reason=" + result.message());
                        failedCount++;
                        continue;
                    }
                    success = true;
                    deliveredItems++;
                } else if (delivery.isCurrency()) {
                    CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(delivery.currency());
                    if (bridge == null || !bridge.available()) {
                        logger.warning("待发放货币桥接不可用，保留记录待下次补发 id=" + delivery.id()
                            + " currency=" + delivery.currency());
                        failedCount++;
                        continue;
                    }
                    CurrencyTransactionResult result = bridge.deposit(player, BigDecimal.valueOf(delivery.amount()));
                    if (!result.success()) {
                        logger.warning("待发放货币入账失败，保留记录待下次补发 id=" + delivery.id()
                            + " reason=" + result.message());
                        failedCount++;
                        continue;
                    }
                    success = true;
                    deliveredCurrency += delivery.amount();
                }
                // 发放成功后删除记录：独立 try-catch，避免 delete 异常导致 catch 误判为发放失败
                if (success) {
                    safeDelete(repository, delivery.id(), logger);
                }
            } catch (Exception e) {
                logger.warning("补发记录处理异常 id=" + delivery.id() + ": " + e.getMessage());
                failedCount++;
            }
        }
        if (failedCount > 0) {
            logger.warning("玩家 " + player.getName() + " 有 " + failedCount + " 条补发记录未成功发放，将在下次上线时重试");
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

    /** 安全删除补发记录：delete 失败时仅记录警告，不影响已完成的发放结果。 */
    private static void safeDelete(MarketRepository repository, long id, Logger logger) {
        try {
            repository.deletePendingDelivery(id);
        } catch (Exception e) {
            logger.warning("删除补发记录失败，可能导致下次重复补发 id=" + id + ": " + e.getMessage());
        }
    }
}
