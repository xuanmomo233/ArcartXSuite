package xuanmo.arcartxsuite.extrabackpack.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.capability.ExtraBackpackAccess;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.extrabackpack.service.ExtraBackpackService;

/**
 * 额外背包模块的管理员命令处理器。
 * <p>
 * 实现 {@link ModuleCommandHandler}，在 {@code /axs extrabackpack ...} 下注册管理员子命令：
 * help / status / info / give / reset。所有动作均要求 {@code arcartxsuite.extrabackpack.admin} 权限。
 * 关联模块：extrabackpack。
 */
public final class ExtraBackpackAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "info", "give", "reset");

    private final Supplier<ExtraBackpackService> serviceProvider;
    private final MessageProvider messages;

    /**
     * 构造管理员命令处理器。
     *
     * @param serviceProvider 服务提供者，延迟获取 {@link ExtraBackpackService} 实例
     * @param messages        消息提供者，用于读取本地化提示文本
     */
    public ExtraBackpackAdminCommand(Supplier<ExtraBackpackService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    /** @return 子命令 id，固定为 {@code "extrabackpack"} */
    @Override public String commandId() { return "extrabackpack"; }
    /** @return 支持的子命令动作列表 */
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !player.hasPermission("arcartxsuite.extrabackpack.admin")) {
            sender.sendMessage(msg("command.no-permission"));
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        try {
            switch (action) {
                case "help" -> sendHelp(sender, label);
                case "status" -> sendStatus(sender);
                case "info" -> handleInfo(sender, args);
                case "give" -> handleGive(sender, args);
                case "reset" -> handleReset(sender, args);
                default -> sender.sendMessage(msg("command.unknown", label));
            }
        } catch (Exception exception) {
            sender.sendMessage(msg("command.error", exception.getMessage() == null ? "" : exception.getMessage()));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(ACTIONS, args[1]);
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3 && List.of("info", "give", "reset").contains(action)) {
            return null; // 玩家名交给 Bukkit 默认补全
        }
        ExtraBackpackService service = serviceProvider.get();
        if (args.length == 4 && List.of("give", "reset").contains(action) && service != null) {
            return filter(service.categoryIds(), args[3]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " extrabackpack";
        sender.sendMessage(msg("command.help-title"));
        sender.sendMessage(msg("command.help-status", cmd));
        sender.sendMessage(msg("command.help-info", cmd));
        sender.sendMessage(msg("command.help-give", cmd));
        sender.sendMessage(msg("command.help-reset", cmd));
    }

    private void sendStatus(CommandSender sender) {
        ExtraBackpackService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("command.service-down"));
            return;
        }
        sender.sendMessage(msg("command.status-title"));
        sender.sendMessage(msg("command.status-enabled", service.enabled() ? "true" : "false"));
        sender.sendMessage(msg("command.status-cached", service.cachedPlayerCount()));
        sender.sendMessage(msg("command.status-dirty", service.dirtyPlayerCount()));
        sender.sendMessage(msg("command.status-categories", String.join(", ", service.categoryIds())));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        ExtraBackpackService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("command.service-down"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(msg("command.info-usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(msg("command.player-offline", args[2]));
            return;
        }
        ExtraBackpackAccess.Snapshot snapshot = service.snapshot(target);
        if (!snapshot.available()) {
            sender.sendMessage(msg("command.info-unavailable"));
            return;
        }
        sender.sendMessage(msg("command.info-title", target.getName()));
        for (ExtraBackpackAccess.CategorySnapshot category : snapshot.categories()) {
            sender.sendMessage(msg("command.info-category",
                category.categoryId(), category.used(), category.capacity()));
        }
    }

    private void handleGive(CommandSender sender, String[] args) throws Exception {
        ExtraBackpackService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("command.service-down"));
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(msg("command.give-usage"));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) {
            sender.sendMessage(msg("command.player-not-found", args[2]));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[4]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(msg("command.give-usage"));
            return;
        }
        long granted = service.grantCapacity(target, args[3], amount);
        if (granted < 0) {
            sender.sendMessage(msg("command.give-invalid", args[3]));
            return;
        }
        if (granted == 0) {
            sender.sendMessage(msg("command.give-maxed"));
            return;
        }
        sender.sendMessage(msg("command.give-success", granted, target.getName() == null ? args[2] : target.getName(), args[3]));
    }

    private void handleReset(CommandSender sender, String[] args) throws Exception {
        ExtraBackpackService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("command.service-down"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(msg("command.reset-usage"));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) {
            sender.sendMessage(msg("command.player-not-found", args[2]));
            return;
        }
        if (!service.resetCapacity(target, args[3])) {
            sender.sendMessage(msg("command.give-invalid", args[3]));
            return;
        }
        sender.sendMessage(msg("command.reset-success", target.getName() == null ? args[2] : target.getName(), args[3]));
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private static List<String> filter(List<String> candidates, String input) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
