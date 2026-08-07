package xuanmo.arcartxsuite.entitytracker.boss.platform;

import java.util.Locale;
import org.bukkit.Server;

/**
 * 服务端平台信息记录，属于 entitytracker 模块的平台检测子系统。
 * <p>
 * 通过服务端名称、版本和类名检测当前运行的服务端平台（Paper、Purpur、Mohist 等），
 * 并标记是否为混合服务端（Hybrid），用于适配不同平台的行为差异。
 *
 * @param displayName    平台显示名称
 * @param hybridServer   是否为混合服务端（如 Mohist、Arclight 等 Bukkit+Forge 混合体）
 */
public record ServerPlatform(String displayName, boolean hybridServer) {

    /**
     * 自动检测当前服务端平台。
     *
     * @param server Bukkit 服务端实例
     * @return 检测到的平台信息
     */
    public static ServerPlatform detect(Server server) {
        String name = safe(server.getName());
        String version = safe(server.getVersion());
        String className = safe(server.getClass().getName());
        String combined = (name + " " + version + " " + className).toLowerCase(Locale.ROOT);

        if (combined.contains("mohist")) {
            return new ServerPlatform("Mohist", true);
        }
        if (combined.contains("arclight")) {
            return new ServerPlatform("Arclight", true);
        }
        if (combined.contains("catserver")) {
            return new ServerPlatform("CatServer", true);
        }
        if (combined.contains("magma")) {
            return new ServerPlatform("Magma", true);
        }
        if (combined.contains("banner")) {
            return new ServerPlatform("Banner", true);
        }
        if (combined.contains("purpur")) {
            return new ServerPlatform("Purpur", false);
        }
        if (combined.contains("pufferfish")) {
            return new ServerPlatform("Pufferfish", false);
        }
        if (combined.contains("paper")) {
            return new ServerPlatform("Paper", false);
        }
        if (combined.contains("spigot")) {
            return new ServerPlatform("Spigot", false);
        }
        if (combined.contains("craftbukkit") || combined.contains("bukkit")) {
            return new ServerPlatform("Bukkit", false);
        }
        if (!name.isBlank()) {
            return new ServerPlatform(name, false);
        }
        return new ServerPlatform(server.getClass().getSimpleName(), false);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

