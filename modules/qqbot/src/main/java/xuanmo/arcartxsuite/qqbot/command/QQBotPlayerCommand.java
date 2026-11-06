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

/**
 * QQBot 模块的玩家命令处理器，实现 {@link TabExecutor}。
 * <p>
 * 关联模块：{@code qqbot}。处理 {@code /qqbot <子命令>} 下的玩家侧操作，
 * 包括绑定 / 解绑 / 查询绑定信息 / 打开 UI 面板 / 管理面板 / @QQ 群员。
 * 仅允许玩家执行，绑定与解绑会联动白名单自动增删。
 */
public final class QQBotPlayerCommand implements TabExecutor {

    /** at 子命令的每玩家冷却（毫秒）——无限制时可向所有 QQ 群刷任意文本 */
    private static final long AT_COOLDOWN_MS = 10_000L;

    private final Supplier<QQBotService> serviceProvider;
    private final Supplier<QQBotUiService> uiServiceProvider;
    private final MessageProvider messages;
    private final java.util.Map<java.util.UUID, Long> atCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 构造玩家命令处理器。
     *
     * @param serviceProvider  QQBotService 延时提供者
     * @param uiServiceProvider QQBotUiService 延时提供者
     * @param messages          消息提供者，可为 {@code null}
     */
    public QQBotPlayerCommand(Supplier<QQBotService> serviceProvider, Supplier<QQBotUiService> uiServiceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.uiServiceProvider = uiServiceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    /**
     * 处理玩家命令的主入口。
     *
     * @param sender  命令发送者，非玩家时提示仅玩家可用
     * @param command Bukkit Command 对象
     * @param label   命令标签
     * @param args    参数数组，{@code args[0]} 为子命令名
     * @return {@code true} 表示命令已处理
     */
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

        if ("help".equalsIgnoreCase(args[0])) {
            sendHelp(player, label);
            return true;
        }

        QQBotBindService bindService = service.bindService();
        switch (args[0].toLowerCase()) {
            case "bind" -> {
                if(args.length<2) { player.sendMessage("请在QQ群申请验证码，然后 /qqbot bind <验证码>"); return true; }
                bindService.confirmBind(player,args[1]).thenAccept(result -> {
                    if(player.isOnline()) player.sendMessage(result.success()?"绑定成功。":result.message());
                });
            }
            case "unbind" -> bindService.unbindByPlayer(player.getUniqueId()).thenAccept(result -> {
                if(player.isOnline()) player.sendMessage(result.success()?"已解绑，认证要求已重新评估。":result.message());
            });
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
                // @群成员会向所有已配置 QQ 群广播文本——必须限权+冷却，防骚扰刷屏
                if (!player.hasPermission("arcartxsuite.qqbot.at")) {
                    player.sendMessage(fullMsg("player.admin.no-permission"));
                    return true;
                }
                Long lastAt = atCooldowns.get(player.getUniqueId());
                long now = System.currentTimeMillis();
                if (lastAt != null && now - lastAt < AT_COOLDOWN_MS) {
                    player.sendMessage(fullMsg("player.at.cooldown", (AT_COOLDOWN_MS - (now - lastAt)) / 1000 + 1));
                    return true;
                }
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
                atCooldowns.put(player.getUniqueId(), now);
                // 发送到所有群（玩家绑定关系所在群由服务层处理）
                service.sendToAllGroups("[MC] " + player.getName() + " @QQ " + targetQq + ": " + msg);
                player.sendMessage(fullMsg("player.at.sent", targetQq));
            }
            default -> sendHelp(player, label);
        }
        return true;
    }

    /**
     * 玩家命令的 Tab 补全，补全一级子命令名。
     *
     * @param sender  命令发送者
     * @param command Bukkit Command 对象
     * @param label   命令标签
     * @param args    当前参数数组
     * @return 补全候选列表，无匹配时返回空列表
     */
    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (String sub : List.of("help", "bind", "unbind", "info", "ui", "admin", "at")) {
                if (sub.startsWith(prefix)) completions.add(sub);
            }
            return completions;
        }
        return List.of();
    }

    private void sendHelp(Player player, String label) {
        String cmd = "/" + label;
        player.sendMessage(fullMsg("player.help.title"));
        player.sendMessage(fullMsg("player.help.bind", cmd));
        player.sendMessage(fullMsg("player.help.unbind", cmd));
        player.sendMessage(fullMsg("player.help.info", cmd));
        player.sendMessage(fullMsg("player.help.ui", cmd));
        player.sendMessage(fullMsg("player.help.at", cmd));
        player.sendMessage(fullMsg("player.help.admin", cmd));
    }
}
