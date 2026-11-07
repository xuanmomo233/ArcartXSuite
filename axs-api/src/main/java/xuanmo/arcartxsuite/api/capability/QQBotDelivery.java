package xuanmo.arcartxsuite.api.capability;

import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;

/**
 * Acknowledged, bounded QQ delivery. Text is never interpreted as CQ codes; destinations must be configured.
 * Completion runs on a transport worker. Schedule Bukkit work back to the main thread.
 * An unknown outcome must be independently checked before retrying; this API never automatically replays sends.
 */
@PublicCapability
public interface QQBotDelivery {
    @NotNull CompletionStage<Result> sendToGroup(long groupId, @NotNull String message);
    @NotNull CompletionStage<Result> sendToAllGroups(@NotNull String message);

    /** sentGroups counts acknowledged successes only. Unknown and partial results require caller attention. */
    record Result(boolean success, boolean outcomeUnknown, int sentGroups, int totalGroups, @NotNull String message) {}
}
