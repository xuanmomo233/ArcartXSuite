package xuanmo.arcartxsuite.battlepass.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.battlepass.packet.BattlePassPacketHandler;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

public final class BattlePassPlayerCommand implements TabExecutor {

    private final Supplier<BattlePassPacketHandler> packetHandlerSupplier;
    private final Supplier<BattlePassService> serviceSupplier;
    private final MessageProvider messages;

    public BattlePassPlayerCommand(Supplier<BattlePassPacketHandler> packetHandlerSupplier,
                                    Supplier<BattlePassService> serviceSupplier, MessageProvider messages) {
        this.packetHandlerSupplier = packetHandlerSupplier;
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages != null ? messages.get("player.only-player") : ChatColor.RED + "该命令仅限玩家使用。");
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
            case "help" -> sendHelp(player, label);
            default -> {
                player.sendMessage(fullMsg("player.unknown-subcommand", label));
                handler.openMain(player);
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                   @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String cmd : List.of("open", "tasks", "rewards", "claim", "help")) {
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
        String key = switch (service.claimRewards(player, level)) {
            case SUCCESS -> "claim.success";
            case LEVEL_NOT_REACHED -> "claim.level-not-reached";
            case ALREADY_CLAIMED -> "claim.already-claimed";
            case NOT_FOUND -> "claim.none";
        };
        player.sendMessage(fullMsg(key, level));
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(plainMsg("player.help-title"));
        player.sendMessage(plainMsg("player.help-open", label));
        player.sendMessage(plainMsg("player.help-tasks", label));
        player.sendMessage(plainMsg("player.help-rewards", label));
        player.sendMessage(plainMsg("player.help-claim", label));
        player.sendMessage(plainMsg("player.help-help", label));
    }

    private String plainMsg(String key, Object... args) {
        return messages == null ? key : messages.get(key, args);
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return key;
        String prefix = messages.get("prefix");
        return prefix + messages.get(key, args);
    }
}
