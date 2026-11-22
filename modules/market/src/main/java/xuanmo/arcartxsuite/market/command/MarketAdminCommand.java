package xuanmo.arcartxsuite.market.command;

import java.util.List;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.market.MarketService;

/**
 * 管理员命令（通过 ModuleCommandHandler 集成到 /axs market）。
 */
public class MarketAdminCommand {

    private final Supplier<MarketService> serviceSupplier;
    private final MessageProvider messages;

    public MarketAdminCommand(Supplier<MarketService> serviceSupplier, MessageProvider messages) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    public List<String> actions() {
        return List.of("help", "status", "clear-expired", "remove", "edit", "open", "ledger", "resolve", "shop-review", "pshop-review", "trades");
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("arcartxsuite.market.admin")) {
            sender.sendMessage(fullMsg("common.no-permission"));
            return true;
        }

        MarketService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return true;
        }

        String action = args.length >= 2 ? args[1].toLowerCase() : "help";
        switch (action) {
            case "trades" -> {
                if(sender instanceof org.bukkit.entity.Player player)service.openTradeManagement(player);
                else sender.sendMessage(fullMsg("common.only-player"));
            }
            case "ledger" -> {
                try {
                    int page=args.length>2?Math.max(0,Integer.parseInt(args[2])):0;
                    if(page>100000) {sender.sendMessage("§c页码过大");return true;}
                    String state=args.length>3 && !"all".equalsIgnoreCase(args[3])?args[3].toUpperCase(java.util.Locale.ROOT):null;
                    java.util.UUID target=args.length>4?java.util.UUID.fromString(args[4]):null;
                    var rows=service.getRepository().operations(state,target,page*10,10);
                    sender.sendMessage("§6交易流水 第 "+(page+1)+" 页（支持状态/玩家 UUID 筛选）");
                    for(var row:rows) {
                        sender.sendMessage("§e"+row.id()+" §f"+row.kind()+" §7"+row.state()+" §f"+row.player());
                        sender.sendMessage("§7父交易="+row.tradeId()+"；引用="+row.reference()+"；金额="+row.amount()+" "+row.currency()+"；"+row.note());
                        if ("CURRENCY".equals(row.kind()) && "REVIEW".equals(row.state())) {
                            sender.sendMessage("§7货币核对使用本次流水编号: "+row.id()+"@"+row.updatedAt());
                        }
                    }
                    if(rows.isEmpty())sender.sendMessage("§7没有更多记录");
                } catch(IllegalArgumentException exception) {sender.sendMessage("§c用法: /axs market ledger [页码，从0开始] [all|READY|RUNNING|REVIEW|DONE|FAILED] [玩家UUID]");}
            }
            case "resolve" -> {
                if(args.length<5) {sender.sendMessage("§e用法: /axs market resolve <流水ID> <delivered|cancel|retry-confirmed> <核对说明>");return true;}
                String note=String.join(" ",java.util.Arrays.copyOfRange(args,4,args.length)).strip();
                if(note.isEmpty() || note.length()>512) {sender.sendMessage("§c核对说明不能为空且不能超过512字符");return true;}
                java.util.UUID actor=sender instanceof org.bukkit.entity.Player player?player.getUniqueId():new java.util.UUID(0,0);
                boolean changed=service.getLedger().trade("ADMIN_RESOLVE",actor,args[2],
                    () -> service.getRepository().resolveOperation(args[2],args[3],sender.getName()+": "+note), result -> result);
                sender.sendMessage(changed?"§a已记录核对结果；retry-confirmed 仅表示已确认未交付后重新入队"
                    :"§c操作未应用：请用 ledger 核对当前 REVIEW 记录，货币核对须使用最新的流水ID@核对版本");
            }
            case "shop-review", "pshop-review" -> {
                boolean playerShop = "pshop-review".equals(action);
                if (args.length < 5) {
                    String modes = playerShop ? "fulfill-paid|refund-paid|cancel-unpaid"
                        : "fulfill-paid|refund-paid|cancel-unpaid|confirm-refunded|retry-refund-uncredited";
                    sender.sendMessage("§e用法: /axs market " + action + " <订单ID> <" + modes + "> <核对依据>");
                    return true;
                }
                String note = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)).strip();
                if (note.isEmpty() || note.length() > 512) {
                    sender.sendMessage("§c核对依据不能为空，且不能超过512字符");
                    return true;
                }
                service.reviewShopOrder(args[2], args[3], sender.getName() + ": " + note, playerShop,
                    result -> sender.sendMessage("§7" + result));
            }
            case "open" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage(fullMsg("common.only-player"));
                    return true;
                }
                String view = args.length > 2 ? args[2].toLowerCase(java.util.Locale.ROOT) : "auction";
                switch (view) {
                    case "auction" -> service.openAuctionUi(player);
                    case "pshop" -> service.openPlayerShopUi(player);
                    case "shop" -> service.openShopUi(player, args.length > 3 ? args[3] : null);
                    case "recycle" -> service.openRecycleUi(player);
                    case "history" -> service.openHistoryUi(player);
                    case "sell" -> service.openSellUi(player);
                    default -> sender.sendMessage(fullMsg("help.open", "/axs market"));
                }
            }
            case "status" -> {
                sender.sendMessage(fullMsg("status.title"));
                sender.sendMessage(fullMsg("status.active-listings", service.getAuctionCount()));
                sender.sendMessage(fullMsg("status.player-shop-listings", service.getPlayerShopCount()));
                sender.sendMessage(fullMsg("status.subsystems", service.subsystemStatus()));
                sender.sendMessage(fullMsg("status.shops", service.getShopCount()));
                sender.sendMessage(fullMsg("status.recycle-entries", service.getRecycleEntryCount()));
                sender.sendMessage(fullMsg("status.list-cache",
                    service.isListCacheConnected()
                        ? fullMsg("status.list-cache-connected")
                        : fullMsg("status.list-cache-disconnected")));
                sender.sendMessage(fullMsg("status.cross-server",
                    service.crossServerActive()
                        ? fullMsg("status.cross-server-active")
                        : fullMsg("status.cross-server-inactive")));
            }
            case "clear-expired" -> {
                service.clearExpired(cleared -> sender.sendMessage(cleared >= 0
                    ? fullMsg("admin.clear-expired-success", cleared) : "§c到期挂单处理失败，请查看日志"));
            }
            case "edit" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage(fullMsg("common.only-player"));
                    return true;
                }
                String shopId = args.length >= 3 ? args[2] : null;
                service.openShopEditUi(player, shopId);
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(fullMsg("admin.remove-usage"));
                    return true;
                }
                try {
                    boolean playerShop = "pshop".equalsIgnoreCase(args[2]);
                    boolean typed = playerShop || "auction".equalsIgnoreCase(args[2]);
                    if (typed && args.length < 4) {
                        sender.sendMessage(fullMsg("admin.remove-usage"));
                        return true;
                    }
                    long id = Long.parseLong(args[typed ? 3 : 2]);
                    service.adminRemoveListing(sender, id, playerShop, (removed, failure) -> {
                        if (failure != null) sender.sendMessage(ChatColor.RED + "下架结果未确认，请核对商品状态和交易流水。");
                        else sender.sendMessage(removed ? fullMsg("admin.remove-success") : fullMsg("admin.remove-not-found"));
                    });
                } catch (NumberFormatException e) {
                    sender.sendMessage(fullMsg("admin.invalid-id"));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            return actions().stream().filter(s -> s.startsWith(input)).toList();
        }
        if (args.length == 3 && "edit".equalsIgnoreCase(args[1])) {
            MarketService service = serviceSupplier.get();
            if (service == null) return List.of();
            String input = args[2].toLowerCase();
            return service.getShopIds().stream()
                .filter(s -> s.toLowerCase().startsWith(input)).sorted().toList();
        }
        if (args.length == 3 && "remove".equalsIgnoreCase(args[1])) {
            return List.of("auction", "pshop").stream().filter(s -> s.startsWith(args[2].toLowerCase())).toList();
        }
        if (args.length == 3 && "open".equalsIgnoreCase(args[1])) {
            return List.of("auction", "pshop", "shop", "recycle", "history", "sell").stream()
                .filter(s -> s.startsWith(args[2].toLowerCase())).toList();
        }
        if(args.length==4 && "resolve".equalsIgnoreCase(args[1])) return List.of("delivered","cancel","retry-confirmed");
        if (args.length==4 && "shop-review".equalsIgnoreCase(args[1])) {
            return List.of("fulfill-paid", "refund-paid", "cancel-unpaid", "confirm-refunded", "retry-refund-uncredited");
        }
        if (args.length == 4 && "pshop-review".equalsIgnoreCase(args[1])) {
            return List.of("fulfill-paid", "refund-paid", "cancel-unpaid");
        }
        if(args.length==4 && "ledger".equalsIgnoreCase(args[1])) return List.of("all","READY","RUNNING","REVIEW","DONE","FAILED");
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        String cmd = "/axs market";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.clear-expired", cmd));
        sender.sendMessage(fullMsg("help.remove", cmd));
        sender.sendMessage(fullMsg("help.edit", cmd));
        sender.sendMessage(fullMsg("help.open", cmd));
        sender.sendMessage("§7"+cmd+" trades §f- 管理员交易管理面板（筛选、详情、关联流水）");
        sender.sendMessage("§7"+cmd+" exchange open <兑换所ID> §f- 在绑定的 Adyeshach NPC 附近打开兑换");
        if (sender.hasPermission("arcartxsuite.market.exchange.admin")) {
            sender.sendMessage("§7"+cmd+" exchange edit [兑换所ID] §f- 编辑兑换所");
            sender.sendMessage("§7"+cmd+" exchange ledger [玩家UUID] §f- 查看待核对兑换");
        }
        sender.sendMessage("§7"+cmd+" ledger [页码] [状态] [玩家UUID] §f- 交易/交付流水");
        sender.sendMessage("§7"+cmd+" resolve <ID 或 ID@核对版本> <delivered|cancel|retry-confirmed> <说明> §f- 货币流水使用 ledger 显示的完整编号");
        sender.sendMessage("§7"+cmd+" shop-review <订单ID> <处理方式> <核对依据> §f- 核对系统商店扣款或退款");
        sender.sendMessage("§7" + cmd + " pshop-review <订单ID> <处理方式> <核对依据> §f- 核对玩家商店扣款");
    }
}
