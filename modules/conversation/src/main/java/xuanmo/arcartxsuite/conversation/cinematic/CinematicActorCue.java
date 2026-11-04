package xuanmo.arcartxsuite.conversation.cinematic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Place or animate a temporary actor visible to this scene's viewer. */
final class CinematicActorCue extends CinematicAction {
    private final String actor;
    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private final String model;
    private final String animation;

    CinematicActorCue(String actor, String world, double x, double y, double z, float yaw, float pitch,
                      String model, String animation) {
        this.actor = actor;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.model = model;
        this.animation = animation;
    }

    String actor() {
        return actor;
    }

    boolean placesActor() {
        return !world.isEmpty();
    }

    @Override
    public void execute(Player player, CinematicActionContext context) {
        throw new IllegalStateException("演员动作需要播放会话");
    }

    void apply(Player player, CinematicActionContext context, String markerId, boolean rebuilding) {
        var bridge = context.npcBridge();
        if (bridge == null || !bridge.isAvailable()) throw new IllegalStateException("演员需要 Adyeshach");
        Object entity = bridge.getPrivateMarker(player, markerId);
        if (!world.isEmpty()) {
            var destination = Bukkit.getWorld(world);
            if (destination == null) throw new IllegalStateException("演员世界未加载: " + world);
            var location = new Location(destination, x, y, z, yaw, pitch);
            if (entity == null) entity = bridge.spawnPrivateMarker(player, markerId, location);
            else if (!bridge.teleportMarker(player, markerId, location)) throw new IllegalStateException("演员定位失败: " + actor);
        }
        if (entity == null) throw new IllegalStateException("演员尚未放置: " + actor);
        if (!model.isEmpty() && !bridge.applyModelForPlayer(player, entity, model, 1.0)) {
            throw new IllegalStateException("演员模型应用失败: " + actor + " / " + model);
        }
        if (!rebuilding && !animation.isEmpty() && !bridge.applyAnimationForPlayer(player, entity, animation, 1, 5, -1)) {
            throw new IllegalStateException("演员动画应用失败: " + actor + " / " + animation);
        }
    }

    @Override
    public String type() {
        return world.isEmpty() ? "actor_animation" : "actor_place";
    }
}
