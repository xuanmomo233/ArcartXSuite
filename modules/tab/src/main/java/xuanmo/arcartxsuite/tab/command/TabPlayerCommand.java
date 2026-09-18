package xuanmo.arcartxsuite.tab.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.tab.sync.TabSyncService;

/**
 * Tab 玩家命令：切换视图 / 翻页 / 查看状态。
 *
 * <ul>
 *   <li>{@code /tab view <name>} 切换 view。仅刷新本玩家。</li>
 *   <li>{@code /tab page <definitionId> <next|prev|N>} 翻页。</li>
 *   <li>{@code /tab refresh} 强制刷新本玩家。</li>
 * </ul>
 *
 * <p>管理 / 调试子命令（debug / snapshot / fake / 指定玩家操作）见
 * {@link TabAdminCommand}，挂载于 {@code /axs tab}。
 */
public final class TabPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("help", "view", "page", "refresh");

    private final Supplier<TabSyncService> serviceSupplier;
    private final MessageProvider messages;

    public TabPlayerCommand(
        Supplier<TabSyncService> serviceSupplier,
        MessageProvider messages
    ) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        TabSyncService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            showHelp(player, label);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "view" -> handleView(service, player, args);
            case "page" -> handlePage(service, player, args);
            case "refresh" -> {
                service.refreshViewer(player, "command");
                player.sendMessage(fullMsg("player.refresh.requested"));
            }
            default -> showHelp(player, label);
        }
        return true;
    }

    private void handleView(TabSyncService service, Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(fullMsg("player.view.current", service.currentView(player)));
            player.sendMessage(fullMsg("player.view.usage"));
            return;
        }
        String view = args[1].trim();
        if (service.setViewerView(player, view)) {
            player.sendMessage(fullMsg("player.view.success", view));
        } else {
            player.sendMessage(fullMsg("player.view.no-change"));
        }
    }

    private void handlePage(TabSyncService service, Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(fullMsg("player.page.usage"));
            return;
        }
        String defId = args[1].trim();
        String pageArg = args[2].trim();
        int target;
        if ("next".equalsIgnoreCase(pageArg)) {
            target = service.currentPage(player, defId) + 1;
        } else if ("prev".equalsIgnoreCase(pageArg)) {
            target = Math.max(0, service.currentPage(player, defId) - 1);
        } else {
            try {
                target = Math.max(0, Integer.parseInt(pageArg));
            } catch (NumberFormatException ex) {
                player.sendMessage(fullMsg("player.page.invalid-page", pageArg));
                return;
            }
        }
        if (service.setViewerPage(player, defId, target)) {
            player.sendMessage(fullMsg("player.page.success", target));
        } else {
            player.sendMessage(fullMsg("player.page.failed"));
        }
    }

    private void showHelp(Player player, String label) {
        player.sendMessage(fullMsg("player.help.title"));
        player.sendMessage(fullMsg("player.help.view", "/" + label));
        player.sendMessage(fullMsg("player.help.page", "/" + label));
        player.sendMessage(fullMsg("player.help.refresh", "/" + label));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(ROOT_ACTIONS, args[0]);
        }
        if (args.length == 3 && "page".equalsIgnoreCase(args[0])) {
            return filter(List.of("next", "prev", "0", "1", "2"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return candidates;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
