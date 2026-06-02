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
            case "showcase" -> {
                WarehouseService.ActionResult showcaseResult = service.showcase(player);
                if (!showcaseResult.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + showcaseResult.message());
                }
            }
            case "preview" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.preview.usage", label));
                    return true;
                }
                UUID targetUuid;
                try {
                    targetUuid = UUID.fromString(args[1]);
                } catch (IllegalArgumentException exception) {
                    player.sendMessage(fullMsg("player.preview.invalid-uuid"));
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
            for (String cmd : List.of("open", "showcase", "preview")) {
                if (cmd.startsWith(prefix)) completions.add(cmd);
            }
            return completions;
        }
        return List.of();
    }
}
