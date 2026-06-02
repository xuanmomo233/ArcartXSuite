package xuanmo.arcartxsuite.license;

import com.google.gson.Gson;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.plugin.java.JavaPlugin;

public final class SecureClock {

    private static final long ROLLBACK_TOLERANCE_MS = 5 * 60 * 1000L;

    private final Gson gson = new Gson();
    private final File file;
    private State state;

    public SecureClock(JavaPlugin plugin) {
        File securityDir = new File(plugin.getDataFolder(), "security");
        if (!securityDir.exists()) {
            securityDir.mkdirs();
        }
        this.file = new File(securityDir, "secure-clock.dat");
        this.state = load();
        detectRollback();
        save();
    }

    public long now() {
        long wall = System.currentTimeMillis();
        if (state.lastTrustedServerTime > 0 && wall < state.lastTrustedServerTime - ROLLBACK_TOLERANCE_MS) {
            return state.lastTrustedServerTime;
        }
        return wall;
    }

    public boolean rollbackDetected() {
        return state.rollbackDetected;
    }

    public long lastTrustedServerTime() {
        return state.lastTrustedServerTime;
    }

    public void trustServerTime(long serverTime) {
        long wall = System.currentTimeMillis();
        state.lastTrustedServerTime = Math.max(state.lastTrustedServerTime, serverTime);
        state.lastObservedWallTime = Math.max(state.lastObservedWallTime, wall);
        state.updatedAt = wall;
        save();
    }

    public TimeState timeState() {
        return new TimeState(
            state.lastTrustedServerTime > 0 ? "WORKER_SIGNED" : "LOCAL_ESTIMATED",
            state.rollbackDetected,
            state.lastTrustedServerTime
        );
    }

    private void detectRollback() {
        long wall = System.currentTimeMillis();
        if (state.lastObservedWallTime > 0 && wall + ROLLBACK_TOLERANCE_MS < state.lastObservedWallTime) {
            state.rollbackDetected = true;
        }
        if (state.lastTrustedServerTime > 0 && wall + ROLLBACK_TOLERANCE_MS < state.lastTrustedServerTime) {
            state.rollbackDetected = true;
        }
        state.bootCounter++;
        state.lastObservedWallTime = Math.max(state.lastObservedWallTime, wall);
        state.updatedAt = wall;
    }

    private State load() {
        try {
            if (file.exists()) {
                State loaded = gson.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), State.class);
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (Exception ignored) {
        }
        return new State();
    }

    private void save() {
        try {
            Files.writeString(file.toPath(), gson.toJson(state), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    static final class State {
        long lastTrustedServerTime;
        long lastObservedWallTime;
        long lastPluginShutdownTime;
        boolean rollbackDetected;
        long bootCounter;
        long updatedAt;
    }

    public record TimeState(String trustLevel, boolean rollbackSuspected, long lastTrustedServerTime) {
    }
}
