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
        return List.of("help", "status", "reload", "clear-expired", "remove");
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

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "status" -> {
                sender.sendMessage(fullMsg("status.title"));
                sender.sendMessage(fullMsg("status.active-listings", service.getAuctionCount()));
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
            case "reload" -> {
                service.reload();
                sender.sendMessage(fullMsg("admin.reloaded"));
            }
            case "clear-expired" -> {
                int cleared = service.clearExpired();
                sender.sendMessage(fullMsg("admin.clear-expired-success", cleared));
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(fullMsg("admin.remove-usage"));
                    return true;
                }
                try {
                    long id = Long.parseLong(args[1]);
                    boolean removed = service.adminRemoveListing(id);
                    sender.sendMessage(removed ? fullMsg("admin.remove-success") : fullMsg("admin.remove-not-found"));
                } catch (NumberFormatException e) {
                    sender.sendMessage(fullMsg("admin.invalid-id"));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length <= 1) {
            String input = args.length == 0 ? "" : args[0].toLowerCase();
            return actions().stream().filter(s -> s.startsWith(input)).toList();
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        String cmd = "/axs market";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.reload", cmd));
        sender.sendMessage(fullMsg("help.clear-expired", cmd));
        sender.sendMessage(fullMsg("help.remove", cmd));
    }
}
