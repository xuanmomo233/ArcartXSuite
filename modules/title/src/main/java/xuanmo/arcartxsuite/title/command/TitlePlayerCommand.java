package xuanmo.arcartxsuite.title.command;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.service.TitleService;

/**
 * 称号模块的玩家命令处理器。
 * <p>
 * 提供 open、equip、unequip、hide、unhide 等子命令，
 * 供玩家打开称号菜单、装备/卸下称号、隐藏/显示称号。
 * 属于 title 模块命令层。
 */
public final class TitlePlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("help", "open", "equip", "unequip", "hide", "unhide");

    private final Supplier<TitleService> serviceProvider;
    private final Supplier<TitleModuleConfiguration> configurationProvider;
    private final MessageProvider messages;

    /**
     * @param serviceProvider       称号服务提供者
     * @param configurationProvider 模块配置提供者，用于 Tab 补全
     * @param messages              消息提供者，用于获取本地化提示文本
     */
    public TitlePlayerCommand(Supplier<TitleService> serviceProvider, Supplier<TitleModuleConfiguration> configurationProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.configurationProvider = configurationProvider;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }

    /**
     * 处理玩家命令分发。
     *
     * @param sender  命令发送者，必须是玩家
     * @param command 命令对象
     * @param label   命令标签
     * @param args    命令参数，args[0] 为子命令名
     * @return 始终返回 {@code true}
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("player.only-player"));
            return true;
        }
        if (!sender.hasPermission("arcartxsuite.title.use")) {
            sender.sendMessage(msg("player.no-permission"));
            return true;
        }
        TitleService titleSvc = serviceProvider.get();
        if (titleSvc == null) {
            sender.sendMessage(msg("player.module-disabled"));
            return true;
        }

        if (args.length > 0 && "help".equalsIgnoreCase(args[0])) {
            showHelp(player);
            return true;
        }

        if (args.length == 0 || "open".equalsIgnoreCase(args[0])) {
            titleSvc.openMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "equip" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("player.equip-usage", label));
                    return true;
                }
                titleSvc.equipTitle(player, args[1]);
                return true;
            }
            case "unequip" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("player.unequip-usage", label));
                    return true;
                }
                if ("all".equalsIgnoreCase(args[1])) {
                    titleSvc.unequipAll(player);
                } else {
                    titleSvc.unequipGroup(player, args[1]);
                }
                return true;
            }
            case "hide" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("player.hide-usage", label));
                    return true;
                }
                titleSvc.hideTitle(player, args[1], true);
                return true;
            }
            case "unhide" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("player.unhide-usage", label));
                    return true;
                }
                titleSvc.unhideTitle(player, args[1], true);
                return true;
            }
            default -> {
                sender.sendMessage(msg("player.usage", label));
                return true;
            }
        }
    }

    /**
     * 提供玩家命令 Tab 补全。
     *
     * @param sender  命令发送者
     * @param command 命令对象
     * @param alias   命令别名
     * @param args    当前已输入的参数
     * @return 补全候选列表
     */
    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            return filter(ROOT_ACTIONS, args[0]);
        }
        TitleModuleConfiguration cfg = configurationProvider.get();
        if (args.length == 2 && cfg != null) {
            String action = args[0].toLowerCase();
            if ("equip".equals(action) || "hide".equals(action) || "unhide".equals(action)) {
                return filter(new ArrayList<>(cfg.titles().keySet()), args[1]);
            }
            if ("unequip".equals(action)) {
                List<String> groups = new ArrayList<>(cfg.groups().keySet());
                groups.add("all");
                return filter(groups, args[1]);
            }
        }
        return List.of();
    }

    private void showHelp(Player player) {
        player.sendMessage(msg("player.help.title"));
        player.sendMessage(msg("player.help.open"));
        player.sendMessage(msg("player.help.equip"));
        player.sendMessage(msg("player.help.unequip"));
        player.sendMessage(msg("player.help.hide"));
        player.sendMessage(msg("player.help.unhide"));
    }

    private List<String> filter(List<String> candidates, String input) {
        String normalized = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
