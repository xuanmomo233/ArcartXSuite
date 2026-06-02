package xuanmo.arcartxsuite.module;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 异步检查 ArcartXSuite 最新版本，启动时通知控制台，OP 加入时通知游戏内。
 */
public final class VersionCheckService implements Listener {

    private static final String VERSION_URL = "https://axs.021209.xyz/api/version";
    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final JavaPlugin plugin;
    private final Logger logger;
    private final String currentVersion;
    private volatile String latestVersion;
    private volatile String updateMessage;

    public VersionCheckService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /**
     * 异步发起版本检查。
     */
    public void checkAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VERSION_URL))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String body = response.body().trim();
                    // 期望返回纯版本号字符串，如 "1.1.0-beta"
                    if (!body.isEmpty() && !body.startsWith("{") && body.length() < 64) {
                        latestVersion = body;
                        if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                            updateMessage = "发现新版本: " + latestVersion + " (当前: " + currentVersion + ")";
                            logger.info("[VersionCheck] " + updateMessage);
                        } else {
                            logger.info("[VersionCheck] 当前已是最新版本 (" + currentVersion + ")");
                        }
                    }
                }
            } catch (Exception e) {
                logger.fine("[VersionCheck] 版本检查失败: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (updateMessage == null) return;
        Player player = event.getPlayer();
        if (player.hasPermission("arcartxsuite.admin")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(PREFIX + ChatColor.YELLOW + updateMessage);
                }
            }, 60L); // 3秒后发送，避免刷屏
        }
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public boolean hasUpdate() {
        return updateMessage != null;
    }
}
