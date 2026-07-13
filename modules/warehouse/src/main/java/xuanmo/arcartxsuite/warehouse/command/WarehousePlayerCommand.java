package xuanmo.arcartxsuite.warehouse.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.warehouse.service.WarehouseService;

public final class WarehousePlayerCommand implements org.bukkit.command.TabExecutor {

    private final Supplier<WarehouseService> serviceProvider;
    private final MessageProvider messages;

    public WarehousePlayerCommand(Supplier<WarehouseService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
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
        if (!player.hasPermission("arcartxsuite.warehouse.use")) {
            player.sendMessage(fullMsg("common.no-permission"));
            return true;
        }
        WarehouseService service = serviceProvider.get();
        if (service == null) {
            player.sendMessage(fullMsg("player.module-down"));
            return true;
        }
        String subCommand = args.length > 0 ? args[0].toLowerCase() : "open";
        switch (subCommand) {
            case "transfer" -> {
                if (args.length < 3 || !("confirm".equalsIgnoreCase(args[1]) || "reject".equalsIgnoreCase(args[1]))) {
                    player.sendMessage(fullMsg("player.transfer.usage", label));
                    break;
                }
                WarehouseService.ActionResult result = "confirm".equalsIgnoreCase(args[1])
                    ? service.confirmPendingTransfer(player, args[2])
                    : service.rejectPendingTransfer(player, args[2]);
                player.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
            }
            case "showcase" -> {
                WarehouseService.ActionResult showcaseResult = service.showcase(player);
                if (!showcaseResult.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + showcaseResult.message());
                }
            }
            case "preview" -> {
                if (!player.hasPermission("arcartxsuite.warehouse.admin")) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + "你没有权限使用此命令。");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.preview.usage", label));
                    return true;
                }
                UUID targetUuid = null;
                try {
                    targetUuid = UUID.fromString(args[1]);
                } catch (IllegalArgumentException ignored) {
                }
                if (targetUuid == null) {
                    Player online = Bukkit.getPlayer(args[1]);
                    if (online != null) {
                        targetUuid = online.getUniqueId();
                    } else {
                        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
                        if (offline.hasPlayedBefore()) {
                            targetUuid = offline.getUniqueId();
                        }
                    }
                }
                if (targetUuid == null) {
                    player.sendMessage(fullMsg("player.preview.not-found", args[1]));
                    return true;
                }
                String warehouseId = args.length > 2 ? args[2] : "";
                WarehouseService.ActionResult previewResult = service.openPreview(player, targetUuid, warehouseId);
                if (!previewResult.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + previewResult.message());
                }
            }
            case "spreview" -> {
                if (args.length < 2) {
                    return true;
                }
                UUID targetUuid = null;
                try {
                    targetUuid = UUID.fromString(args[1]);
                } catch (IllegalArgumentException ignored) {
                }
                if (targetUuid == null) {
                    Player online = Bukkit.getPlayer(args[1]);
                    if (online != null) {
                        targetUuid = online.getUniqueId();
                    } else {
                        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
                        if (offline.hasPlayedBefore()) {
                            targetUuid = offline.getUniqueId();
                        }
                    }
                }
                if (targetUuid == null) {
                    return true;
                }
                String warehouseId = args.length > 2 ? args[2] : "";
                WarehouseService.ActionResult previewResult = service.openPreview(player, targetUuid, warehouseId);
                if (!previewResult.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + previewResult.message());
                }
            }
            case "open" -> {
                WarehouseService.ActionResult result = service.openMenu(player);
                if (!result.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + result.message());
                }
            }
            default -> {
                WarehouseService.ActionResult result = service.openMenu(player);
                if (!result.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + result.message());
                }
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String cmd : List.of("open", "showcase", "preview", "transfer")) {
                if (cmd.startsWith(prefix)) completions.add(cmd);
            }
            return completions;
        }
        if (args.length == 2 && "preview".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (name.toLowerCase().startsWith(prefix)) {
                    completions.add(name);
                }
            }
            return completions;
        }
        return List.of();
    }
}
