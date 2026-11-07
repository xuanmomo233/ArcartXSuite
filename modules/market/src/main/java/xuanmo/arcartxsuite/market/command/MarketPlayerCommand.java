package xuanmo.arcartxsuite.market.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.market.MarketService;

/**
 * 玩家命令 /market。
 */
public class MarketPlayerCommand implements TabExecutor {

    private final Supplier<MarketService> serviceSupplier;
    private final MessageProvider messages;

    public MarketPlayerCommand(Supplier<MarketService> serviceSupplier, MessageProvider messages) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return true;
        }

        MarketService service = serviceSupplier.get();
        if (service == null) {
            player.sendMessage(fullMsg("common.service-down"));
            return true;
        }

        if (args.length == 0) {
            // 打开拍卖行主界面
            service.openAuctionUi(player);
            return true;
        }

        if (args.length > 0 && "help".equalsIgnoreCase(args[0])) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(player);
            case "shop" -> {
                if (args.length > 1) {
                    service.openShopUi(player, args[1]);
                } else {
                    service.openShopListUi(player);
                }
            }
            case "auction", "ah" -> service.openAuctionUi(player);
            case "lot" -> {
                if(args.length<2){player.sendMessage("§e用法: /market lot <拍品ID>");return true;}
                try{service.openAuctionRoom(player,Long.parseLong(args[1]));}
                catch(NumberFormatException exception){player.sendMessage("§c拍品ID必须为整数");}
            }
            case "bids" -> service.openAuctionCategory(player,"participating");
            case "ending" -> service.openAuctionCategory(player,"ending");
            case "pshop", "playershop" -> {
                if(args.length>1 && "edit".equalsIgnoreCase(args[1]))service.openPlayerStoreEditor(player);
                else if(args.length>1 && "mine".equalsIgnoreCase(args[1]))service.openMyPlayerShopUi(player);
                else if(args.length>1 && "market".equalsIgnoreCase(args[1]))service.openPlayerShopMarketUi(player);
                else service.openPlayerShopUi(player);
            }
            case "store" -> {
                try{service.openPlayerStore(player,args.length>1?java.util.UUID.fromString(args[1]):player.getUniqueId());}
                catch(IllegalArgumentException exception){player.sendMessage("§e用法: /market store [店主UUID]；省略时打开自己的店铺");}
            }
            case "sellui" -> service.openSellUi(player);
            case "sell", "list" -> {
                // /market sell <起拍价> [时长秒] [货币]
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.sell.usage"));
                    return true;
                }
                handleSell(player, args);
            }
            case "recycle" -> {
                if(args.length>1 && Set.of("protect","protection","settings").contains(args[1].toLowerCase(java.util.Locale.ROOT))){
                    service.openRecycleProtection(player);
                } else if (args.length > 1 && "all".equalsIgnoreCase(args[1])) {
                    service.requestBatchRecycle(player);
                } else {
                    service.openRecycleUi(player);
                }
            }
            case "history" -> service.openHistoryUi(player);
            case "cancel" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.cancel.usage"));
                    return true;
                }
                try {
                    long listingId = Long.parseLong(args[1]);
                    if (args.length > 2 && "pshop".equalsIgnoreCase(args[2])) {
                        service.cancelPlayerShopListing(player, listingId);
                    } else {
                        service.cancelListing(player, listingId);
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(fullMsg("player.cancel.invalid-id"));
                }
            }
            case "search" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.search.usage"));
                    return true;
                }
                String keyword = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                service.searchAuction(player, keyword);
            }
            case "my" -> {
                if (args.length > 1 && "pshop".equalsIgnoreCase(args[1])) service.openMyPlayerShopUi(player);
                else service.openMyListingsUi(player);
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleSell(Player player, String[] args) {
        MarketService service = serviceSupplier.get();
        if (service == null) return;

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.getType().isAir()) {
            player.sendMessage(fullMsg("player.sell.air"));
            return;
        }

        try {
            double startingBid = Double.parseDouble(args[1]);
            long duration = args.length > 2 ? Long.parseLong(args[2]) : 0;
            String currency = args.length > 3 ? args[3] : null;
            service.createListing(player, handItem, startingBid, currency, duration);
        } catch (NumberFormatException e) {
            player.sendMessage(fullMsg("player.sell.invalid-price"));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(fullMsg("player.help.title"));
        player.sendMessage(fullMsg("player.help.auction"));
        player.sendMessage("§7/market lot <ID> §f- 打开单件拍品竞价厅");
        player.sendMessage("§7/market bids /market ending §f- 我的竞拍 / 即将结束");
        player.sendMessage(fullMsg("player.help.pshop"));
        player.sendMessage(fullMsg("player.help.shop"));
        player.sendMessage(fullMsg("player.help.sell"));
        player.sendMessage(fullMsg("player.help.recycle"));
        player.sendMessage(fullMsg("player.help.history"));
        player.sendMessage(fullMsg("player.help.my"));
        player.sendMessage(fullMsg("player.help.search"));
        player.sendMessage(fullMsg("player.help.cancel"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("help", "shop", "auction", "lot", "bids", "ending", "pshop", "store", "sell", "sellui", "recycle", "history", "my", "search", "cancel");
            return filterStartsWith(subs, args[0]);
        }
        if (args.length == 2) {
            if("pshop".equalsIgnoreCase(args[0]) || "playershop".equalsIgnoreCase(args[0]))return filterStartsWith(List.of("mine","edit","market"),args[1]);
            if ("my".equalsIgnoreCase(args[0])) return filterStartsWith(List.of("auction", "pshop"), args[1]);
            if ("recycle".equalsIgnoreCase(args[0])) {
                return filterStartsWith(List.of("all","protect"), args[1]);
            }
            if ("shop".equalsIgnoreCase(args[0])) {
                MarketService service = serviceSupplier.get();
                if (service != null) {
                    return filterStartsWith(new ArrayList<>(service.getShopIds()), args[1]);
                }
            }
            if ("cancel".equalsIgnoreCase(args[0]) && sender instanceof Player) {
                MarketService service = serviceSupplier.get();
                if (service != null) {
                    return filterStartsWith(service.getMyListingIds((Player) sender), args[1]);
                }
            }
        }
        if (args.length == 3 && "cancel".equalsIgnoreCase(args[0])) return filterStartsWith(List.of("auction", "pshop"), args[2]);
        return List.of();
    }

    private List<String> filterStartsWith(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}
