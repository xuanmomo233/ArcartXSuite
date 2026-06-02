package xuanmo.arcartxsuite.combateffect.deathbuffer;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public record DeathBufferConfiguration(
    boolean enabled,
    long durationMs,
    String shader,
    boolean thirdPersonCamera,
    String cameraPreset,
    boolean chronosEnabled,
    String chronosStateId,
    boolean blockAutoRespawn,
    Set<String> worldBlacklist,
    boolean debug
) {

    public static DeathBufferConfiguration load(ConfigurationSection section) {
        if (section == null) {
            return new DeathBufferConfiguration(
                false, 3000L, "", false, "", false, "", false, Set.of(), false
            );
        }
        boolean enabled = section.getBoolean("enabled", false);
        long durationMs = Math.max(500L, section.getLong("duration", 3000L));
        String shader = section.getString("visuals.shader", "");
        boolean thirdPersonCamera = section.getBoolean("visuals.third-person-camera", true);
        String cameraPreset = section.getString("visuals.camera-preset", "");
        boolean chronosEnabled = section.getBoolean("visuals.chronos.enabled", false);
        String chronosStateId = section.getString("visuals.chronos.state-id", "死亡");
        boolean blockAutoRespawn = section.getBoolean("block-auto-respawn", true);
        boolean debug = section.getBoolean("debug", false);
        Set<String> worldBlacklist = new HashSet<>(section.getStringList("world-blacklist"));
        return new DeathBufferConfiguration(
            enabled, durationMs, shader, thirdPersonCamera, cameraPreset,
            chronosEnabled, chronosStateId, blockAutoRespawn, worldBlacklist, debug
        );
    }

    public int durationTicks() {
        return (int) Math.max(10, durationMs / 50);
    }
}
