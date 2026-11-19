package xuanmo.arcartxsuite.qqbot.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.qqbot.service.QQBotBindService;
import xuanmo.arcartxsuite.qqbot.service.QQBotService;
import xuanmo.arcartxsuite.qqbot.service.QQBotUiService;

/** Player commands delegate binding to the host and delivery policy to the shared gateway. */
public final class QQBotPlayerCommand implements TabExecutor {
    private final JavaPlugin plugin;
    private final Supplier<QQBotService> serviceProvider;
    private final Supplier<QQBotUiService> uiServiceProvider;
    private final MessageProvider messages;

    public QQBotPlayerCommand(JavaPlugin plugin, Supplier<QQBotService> serviceProvider,
                              Supplier<QQBotUiService> uiServiceProvider, MessageProvider messages) {
        this.plugin = plugin; this.serviceProvider = serviceProvider;
        this.uiServiceProvider = uiServiceProvider; this.messages = messages;
    }
    private String message(String key, Object... args) {
        return messages == null ? key : messages.get("prefix") + messages.get(key, args);
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(message("common.only-player")); return true; }
        if (!player.hasPermission("arcartxsuite.qqbot.use")) { sender.sendMessage(message("common.no-permission")); return true; }
        QQBotService service = serviceProvider.get();
        if (service == null) { player.sendMessage(message("common.service-down")); return true; }
        String action = args.length == 0 ? "ui" : args[0].toLowerCase(java.util.Locale.ROOT);
        QQBotBindService binding = service.bindService();
        switch (action) {
            case "help" -> help(player, label);
            case "ui" -> {
                QQBotUiService ui = uiServiceProvider.get();
                if (ui == null) player.sendMessage(message("player.ui.not-ready"));
                else ui.openBindCenter(player);
            }
            case "bind" -> {
                if (args.length != 2 || !args[1].matches("[A-Za-z0-9-]{4,64}")) {
                    player.sendMessage(message("player.bind.usage", label)); return true;
                }
                player.sendMessage(message("player.bind.pending"));
                completeBinding(player, service, binding.confirmBind(player, args[1]));
            }
            case "unbind" -> {
                if (args.length < 2 || !"confirm".equalsIgnoreCase(args[1])) {
                    player.sendMessage(message("player.unbind.confirm", label)); return true;
                }
                player.sendMessage(message("player.unbind.pending"));
                completeBinding(player, service, binding.unbindByPlayer(player.getUniqueId()));
            }
            case "info" -> {
                java.util.UUID profileId = player.getUniqueId();
                service.submitIo(() -> binding.findByPlayer(profileId)).whenComplete((result, failure) ->
                    reply(player, service, failure != null ? message("common.operation-failed")
                        : result == null ? message("player.info.none") : message("player.info.bind", result.qqId())));
            }
            case "admin" -> {
                if (!player.hasPermission("arcartxsuite.qqbot.admin")) {
                    player.sendMessage(message("common.no-permission")); return true;
                }
                QQBotUiService ui = uiServiceProvider.get();
                if (ui == null) player.sendMessage(message("player.ui.not-ready")); else ui.openAdminPanel(player);
            }
            case "send" -> {
                if (args.length < 2) { player.sendMessage(message("player.send.usage", label)); return true; }
                player.sendMessage(message("player.send.pending"));
                service.sendPlayerMessage(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)))
                    .whenComplete((result, error) -> reply(player, service, error != null ? message("common.operation-failed")
                        : (result.success() ? "§a" : "§c") + result.message()));
            }
            case "at" -> {
                if (args.length < 3) { player.sendMessage(message("player.at.usage", label)); return true; }
                long target;
                try {
                    target = Long.parseLong(args[1]);
                    if (target <= 0) throw new NumberFormatException();
                } catch (NumberFormatException error) {
                    player.sendMessage(message("player.at.invalid-qq", args[1])); return true;
                }
                player.sendMessage(message("player.send.pending"));
                service.sendPlayerMention(player, target, String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)))
                    .whenComplete((result, error) -> reply(player, service, error != null ? message("common.operation-failed")
                        : (result.success() ? "§a" : "§c") + result.message()));
            }
            default -> help(player, label);
        }
        return true;
    }

    private void completeBinding(Player player, QQBotService service, CompletionStage<QQBotBindService.BindResult> pending) {
        pending.whenComplete((result, error) -> reply(player, service,
            error != null ? message("common.operation-failed") : (result.success() ? "§a" : "§c") + QQBotUiService.bindingMessage(result.message())));
    }
    private void reply(Player player, QQBotService service, String text) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (serviceProvider.get() == service && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player) player.sendMessage(text);
        });
    }
    @Override @Nullable public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                         @NotNull String label, @NotNull String[] args) {
        if (args.length == 2 && "unbind".equalsIgnoreCase(args[0])) return List.of("confirm");
        if (args.length != 1) return List.of();
        List<String> result = new ArrayList<>();
        for (String action : List.of("ui", "help", "bind", "unbind", "info", "send", "at", "admin")) {
            if ("admin".equals(action) && !sender.hasPermission("arcartxsuite.qqbot.admin")) continue;
            if ("send".equals(action) && !sender.hasPermission("arcartxsuite.qqbot.send")) continue;
            if ("at".equals(action) && !sender.hasPermission("arcartxsuite.qqbot.at")) continue;
            if (action.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) result.add(action);
        }
        return result;
    }
    private void help(Player player, String label) {
        String command = "/" + label;
        player.sendMessage(message("player.help.title"));
        for (String action : List.of("ui", "bind", "unbind", "info", "send", "at")) player.sendMessage(message("player.help." + action, command));
        if (player.hasPermission("arcartxsuite.qqbot.admin")) player.sendMessage(message("player.help.admin", command));
    }
}
