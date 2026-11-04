package xuanmo.arcartxsuite.conversation.cinematic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Moves an already placed private actor between two known marks. */
record CinematicActorMove(String actor, String world, Mark from, Mark to, long durationTicks) {
    record Mark(double x, double y, double z, double yaw, double pitch) {
    }

    void sample(Player player, CinematicActionContext context, String markerId, long elapsed) {
        var targetWorld = Bukkit.getWorld(world);
        if (targetWorld == null) throw new IllegalStateException("演员世界未加载: " + world);
        var bridge = context.npcBridge();
        double progress = Math.min(1.0, (double) elapsed / durationTicks);
        double yaw = from.yaw + Math.IEEEremainder(to.yaw - from.yaw, 360.0) * progress;
        Location location = new Location(targetWorld, lerp(from.x, to.x, progress), lerp(from.y, to.y, progress),
            lerp(from.z, to.z, progress), (float) yaw, (float) lerp(from.pitch, to.pitch, progress));
        if (bridge == null || !bridge.teleportMarker(player, markerId, location)) {
            throw new IllegalStateException("演员尚未放置或移动失败: " + actor);
        }
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    static final class Action extends CinematicAction {
        final CinematicActorMove move;

        Action(CinematicActorMove move) {
            this.move = move;
        }

        @Override
        public void execute(Player player, CinematicActionContext context) {
            throw new IllegalStateException("演员移动需要播放会话");
        }

        @Override
        public String type() {
            return "actor_move";
        }
    }
}
