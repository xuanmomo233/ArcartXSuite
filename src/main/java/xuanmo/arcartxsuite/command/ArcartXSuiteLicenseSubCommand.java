package xuanmo.arcartxsuite.command;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import xuanmo.arcartxsuite.ArcartXSuitePlugin;
import xuanmo.arcartxsuite.license.HostFingerprint;
import xuanmo.arcartxsuite.license.LicenseDecision;
import xuanmo.arcartxsuite.license.LicenseDiagnostics;
import xuanmo.arcartxsuite.license.LicenseGateway.LicenseAuthException;
import xuanmo.arcartxsuite.license.LicenseGateway.LicenseNetworkException;
import xuanmo.arcartxsuite.license.LicenseMessages;
import xuanmo.arcartxsuite.license.LicenseService;

/**
 * /arcartxsuite license &lt;子命令&gt; 处理器。
 * <p>
 * 子命令：status / refresh / activate / rebind / cloud-code / fingerprint
 */
public final class ArcartXSuiteLicenseSubCommand {

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final List<String> SUB_ACTIONS = List.of(
        "status", "refresh", "activate", "rebind", "cloud-code", "fingerprint"
    );
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ArcartXSuitePlugin plugin;

    public ArcartXSuiteLicenseSubCommand(ArcartXSuitePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 处理 {@code /arcartxsuite license <args...>}.
     *
     * @param subArgs 已剥离 {@code license} 之后的参数
     */
    public boolean execute(CommandSender sender, String[] subArgs) {
        LicenseService service = plugin.getLicenseService();
        if (service == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "LicenseService 未初始化。");
            return true;
        }
        if (subArgs.length == 0 || "help".equalsIgnoreCase(subArgs[0])) {
            sendHelp(sender);
            return true;
        }
        String action = subArgs[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "status" -> handleStatus(sender, service);
            case "refresh" -> handleRefresh(sender, service);
            case "activate" -> handleActivate(sender, service);
            case "rebind" -> handleRebind(sender, service);
            case "cloud-code", "cloudcode" -> handleCloudCode(sender, service);
            case "fingerprint" -> handleFingerprint(sender, service);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    public List<String> tabComplete(String[] subArgs) {
        if (subArgs.length == 1) {
            return filter(SUB_ACTIONS, subArgs[0]);
        }
        return List.of();
    }

    // ─── 子命令实现 ────────────────────────────────────────────

    private boolean handleStatus(CommandSender sender, LicenseService service) {
        LicenseDiagnostics d = service.diagnostics();
        sender.sendMessage(PREFIX + ChatColor.GRAY + "状态: " + ChatColor.WHITE + LicenseMessages.state(d.state()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "原因: " + ChatColor.WHITE + (d.reason().isEmpty() ? "-" : d.reason()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "QQ: " + ChatColor.WHITE + (d.ownerQq().isEmpty() ? "-" : d.ownerQq()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "Subject: " + ChatColor.WHITE + (d.subjectId().isEmpty() ? "-" : d.subjectId()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "License ID: " + ChatColor.WHITE + (d.licenseId().isEmpty() ? "-" : d.licenseId()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "Activation ID: " + ChatColor.WHITE + (d.activationId().isEmpty() ? "-" : d.activationId()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "模块: " + ChatColor.WHITE + (d.modules().isEmpty() ? "-" : d.modules()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "使用缓存: " + (d.usingCache() ? ChatColor.YELLOW + "是" : ChatColor.GREEN + "否"));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "授权入口: " + ChatColor.WHITE + (d.endpoints().isEmpty() ? "-" : d.endpoints()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "代理: " + ChatColor.WHITE + (d.proxy().isEmpty() ? "-" : d.proxy()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "预检: " + ChatColor.WHITE + (d.preflightSummary().isEmpty() ? "-" : d.preflightSummary()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "最后操作: " + ChatColor.WHITE + (d.lastOperation().isEmpty() ? "-" : d.lastOperation()));
        sender.sendMessage(PREFIX + ChatColor.GRAY + "成功入口: " + ChatColor.WHITE + (d.lastSuccessfulEndpoint().isEmpty() ? "-" : d.lastSuccessfulEndpoint()));
        if (!d.lastFailureSummary().isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "最后失败: " + ChatColor.RED + d.lastFailureSummary());
        }
        sender.sendMessage(PREFIX + ChatColor.GRAY + "授权码结果: " + ChatColor.WHITE + (d.keyResults().isEmpty() ? "-" : d.keyResults()));
        if (d.expiresAt() > 0L) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "过期时间: " + ChatColor.WHITE + formatInstant(d.expiresAt()));
        }
        if (d.refreshAfter() > 0L) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "刷新时间: " + ChatColor.WHITE + formatInstant(d.refreshAfter()));
        }
        sender.sendMessage(PREFIX + ChatColor.GRAY + "机器指纹: " + ChatColor.WHITE + (d.fingerprintHash().isEmpty() ? "-" : d.fingerprintHash()));
        if (d.rollbackDetected()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "检测到系统时间回滚（rollbackDetected=true）");
        }
        return true;
    }

    private boolean handleRefresh(CommandSender sender, LicenseService service) {
        return runAsync(sender, "刷新授权票据", () -> service.refresh(false));
    }

    private boolean handleActivate(CommandSender sender, LicenseService service) {
        return runAsync(sender, "激活授权（首次绑定）", () -> service.refresh(true));
    }

    private boolean handleRebind(CommandSender sender, LicenseService service) {
        return runAsync(sender, "换绑授权到当前服务器", service::rebind);
    }

    private boolean handleCloudCode(CommandSender sender, LicenseService service) {
        sender.sendMessage(PREFIX + ChatColor.GRAY + "正在生成云端换绑挑战码…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject response = service.cloudChallenge();
                String challengeCode = response.has("challengeCode") ? response.get("challengeCode").getAsString() : "";
                long expiresAt = response.has("expiresAt") ? response.get("expiresAt").getAsLong() : 0L;
                if (challengeCode.isBlank()) {
                    sendOnMain(sender, PREFIX + ChatColor.RED + "授权中心未返回 challengeCode。");
                    return;
                }
                sendOnMain(sender, PREFIX + ChatColor.GREEN + "挑战码: " + ChatColor.WHITE + challengeCode);
                if (expiresAt > 0L) {
                    sendOnMain(sender, PREFIX + ChatColor.GRAY + "有效期至: " + ChatColor.WHITE + formatInstant(expiresAt));
                }
                sendOnMain(sender, PREFIX + ChatColor.GRAY + "请在 10 分钟内于云端网页换绑流程中粘贴此挑战码。");
            } catch (LicenseAuthException exception) {
                sendOnMain(sender, PREFIX + ChatColor.RED + "授权被拒绝: "
                    + LicenseMessages.authError(exception.errorCode()));
            } catch (LicenseNetworkException exception) {
                sendOnMain(sender, PREFIX + ChatColor.RED + "授权入口不可达: " + exception.getMessage());
            } catch (RuntimeException exception) {
                sendOnMain(sender, PREFIX + ChatColor.RED + "生成挑战码失败: " + exception.getMessage());
            }
        });
        return true;
    }

    private boolean handleFingerprint(CommandSender sender, LicenseService service) {
        HostFingerprint.Snapshot snapshot = service.fingerprint();
        sender.sendMessage(PREFIX + ChatColor.GRAY + "hash: " + ChatColor.WHITE + snapshot.hash());
        sender.sendMessage(PREFIX + ChatColor.GRAY + "localSaltHash: " + ChatColor.WHITE + snapshot.localSaltHash());
        sender.sendMessage(PREFIX + ChatColor.GRAY + "组件:");
        for (HostFingerprint.Component component : snapshot.components()) {
            sender.sendMessage(
                PREFIX + ChatColor.GRAY + "  - " + ChatColor.WHITE + component.name()
                    + ChatColor.GRAY + " (weight=" + component.weight() + ") "
                    + ChatColor.DARK_GRAY + component.hash()
            );
        }
        return true;
    }

    // ─── 辅助 ─────────────────────────────────────────────────

    private boolean runAsync(CommandSender sender, String action, AsyncTask task) {
        sender.sendMessage(PREFIX + ChatColor.GRAY + "正在" + action + "…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LicenseDecision decision = task.run();
                sendOnMain(sender, PREFIX + ChatColor.GRAY + "结果: " + ChatColor.WHITE
                    + LicenseMessages.state(decision.state()));
                if (!decision.reason().isEmpty()) {
                    sendOnMain(sender, PREFIX + ChatColor.GRAY + "原因: " + ChatColor.WHITE + decision.reason());
                }
                if (!decision.modules().isEmpty()) {
                    sendOnMain(sender, PREFIX + ChatColor.GRAY + "已解锁模块: "
                        + ChatColor.WHITE + String.join(", ", decision.modules()));
                }
            } catch (RuntimeException exception) {
                sendOnMain(sender, PREFIX + ChatColor.RED + action + "失败: " + exception.getMessage());
            }
        });
        return true;
    }

    private void sendOnMain(CommandSender sender, String message) {
        if (Bukkit.isPrimaryThread()) {
            sender.sendMessage(message);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GRAY + "授权命令:");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  license status" + ChatColor.GRAY + " - 查看授权状态、模块、入口、缓存");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  license refresh" + ChatColor.GRAY + " - 刷新当前服务器的授权票据");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  license activate" + ChatColor.GRAY + " - 主动激活当前服务器");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  license rebind" + ChatColor.GRAY + " - 把授权码换绑到当前服务器（消耗换绑次数）");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  license cloud-code" + ChatColor.GRAY + " - 生成云端网页换绑挑战码");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  license fingerprint" + ChatColor.GRAY + " - 输出本服机器指纹及组件");
    }

    private static String formatInstant(long epochMillis) {
        if (epochMillis <= 0L) {
            return "-";
        }
        return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    private static List<String> filter(List<String> source, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return source;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        return source.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    @FunctionalInterface
    private interface AsyncTask {
        LicenseDecision run();
    }
}
