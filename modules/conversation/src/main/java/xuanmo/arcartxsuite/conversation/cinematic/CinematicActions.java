package xuanmo.arcartxsuite.conversation.cinematic;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
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

        public TeleportAction(long delayAfterMs, String worldName, double x, double y, double z, Float yaw, Float pitch) {
            super(delayAfterMs);
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
            if (world == null) {
                world = player.getWorld();
            }
            Location loc = new Location(world, x, y, z);
            if (yaw != null) loc.setYaw(yaw);
            if (pitch != null) loc.setPitch(pitch);
            player.teleport(loc);
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
        private final int ticksPerFrame;

        public CameraPathAction(long delayAfterMs, String worldName, List<double[]> points, long durationMs, int ticksPerFrame) {
            super(delayAfterMs);
            this.worldName = worldName;
            this.points = points;
            this.durationMs = Math.max(50L, durationMs);
            this.ticksPerFrame = Math.max(1, ticksPerFrame);
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            if (points == null || points.size() < 2) return;
            var world = Bukkit.getWorld(worldName);
            if (world == null) world = player.getWorld();

            int totalFrames = (int) (durationMs / (ticksPerFrame * 50L));
            if (totalFrames < 1) totalFrames = 1;
            final int finalFrames = totalFrames;
            final var finalWorld = world;

            for (int frame = 0; frame <= finalFrames; frame++) {
                double progress = (double) frame / finalFrames;
                double[] pos = interpolatePath(points, progress);
                long delay = (long) frame * ticksPerFrame;
                AxsScheduler.runTaskLater(JavaPlugin.getProvidingPlugin(CinematicActions.class), () -> {
                    if (!player.isOnline()) return;
                    player.teleport(new Location(finalWorld, pos[0], pos[1], pos[2],
                        (float) pos[3], (float) pos[4]));
                }, delay);
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
                lerp(p0.length > 3 ? p0[3] : 0, p1.length > 3 ? p1[3] : 0, localT),
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

        public TitleAction(long delayAfterMs, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            super(delayAfterMs);
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

        public ActionBarAction(long delayAfterMs, String message) {
            super(delayAfterMs);
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

    // ─── Wait ──────────────────────────────────────────────

    public static final class WaitAction extends CinematicAction {
        public WaitAction(long delayAfterMs) {
            super(delayAfterMs);
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            // 纯等待，不做任何操作
        }

        @Override
        public String type() {
            return "wait";
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

        public SoundAction(long delayAfterMs, String soundName, String worldName,
                           double x, double y, double z, float volume, float pitch, boolean atPlayer) {
            super(delayAfterMs);
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
    }

    // ─── Game Mode ─────────────────────────────────────────

    public static final class GameModeAction extends CinematicAction {
        private final GameMode gameMode;

        public GameModeAction(long delayAfterMs, GameMode gameMode) {
            super(delayAfterMs);
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

        public NpcAnimationAction(long delayAfterMs, String npcName, String animation,
                                  double speed, int transitionTime, long keepTime) {
            super(delayAfterMs);
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

        public NpcModelAction(long delayAfterMs, String npcName, String modelId, double scale) {
            super(delayAfterMs);
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

        public MessageAction(long delayAfterMs, String message) {
            super(delayAfterMs);
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

        public CommandAction(long delayAfterMs, String command, boolean asConsole) {
            super(delayAfterMs);
            this.command = command;
            this.asConsole = asConsole;
        }

        @Override
        public void execute(Player player, CinematicActionContext ctx) {
            String resolved = command.replace("{player}", player.getName());
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

        public SpeedAction(long delayAfterMs, Float walkSpeed, Float flySpeed) {
            super(delayAfterMs);
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

        public LockMovementAction(long delayAfterMs, boolean locked) {
            super(delayAfterMs);
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

        public SubtitleAction(long delayAfterMs, String groupId) {
            super(delayAfterMs);
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

        public UiPacketAction(long delayAfterMs, String uiId, String handler, Map<String, Object> payload) {
            super(delayAfterMs);
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

        public ArcartXSoundAction(long delayAfterMs, String resourcePath, String category,
                                   String worldName, double x, double y, double z,
                                   int distOrRoll, double pitch, int keepTimeMs, boolean atPlayer) {
            super(delayAfterMs);
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
