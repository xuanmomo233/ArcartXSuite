package xuanmo.arcartxsuite.map.command;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.service.MapService;

public final class MapPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("open", "hud", "cleartrack");

    private final Supplier<MapService> serviceProvider;
    private final Supplier<MapModuleConfiguration> configurationProvider;
    private final MessageProvider messages;

    public MapPlayerCommand(
        Supplier<MapService> serviceProvider,
        Supplier<MapModuleConfiguration> configurationProvider,
        MessageProvider messages
    ) {
        this.serviceProvider = serviceProvider;
        this.configurationProvider = configurationProvider;
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
        if (!player.hasPermission("arcartxsuite.map.use")) {
            player.sendMessage(fullMsg("common.no-permission"));
            return true;
        }
        MapService svc = serviceProvider.get();
        if (svc == null) {
            player.sendMessage(fullMsg("common.module-down"));
            return true;
        }

        if (args.length == 0 || "open".equalsIgnoreCase(args[0])) {
            String worldId = args.length >= 2 ? args[1] : "";
            sendResult(player, svc.openMenuFor(player, worldId));
            return true;
        }

        if ("hud".equalsIgnoreCase(args[0])) {
            String mode = args.length >= 2 ? args[1] : "toggle";
            sendResult(player, svc.setHud(player, mode));
            return true;
        }

        if ("cleartrack".equalsIgnoreCase(args[0])) {
            sendResult(player, svc.clearTrackCommand(player));
            return true;
        }

        player.sendMessage(fullMsg("player.usage", label));
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
            return ROOT_ACTIONS.stream().filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && "hud".equalsIgnoreCase(args[0])) {
            return List.of("on", "off", "toggle").stream().filter(value -> value.startsWith(args[1].toLowerCase())).toList();
        }
        MapModuleConfiguration cfg = configurationProvider.get();
        if (args.length == 2 && "open".equalsIgnoreCase(args[0]) && cfg != null) {
            return new ArrayList<>(cfg.worlds().keySet()).stream()
                .filter(value -> value.startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }

    private void sendResult(Player player, MapOperationResult result) {
        if (result == null || result.message().isBlank()) {
            return;
        }
        player.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }
}
