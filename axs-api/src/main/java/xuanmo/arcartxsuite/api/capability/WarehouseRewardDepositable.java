package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.inventory.ItemStack;

/** Internal Suite capability for items that already have a durable owner and request ID. */
public interface WarehouseRewardDepositable {
    enum Result { STORED, DECLINED, RETRY }

    boolean usesSharedHostStorage();

    /**
     * Called on the server thread to snapshot the items. The full batch and its receipt
     * are committed on a worker. Only a recorded DECLINED result permits a different sink.
     */
    CompletionStage<Result> depositReward(UUID request, UUID recipient, List<ItemStack> items);
}
