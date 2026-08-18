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

/**
 * 钓鱼模块的玩家命令处理器。
 * <p>
 * 关联模块：{@code fishing}。默认打开鱼类图鉴 UI；
 * 支持 {@code sell} 子命令出售手中或全部鱼类物品；
 * 支持 {@code talents} / {@code rewards} / {@code tasks} 子命令打开对应 UI。
 */
public final class FishingPlayerCommand implements TabExecutor {

    private final java.util.function.Supplier<PacketBridgeAPI> packetBridgeSupplier;
    private final java.util.function.Supplier<String> collectionUiIdSupplier;
    private final java.util.function.Supplier<String> talentsUiIdSupplier;
    private final java.util.function.Supplier<String> rewardsUiIdSupplier;
    private final java.util.function.Supplier<String> tasksUiIdSupplier;
    private final java.util.function.Supplier<FishingService> serviceSupplier;
    private final java.util.function.BiFunction<String, Object[], String> msg;

    public FishingPlayerCommand(@NotNull java.util.function.Supplier<PacketBridgeAPI> packetBridgeSupplier,
                                @NotNull java.util.function.Supplier<String> collectionUiIdSupplier,
                                @NotNull java.util.function.Supplier<FishingService> serviceSupplier,
                                @NotNull java.util.function.BiFunction<String, Object[], String> msg) {
        this(packetBridgeSupplier, collectionUiIdSupplier, null, null, null, serviceSupplier, msg);
    }

    public FishingPlayerCommand(@NotNull java.util.function.Supplier<PacketBridgeAPI> packetBridgeSupplier,
                                @NotNull java.util.function.Supplier<String> collectionUiIdSupplier,
                                @Nullable java.util.function.Supplier<String> talentsUiIdSupplier,
                                @Nullable java.util.function.Supplier<String> rewardsUiIdSupplier,
                                @Nullable java.util.function.Supplier<String> tasksUiIdSupplier,
                                @NotNull java.util.function.Supplier<FishingService> serviceSupplier,
                                @NotNull java.util.function.BiFunction<String, Object[], String> msg) {
        this.packetBridgeSupplier = packetBridgeSupplier;
        this.collectionUiIdSupplier = collectionUiIdSupplier;
        this.talentsUiIdSupplier = talentsUiIdSupplier;
        this.rewardsUiIdSupplier = rewardsUiIdSupplier;
        this.tasksUiIdSupplier = tasksUiIdSupplier;
        this.serviceSupplier = serviceSupplier;
        this.msg = msg;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.apply("command_player_only", new Object[]{}));
            return true;
        }

        FishingService service = serviceSupplier.get();

        // help 子命令
        if (args.length > 0 && "help".equalsIgnoreCase(args[0])) {
            showHelp(sender);
            return true;
        }

        // sell 子命令
        if (args.length > 0 && "sell".equalsIgnoreCase(args[0])) {
            if (service == null) {
                sender.sendMessage(msg.apply("command_service_unavailable", new Object[]{}));
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

        // talents 子命令 — 打开天赋树 UI
        if (args.length > 0 && "talents".equalsIgnoreCase(args[0])) {
            return openUi(sender, player, service, talentsUiIdSupplier, "talents");
        }

        // rewards 子命令 — 打开赛季奖励 UI
        if (args.length > 0 && "rewards".equalsIgnoreCase(args[0])) {
            return openUi(sender, player, service, rewardsUiIdSupplier, "rewards");
        }

        // tasks 子命令 — 打开任务列表 UI
        if (args.length > 0 && "tasks".equalsIgnoreCase(args[0])) {
            return openUi(sender, player, service, tasksUiIdSupplier, "tasks");
        }

        // 默认：打开图鉴 UI
        return openUi(sender, player, service, collectionUiIdSupplier, "collection");
    }

    private boolean openUi(@NotNull CommandSender sender, @NotNull Player player,
                           @Nullable FishingService service,
                           @Nullable java.util.function.Supplier<String> uiIdSupplier,
                           @NotNull String uiName) {
        PacketBridgeAPI bridge = packetBridgeSupplier.get();
        if (bridge == null || !bridge.isAvailable()) {
            sender.sendMessage(msg.apply("command_ui_unavailable", new Object[]{}));
            return true;
        }
        String uiId = uiIdSupplier != null ? uiIdSupplier.get() : null;
        if (uiId == null || uiId.isBlank()) {
            sender.sendMessage(msg.apply("command_ui_not_configured", new Object[]{}));
            return true;
        }
        bridge.openUi(player, uiId);
        // UI 数据推送由 packetHandler.init 处理
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                   @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return java.util.stream.Stream.of("help", "sell", "talents", "rewards", "tasks")
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

    private void showHelp(@NotNull CommandSender sender) {
        sender.sendMessage(msg.apply("player.help.title", new Object[]{}));
        sender.sendMessage(msg.apply("player.help.open", new Object[]{}));
        sender.sendMessage(msg.apply("player.help.sell", new Object[]{}));
        sender.sendMessage(msg.apply("player.help.sell-all", new Object[]{}));
        sender.sendMessage(msg.apply("player.help.talents", new Object[]{}));
        sender.sendMessage(msg.apply("player.help.rewards", new Object[]{}));
        sender.sendMessage(msg.apply("player.help.tasks", new Object[]{}));
    }
}
