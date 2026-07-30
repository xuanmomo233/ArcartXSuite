package xuanmo.arcartxsuite.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NMS 临时 OP 执行器。
 * <p>
 * 参考 Asteroid 的 FakeOp 设计，通过反射临时将玩家加入 OP 列表 -> 执行命令 -> 移除 OP，
 * 不需要 ByteBuddy 动态代理，不存在多版本 CraftPlayer 子类问题。
 * <p>
 * 所有 NMS/authlib 类通过反射加载，编译时不需要依赖 NMS 包。
 * 反射仅在首次调用时执行一次并缓存，后续调用零反射开销。
 * 如果反射初始化失败（如不支持的版本或混合端），{@link #available()} 返回 false，
 * 调用方应降级到 setOp 方式。
 */
public final class AxsFakeOp {

    private final Logger logger;
    private volatile boolean available;
    private volatile boolean initialized;

    private Method getHandleMethod;
    private Method getServerMethod;
    private Method getPlayerListMethod;
    private Method getOpsMethod;
    private Method isOpMethod;
    private Method opsAddMethod;
    private Method deopMethod;
    private Method recalculatePermissionsMethod;
    private Method getGameProfileMethod;
    private Class<?> opListEntryClass;
    private Class<?> gameProfileClass;

    public AxsFakeOp(Logger logger) {
        this.logger = logger;
    }

    private void tryInitialize() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            try {
                String craftPlayerClassName = getCraftPlayerClassName();
                Class<?> craftPlayerClass = Class.forName(craftPlayerClassName);
                getHandleMethod = craftPlayerClass.getMethod("getHandle");

                Class<?> serverPlayerClass = getHandleMethod.getReturnType();
                getServerMethod = serverPlayerClass.getMethod("getServer");

                Class<?> serverClass = getServerMethod.getReturnType();
                getPlayerListMethod = serverClass.getMethod("getPlayerList");

                Class<?> playerListClass = getPlayerListMethod.getReturnType();
                getOpsMethod = playerListClass.getMethod("getOps");

                gameProfileClass = tryLoadClass(
                    "com.mojang.authlib.GameProfile",
                    "org.bukkit.profile.PlayerProfile"
                );
                if (gameProfileClass == null) {
                    throw new RuntimeException("无法加载 GameProfile 类");
                }

                isOpMethod = playerListClass.getMethod("isOp", gameProfileClass);
                deopMethod = playerListClass.getMethod("deop", gameProfileClass);

                opListEntryClass = tryLoadClass(
                    getNmsClassName("server.players.ServerOpListEntry"),
                    "net.minecraft.server.players.ServerOpListEntry"
                );
                if (opListEntryClass == null) {
                    throw new RuntimeException("无法加载 ServerOpListEntry 类");
                }

                Class<?> opListClass = getOpsMethod.getReturnType();
                opsAddMethod = opListClass.getMethod("add", opListEntryClass);

                getGameProfileMethod = serverPlayerClass.getMethod("getGameProfile");
                recalculatePermissionsMethod = Player.class.getMethod("recalculatePermissions");

                available = true;
                initialized = true;
                logger.info("AxsFakeOp: 已初始化（NMS 反射模式）。");
            } catch (Exception e) {
                available = false;
                initialized = true;
                logger.log(Level.WARNING, "AxsFakeOp 初始化失败，将降级到 setOp 方式: " + e.getMessage(), e);
            }
        }
    }

    public Boolean execute(Player player, String command) {
        if (!available()) {
            return null;
        }
        try {
            Object serverPlayer = getHandleMethod.invoke(player);
            Object server = getServerMethod.invoke(serverPlayer);
            Object playerList = getPlayerListMethod.invoke(server);
            Object gameProfile = getGameProfileMethod.invoke(serverPlayer);

            boolean wasOp = (boolean) isOpMethod.invoke(playerList, gameProfile);
            try {
                if (!wasOp) {
                    Object opEntry = opListEntryClass
                        .getConstructor(gameProfileClass, int.class, boolean.class)
                        .newInstance(gameProfile, 4, false);
                    opsAddMethod.invoke(getOpsMethod.invoke(playerList), opEntry);
                    recalculatePermissionsMethod.invoke(player);
                }
                return Bukkit.dispatchCommand(player, command);
            } finally {
                if (!wasOp) {
                    deopMethod.invoke(playerList, gameProfile);
                    recalculatePermissionsMethod.invoke(player);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "AxsFakeOp 执行失败: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean available() {
        if (!initialized) {
            tryInitialize();
        }
        return available;
    }

    private String getCraftPlayerClassName() {
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        if (pkg.contains(".v")) {
            return pkg + ".entity.CraftPlayer";
        }
        return "org.bukkit.craftbukkit.entity.CraftPlayer";
    }

    private String getNmsClassName(String simpleName) {
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        if (pkg.contains(".v")) {
            String version = pkg.substring(pkg.lastIndexOf('.') + 1);
            return "net.minecraft.server." + version + "." + simpleName;
        }
        return "net.minecraft." + simpleName;
    }

    private Class<?> tryLoadClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }
}
