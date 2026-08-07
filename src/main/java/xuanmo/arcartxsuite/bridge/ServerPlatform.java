package xuanmo.arcartxsuite.bridge;

import java.util.Locale;
import org.bukkit.Server;

/**
 * 服务端平台描述记录，标识当前服务端是否为混合端（Mohist/Arclight 等）。
 *
 * @param displayName   平台展示名（如 "Purpur"、"Mohist"）
 * @param hybridServer  是否为混合端（Forge+Bukkit），影响序列化与反射兼容策略
 */
public record ServerPlatform(String displayName, boolean hybridServer) {

    /**
     * 探测当前服务端平台类型。
     *
     * @param server Bukkit 服务端实例
     * @return 平台描述
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
