package xuanmo.arcartxsuite.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.logging.Logger;

/**
 * Paper 1.21+ 安全聊天签名绕过服务。
 * <p>
 * Paper 1.21 引入 secure-chat validation，要求所有聊天消息携带 Mojang 签名。
 * LittleSkin / 离线玩家没有正版签名，聊天会被拦截（客户端提示 "Chat message validation failure"）。
 * <p>
 * 本服务在玩家加入时，通过反射清除 NMS 层中与聊天签名验证相关的字段，
 * 使 Paper 跳过对该玩家的签名校验，从而允许混合登录玩家正常发送聊天消息。
 */
public final class ChatSignBypassService implements Listener {

    private static final String[] SERVER_PLAYER_FIELDS = {
        "chatSession",
        "remoteChatSession",
        "signedMessageDecoder",
    };
    private static final String[] CONNECTION_FIELDS = {
        "chatDecorator",
        "signedMessageDecoder",
        "chatMessageValidator",
    };

    private final JavaPlugin plugin;
    private final Logger logger;
    private final boolean enabled;
    private boolean paperDetected;
    private volatile boolean reflectionSuccessLogged;

    public ChatSignBypassService(JavaPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = enabled;
    }

    public void initialize() {
        if (!enabled) {
            logger.fine("聊天签名绕过已关闭（混合登录/离线玩家可能在 Paper 1.21+ 无法发送聊天消息）。");
            return;
        }
        detectPaper();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // 对当前在线玩家立即生效（热重载场景）
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyBypass(player);
        }
        logger.info("聊天签名绕过服务已启用（解决 LittleSkin / 离线玩家 Paper 1.21+ 聊天失败）。");
    }

    public void shutdown() {
        // Listener 由 Bukkit 自动注销；无需额外清理。
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (enabled) {
            applyBypass(event.getPlayer());
        }
    }

    private void detectPaper() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            paperDetected = true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("io.papermc.paper.configuration.Configuration");
                paperDetected = true;
            } catch (ClassNotFoundException e2) {
                paperDetected = false;
            }
        }
        if (!paperDetected) {
            logger.warning("当前不是 Paper 服务端，聊天签名绕过可能无效。");
        }
    }

    private void applyBypass(Player player) {
        if (!paperDetected) {
            return;
        }
        try {
            // CraftPlayer -> getHandle() -> ServerPlayer (NMS)
            Object handle = player.getClass().getMethod("getHandle").invoke(player);

            // 1. 尝试清除 ServerPlayer 上的聊天会话字段
            boolean playerFieldCleared = false;
            for (String fieldName : SERVER_PLAYER_FIELDS) {
                if (trySetField(handle, fieldName, null)) {
                    playerFieldCleared = true;
                    break;
                }
            }

            // 2. 尝试清除 ServerGamePacketListenerImpl 上的签名字段
            // ServerPlayer.connection -> ServerGamePacketListenerImpl
            Object connection = handle.getClass().getField("connection").get(handle);
            boolean connectionFieldCleared = false;
            for (String fieldName : CONNECTION_FIELDS) {
                if (trySetField(connection, fieldName, null)) {
                    connectionFieldCleared = true;
                    break;
                }
            }

            if ((playerFieldCleared || connectionFieldCleared) && !reflectionSuccessLogged) {
                reflectionSuccessLogged = true;
                logger.fine("已绕过 " + player.getName() + " 的聊天签名验证（后续同类型日志将抑制）。");
            }
        } catch (Exception e) {
            logger.warning("聊天签名绕过反射失败: " + e.getMessage());
        }
    }

    private boolean trySetField(Object target, String fieldName, Object value) {
        try {
            Field field = findFieldInHierarchy(target.getClass(), fieldName);
            if (field == null) {
                return false;
            }
            field.setAccessible(true);
            field.set(target, value);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
