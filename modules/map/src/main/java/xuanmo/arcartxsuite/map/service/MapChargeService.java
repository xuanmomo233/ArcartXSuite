package xuanmo.arcartxsuite.map.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI.CurrencyBridge;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.CurrencyCost;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.ItemCost;
import xuanmo.arcartxsuite.map.model.MapOperationResult;

/**
 * 地图扣费域：多币种扣费（预检+逐项扣+失败回滚）、物品消耗两阶段提交
 * （prepare 计算扣除槽位 → apply 应用），供锚点解锁/传送复用。
 * <p>自 {@link MapService} 提取，持有主类引用回调 currencyBridgeManager/itemMatcherSupport/message。
 */
final class MapChargeService {

    private final MapService service;

    MapChargeService(MapService service) {
        this.service = service;
    }


    CurrencyChargeReceipt chargeCurrencies(Player player, List<CurrencyCost> costs) {
        if (costs == null || costs.isEmpty()) {
            return CurrencyChargeReceipt.success(List.of());
        }
        for (CurrencyCost cost : costs) {
            CurrencyBridge bridge = service.currencyBridgeManager.bridge(cost.currencyId());
            if (bridge == null || !bridge.available()) {
                return CurrencyChargeReceipt.failure(service.message("player.currency-bridge-unavailable", cost.currencyId()));
            }
            if (bridge.balance(player).compareTo(cost.amount()) < 0) {
                return CurrencyChargeReceipt.failure(service.message("player.insufficient-balance", cost.currencyId()));
            }
        }
        List<CurrencyCost> successfulCharges = new ArrayList<>();
        for (CurrencyCost cost : costs) {
            CurrencyBridge bridge = service.currencyBridgeManager.bridge(cost.currencyId());
            if (bridge == null || !bridge.available()) {
                rollbackCurrencies(player, successfulCharges);
                return CurrencyChargeReceipt.failure(service.message("player.currency-bridge-unavailable", cost.currencyId()));
            }
            CurrencyTransactionResult result = bridge.withdraw(player, cost.amount());
            if (!result.success()) {
                rollbackCurrencies(player, successfulCharges);
                return CurrencyChargeReceipt.failure(result.message().isBlank() ? service.message("player.charge-failed", cost.currencyId()) : result.message());
            }
            successfulCharges.add(cost);
        }
        return CurrencyChargeReceipt.success(successfulCharges);
    }


    void rollbackCurrencies(Player player, List<CurrencyCost> successfulCharges) {
        for (CurrencyCost cost : successfulCharges) {
            CurrencyBridge bridge = service.currencyBridgeManager.bridge(cost.currencyId());
            if (bridge != null && bridge.available()) {
                bridge.deposit(player, cost.amount());
            }
        }
    }


    PreparedItemConsumption prepareItemConsumption(Player player, List<ItemCost> itemCosts) {
        if (itemCosts == null || itemCosts.isEmpty()) {
            return PreparedItemConsumption.empty();
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] snapshot = cloneContents(contents);
        int[] remaining = new int[contents.length];
        int[] deductions = new int[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            remaining[slot] = contents[slot] == null ? 0 : contents[slot].getAmount();
        }
        for (ItemCost cost : itemCosts) {
            int need = Math.max(1, cost.amount());
            for (int slot = 0; slot < contents.length && need > 0; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || stack.getType().isAir() || remaining[slot] <= 0 || !service.itemMatcherSupport.matches(cost.matcher(), stack)) {
                    continue;
                }
                int take = Math.min(need, remaining[slot]);
                remaining[slot] -= take;
                deductions[slot] += take;
                need -= take;
            }
            if (need > 0) {
                return PreparedItemConsumption.failure(service.message("player.missing-unlock-items"));
            }
        }
        return PreparedItemConsumption.success(snapshot, deductions);
    }


    MapOperationResult applyItemConsumption(Player player, PreparedItemConsumption prepared) {
        if (prepared == null || !prepared.success() || !prepared.hasChanges()) {
            return MapOperationResult.success("");
        }
        ItemStack[] updatedContents = cloneContents(prepared.originalContents());
        for (int slot = 0; slot < updatedContents.length; slot++) {
            if (prepared.deductions()[slot] <= 0 || updatedContents[slot] == null) {
                continue;
            }
            int updatedAmount = updatedContents[slot].getAmount() - prepared.deductions()[slot];
            if (updatedAmount <= 0) {
                updatedContents[slot] = null;
            } else {
                updatedContents[slot].setAmount(updatedAmount);
            }
        }
        player.getInventory().setStorageContents(updatedContents);
        player.updateInventory();
        return MapOperationResult.success("");
    }


    static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents == null ? 0 : contents.length];
        if (contents == null) {
            return clone;
        }
        for (int index = 0; index < contents.length; index++) {
            clone[index] = contents[index] == null ? null : contents[index].clone();
        }
        return clone;
    }


    record CurrencyChargeReceipt(
        boolean success,
        String message,
        List<CurrencyCost> successfulCharges
    ) {

        static CurrencyChargeReceipt success(List<CurrencyCost> successfulCharges) {
            return new CurrencyChargeReceipt(true, "", List.copyOf(successfulCharges));
        }

        static CurrencyChargeReceipt failure(String message) {
            return new CurrencyChargeReceipt(false, message, List.of());
        }
    }


    record PreparedItemConsumption(
        boolean success,
        String message,
        ItemStack[] originalContents,
        int[] deductions
    ) {

        static PreparedItemConsumption empty() {
            return new PreparedItemConsumption(true, "", new ItemStack[0], new int[0]);
        }

        static PreparedItemConsumption success(ItemStack[] originalContents, int[] deductions) {
            return new PreparedItemConsumption(true, "", originalContents, deductions.clone());
        }

        static PreparedItemConsumption failure(String message) {
            return new PreparedItemConsumption(false, message, new ItemStack[0], new int[0]);
        }

        boolean hasChanges() {
            for (int deduction : deductions) {
                if (deduction > 0) {
                    return true;
                }
            }
            return false;
        }
    }
}
