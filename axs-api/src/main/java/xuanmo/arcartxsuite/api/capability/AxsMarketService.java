package xuanmo.arcartxsuite.api.capability;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
        /** 玩家店铺目录 */
        PLAYER_SHOP,
        /** 我的挂单 */
        MY_LISTINGS
    }

    /**
     * 为玩家打开指定市场 UI 视图。
     */
    void openUi(@NotNull Player player, @NotNull MarketView view);

    /** Opens a named system shop if it is available to this player. */
    default boolean openSystemShop(@NotNull Player player, @NotNull String id) { return false; }

    /** 打开指定拍品的独立竞价厅；主线程调用。旧实现可返回 false 表示不支持。 */
    default boolean openAuctionLot(@NotNull Player player,long listingId) { return false; }

    /** 按店主 UUID 打开独立玩家店铺；主线程调用。 */
    default boolean openPlayerStore(@NotNull Player player,@NotNull java.util.UUID owner) { return false; }

    /** Submit on the server thread. Resolves the visible description, or null when unavailable. */
    @NotNull CompletionStage<String> describeChatLink(@NotNull Player viewer, @NotNull String type, @NotNull String key);

    /** Submit on the server thread. Opens the view and resolves its current description, or null on rejection. */
    @NotNull CompletionStage<String> openChatLink(@NotNull Player viewer, @NotNull String type, @NotNull String key);

    /** 打开调用玩家自己的店铺资料编辑页；主线程调用。 */
    default boolean openOwnStoreEditor(@NotNull Player player) { return false; }

    /** 打开调用玩家自己的回收保护；主线程调用。 */
    default boolean openRecycleProtection(@NotNull Player player) { return false; }

    /** 打开交易管理；实现仍须验证管理员权限。主线程调用。 */
    default boolean openTradeManagement(@NotNull Player player) { return false; }


    /**
     * 替玩家创建一条拍卖挂单（从玩家手中取走物品）。
     * <p>
     * 在 Bukkit 主线程提交。原子服确认背包扣除已随自然存档保存并提交发布后，
     * Future 返回 {@code true}。请求被拒绝、取消或未发布时返回 {@code false}。
     * 停用时未确认最终结果的请求可能异常完成，已暂存的挂单仍由原子服恢复。
     * 不得在主线程等待，回调涉及 Bukkit 对象时先切回主线程。
     * <p>
     * {@code item} 的类型和元数据必须与玩家主手物品匹配，数量不得超过主手持量。
     *
     * @param player          卖家
     * @param item            上架物品
     * @param startingBid     起拍价
     * @param currency        货币 id，null 使用默认货币
     * @param durationSeconds 持续秒数
     */
    @NotNull CompletableFuture<Boolean> createAuctionListing(@NotNull Player player, @NotNull ItemStack item,
                              double startingBid, @Nullable String currency, long durationSeconds);

    /**
     * 取消玩家的一条拍卖挂单。
     * <p>
     * 在主线程提交。后台事务确认后完成，{@code true} 表示撤单及待交付记录已提交，
     * {@code false} 表示请求被拒绝，提交结果无法确认时异常完成。
     * 结果可能在后台线程完成，操作 Bukkit 对象前必须切回主线程。
     */
    @NotNull CompletableFuture<Boolean> cancelListing(@NotNull Player player, long listingId);

    /**
     * 打开拍卖 UI 并按关键字搜索。
     * <p>
     * 在调用线程同步执行（须主线程），返回的 Future 已完成：
     * {@code false} 表示市场服务不可用。
     */
    @NotNull CompletableFuture<Boolean> searchAuction(@NotNull Player player, @Nullable String keyword);

    /** 当前活跃拍卖挂单总数。 */
    int auctionCount();

    /** 系统商店条目数。 */
    int shopCount();

    /** 玩家当前自己的拍卖挂单数。 */
    int myListingCount(@NotNull Player player);

    /** 玩家商店功能是否启用。 */
    boolean playerShopEnabled();
}
