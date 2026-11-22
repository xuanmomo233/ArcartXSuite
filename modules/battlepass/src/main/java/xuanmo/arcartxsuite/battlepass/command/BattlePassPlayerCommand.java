package xuanmo.arcartxsuite.battlepass.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.battlepass.packet.BattlePassPacketHandler;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

/**
 * 战令玩家命令处理器，实现 {@link TabExecutor}。
 * <p>
 * 处理 {@code /bp} 和 {@code /battlepass} 玩家子命令，包括 open、tasks、rewards、claim、help，
 * 通过 {@link BattlePassPacketHandler} 打开 UI 并通过 {@link BattlePassService} 处理奖励领取。
 */
public final class BattlePassPlayerCommand implements TabExecutor {

    private final Plugin plugin;
    private final Supplier<BattlePassPacketHandler> packetHandlerSupplier;
    private final Supplier<BattlePassService> serviceSupplier;
    private final MessageProvider messages;

    /**
     * 构造战令玩家命令处理器。
     *
     * @param packetHandlerSupplier 包处理器提供者（延迟获取，可能为 null）
     * @param serviceSupplier       BattlePassService 提供者（延迟获取，可能为 null）
     * @param messages              消息提供者
     */
    public BattlePassPlayerCommand(Plugin plugin, Supplier<BattlePassPacketHandler> packetHandlerSupplier,
                                    Supplier<BattlePassService> serviceSupplier, MessageProvider messages) {
        this.plugin = plugin;
        this.packetHandlerSupplier = packetHandlerSupplier;
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 处理战令玩家子命令（open/tasks/rewards/claim/help），仅限玩家执行。
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages != null ? messages.get("player.only-player") : ChatColor.RED + "该命令仅限玩家使用。");
            return true;
        }

        if (args.length > 0 && "help".equalsIgnoreCase(args[0])) {
            showHelp(player);
            return true;
        }

        BattlePassPacketHandler handler = packetHandlerSupplier.get();
        if (handler == null) {
            player.sendMessage(fullMsg("player.module-down"));
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "open";
        switch (sub) {
            case "open", "" -> handler.openMain(player);
            case "tasks" -> handler.openTasks(player);
            case "rewards" -> handler.openRewards(player);
            case "claim" -> handleClaim(player, args, label);
            case "go" -> {
                if (args.length == 2) handler.navigateTask(player, args[1], false);
                else player.sendMessage(fullMsg("player.help.go"));
            }
            default -> {
                player.sendMessage(fullMsg("player.unknown-subcommand", label));
                handler.openMain(player);
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 补全第一参数的子命令名（open/tasks/rewards/claim/help）。
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                   @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String cmd : List.of("open", "tasks", "rewards", "claim", "go", "help")) {
                if (cmd.startsWith(prefix)) completions.add(cmd);
            }
            return completions;
        }
        return null;
    }

    private void handleClaim(Player player, String[] args, String label) {
        BattlePassService service = serviceSupplier.get();
        if (service == null) {
            player.sendMessage(fullMsg("player.module-down"));
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args.length > 1 ? args[1] : "");
        } catch (NumberFormatException e) {
            player.sendMessage(fullMsg("claim.usage", label));
            return;
        }
        var session = AuthAccess.snapshot(player.getUniqueId());
        service.claimLevelAsync(player, level, () -> true).whenComplete((result, error) -> service.onMain(() -> {
            var current = AuthAccess.snapshot(player.getUniqueId());
            if (!player.isOnline() || !player.hasPermission("arcartxsuite.battlepass.use")
                || !current.admitted() || current.generation() != session.generation()
                || !Objects.equals(current.connectionId(), session.connectionId())) return;
            player.sendMessage(fullMsg(error == null ? result.messageKey() : "claim.retry", level));
        }));
    }

    private void showHelp(Player player) {
        player.sendMessage(fullMsg("player.help.title"));
        player.sendMessage(fullMsg("player.help.open"));
        player.sendMessage(fullMsg("player.help.tasks"));
        player.sendMessage(fullMsg("player.help.rewards"));
        player.sendMessage(fullMsg("player.help.claim"));
        player.sendMessage(fullMsg("player.help.go"));
        player.sendMessage(fullMsg("player.help.help"));
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return key;
        String prefix = messages.get("prefix");
        return prefix + messages.get(key, args);
    }
}
