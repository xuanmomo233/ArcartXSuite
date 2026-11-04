package xuanmo.arcartxsuite.conversation.cinematic;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 电影序列配置解析。
 * <p>
 * 从场景文件读取并校验电影时间轴。
 */
public final class CinematicConfiguration {

    private final Map<String, CinematicSequence> sequences = new LinkedHashMap<>();

    private CinematicConfiguration() {
    }

    /**
     * 从指定目录加载所有 {@code .yml} 文件并合并其中的电影序列定义。
     * <p>
     * 每个文件可包含 {@code cinematic.cinematics} 或顶层 {@code cinematics} 节，
     * 所有文件中的序列会被合并到同一个 {@link CinematicConfiguration} 实例中。
     * 文件按文件名排序加载，同 ID 的后续序列会被拒绝。
     *
     * @param dir 存放电影序列配置的目录，不存在或非目录时返回空配置
     * @return 合并后的配置实例
     */
    public static CinematicConfiguration loadFromDirectory(File dir, Logger logger) {
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
                FileConfiguration config = new YamlConfiguration();
                config.load(file);
                ConfigurationSection cinematicsSection = config.getConfigurationSection("cinematic.cinematics");
                if (cinematicsSection == null) {
                    cinematicsSection = config.getConfigurationSection("cinematics");
                }
                if (cinematicsSection == null) continue;
                for (String sequenceId : cinematicsSection.getKeys(false)) {
                    ConfigurationSection seqSection = cinematicsSection.getConfigurationSection(sequenceId);
                    try {
                        if (seqSection == null) throw new IllegalArgumentException("序列必须是配置节");
                        CinematicSequence sequence = parseSequence(sequenceId, seqSection);
                        if (instance.sequences.putIfAbsent(sequenceId, sequence) != null) {
                            throw new IllegalArgumentException("序列 ID 重复");
                        }
                    } catch (RuntimeException exception) {
                        logger.warning(file.getName() + " / " + sequenceId + ": " + exception.getMessage());
                    }
                }
            } catch (Exception exception) {
                logger.warning(file.getName() + ": " + exception.getMessage());
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
        long duration = readTimelineTick(section.get("duration-ms"), "duration-ms");
        if (duration == 0) throw new IllegalArgumentException("序列时长必须大于 0");
        List<?> timeline = section.getList("timeline");
        if (timeline == null || timeline.isEmpty()) throw new IllegalArgumentException("timeline 必须是非空列表");
        List<CinematicSequence.Cue> cues = new ArrayList<>();
        for (int index = 0; index < timeline.size(); index++) {
            try {
                if (!(timeline.get(index) instanceof Map<?, ?> raw)) throw new IllegalArgumentException("动作必须是映射");
                long tick = readTimelineTick(raw.get("at-ms"), "at-ms");
                CinematicAction action = parseAction(raw);
                if (action == null) throw new IllegalArgumentException("动作未知或缺少必填字段: " + raw.get("type"));
                long length = action instanceof CinematicActions.CameraPathAction camera ? camera.durationTicks()
                    : action instanceof CinematicActorMove.Action movement ? movement.move.durationTicks() : 0;
                if (tick + length > duration) throw new IllegalArgumentException("动作超出序列时长");
                cues.add(new CinematicSequence.Cue(tick, action));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("timeline[" + (index + 1) + "]: " + exception.getMessage(), exception);
            }
        }
        cues.sort(Comparator.comparingLong(CinematicSequence.Cue::tick));
        var placed = new java.util.HashSet<String>();
        for (var cue : cues) {
            if (cue.action() instanceof CinematicActorCue actor) {
                if (actor.placesActor()) placed.add(actor.actor());
                else if (!placed.contains(actor.actor())) throw new IllegalArgumentException("动画前未放置演员: " + actor.actor());
            } else if (cue.action() instanceof CinematicActorMove.Action movement && !placed.contains(movement.move.actor())) {
                throw new IllegalArgumentException("移动前未放置演员: " + movement.move.actor());
            }
        }
        return new CinematicSequence(id, name, duration, cues);
    }

    static long readTimelineTick(Object raw, String field) {
        if (!(raw instanceof Number number) || number.doubleValue() != number.longValue()
            || number.longValue() < 0 || number.longValue() > 3_600_000 || number.longValue() % 50 != 0) {
            throw new IllegalArgumentException(field + " 必须是 0 到 3600000 之间的 50 毫秒整数倍");
        }
        return number.longValue() / 50;
    }

    @SuppressWarnings("unchecked")
    private static CinematicAction parseAction(Map<?, ?> raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        String type = stringLower(map.get("type"));

        return switch (type) {
            case "teleport" -> parseTeleport(map);
            case "camera_path" -> parseCameraPath(map);
            case "title" -> parseTitle(map);
            case "action_bar" -> parseActionBar(map);
            case "sound" -> parseSound(map);
            case "game_mode" -> parseGameMode(map);
            case "npc_animation" -> parseNpcAnimation(map);
            case "npc_model" -> parseNpcModel(map);
            case "actor_place" -> new CinematicActorCue(requiredText(map, "actor"), requiredText(map, "world"),
                coordinate(map, "x"), coordinate(map, "y"), coordinate(map, "z"),
                readFloatOrDefault(map.get("yaw"), 0), readFloatOrDefault(map.get("pitch"), 0),
                string(map.get("model")), "");
            case "actor_animation" -> new CinematicActorCue(requiredText(map, "actor"), "", 0, 0, 0, 0, 0,
                "", requiredText(map, "animation"));
            case "actor_move" -> parseActorMove(map);
            case "message" -> new CinematicActions.MessageAction(string(map.get("message")));
            case "command" -> new CinematicActions.CommandAction(
                string(map.get("command")), bool(map.get("as-console"), false));
            case "speed" -> new CinematicActions.SpeedAction(
                readFloat(map.get("walk-speed")), readFloat(map.get("fly-speed")));
            case "lock_movement" -> new CinematicActions.LockMovementAction(
                bool(map.get("locked"), true));
            case "subtitle" -> new CinematicActions.SubtitleAction(
                string(map.get("group-id")));
            case "ui_packet" -> parseUiPacket(map);
            case "arcartx_sound" -> parseArcartXSound(map);
            default -> null;
        };
    }

    private static CinematicActions.TeleportAction parseTeleport(Map<String, Object> map) {
        String world = requiredText(map, "world");
        double x = coordinate(map, "x");
        double y = coordinate(map, "y");
        double z = coordinate(map, "z");
        Float yaw = readFloat(map.get("yaw"));
        Float pitch = readFloat(map.get("pitch"));
        return new CinematicActions.TeleportAction(world, x, y, z, yaw, pitch);
    }

    private static CinematicActions.CameraPathAction parseCameraPath(Map<String, Object> map) {
        String world = requiredText(map, "world");
        long duration = readTimelineTick(map.get("duration-ms"), "镜头 duration-ms") * 50;
        if (duration == 0) throw new IllegalArgumentException("镜头时长必须大于 0");
        List<double[]> points = new ArrayList<>();
        Object rawPoints = map.get("points");
        if (rawPoints instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> pointMap) {
                    double[] arr = new double[5];
                    arr[0] = coordinate(pointMap, "x");
                    arr[1] = coordinate(pointMap, "y");
                    arr[2] = coordinate(pointMap, "z");
                    arr[3] = pointMap.containsKey("yaw") ? coordinate(pointMap, "yaw") : 0;
                    arr[4] = pointMap.containsKey("pitch") ? coordinate(pointMap, "pitch") : 0;
                    points.add(arr);
                } else throw new IllegalArgumentException("取景点必须是映射");
            }
        }
        if (points.size() < 2) throw new IllegalArgumentException("镜头至少需要两个取景点");
        return new CinematicActions.CameraPathAction(world, List.copyOf(points), duration);
    }

    private static String requiredText(Map<?, ?> map, String key) {
        String value = string(map.get(key));
        if (value.isBlank()) throw new IllegalArgumentException("缺少 " + key);
        return value;
    }

    private static CinematicActorMove.Action parseActorMove(Map<?, ?> map) {
        long duration = readTimelineTick(map.get("duration-ms"), "演员移动 duration-ms");
        if (duration == 0) throw new IllegalArgumentException("演员移动时长必须大于 0");
        return new CinematicActorMove.Action(new CinematicActorMove(
            requiredText(map, "actor"), requiredText(map, "world"), readMark(map.get("from")), readMark(map.get("to")), duration
        ));
    }

    private static CinematicActorMove.Mark readMark(Object value) {
        if (!(value instanceof Map<?, ?> mark)) throw new IllegalArgumentException("from / to 必须是坐标映射");
        return new CinematicActorMove.Mark(coordinate(mark, "x"), coordinate(mark, "y"), coordinate(mark, "z"),
            mark.containsKey("yaw") ? coordinate(mark, "yaw") : 0,
            mark.containsKey("pitch") ? coordinate(mark, "pitch") : 0);
    }

    private static double coordinate(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(key + " 必须是有限数字");
        }
        return number.doubleValue();
    }

    private static CinematicActions.TitleAction parseTitle(Map<String, Object> map) {
        String title = string(map.get("title"));
        String subtitle = string(map.get("subtitle"));
        int fadeIn = (int) readLong(map.get("fade-in"), 10);
        int stay = (int) readLong(map.get("stay"), 60);
        int fadeOut = (int) readLong(map.get("fade-out"), 10);
        return new CinematicActions.TitleAction(title, subtitle, fadeIn, stay, fadeOut);
    }

    private static CinematicActions.ActionBarAction parseActionBar(Map<String, Object> map) {
        return new CinematicActions.ActionBarAction(string(map.get("message")));
    }

    private static CinematicActions.SoundAction parseSound(Map<String, Object> map) {
        String sound = string(map.get("sound"));
        String world = string(map.getOrDefault("world", "world"));
        double x = readDouble(map.get("x"), 0);
        double y = readDouble(map.get("y"), 0);
        double z = readDouble(map.get("z"), 0);
        float volume = readFloatOrDefault(map.get("volume"), 1.0f);
        float pitch = readFloatOrDefault(map.get("pitch"), 1.0f);
        boolean atPlayer = bool(map.get("at-player"), true);
        return new CinematicActions.SoundAction(sound, world, x, y, z, volume, pitch, atPlayer);
    }

    private static CinematicActions.GameModeAction parseGameMode(Map<String, Object> map) {
        String modeStr = stringLower(map.get("mode"));
        GameMode mode;
        try {
            mode = GameMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知游戏模式: " + modeStr);
        }
        return new CinematicActions.GameModeAction(mode);
    }

    private static CinematicActions.NpcAnimationAction parseNpcAnimation(Map<String, Object> map) {
        return new CinematicActions.NpcAnimationAction(
            string(map.get("npc")),
            string(map.get("animation")),
            readDouble(map.get("speed"), 1.0),
            (int) readLong(map.get("transition-time"), 5),
            readLong(map.get("keep-time"), -1L));
    }

    private static CinematicActions.NpcModelAction parseNpcModel(Map<String, Object> map) {
        return new CinematicActions.NpcModelAction(
            string(map.get("npc")),
            string(map.get("model")),
            readDouble(map.get("scale"), 1.0));
    }

    private static CinematicActions.UiPacketAction parseUiPacket(Map<String, Object> map) {
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
        return new CinematicActions.UiPacketAction(uiId, handler, payload);
    }

    private static CinematicActions.ArcartXSoundAction parseArcartXSound(Map<String, Object> map) {
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
        return new CinematicActions.ArcartXSoundAction(resourcePath, category,
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
