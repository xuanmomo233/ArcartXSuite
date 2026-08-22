package xuanmo.arcartxsuite.proxy.velocity;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import com.velocitypowered.api.command.SimpleCommand;

import xuanmo.arcartxsuite.proxy.common.auth.AuthlibInjectorManager;
import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;
import xuanmo.arcartxsuite.proxy.common.model.YggdrasilSource;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Velocity 代理端管理命令 /axsproxy。
 */
public class ProxyCommand implements SimpleCommand {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ArcartXSuiteVelocity plugin;
    private final ProxyConfig config;
    private final Logger logger;

    public ProxyCommand(ArcartXSuiteVelocity plugin, ProxyConfig config, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            sendHelp(invocation);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                config.load("proxy-config.yml");
                invocation.source().sendMessage(Component.text("[ArcartXSuite Proxy] 配置已重载。", NamedTextColor.GREEN));
            }
            case "status" -> sendStatus(invocation);
            case "auth" -> handleAuth(invocation, Arrays.copyOfRange(args, 1, args.length));
            case "help" -> sendHelp(invocation);
            default -> sendHelp(invocation);
        }
    }

    private void handleAuth(Invocation invocation, String[] args) {
        if (args.length == 0) {
            invocation.source().sendMessage(Component.text("用法: /axsproxy auth <setup|status|update>", NamedTextColor.RED));
            return;
        }

        AuthlibInjectorManager mgr = new AuthlibInjectorManager(
            logger, plugin.getDataFolder(), config.mixedProxyPort(),
            config.mixedProxyBindHost(), config.mixedProxyToken(), config.mixedProxyAllowIps(),
            AuthlibInjectorManager.Platform.VELOCITY);

        switch (args[0].toLowerCase()) {
            case "setup" -> {
                invocation.source().sendMessage(Component.text("[Auth] 正在配置 authlib-injector...", NamedTextColor.YELLOW));
                String result = mgr.setupAll(config.yggdrasilSource());
                if (result == null) {
                    invocation.source().sendMessage(Component.text("[Auth] 配置失败，请查看控制台日志。", NamedTextColor.RED));
                } else {
                    for (String line : result.split("\n")) {
                        invocation.source().sendMessage(Component.text(line, NamedTextColor.WHITE));
                    }
                }
            }
            case "status" -> sendAuthStatus(invocation, mgr);
            case "update" -> {
                invocation.source().sendMessage(Component.text("[Auth] 正在更新 authlib-injector...", NamedTextColor.YELLOW));
                if (mgr.downloadOrUpdate() != null) {
                    invocation.source().sendMessage(Component.text("[Auth] authlib-injector 已更新到最新版。", NamedTextColor.GREEN));
                    invocation.source().sendMessage(Component.text("[Auth] 请重新执行 /axsproxy auth setup 生成启动脚本。", NamedTextColor.YELLOW));
                } else {
                    invocation.source().sendMessage(Component.text("[Auth] 更新失败，请查看控制台日志。", NamedTextColor.RED));
                }
            }
            default -> invocation.source().sendMessage(Component.text("用法: /axsproxy auth <setup|status|update>", NamedTextColor.RED));
        }
    }

    private void sendStatus(Invocation invocation) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6===== ArcartXSuite Proxy 状态 =====\n");
        sb.append("§7拒绝离线: §f").append(config.denyOffline()).append("\n");
        sb.append("§7调试模式: §f").append(config.debug()).append("\n");
        sb.append("§7混合登录: §f").append(config.authEnabled() ? "启用" : "禁用").append("\n");
        if (config.authEnabled()) {
            sb.append("§7混合代理端口: §f").append(config.mixedProxyPort()).append("\n");
            sb.append("§7Yggdrasil 源: §f").append(config.yggdrasilSource()).append("\n");
        }
        sb.append("§7Yggdrasil 源列表:\n");
        List<YggdrasilSource> sources = config.sources();
        if (sources.isEmpty()) {
            sb.append("  §c(无)\n");
        } else {
            for (YggdrasilSource s : sources) {
                sb.append("  §a").append(s.name())
                  .append(" §7(").append(s.apiUrl()).append(")\n");
            }
        }
        invocation.source().sendMessage(LEGACY.deserialize(sb.toString()));
    }

    private void sendAuthStatus(Invocation invocation, AuthlibInjectorManager mgr) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6===== Authlib-Injector 状态 =====\n");

        boolean agentLoaded = mgr.isAgentLoaded();
        sb.append("§7authlib-injector Agent: ")
          .append(agentLoaded ? "§a已加载" : "§c未加载").append("\n");

        boolean proxyReachable = mgr.isMixedProxyReachable();
        sb.append("§7本地混合代理 (端口 ").append(config.mixedProxyPort()).append("): ")
          .append(proxyReachable ? "§a已就绪" : "§c未启动").append("\n");

        File authlibJar = new File(plugin.getDataFolder(), "authlib-injector.jar");
        sb.append("§7authlib-injector.jar: ")
          .append(authlibJar.exists() ? "§a已下载" : "§c未下载").append("\n");

        if (!agentLoaded) {
            sb.append("\n§e提示: authlib-injector 未加载，请执行 /axsproxy auth setup\n");
            sb.append("§e      然后使用 start-mixed-auth 脚本启动代理端\n");
        } else if (!proxyReachable) {
            sb.append("\n§e警告: 混合代理未启动！\n");
            sb.append("§e      请使用 start-mixed-auth 脚本启动代理端\n");
        }

        invocation.source().sendMessage(LEGACY.deserialize(sb.toString()));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            return List.of("reload", "status", "auth", "help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("auth")) {
            return List.of("setup", "status", "update");
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("arcartxsuite.proxy.admin");
    }

    private void sendHelp(Invocation invocation) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6===== ArcartXSuite Proxy 命令 =====\n");
        sb.append("§7/axsproxy reload §f- 重载配置\n");
        sb.append("§7/axsproxy status §f- 查看状态\n");
        sb.append("§7/axsproxy auth setup §f- 配置混合登录\n");
        sb.append("§7/axsproxy auth status §f- 查看 auth 状态\n");
        sb.append("§7/axsproxy auth update §f- 更新 authlib-injector\n");
        sb.append("§7/axsproxy help  §f- 显示帮助\n");
        invocation.source().sendMessage(LEGACY.deserialize(sb.toString()));
    }
}
