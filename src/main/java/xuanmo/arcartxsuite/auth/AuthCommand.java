package xuanmo.arcartxsuite.auth;

import java.util.List;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * /axs auth 子命令处理器。
 */
public class AuthCommand {

    private final AuthlibInjectorManager manager;
    private final Logger logger;

    public AuthCommand(AuthlibInjectorManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status" -> {
                boolean loaded = manager.isAgentLoaded();
                sender.sendMessage(ChatColor.GOLD + "===== authlib-injector 状态 =====");
                sender.sendMessage(ChatColor.GRAY + "已加载: " + (loaded ? ChatColor.GREEN + "是" : ChatColor.RED + "否"));
                boolean proxyUp = manager.isMixedProxyReachable();
                sender.sendMessage(ChatColor.GRAY + "本地混合代理(端口 " + manager.getMixedProxyPort() + "): "
                    + (proxyUp ? ChatColor.GREEN + "就绪 " + manager.getMixedProxyUrl()
                               : ChatColor.RED + "未运行"));
                if (!proxyUp) {
                    sender.sendMessage(ChatColor.YELLOW + "  提示: 混合登录需用 start-mixed-auth 脚本启动服务器（代理为独立进程）。");
                }
                if (loaded) {
                    manager.checkVersionAndNotify();
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "提示: 未检测到 authlib-injector，多方认证未启用。");
                    sender.sendMessage(ChatColor.GRAY + "执行 /axs auth setup 自动配置。");
                }
            }
            case "setup" -> {
                if (!sender.hasPermission("arcartxsuite.auth.admin")) {
                    sender.sendMessage(ChatColor.RED + "权限不足。");
                    return true;
                }
                String yggdrasilUrl = args.length > 1 ? args[1]
                    : "https://littleskin.cn/api/yggdrasil?mixed";
                sender.sendMessage(ChatColor.YELLOW + "正在配置 authlib-injector，请稍候...");
                String result = manager.setupAll(yggdrasilUrl);
                for (String line : result.split("\n")) {
                    sender.sendMessage(line);
                }
            }
            case "update" -> {
                if (!sender.hasPermission("arcartxsuite.auth.admin")) {
                    sender.sendMessage(ChatColor.RED + "权限不足。");
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + "正在下载最新版 authlib-injector...");
                java.io.File jar = manager.downloadOrUpdate();
                if (jar != null) {
                    sender.sendMessage(ChatColor.GREEN + "下载完成: " + jar.getAbsolutePath());
                    sender.sendMessage(ChatColor.YELLOW + "请重启服务器以使用新版本。");
                } else {
                    sender.sendMessage(ChatColor.RED + "下载失败，请检查网络或手动下载。");
                }
            }
            case "check" -> {
                sender.sendMessage(ChatColor.YELLOW + "正在检测版本...");
                manager.checkVersionAndNotify();
                sender.sendMessage(ChatColor.GREEN + "检测完成，请查看控制台输出。");
            }
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 1) {
            return List.of("status", "setup", "update", "check", "help");
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== /axs auth 命令 =====");
        sender.sendMessage(ChatColor.GRAY + "/axs auth status  " + ChatColor.WHITE + "- 查看 authlib-injector 状态");
        sender.sendMessage(ChatColor.GRAY + "/axs auth setup [api]" + ChatColor.WHITE + "- 一键配置（默认 LittleSkin Mixed）");
        sender.sendMessage(ChatColor.GRAY + "/axs auth update   " + ChatColor.WHITE + "- 下载/更新 authlib-injector");
        sender.sendMessage(ChatColor.GRAY + "/axs auth check    " + ChatColor.WHITE + "- 检测最新版本");
    }
}
