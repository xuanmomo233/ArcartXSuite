package xuanmo.arcartxsuite.questgps.command;

import java.util.List;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.questgps.service.QuestGpsService;

public final class QuestGpsPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("open", "cleartrack");

    private final Supplier<QuestGpsService> serviceProvider;
    private final MessageProvider messages;

    public QuestGpsPlayerCommand(Supplier<QuestGpsService> serviceProvider, MessageProvider messages) {
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
        if (!player.hasPermission("arcartxsuite.questgps.use")) {
            player.sendMessage(fullMsg("common.no-permission"));
            return true;
        }
        QuestGpsService svc = serviceProvider.get();
        if (svc == null) {
            player.sendMessage(fullMsg("common.service-down"));
            return true;
        }

        if (args.length == 0 || "open".equalsIgnoreCase(args[0])) {
            svc.openMenu(player);
            return true;
        }
        if ("cleartrack".equalsIgnoreCase(args[0])) {
            svc.clearTrack(player);
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
        return List.of();
    }
}
