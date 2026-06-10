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

public final class BattlePassPlayerCommand implements TabExecutor {

    private final Supplier<BattlePassPacketHandler> packetHandlerSupplier;
    private final MessageProvider messages;

    public BattlePassPlayerCommand(Supplier<BattlePassPacketHandler> packetHandlerSupplier, MessageProvider messages) {
        this.packetHandlerSupplier = packetHandlerSupplier;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "该命令仅限玩家使用。");
            return true;
        }

        BattlePassPacketHandler handler = packetHandlerSupplier.get();
        if (handler == null) {
            player.sendMessage(fullMsg("player.module-down"));
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "open";
        switch (sub) {
            case "open", "" -> handler.openMain(player);
            case "tasks" -> handler.openTasks(player);
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
            for (String cmd : List.of("open", "tasks", "help")) {
                if (cmd.startsWith(prefix)) completions.add(cmd);
            }
            return completions;
        }
        return null;
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(ChatColor.GOLD + "==== BattlePass 命令 ====");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " " + ChatColor.WHITE + "— 打开战令主界面");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " tasks" + ChatColor.WHITE + "— 打开任务列表");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " help" + ChatColor.WHITE + "— 显示帮助");
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return key;
        String prefix = messages.get("prefix");
        return prefix + messages.get(key, args);
    }
}
