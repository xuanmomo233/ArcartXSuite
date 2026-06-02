package xuanmo.arcartxsuite.warehouse.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.warehouse.service.WarehouseService;

public final class WarehouseAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "open", "info", "password", "bank");

    private final Supplier<WarehouseService> serviceProvider;
    private final MessageProvider messages;

    public WarehouseAdminCommand(Supplier<WarehouseService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "warehouse"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(fullMsg("common.reload-hint", label));
            case "open" -> handleOpen(sender, args);
            case "info" -> handleInfo(sender, args);
            case "password" -> handlePassword(sender, args);
            case "bank" -> handleBank(sender, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        WarehouseService svc = serviceProvider.get();
        if (args.length == 4) {
            if ("password".equalsIgnoreCase(args[1])) return filter(List.of("clear"), args[3]);
            if ("bank".equalsIgnoreCase(args[1]) && svc != null) return filter(new ArrayList<>(svc.currencyIds()), args[3]);
        }
        if (args.length == 5 && "bank".equalsIgnoreCase(args[1])) {
            return filter(List.of("set", "add", "take"), args[4]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " warehouse";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.open", cmd));
        sender.sendMessage(fullMsg("help.info", cmd));
        sender.sendMessage(fullMsg("help.password", cmd));
        sender.sendMessage(fullMsg("help.bank", cmd));
    }

    private void sendStatus(CommandSender sender) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.cached", svc.cachedPlayerCount()));
        sender.sendMessage(fullMsg("status.dirty", svc.dirtyPlayerCount()));
        sender.sendMessage(fullMsg("status.currency", String.join(", ", svc.currencyIds())));
    }

    private void handleOpen(CommandSender sender, String[] args) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.open.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("admin.open.offline", args[2])); return; }
        WarehouseService.ActionResult result = svc.openMenu(target);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleInfo(CommandSender sender, String[] args) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.info.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        UUID uuid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        String name = target != null ? target.getName() : args[2];
        String prefix = messages != null ? messages.get("prefix") : "";
        svc.describePlayer(uuid, name,
            lines -> lines.forEach(line -> sender.sendMessage(prefix + line)),
            error -> sender.sendMessage(prefix + ChatColor.RED + error)
        );
    }

    private void handlePassword(CommandSender sender, String[] args) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 4 || !"clear".equalsIgnoreCase(args[3])) {
            sender.sendMessage(fullMsg("admin.password.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        UUID uuid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        sender.sendMessage(fullMsg("admin.password.clear-processing", args[2]));
        svc.adminClearSecondaryPassword(uuid, result ->
            sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message())
        );
    }

    private void handleBank(CommandSender sender, String[] args) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 6) {
            sender.sendMessage(fullMsg("admin.bank.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        UUID uuid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        svc.adminAdjustWallet(uuid, args[3], args[4], args[5], result ->
            sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message())
        );
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
