package xuanmo.arcartxsuite.auth.runtime;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.List;
import org.bukkit.Bukkit;

/** Evidence from the server process. An offline profile is never an identity proof. */
record LoginTransport(boolean online, String bindAddress, boolean forwarding, List<String> jvmArguments) {
    static LoginTransport current() {
        ClassLoader server = Bukkit.getServer().getClass().getClassLoader();
        return new LoginTransport(Bukkit.getOnlineMode(), Bukkit.getIp(), forwarding(server),
            ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    boolean localPasswordAvailable() {
        return !online && !forwarding && isLoopbackBind(bindAddress);
    }

    boolean directOnlineAvailable() { return online && !forwarding; }

    static boolean localPeer(InetSocketAddress address) {
        return address != null && address.getAddress() != null && address.getAddress().isLoopbackAddress();
    }

    private static boolean isLoopbackBind(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address);
    }

    private static boolean forwarding(ClassLoader server) {
        try {
            Class<?> spigot = Class.forName("org.spigotmc.SpigotConfig", false, server);
            if (spigot.getField("bungee").getBoolean(null)) return true;
            Class<?> paper;
            try {
                paper = Class.forName("io.papermc.paper.configuration.GlobalConfiguration", false, server);
            } catch (ClassNotFoundException spigotOnly) {
                return false;
            }
            Object config = paper.getMethod("get").invoke(null);
            Object proxies = paper.getField("proxies").get(config);
            Object velocity = proxies.getClass().getField("velocity").get(proxies);
            return velocity.getClass().getField("enabled").getBoolean(velocity);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new IllegalStateException("无法确认服务器直连转发设置", failure);
        }
    }
}
