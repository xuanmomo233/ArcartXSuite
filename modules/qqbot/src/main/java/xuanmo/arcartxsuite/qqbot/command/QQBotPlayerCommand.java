package xuanmo.arcartxsuite.qqbot.command;

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
import xuanmo.arcartxsuite.qqbot.service.QQBotBindService;
import xuanmo.arcartxsuite.qqbot.service.QQBotService;
import xuanmo.arcartxsuite.qqbot.service.QQBotUiService;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;

public final class QQBotPlayerCommand implements TabExecutor {

    private final Supplier<QQBotService> serviceProvider;
    private final Supplier<QQBotUiService> uiServiceProvider;
    private final MessageProvider messages;

    public QQBotPlayerCommand(Supplier<QQBotService> serviceProvider, Supplier<QQBotUiService> uiServiceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.uiServiceProvider = uiServiceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return true;
        }

        QQBotService service = serviceProvider.get();
        if (service == null) {
            player.sendMessage(fullMsg("common.service-down"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player, label);
            return true;
        }

        QQBotBindService bindService = service.bindService();
        switch (args[0].toLowerCase()) {
            case "bind" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.bind.usage", label));
                    return true;
                }
                QQBotBindService.BindResult result = bindService.confirmBind(player, args[1]);
                if (result.success()) {
                    String msg = service.configuration().messages().bindSuccessGame()
                        .replace("{qq}", result.message())
                        .replace("{name}", player.getName());
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.translateAlternateColorCodes('&', msg));

                    // 自动加白名单
                    if (service.configuration().whitelist().enabled()
                        && service.configuration().whitelist().autoAddOnBind()) {
                        String wlCmd = service.configuration().whitelist().addCommand()
                            .replace("{name}", player.getName());
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), wlCmd);
                    }
                } else {
                    String msg = service.configuration().messages().codeInvalid();
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.translateAlternateColorCodes('&', msg));
                }
            }
            case "unbind" -> {
                QQBotBindService.BindResult result = bindService.unbindByPlayer(player.getUniqueId());
                if (result.success()) {
                    String msg = service.configuration().messages().unbindSuccessGame()
                        .replace("{qq}", result.message());
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.translateAlternateColorCodes('&', msg));

                    // 自动移除白名单
                    if (service.configuration().whitelist().enabled()
                        && service.configuration().whitelist().autoRemoveOnUnbind()) {
                        String wlCmd = service.configuration().whitelist().removeCommand()
                            .replace("{name}", player.getName());
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), wlCmd);
                    }
                } else {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + result.message());
                }
            }
            case "info" -> {
                QQBotBinding binding = bindService.findByPlayer(player.getUniqueId());
                if (binding != null) {
                    player.sendMessage(fullMsg("player.info.bind", binding.qqId()));
                } else {
                    player.sendMessage(fullMsg("player.info.none"));
                }
            }
            case "ui" -> {
                QQBotUiService uiService = uiServiceProvider.get();
                if (uiService == null) {
                    player.sendMessage(fullMsg("player.ui.not-ready"));
                    return true;
                }
                uiService.openBindCenter(player);
            }
            case "admin" -> {
                if (!player.hasPermission("arcartxsuite.qqbot.admin")) {
                    player.sendMessage(fullMsg("player.admin.no-permission"));
                    return true;
                }
                QQBotUiService uiService = uiServiceProvider.get();
                if (uiService == null) {
                    player.sendMessage(fullMsg("player.ui.not-ready"));
                    return true;
                }
                uiService.openAdminPanel(player);
            }
            case "at" -> {
                if (args.length < 3) {
                    player.sendMessage(fullMsg("player.at.usage", label));
                    return true;
                }
                long targetQq;
                try {
                    targetQq = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(fullMsg("player.at.invalid-qq", args[1]));
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i > 2) sb.append(" ");
                    sb.append(args[i]);
                }
                String msg = sb.toString();
                // 发送到所有群（玩家绑定关系所在群由服务层处理）
                service.sendToAllGroups("[MC] " + player.getName() + " @QQ " + targetQq + ": " + msg);
                player.sendMessage(fullMsg("player.at.sent", targetQq));
            }
            default -> sendHelp(player, label);
        }
        return true;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (String sub : List.of("bind", "unbind", "info", "ui", "admin", "at")) {
                if (sub.startsWith(prefix)) completions.add(sub);
            }
            return completions;
        }
        return List.of();
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(fullMsg("player.help.title"));
        player.sendMessage(fullMsg("player.help.bind", label));
        player.sendMessage(fullMsg("player.help.unbind", label));
        player.sendMessage(fullMsg("player.help.info", label));
        player.sendMessage(fullMsg("player.help.ui", label));
        player.sendMessage(fullMsg("player.help.at", label));
        player.sendMessage(fullMsg("player.help.admin", label));
    }
}
