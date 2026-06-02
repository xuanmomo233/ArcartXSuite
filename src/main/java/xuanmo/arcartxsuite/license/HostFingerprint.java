package xuanmo.arcartxsuite.license;

import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class HostFingerprint {

    private final JavaPlugin plugin;
    private final File securityDir;

    public HostFingerprint(JavaPlugin plugin) {
        this.plugin = plugin;
        this.securityDir = new File(plugin.getDataFolder(), "security");
    }

    public Snapshot snapshot() {
        String salt = localSalt();
        List<Component> components = new ArrayList<>();
        components.add(new Component("local_salt", sha256Hex(salt), 30));
        components.add(new Component("server_port", sha256Hex(String.valueOf(Bukkit.getPort())), 10));
        components.add(new Component("data_folder", sha256Hex(plugin.getDataFolder().getAbsolutePath()), 20));
        components.add(new Component("java_runtime", sha256Hex(System.getProperty("java.vendor", "") + "|" + System.getProperty("java.version", "")), 10));
        components.add(new Component("os_arch", sha256Hex(System.getProperty("os.name", "") + "|" + System.getProperty("os.arch", "")), 10));
        components.add(new Component("network", sha256Hex(firstHardwareAddress()), 20));
        return new Snapshot("sha256:" + sha256Hex(components.toString()), "sha256:" + sha256Hex(salt), components);
    }

    private String localSalt() {
        if (!securityDir.exists()) {
            securityDir.mkdirs();
        }
        File saltFile = new File(securityDir, "local-salt.dat");
        try {
            if (saltFile.exists()) {
                return Files.readString(saltFile.toPath(), StandardCharsets.UTF_8).trim();
            }
            byte[] salt = new byte[32];
            new SecureRandom().nextBytes(salt);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
            Files.writeString(saltFile.toPath(), encoded, StandardCharsets.UTF_8);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取或创建 local-salt.dat", exception);
        }
    }

    private String firstHardwareAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                byte[] address = network.getHardwareAddress();
                if (address != null && address.length > 0 && !network.isLoopback()) {
                    return HexFormat.of().formatHex(address);
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public record Snapshot(String hash, String localSaltHash, List<Component> components) {
    }

    public record Component(String name, String hash, int weight) {
    }
}
