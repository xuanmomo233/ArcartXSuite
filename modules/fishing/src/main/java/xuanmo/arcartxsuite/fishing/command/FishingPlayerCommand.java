package xuanmo.arcartxsuite.fishing.command;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.fishing.service.FishingService;

public final class FishingPlayerCommand implements TabExecutor {

    private final java.util.function.Supplier<PacketBridgeAPI> packetBridgeSupplier;
    private final java.util.function.Supplier<String> collectionUiIdSupplier;
    private final java.util.function.Supplier<FishingService> serviceSupplier;

    public FishingPlayerCommand(@NotNull java.util.function.Supplier<PacketBridgeAPI> packetBridgeSupplier,
                                @NotNull java.util.function.Supplier<String> collectionUiIdSupplier,
                                @NotNull java.util.function.Supplier<FishingService> serviceSupplier) {
        this.packetBridgeSupplier = packetBridgeSupplier;
        this.collectionUiIdSupplier = collectionUiIdSupplier;
        this.serviceSupplier = serviceSupplier;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家执行。");
            return true;
        }

        FishingService service = serviceSupplier.get();

        // sell 子命令
        if (args.length > 0 && "sell".equalsIgnoreCase(args[0])) {
            if (service == null) {
                sender.sendMessage("&c服务暂不可用。");
                return true;
            }
            String result;
            if (args.length > 1 && "all".equalsIgnoreCase(args[1])) {
                result = service.sellAllFish(player);
            } else {
                result = service.sellFishInHand(player);
            }
            sender.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', result));
            return true;
        }

        // 默认：打开图鉴 UI
        PacketBridgeAPI bridge = packetBridgeSupplier.get();
        if (bridge == null || !bridge.isAvailable()) {
            sender.sendMessage("&cUI 系统暂不可用。");
            return true;
        }

        String uiId = collectionUiIdSupplier.get();
        if (uiId == null || uiId.isBlank()) {
            sender.sendMessage("&c图鉴界面未配置。");
            return true;
        }

        if (service != null) {
            service.pushCollectionData(player, uiId);
        }
        bridge.openUi(player, uiId);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                   @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return java.util.stream.Stream.of("sell")
                .filter(a -> a.startsWith(args[0].toLowerCase()))
                .toList();
        }
        if (args.length == 2 && "sell".equalsIgnoreCase(args[0])) {
            return java.util.stream.Stream.of("all")
                .filter(a -> a.startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
