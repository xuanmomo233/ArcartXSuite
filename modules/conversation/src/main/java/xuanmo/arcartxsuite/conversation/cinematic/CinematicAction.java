package xuanmo.arcartxsuite.conversation.cinematic;

import org.bukkit.entity.Player;

/** A cue executed on the server thread. */
public abstract class CinematicAction {

    /**
     * 在主线程执行此动作。
     *
     * @param player  目标玩家
     * @param ctx     桥接上下文（各桥接可能为 null）
     */
    public abstract void execute(Player player, CinematicActionContext ctx);

    /** 动作类型标识，用于配置解析和调试 */
    public abstract String type();
}
