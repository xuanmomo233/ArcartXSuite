package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 全球市场公开服务接口（公开 capability）。
 * <p>
 * 由 Market 模块实现，覆盖拍卖行 / 系统商店 / 回收 / 玩家商店的
 * UI 打开与常用操作入口。所有方法请在 Bukkit 主线程调用。
 * <p>
 * 外部插件经 {@code AxsCapabilities.get(AxsMarketService.class)} 获取；
 * 市场事件可订阅 EventBus 主题 {@code axs.market.*}
 * （listing_created / auction_purchased / shop_dynamic_triggered）。
 */
@PublicCapability
public interface AxsMarketService {

    /** 市场 UI 视图。 */
    enum MarketView {
        /** 拍卖行列表 */
        AUCTION,
        /** 拍卖行上架（出售）页 */
        AUCTION_SELL,
        /** 系统商店列表 */
        SHOP_LIST,
        /** 系统商店 */
        SHOP,
        /** 回收页 */
        RECYCLE,
        /** 交易记录 */
        HISTORY,
        /** 出售（快速上架入口） */
        SELL,
        /** 玩家商店 */
        PLAYER_SHOP,
        /** 我的挂单 */
        MY_LISTINGS
    }

    /**
     * 为玩家打开指定市场 UI 视图。
     */
    void openUi(@NotNull Player player, @NotNull MarketView view);

    /**
     * 替玩家创建一条拍卖挂单（从玩家手中取走物品）。
     *
     * @param player          卖家
     * @param item            上架物品
     * @param startingBid     起拍价
     * @param currency        货币 id，null 使用默认货币
     * @param durationSeconds 持续秒数
     */
    void createAuctionListing(@NotNull Player player, @NotNull ItemStack item,
                              double startingBid, @Nullable String currency, long durationSeconds);

    /**
     * 取消玩家的一条拍卖挂单。
     */
    void cancelListing(@NotNull Player player, long listingId);

    /**
     * 打开拍卖 UI 并按关键字搜索。
     */
    void searchAuction(@NotNull Player player, @Nullable String keyword);

    /** 当前活跃拍卖挂单总数。 */
    int auctionCount();

    /** 系统商店条目数。 */
    int shopCount();

    /** 玩家当前自己的拍卖挂单数。 */
    int myListingCount(@NotNull Player player);

    /** 玩家商店功能是否启用。 */
    boolean playerShopEnabled();
}
