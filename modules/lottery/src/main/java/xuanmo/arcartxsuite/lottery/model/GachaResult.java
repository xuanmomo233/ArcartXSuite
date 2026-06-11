package xuanmo.arcartxsuite.lottery.model;

import java.util.List;
import org.jetbrains.annotations.NotNull;

public record GachaResult(
    @NotNull List<PoolItem> items,
    @NotNull PlayerGachaState finalState,
    boolean guaranteed,
    boolean fatePointTriggered
) {
}
