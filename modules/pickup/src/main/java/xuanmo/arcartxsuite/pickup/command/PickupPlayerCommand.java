package xuanmo.arcartxsuite.pickup.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.pickup.service.LootScannerService;
import xuanmo.arcartxsuite.pickup.service.PickupService;

/**
 * Pickup 玩家指令。
 * <p>
 * 用法：
 * <ul>
 *   <li>{@code /pickup} — 切换拾取功能开关（toggle）</li>
 *   <li>{@code /pickup on} — 开启拾取功能</li>
 *   <li>{@code /pickup off} — 关闭拾取功能</li>
 *   <li>{@code /pickup status} — 查看当前状态</li>
 * </ul>
 * <p>
 * 权限：{@code arcartxsuite.pickup.use}
 */
public final class PickupPlayerCommand implements TabExecutor {

    private static final String PERMISSION = "arcartxsuite.pickup.use";
    private static final List<String> ACTIONS = List.of("on", "off", "status");

    private final Supplier<PickupService> notificationServiceSupplier;
    private final Supplier<LootScannerService> scannerServiceSupplier;
    private final MessageProvider messages;

    public PickupPlayerCommand(
        Supplier<PickupService> notificationServiceSupplier,
        Supplier<LootScannerService> scannerServiceSupplier,
        MessageProvider messages
    ) {
        this.notificationServiceSupplier = notificationServiceSupplier;
        this.scannerServiceSupplier = scannerServiceSupplier;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("player-only"));
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(msg("no-permission"));
            return true;
        }

        String action = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "toggle";

        switch (action) {
            case "on" -> handleToggle(player, Boolean.TRUE);
            case "off" -> handleToggle(player, Boolean.FALSE);
            case "toggle" -> handleToggle(player, null);
            case "status" -> handleStatus(player);
            default -> handleToggle(player, null);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player) || args.length != 1) {
            return List.of();
        }
        return filter(ACTIONS, args[0]);
    }

    private void handleToggle(Player player, Boolean enabled) {
        PickupService notifSvc = notificationServiceSupplier.get();
        LootScannerService scannerSvc = scannerServiceSupplier.get();

        if (notifSvc == null && scannerSvc == null) {
            player.sendMessage(msg("module-disabled"));
            return;
        }

        boolean newState;
        if (scannerSvc != null) {
            newState = scannerSvc.setEnabled(player.getUniqueId(), enabled);
        } else {
            newState = notifSvc.setEnabled(player.getUniqueId(), enabled);
        }

        if (newState) {
            player.sendMessage(msg("toggle.enabled"));
        } else {
            player.sendMessage(msg("toggle.disabled"));
        }
    }

    private void handleStatus(Player player) {
        PickupService notifSvc = notificationServiceSupplier.get();
        LootScannerService scannerSvc = scannerServiceSupplier.get();

        if (notifSvc == null && scannerSvc == null) {
            player.sendMessage(msg("module-disabled"));
            return;
        }

        boolean isEnabled;
        String modeName;
        if (scannerSvc != null) {
            isEnabled = scannerSvc.isEnabled(player.getUniqueId());
            modeName = messages.get("mode.scanner");
        } else {
            isEnabled = notifSvc.isEnabled(player.getUniqueId());
            modeName = messages.get("mode.notification");
        }

        player.sendMessage(msg("status.title"));
        player.sendMessage(msg("status.mode", modeName));
        player.sendMessage(msg("status.state", isEnabled ? messages.get("state.on") : messages.get("state.off")));
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
