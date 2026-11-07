package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Binds the embedded personal warehouse panel to an already-open host UI. */
public interface WarehousePanelAccess {
    List<String> categoryIds();

    String classify(ItemStack stack);

    void handle(Player player, String uiId, List<String> data);

    void close(String uiId);
}
