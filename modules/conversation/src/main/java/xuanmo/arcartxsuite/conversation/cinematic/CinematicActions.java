package xuanmo.arcartxsuite.conversation.cinematic;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.SoundPlayerBridgeAPI;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;

/**
 * 电影序列中所有内置动作类型的集合。
 */
public final class CinematicActions {

    private CinematicActions() {
    }

    // ─── Teleport ──────────────────────────────────────────

    public static final class TeleportAction extends CinematicAction {
        private final String worldName;
        private final double x, y, z;
        private final Float yaw, pitch;

        public TeleportAction(String worldName, double x, double y, double z, Float yaw, Float pitch) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            var world = Bukkit.getWorld(worldName);
            if (world == null) throw new IllegalStateException("传送世界未加载: " + worldName);
            Location loc = new Location(world, x, y, z);
            if (yaw != null) loc.setYaw(yaw);
            if (pitch != null) loc.setPitch(pitch);
            if (!player.teleport(loc)) throw new IllegalStateException("场景传送被取消");
        }

        @Override
        public String type() {
            return "teleport";
        }
    }

    // ─── Camera Path (平滑插值传送) ───────────────────────

    public static final class CameraPathAction extends CinematicAction {
        private final String worldName;
        private final List<double[]> points;
        private final long durationMs;

        public CameraPathAction(String worldName, List<double[]> points, long durationMs) {
            this.worldName = worldName;
            this.points = points;
            this.durationMs = Math.max(50L, durationMs);
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            sample(player, 0);
        }

        public long durationTicks() {
            return durationMs / 50;
        }

        public void sample(Player player, long elapsedTicks) {
            var world = Bukkit.getWorld(worldName);
            if (world == null) throw new IllegalStateException("镜头世界未加载: " + worldName);
            double progress = Math.min(1.0, (double) elapsedTicks / durationTicks());
            double[] pos = interpolatePath(points, progress);
            if (!player.teleport(new Location(world, pos[0], pos[1], pos[2], (float) pos[3], (float) pos[4]))) {
                throw new IllegalStateException("镜头传送被取消");
            }
        }

        @Override
        public String type() {
            return "camera_path";
        }

        private static double[] interpolatePath(List<double[]> points, double progress) {
            if (points.size() == 1) return points.get(0);
            double segmentProgress = progress * (points.size() - 1);
            int segmentIndex = (int) Math.floor(segmentProgress);
            if (segmentIndex >= points.size() - 1) return points.get(points.size() - 1);
            double localT = segmentProgress - segmentIndex;
            double[] p0 = points.get(segmentIndex);
            double[] p1 = points.get(segmentIndex + 1);
            return new double[]{
                lerp(p0[0], p1[0], localT),
                lerp(p0[1], p1[1], localT),
                lerp(p0[2], p1[2], localT),
                p0[3] + Math.IEEEremainder(p1[3] - p0[3], 360.0) * localT,
                lerp(p0.length > 4 ? p0[4] : 0, p1.length > 4 ? p1[4] : 0, localT)
            };
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }

    // ─── Title ─────────────────────────────────────────────

    public static final class TitleAction extends CinematicAction {
        private final String title;
        private final String subtitle;
        private final int fadeIn, stay, fadeOut;

        public TitleAction(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.fadeIn = fadeIn;
            this.stay = stay;
            this.fadeOut = fadeOut;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }

        @Override
        public String type() {
            return "title";
        }
    }

    // ─── Action Bar ────────────────────────────────────────

    public static final class ActionBarAction extends CinematicAction {
        private final String message;

        public ActionBarAction(String message) {
            this.message = message == null ? "" : message;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
        }

        @Override
        public String type() {
            return "action_bar";
        }
    }

    // ─── Sound ─────────────────────────────────────────────

    public static final class SoundAction extends CinematicAction {
        private final String soundName;
        private final String worldName;
        private final double x, y, z;
        private final float volume;
        private final float pitch;
        private final boolean atPlayer;

        public SoundAction(String soundName, String worldName,
                           double x, double y, double z, float volume, float pitch, boolean atPlayer) {
            this.soundName = soundName;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.volume = volume;
            this.pitch = pitch;
            this.atPlayer = atPlayer;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT));
                if (atPlayer) {
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } else {
                    var world = Bukkit.getWorld(worldName);
                    if (world == null) world = player.getWorld();
                    player.playSound(new Location(world, x, y, z), sound, volume, pitch);
                }
            } catch (IllegalArgumentException ignored) {
                player.playSound(player.getLocation(), soundName, volume, pitch);
            }
        }

        @Override
        public String type() {
            return "sound";
        }

        public void stop(Player player) {
            Sound sound;
            try {
                sound = Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                player.stopSound(soundName);
                return;
            }
            player.stopSound(sound);
        }
    }

    // ─── Game Mode ─────────────────────────────────────────

    public static final class GameModeAction extends CinematicAction {
        private final GameMode gameMode;

        public GameModeAction(GameMode gameMode) {
            this.gameMode = gameMode;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            player.setGameMode(gameMode);
        }

        @Override
        public String type() {
            return "game_mode";
        }
    }

    // ─── NPC Animation ─────────────────────────────────────

    public static final class NpcAnimationAction extends CinematicAction {
        private final String npcName;
        private final String animation;
        private final double speed;
        private final int transitionTime;
        private final long keepTime;

        public NpcAnimationAction(String npcName, String animation,
                                  double speed, int transitionTime, long keepTime) {
            this.npcName = npcName;
            this.animation = animation;
            this.speed = speed;
            this.transitionTime = transitionTime;
            this.keepTime = keepTime;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            if (ctx == null) return;
            AdyeshachNpcBridgeAPI npcBridge = ctx.npcBridge();
            if (npcBridge == null || !npcBridge.isAvailable()) return;
            var npcOpt = npcBridge.findByName(npcName);
            if (npcOpt.isEmpty()) return;
            npcBridge.applyAnimation(npcOpt.get(), animation, speed, transitionTime, keepTime);
        }

        @Override
        public String type() {
            return "npc_animation";
        }
    }

    // ─── NPC Model ─────────────────────────────────────────

    public static final class NpcModelAction extends CinematicAction {
        private final String npcName;
        private final String modelId;
        private final double scale;

        public NpcModelAction(String npcName, String modelId, double scale) {
            this.npcName = npcName;
            this.modelId = modelId;
            this.scale = scale;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            if (ctx == null) return;
            AdyeshachNpcBridgeAPI npcBridge = ctx.npcBridge();
            if (npcBridge == null || !npcBridge.isAvailable()) return;
            var npcOpt = npcBridge.findByName(npcName);
            if (npcOpt.isEmpty()) return;
            npcBridge.applyModel(npcOpt.get(), modelId, scale);
        }

        @Override
        public String type() {
            return "npc_model";
        }
    }

    // ─── Message ───────────────────────────────────────────

    public static final class MessageAction extends CinematicAction {
        private final String message;

        public MessageAction(String message) {
            this.message = message == null ? "" : message;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            player.sendMessage(message);
        }

        @Override
        public String type() {
            return "message";
        }
    }

    // ─── Command ───────────────────────────────────────────

    public static final class CommandAction extends CinematicAction {
        private final String command;
        private final boolean asConsole;

        public CommandAction(String command, boolean asConsole) {
            this.command = command;
            this.asConsole = asConsole;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            String resolved = command.replace("{player}", xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName()));
            // 展开后统一校验：命令模板其余部分（含其他占位符）可能携带分隔符
            try {
                resolved = xuanmo.arcartxsuite.api.command.CommandSanitizer.validateExpandedCommand(resolved);
            } catch (IllegalArgumentException e) {
                return;
            }
            if (asConsole) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            } else {
                player.performCommand(resolved.startsWith("/") ? resolved.substring(1) : resolved);
            }
        }

        @Override
        public String type() {
            return "command";
        }
    }

    // ─── Fly Speed / Walk Speed ────────────────────────────

    public static final class SpeedAction extends CinematicAction {
        private final Float walkSpeed;
        private final Float flySpeed;

        public SpeedAction(Float walkSpeed, Float flySpeed) {
            this.walkSpeed = walkSpeed;
            this.flySpeed = flySpeed;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            if (walkSpeed != null) player.setWalkSpeed(walkSpeed);
            if (flySpeed != null) player.setFlySpeed(flySpeed);
        }

        @Override
        public String type() {
            return "speed";
        }
    }

    // ─── Lock Movement ─────────────────────────────────────

    public static final class LockMovementAction extends CinematicAction {
        private final boolean locked;

        public LockMovementAction(boolean locked) {
            this.locked = locked;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            if (locked) {
                player.setWalkSpeed(0f);
                player.setFlySpeed(0f);
            } else {
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);
            }
        }

        @Override
        public String type() {
            return "lock_movement";
        }
    }

    // ─── Subtitle (Announcer 字幕组) ─────────────────────

    public static final class SubtitleAction extends CinematicAction {
        private final String groupId;

        public SubtitleAction(String groupId) {
            this.groupId = groupId;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            if (ctx == null) return;
            SubtitlePlayable subtitle = ctx.subtitlePlayable();
            if (subtitle == null) return;
            subtitle.playGroup(player, groupId);
        }

        @Override
        public String type() {
            return "subtitle";
        }
    }

    // ─── UI Packet (发送包到 ArcartX 客户端 UI) ───────────

    public static final class UiPacketAction extends CinematicAction {
        private final String uiId;
        private final String handler;
        private final Map<String, Object> payload;

        public UiPacketAction(String uiId, String handler, Map<String, Object> payload) {
            this.uiId = uiId;
            this.handler = handler;
            this.payload = payload;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            if (ctx == null) return;
            PacketBridgeAPI bridge = ctx.packetBridge();
            if (bridge == null || !bridge.isAvailable()) return;
            bridge.sendPacket(player, uiId, handler, payload);
        }

        @Override
        public String type() {
            return "ui_packet";
        }
    }

    // ─── ArcartX Sound (自定义资源包音效) ────────────────

    public static final class ArcartXSoundAction extends CinematicAction {
        private final String resourcePath;
        private final String category;
        private final String worldName;
        private final double x, y, z;
        private final int distOrRoll;
        private final double pitch;
        private final int keepTimeMs;
        private final boolean atPlayer;

        public ArcartXSoundAction(String resourcePath, String category,
                                   String worldName, double x, double y, double z,
                                   int distOrRoll, double pitch, int keepTimeMs, boolean atPlayer) {
            this.resourcePath = resourcePath;
            this.category = category == null ? "master" : category;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.distOrRoll = distOrRoll;
            this.pitch = pitch;
            this.keepTimeMs = keepTimeMs;
            this.atPlayer = atPlayer;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            if (ctx == null) return;
            SoundPlayerBridgeAPI soundPlayer = ctx.soundPlayer();
            if (soundPlayer == null || !soundPlayer.isAvailable()) return;
            if (atPlayer) {
                soundPlayer.playSoundForSelf(player, resourcePath, category, (float) pitch, keepTimeMs);
            } else {
                var world = Bukkit.getWorld(worldName);
                if (world == null) world = player.getWorld();
                soundPlayer.playSoundForPlayer(player, new Location(world, x, y, z),
                    resourcePath, category, distOrRoll, pitch, keepTimeMs);
            }
        }

        @Override
        public String type() {
            return "arcartx_sound";
        }
    }
}
