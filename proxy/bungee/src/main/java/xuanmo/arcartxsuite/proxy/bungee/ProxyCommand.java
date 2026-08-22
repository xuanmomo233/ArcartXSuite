package xuanmo.arcartxsuite.proxy.bungee;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import xuanmo.arcartxsuite.proxy.common.auth.AuthlibInjectorManager;
import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;
import xuanmo.arcartxsuite.proxy.common.model.YggdrasilSource;

/**
 * BungeeCord 代理端管理命令（/axsproxy），提供：
 * <ul>
 *   <li>{@code reload} — 重载配置</li>
 *   <li>{@code status} — 查看代理端 + 混合代理 + authlib-injector 状态</li>
 *   <li>{@code auth setup} — 下载 authlib-injector + 生成混合登录启动脚本</li>
 *   <li>{@code auth status} — 查看 authlib-injector + 混合代理连通性</li>
 *   <li>{@code auth update} — 更新 authlib-injector 到最新版</li>
 *   <li>{@code help} — 显示帮助</li>
 * </ul>
 */
public class ProxyCommand extends Command implements TabExecutor {

    private final ArcartXSuiteBungee plugin;
    private final ProxyConfig config;
    private final Logger logger;

    public ProxyCommand(ArcartXSuiteBungee plugin, ProxyConfig config, Logger logger) {
        super("axsproxy", "arcartxsuite.proxy.admin");
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                config.load("proxy-config.yml");
                sender.sendMessage(ChatColor.GREEN + "[ArcartXSuite Proxy] 配置已重载。");
            }
            case "status" -> sendStatus(sender);
            case "auth" -> handleAuth(sender, Arrays.copyOfRange(args, 1, args.length));
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
    }

    private void handleAuth(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "用法: /axsproxy auth <setup|status|update>");
            return;
        }

        AuthlibInjectorManager mgr = new AuthlibInjectorManager(
            logger, plugin.getDataFolder(), config.mixedProxyPort(),
            config.mixedProxyBindHost(), config.mixedProxyToken(), config.mixedProxyAllowIps(),
            AuthlibInjectorManager.Platform.BUNGEECORD);

        switch (args[0].toLowerCase()) {
            case "setup" -> {
                sender.sendMessage(ChatColor.YELLOW + "[Auth] 正在配置 authlib-injector...");
                String result = mgr.setupAll(config.yggdrasilSource());
                if (result == null) {
                    sender.sendMessage(ChatColor.RED + "[Auth] 配置失败，请查看控制台日志。");
                } else {
                    for (String line : result.split("\n")) {
                        sender.sendMessage(ChatColor.WHITE + line);
                    }
                }
            }
            case "status" -> sendAuthStatus(sender, mgr);
            case "update" -> {
                sender.sendMessage(ChatColor.YELLOW + "[Auth] 正在更新 authlib-injector...");
                if (mgr.downloadOrUpdate() != null) {
                    sender.sendMessage(ChatColor.GREEN + "[Auth] authlib-injector 已更新到最新版。");
                    sender.sendMessage(ChatColor.YELLOW + "[Auth] 请重新执行 /axsproxy auth setup 生成启动脚本。");
                } else {
                    sender.sendMessage(ChatColor.RED + "[Auth] 更新失败，请查看控制台日志。");
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "用法: /axsproxy auth <setup|status|update>");
        }
    }

    private void sendStatus(CommandSender sender) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GOLD).append("===== ArcartXSuite Proxy 状态 =====\n");
        sb.append(ChatColor.GRAY).append("拒绝离线: ").append(ChatColor.WHITE).append(config.denyOffline()).append("\n");
        sb.append(ChatColor.GRAY).append("调试模式: ").append(ChatColor.WHITE).append(config.debug()).append("\n");
        sb.append(ChatColor.GRAY).append("混合登录: ").append(ChatColor.WHITE).append(config.authEnabled() ? "启用" : "禁用").append("\n");
        if (config.authEnabled()) {
            sb.append(ChatColor.GRAY).append("混合代理端口: ").append(ChatColor.WHITE).append(config.mixedProxyPort()).append("\n");
            sb.append(ChatColor.GRAY).append("Yggdrasil 源: ").append(ChatColor.WHITE).append(config.yggdrasilSource()).append("\n");
        }
        sb.append(ChatColor.GRAY).append("Yggdrasil 源列表:\n");
        List<YggdrasilSource> sources = config.sources();
        if (sources.isEmpty()) {
            sb.append(ChatColor.RED).append("  (无)\n");
        } else {
            for (YggdrasilSource s : sources) {
                sb.append(ChatColor.GREEN).append("  ").append(s.name())
                  .append(ChatColor.GRAY).append(" (").append(s.apiUrl()).append(")\n");
            }
        }
        sender.sendMessage(sb.toString());
    }

    private void sendAuthStatus(CommandSender sender, AuthlibInjectorManager mgr) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GOLD).append("===== Authlib-Injector 状态 =====\n");

        boolean agentLoaded = mgr.isAgentLoaded();
        sb.append(ChatColor.GRAY).append("authlib-injector Agent: ");
        sb.append(agentLoaded ? ChatColor.GREEN + "已加载" : ChatColor.RED + "未加载").append("\n");

        boolean proxyReachable = mgr.isMixedProxyReachable();
        sb.append(ChatColor.GRAY).append("本地混合代理 (端口 ").append(config.mixedProxyPort()).append("): ");
        sb.append(proxyReachable ? ChatColor.GREEN + "已就绪" : ChatColor.RED + "未启动").append("\n");

        File authlibJar = new File(plugin.getDataFolder(), "authlib-injector.jar");
        sb.append(ChatColor.GRAY).append("authlib-injector.jar: ");
        sb.append(authlibJar.exists() ? ChatColor.GREEN + "已下载" : ChatColor.RED + "未下载").append("\n");

        if (!agentLoaded) {
            sb.append(ChatColor.YELLOW).append("\n提示: authlib-injector 未加载，请执行 /axsproxy auth setup\n");
            sb.append(ChatColor.YELLOW).append("      然后使用 start-mixed-auth 脚本启动代理端\n");
        } else if (!proxyReachable) {
            sb.append(ChatColor.YELLOW).append("\n警告: 混合代理未启动！\n");
            sb.append(ChatColor.YELLOW).append("      请使用 start-mixed-auth 脚本启动代理端\n");
        }

        sender.sendMessage(sb.toString());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status", "auth", "help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("auth")) {
            return List.of("setup", "status", "update");
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GOLD).append("===== ArcartXSuite Proxy 命令 =====\n");
        sb.append(ChatColor.GRAY).append("/axsproxy reload ").append(ChatColor.WHITE).append("- 重载配置\n");
        sb.append(ChatColor.GRAY).append("/axsproxy status ").append(ChatColor.WHITE).append("- 查看状态\n");
        sb.append(ChatColor.GRAY).append("/axsproxy auth setup ").append(ChatColor.WHITE).append("- 配置混合登录\n");
        sb.append(ChatColor.GRAY).append("/axsproxy auth status ").append(ChatColor.WHITE).append("- 查看 auth 状态\n");
        sb.append(ChatColor.GRAY).append("/axsproxy auth update ").append(ChatColor.WHITE).append("- 更新 authlib-injector\n");
        sb.append(ChatColor.GRAY).append("/axsproxy help  ").append(ChatColor.WHITE).append("- 显示帮助\n");
        sender.sendMessage(sb.toString());
    }
}
