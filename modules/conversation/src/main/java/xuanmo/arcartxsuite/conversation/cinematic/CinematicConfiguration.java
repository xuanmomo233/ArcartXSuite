package xuanmo.arcartxsuite.conversation.cinematic;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 电影序列配置解析。
 * <p>
 * 从 ArcartXConversation.yml 的 {@code cinematics} 节读取序列定义。
 */
public final class CinematicConfiguration {

    private final Map<String, CinematicSequence> sequences = new LinkedHashMap<>();

    private CinematicConfiguration() {
    }

    public static CinematicConfiguration load(FileConfiguration config) {
        CinematicConfiguration instance = new CinematicConfiguration();
        // 支持 cinematic.cinematics 和顶层 cinematics 两种路径
        ConfigurationSection cinematicsSection = config.getConfigurationSection("cinematic.cinematics");
        if (cinematicsSection == null) {
            cinematicsSection = config.getConfigurationSection("cinematics");
        }
        if (cinematicsSection == null) {
            return instance;
        }
        for (String sequenceId : cinematicsSection.getKeys(false)) {
            ConfigurationSection seqSection = cinematicsSection.getConfigurationSection(sequenceId);
            if (seqSection == null) continue;
            try {
                CinematicSequence sequence = parseSequence(sequenceId, seqSection);
                instance.sequences.put(sequenceId, sequence);
            } catch (RuntimeException ignored) {
                // 跳过解析失败的序列
            }
        }
        return instance;
    }

    /**
     * 从指定目录加载所有 {@code .yml} 文件并合并其中的电影序列定义。
     * <p>
     * 每个文件可包含 {@code cinematic.cinematics} 或顶层 {@code cinematics} 节，
     * 所有文件中的序列会被合并到同一个 {@link CinematicConfiguration} 实例中。
     * 文件按文件名排序加载，后加载的文件中同 ID 的序列会覆盖先前的。
     *
     * @param dir 存放电影序列配置的目录，不存在或非目录时返回空配置
     * @return 合并后的配置实例
     */
    public static CinematicConfiguration loadFromDirectory(File dir) {
        CinematicConfiguration instance = new CinematicConfiguration();
        if (dir == null || !dir.isDirectory()) {
            return instance;
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            return instance;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection cinematicsSection = config.getConfigurationSection("cinematic.cinematics");
                if (cinematicsSection == null) {
                    cinematicsSection = config.getConfigurationSection("cinematics");
                }
                if (cinematicsSection == null) continue;
                for (String sequenceId : cinematicsSection.getKeys(false)) {
                    ConfigurationSection seqSection = cinematicsSection.getConfigurationSection(sequenceId);
                    if (seqSection == null) continue;
                    try {
                        CinematicSequence sequence = parseSequence(sequenceId, seqSection);
                        instance.sequences.put(sequenceId, sequence);
                    } catch (RuntimeException ignored) {
                        // 跳过解析失败的序列
                    }
                }
            } catch (RuntimeException ignored) {
                // 跳过无法加载的文件
            }
        }
        return instance;
    }

    public CinematicSequence getSequence(String id) {
        return sequences.get(id);
    }

    public Map<String, CinematicSequence> sequences() {
        return Map.copyOf(sequences);
    }

    public boolean isEmpty() {
        return sequences.isEmpty();
    }

    private static CinematicSequence parseSequence(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        List<CinematicAction> actions = new ArrayList<>();
        List<?> actionList = section.getList("actions");
        if (actionList != null) {
            for (Object item : actionList) {
                if (item instanceof Map<?, ?> rawMap) {
                    CinematicAction action = parseAction(rawMap);
                    if (action != null) {
                        actions.add(action);
                    }
                }
            }
        }
        return new CinematicSequence(id, name, List.copyOf(actions));
    }

    @SuppressWarnings("unchecked")
    private static CinematicAction parseAction(Map<?, ?> raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        String type = stringLower(map.get("type"));
        long delayAfter = readLong(map.get("delay-after-ms"), 0L);

        return switch (type) {
            case "teleport" -> parseTeleport(map, delayAfter);
            case "camera_path" -> parseCameraPath(map, delayAfter);
            case "title" -> parseTitle(map, delayAfter);
            case "action_bar" -> parseActionBar(map, delayAfter);
            case "wait" -> new CinematicActions.WaitAction(delayAfter);
            case "sound" -> parseSound(map, delayAfter);
            case "game_mode" -> parseGameMode(map, delayAfter);
            case "npc_animation" -> parseNpcAnimation(map, delayAfter);
            case "npc_model" -> parseNpcModel(map, delayAfter);
            case "message" -> new CinematicActions.MessageAction(delayAfter, string(map.get("message")));
            case "command" -> new CinematicActions.CommandAction(delayAfter,
                string(map.get("command")), bool(map.get("as-console"), false));
            case "speed" -> new CinematicActions.SpeedAction(delayAfter,
                readFloat(map.get("walk-speed")), readFloat(map.get("fly-speed")));
            case "lock_movement" -> new CinematicActions.LockMovementAction(delayAfter,
                bool(map.get("locked"), true));
            case "subtitle" -> new CinematicActions.SubtitleAction(delayAfter,
                string(map.get("group-id")));
            case "ui_packet" -> parseUiPacket(map, delayAfter);
            case "arcartx_sound" -> parseArcartXSound(map, delayAfter);
            default -> null;
        };
    }

    private static CinematicActions.TeleportAction parseTeleport(Map<String, Object> map, long delay) {
        String world = string(map.getOrDefault("world", "world"));
        double x = readDouble(map.get("x"), 0);
        double y = readDouble(map.get("y"), 0);
        double z = readDouble(map.get("z"), 0);
        Float yaw = readFloat(map.get("yaw"));
        Float pitch = readFloat(map.get("pitch"));
        return new CinematicActions.TeleportAction(delay, world, x, y, z, yaw, pitch);
    }

    private static CinematicActions.CameraPathAction parseCameraPath(Map<String, Object> map, long delay) {
        String world = string(map.getOrDefault("world", "world"));
        long duration = readLong(map.get("duration-ms"), 3000L);
        int ticksPerFrame = (int) readLong(map.get("ticks-per-frame"), 2L);
        List<double[]> points = new ArrayList<>();
        Object rawPoints = map.get("points");
        if (rawPoints instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> pointMap) {
                    double[] arr = new double[5];
                    arr[0] = readDouble(pointMap.get("x"), 0);
                    arr[1] = readDouble(pointMap.get("y"), 0);
                    arr[2] = readDouble(pointMap.get("z"), 0);
                    arr[3] = readDouble(pointMap.get("yaw"), 0);
                    arr[4] = readDouble(pointMap.get("pitch"), 0);
                    points.add(arr);
                }
            }
        }
        return new CinematicActions.CameraPathAction(delay, world, List.copyOf(points), duration, ticksPerFrame);
    }

    private static CinematicActions.TitleAction parseTitle(Map<String, Object> map, long delay) {
        String title = string(map.get("title"));
        String subtitle = string(map.get("subtitle"));
        int fadeIn = (int) readLong(map.get("fade-in"), 10);
        int stay = (int) readLong(map.get("stay"), 60);
        int fadeOut = (int) readLong(map.get("fade-out"), 10);
        return new CinematicActions.TitleAction(delay, title, subtitle, fadeIn, stay, fadeOut);
    }

    private static CinematicActions.ActionBarAction parseActionBar(Map<String, Object> map, long delay) {
        return new CinematicActions.ActionBarAction(delay, string(map.get("message")));
    }

    private static CinematicActions.SoundAction parseSound(Map<String, Object> map, long delay) {
        String sound = string(map.get("sound"));
        String world = string(map.getOrDefault("world", "world"));
        double x = readDouble(map.get("x"), 0);
        double y = readDouble(map.get("y"), 0);
        double z = readDouble(map.get("z"), 0);
        float volume = readFloatOrDefault(map.get("volume"), 1.0f);
        float pitch = readFloatOrDefault(map.get("pitch"), 1.0f);
        boolean atPlayer = bool(map.get("at-player"), true);
        return new CinematicActions.SoundAction(delay, sound, world, x, y, z, volume, pitch, atPlayer);
    }

    private static CinematicActions.GameModeAction parseGameMode(Map<String, Object> map, long delay) {
        String modeStr = stringLower(map.get("mode"));
        GameMode mode;
        try {
            mode = GameMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = GameMode.SPECTATOR;
        }
        return new CinematicActions.GameModeAction(delay, mode);
    }

    private static CinematicActions.NpcAnimationAction parseNpcAnimation(Map<String, Object> map, long delay) {
        return new CinematicActions.NpcAnimationAction(delay,
            string(map.get("npc")),
            string(map.get("animation")),
            readDouble(map.get("speed"), 1.0),
            (int) readLong(map.get("transition-time"), 5),
            readLong(map.get("keep-time"), -1L));
    }

    private static CinematicActions.NpcModelAction parseNpcModel(Map<String, Object> map, long delay) {
        return new CinematicActions.NpcModelAction(delay,
            string(map.get("npc")),
            string(map.get("model")),
            readDouble(map.get("scale"), 1.0));
    }

    private static CinematicActions.UiPacketAction parseUiPacket(Map<String, Object> map, long delay) {
        String uiId = string(map.get("ui-id"));
        String handler = string(map.get("handler"));
        if (uiId.isBlank() || handler.isBlank()) return null;
        Map<String, Object> payload = new LinkedHashMap<>();
        Object rawPayload = map.get("payload");
        if (rawPayload instanceof Map<?, ?> payloadMap) {
            for (Map.Entry<?, ?> entry : payloadMap.entrySet()) {
                payload.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return new CinematicActions.UiPacketAction(delay, uiId, handler, payload);
    }

    private static CinematicActions.ArcartXSoundAction parseArcartXSound(Map<String, Object> map, long delay) {
        String resourcePath = string(map.get("resource-path"));
        if (resourcePath.isBlank()) return null;
        String category = string(map.getOrDefault("category", "master"));
        String world = string(map.getOrDefault("world", "world"));
        double x = readDouble(map.get("x"), 0);
        double y = readDouble(map.get("y"), 0);
        double z = readDouble(map.get("z"), 0);
        int distOrRoll = (int) readLong(map.get("dist-or-roll"), 16);
        double pitch = readDouble(map.get("pitch"), 1.0);
        int keepTimeMs = (int) readLong(map.get("keep-time-ms"), 5000);
        boolean atPlayer = bool(map.get("at-player"), true);
        return new CinematicActions.ArcartXSoundAction(delay, resourcePath, category,
            world, x, y, z, distOrRoll, pitch, keepTimeMs, atPlayer);
    }

    // ─── 工具方法 ──────────────────────────────────────────

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringLower(Object value) {
        return string(value).toLowerCase(Locale.ROOT);
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    private static long readLong(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static double readDouble(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static Float readFloat(Object value) {
        if (value instanceof Number n) return n.floatValue();
        if (value instanceof String s) {
            try { return Float.parseFloat(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static float readFloatOrDefault(Object value, float fallback) {
        Float f = readFloat(value);
        return f == null ? fallback : f;
    }
}
