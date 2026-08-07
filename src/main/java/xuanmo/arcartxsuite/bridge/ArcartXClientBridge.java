package xuanmo.arcartxsuite.bridge;

import java.util.function.Consumer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.api.ArcartXAPI;
import priv.seventeen.artist.arcartx.core.network.ArcartXNetworkSender;
import priv.seventeen.artist.arcartx.util.EntityUtils;
import priv.seventeen.artist.arcartx.util.collections.PlayerCallBack;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;

/**
 * {@link ClientBridgeAPI} 的默认实现，桥接 ArcartX 客户端 MOD 的网络发送器，
 * 提供向客户端推送数据包的能力。
 * <p>
 * ArcartX 为必需依赖，但运行时仍通过 {@link #initialize()} 检测可用性以兜底。
 */
public class ArcartXClientBridge implements ClientBridgeAPI {

    private final JavaPlugin plugin;
    private boolean available;
    private ArcartXNetworkSender networkSender;

    public ArcartXClientBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        available = false;
        networkSender = null;

        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return false;
        }

        try {
            networkSender = ArcartXAPI.getNetworkSender();
            if (networkSender == null) {
                throw new IllegalStateException("ArcartXNetworkSender 不可用。");
            }
            available = true;
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("初始化 ArcartX 客户端桥接失败: " + exception.getMessage());
            return false;
        }
    }

    public void shutdown() {
        available = false;
        networkSender = null;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean sendDamageDisplay(Player player, String configId, double amount, Entity target) {
        if (!available || player == null || configId == null || configId.isBlank() || networkSender == null) {
            return false;
        }
        try {
            networkSender.sendDamageDisplay(player, configId, amount, target);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX 伤害数字发送失败: " + exception.getMessage());
            return false;
        }
    }

    public boolean sendServerVariable(Player player, String variableName, Object value) {
        if (!available || player == null || variableName == null || variableName.isBlank() || networkSender == null) {
            return false;
        }
        try {
            networkSender.sendServerVariable(player, variableName, value);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX 服务器变量发送失败: " + exception.getMessage());
            return false;
        }
    }

    public boolean forEachSeenPlayer(Entity entity, Consumer<Player> consumer) {
        if (!available || entity == null || consumer == null) {
            return false;
        }
        try {
            PlayerCallBack callback = consumer::accept;
            EntityUtils.doWithSeenBy(entity, callback);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX 可视玩家遍历失败: " + exception.getMessage());
            return false;
        }
    }
}
