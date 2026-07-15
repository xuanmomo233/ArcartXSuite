package xuanmo.arcartxsuite.market.placeholder;

import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.market.MarketService;

/**
 * PlaceholderAPI Ã¦ÂÂ©Ã¥Â±ÂÃ¯Â¼Â%axsmarket_xxx%Ã£ÂÂ
 */
public class MarketPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<MarketService> serviceSupplier;

    public MarketPlaceholderExpansion(JavaPlugin plugin, Supplier<MarketService> serviceSupplier) {
        this.plugin = plugin;
        this.serviceSupplier = serviceSupplier;
    }

    @Override public @NotNull String getIdentifier() { return "axsmarket"; }
    @Override public @NotNull String getAuthor() { return "ArcartXSuite"; }
    @Override public @NotNull String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        MarketService service = serviceSupplier.get();
        if (service == null) return "N/A";

        return switch (params.toLowerCase()) {
            case "auction_count" -> String.valueOf(service.getAuctionCount());
            case "shop_count" -> String.valueOf(service.getShopCount());
            case "recycle_count" -> String.valueOf(service.getRecycleEntryCount());
            case "redis_status", "list_cache_status" -> service.isListCacheConnected() ? "Ã¥Â·Â²Ã¨Â¿ÂÃ¦ÂÂ¥" : "Ã¦ÂÂªÃ¨Â¿ÂÃ¦ÂÂ¥";
            case "cross_server_status" -> service.crossServerActive() ? "Ã¥Â·Â²Ã¥ÂÂ¯Ã§ÂÂ¨" : "Ã¦ÂÂªÃ¥ÂÂ¯Ã§ÂÂ¨";
            case "my_listings" -> player != null ? String.valueOf(getMyListingsCount(service, player)) : "0";
            default -> null;
        };
    }

    private int getMyListingsCount(MarketService service, Player player) {
        return service.getMyListingsCount(player);
    }
}