package xuanmo.arcartxsuite.combateffect.deathbuffer;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 死亡缓冲模块的配置记录。
 * <p>
 * 属于 combateffect 模块的 deathbuffer 子模块，负责定义玩家死亡后的
 * 缓冲时长、视觉特效（着色器、第三人称相机）、Chronos 状态切换、
 * 自动复活拦截及世界黑名单等参数。
 *
 * @param enabled             是否启用死亡缓冲
 * @param durationMs          缓冲持续时间（毫秒）
 * @param shader              死亡缓冲期间应用的着色器资源路径
 * @param thirdPersonCamera   是否切换到第三人称视角
 * @param cameraPreset        相机预设名称
 * @param chronosEnabled      是否启用 Chronos 状态切换
 * @param chronosStateId      Chronos 状态 ID
 * @param blockAutoRespawn    是否拦截自动复活
 * @param worldBlacklist      不生效的世界名称集合
 * @param debug               是否开启调试日志
 */
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

    /**
     * 从配置节点加载死亡缓冲配置。
     * <p>
     * 当配置节点为 {@code null} 时返回全部禁用的默认配置。
     *
     * @param section Bukkit 配置节点
     * @return 加载后的配置记录
     */
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

    /**
     * 将缓冲持续时间从毫秒换算为游戏刻（tick），最小为 10 tick。
     *
     * @return 持续时间（tick）
     */
    public int durationTicks() {
        return (int) Math.max(10, durationMs / 50);
    }
}
