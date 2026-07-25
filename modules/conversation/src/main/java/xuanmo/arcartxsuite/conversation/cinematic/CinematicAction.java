package xuanmo.arcartxsuite.conversation.cinematic;

import org.bukkit.entity.Player;

/**
 * 电影序列中的单个动作。
 * <p>
 * 每个动作在序列中被依次执行，{@link #execute} 返回一个延迟（毫秒），
 * 表示执行完毕后需要等待多久再执行下一个动作。
 */
public abstract class CinematicAction {

    protected final long delayAfterMs;

    protected CinematicAction(long delayAfterMs) {
        this.delayAfterMs = Math.max(0L, delayAfterMs);
    }

    /** 动作结束后等待的毫秒数 */
    public long delayAfterMs() {
        return delayAfterMs;
    }

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
