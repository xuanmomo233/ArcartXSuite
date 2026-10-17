package xuanmo.arcartxsuite.warehouse.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.CategoryDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.DepositProductDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.InterestTier;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.SharedPermissionTier;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.WarehouseDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.WarehouseLevelDefinition;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedMemberRecord;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.SortField;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.FixedDepositRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedWarehouseRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SlotItemRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.WarehouseRecord;
import xuanmo.arcartxsuite.warehouse.service.WarehouseService.ViewState;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import java.util.Base64;

/**
 * 仓库 UI 包构建器：把 {@link ViewState}+仓储数据翻译为 ArcartX UI payload Map。
 * <p>自 {@link WarehouseService} 提取的纯构建域——只读视图状态与仓库数据，不写玩家状态。
 */
final class WarehousePacketFactory {

    private final WarehouseService service;

    WarehousePacketFactory(WarehouseService service) {
        this.service = service;
    }

    /**
     * 构建仓库存取界面的数据包，包含分页槽位、选中物品、背包信息、容量与权限状态。
     */
    Map<String, Object> buildStoragePacket(Player player, WarehouseService.ViewState state) throws Exception {
        List<SlotItemRecord> visibleSlots = visibleSlots(state);
        String slotOrder = visibleSlots.stream().map(i -> String.valueOf(i.slot())).collect(Collectors.joining(","));
        service.debug("buildStoragePacket visibleSlots order: " + slotOrder);
        Map<String, Object> slots = new LinkedHashMap<>();
        Map<String, Object> packet = basePacket(player, state);
        int pageSize = pageSize();
        int pageTotal = Math.max(1, (visibleSlots.size() + pageSize - 1) / pageSize);
        state.setPage(Math.max(1, Math.min(state.page(), pageTotal)));
        int start = (state.page() - 1) * pageSize;
        for (int displaySlot = 0; displaySlot < pageSize; displaySlot++) {
            int index = start + displaySlot;
            SlotItemRecord item = index >= 0 && index < visibleSlots.size()
                ? visibleSlots.get(index)
                : null;
            Map<String, Object> row = item != null
                ? slotPacket(item)
                : emptySlotPacket(displaySlot);
            String key = Integer.toString(displaySlot);
            row.put("key", key);
            slots.put(key, row);
            if (item != null) {
                String itemJson = displayItemJson(item);
                if (!itemJson.isBlank()) {
                    packet.put("slotItemJson" + key, itemJson);
                }
            }
        }
        String slotKeys = String.join(",", slots.keySet());
        service.debug("buildStoragePacket slots keys order: " + slotKeys);
        int selectedDisplaySlot = selectedDisplaySlot(state, visibleSlots);
        SlotItemRecord selected = service.validSlot(state.selectedSlot())
            ? service.repository.loadSlot(state.ownerType(), state.ownerId(), state.warehouseId(), state.selectedSlot()).orElse(null)
            : null;
        packet.put("ui", "storage");
        packet.put("action_token", service.storageActionTokens.getOrDefault(player.getUniqueId(), ""));
        packet.put("sortId", state.sortId());
        packet.put("sortText", sortText(state.sortId()));
        packet.put("slots", slots);
        packet.put("slotCount", pageSize);
        packet.put("selectedSlot", selectedDisplaySlot);
        packet.put("selectedActualSlot", state.selectedSlot());
        packet.put("selectedItem", selected == null ? emptySelectionPacket() : slotPacket(selected));
        packet.put("backpack", backpackPacket(player));
        packet.put("page", state.page());
        packet.put("pageTotal", pageTotal);
        packet.put("pageText", state.page() + "/" + pageTotal);
        packet.put("usedSlots", service.countSlots(state.ownerType(), state.ownerId(), state.warehouseId()));
        packet.put("matchedSlots", visibleSlots.size());
        putCapacityFields(player, state, packet);
        packet.put("readOnly", !service.canModifyCurrent(player, state));
        packet.put("sharedEditMode", WarehouseService.OWNER_SHARED.equals(state.ownerType()) && state.sharedEditMode());
        packet.put("sharedCanEdit", service.sharedCanEdit(player, state));
        packet.put("lockOwner", service.lockOwnerName(state));
        packet.put("maxPersonalCount", ((java.util.Map<?,?>) packet.getOrDefault("personalWarehouses", java.util.Map.of())).size());
        packet.put("maxSharedCount", ((java.util.Map<?,?>) packet.getOrDefault("sharedWarehouses", java.util.Map.of())).size());
        packet.put("maxCategoryCount", ((java.util.Map<?,?>) packet.getOrDefault("categories", java.util.Map.of())).size());
        return packet;
    }

    /**
     * 构建共享管理界面的数据包，包含成员列表、搜索结果、自动拾取设置等。
     */
    Map<String, Object> buildManagePacket(Player player, WarehouseService.ViewState state) throws Exception {
        Map<String, Object> packet = basePacket(player, state);
        Map<String, Object> sharedMembers = sharedMemberPacket(player, state);
        Map<String, Object> searchResults = searchResultPacket(state);
        packet.put("ui", "manage");
        packet.put("action_token", service.manageActionTokens.getOrDefault(player.getUniqueId(), ""));
        putCapacityFields(player, state, packet);
        packet.put("sharedMembers", sharedMembers);
        packet.put("sharedMemberTexts", fieldMap(sharedMembers, "text"));
        packet.put("sharedMemberNames", fieldMap(sharedMembers, "name"));
        packet.put("sharedMemberRoles", fieldMap(sharedMembers, "role"));
        packet.put("searchResults", searchResults);
        packet.put("searchResultTexts", fieldMap(searchResults, "text"));
        packet.put("searchResultSlots", fieldMap(searchResults, "slot"));
        packet.put("readOnly", !service.canModifyCurrent(player, state));
        packet.put("sharedEditMode", WarehouseService.OWNER_SHARED.equals(state.ownerType()) && state.sharedEditMode());
        packet.put("sharedCanEdit", service.sharedCanEdit(player, state));
        packet.put("lockOwner", service.lockOwnerName(state));
        packet.put("autoPickup", state.autoPickup());
        packet.put("autoPickupMythic", state.autoPickupMythic());
        packet.put("autoPickupNotify", state.autoPickupNotify());
        packet.put("serverAutoPickup", service.configuration.pickup().enabled());
        packet.put("serverMythicLoot", service.configuration.pickup().mythicLootEnabled());
        packet.put("showcaseEnabled", service.currentShowcaseEnabled(player, state));
        packet.put("maxManageCount", ((java.util.Map<?,?>) packet.getOrDefault("manageWarehouses", java.util.Map.of())).size());
        packet.put("maxMemberCount", sharedMembers.size());
        return packet;
    }

    /**
     * 构建银行界面的数据包，包含活期余额、定期产品和当前定期列表。
     */
    Map<String, Object> buildBankPacket(Player player, WarehouseService.ViewState state) throws Exception {
        Map<String, Object> packet = basePacket(player, state);
        Map<String, Object> balances = bankBalancePacket(player);
        Map<String, Object> products = depositProductPacket(player, state.selectedCurrency());
        Map<String, Object> fixedDeposits = fixedDepositPacket(player);
        packet.put("ui", "bank");
        packet.put("action_token", service.bankActionTokens.getOrDefault(player.getUniqueId(), ""));
        packet.put("balances", balances);
        packet.put("balanceTexts", fieldMap(balances, "text"));
        packet.put("products", products);
        packet.put("productTexts", fieldMap(products, "text"));
        packet.put("fixedDeposits", fixedDeposits);
        packet.put("fixedDepositTexts", fieldMap(fixedDeposits, "text"));
        packet.put("maxBalanceCount", balances.size());
        packet.put("maxProductCount", products.size());
        packet.put("maxFixedCount", fixedDeposits.size());
        packet.put("selectedCurrency", state.selectedCurrency());
        return packet;
    }

    Map<String, Object> basePacket(Player player, WarehouseService.ViewState state) throws Exception {
        Map<String, Object> packet = new LinkedHashMap<>();
        Map<String, Object> categories = categoryPacket();
        Map<String, Object> personalWarehouses;
        Map<String, Object> sharedWarehouses;
        if (state.previewMode() && state.previewTargetUuid() != null) {
            personalWarehouses = previewPersonalWarehousePacket(state.previewTargetUuid());
            sharedWarehouses = previewSharedWarehousePacket(state.previewTargetUuid());
        } else {
            personalWarehouses = personalWarehousePacket(player);
            sharedWarehouses = sharedWarehousePacket(player);
        }
        Map<String, Object> manageWarehouses = manageWarehousePacket(personalWarehouses, sharedWarehouses, state);
        packet.put("packetId", service.configuration.ui().packetId());
        packet.put("ownerType", state.ownerType());
        packet.put("ownerId", state.ownerId());
        packet.put("warehouseId", state.warehouseId());
        packet.put("warehouseName", currentWarehouseName(player, state));
        packet.put("categoryId", state.categoryId());
        packet.put("search", state.search());
        packet.put("categories", categories);
        packet.put("categoryTexts", fieldMap(categories, "text"));
        SecondaryPasswordAccess passwordAccess = service.secondaryPasswordSupplier == null
            ? null : service.secondaryPasswordSupplier.get();
        packet.put("unlocked", passwordAccess != null && passwordAccess.isUnlocked(player));
        packet.put("hasPassword", passwordAccess != null && passwordAccess.isPasswordSet(player));
        packet.put("personalWarehouses", personalWarehouses);
        packet.put("personalWarehouseTexts", fieldMap(personalWarehouses, "text"));
        packet.put("sharedWarehouses", sharedWarehouses);
        packet.put("sharedWarehouseStorageTexts", fieldMap(sharedWarehouses, "storageText"));
        packet.put("sharedWarehouseManageTexts", fieldMap(sharedWarehouses, "manageText"));
        packet.put("manageWarehouses", manageWarehouses);
        packet.put("manageWarehouseTexts", fieldMap(manageWarehouses, "text"));
        packet.put("manageWarehouseTypes", fieldMap(manageWarehouses, "ownerType"));
        packet.put("manageWarehouseTargets", fieldMap(manageWarehouses, "target"));
        packet.put("manageWarehouseSelected", fieldMap(manageWarehouses, "selected"));
        packet.put("sharedCreateCostText", service.sharedCreateCostText());
        packet.put("sharedRoleOwnerName", service.sharedRoleName("owner"));
        packet.put("sharedRoleMemberName", service.sharedRoleName("member"));
        packet.put("sharedRoleViewerName", service.sharedRoleName("viewer"));
        packet.put("storageUiId", service.storageRuntimeUiId);
        packet.put("manageUiId", service.manageRuntimeUiId);
        packet.put("bankUiId", service.bankRuntimeUiId);
        return packet;
    }

    Map<String, Object> fieldMap(Map<String, Object> rows, String field) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rows.entrySet()) {
            Object row = entry.getValue();
            if (row instanceof Map<?, ?> map) {
                Object value = map.get(field);
                result.put(entry.getKey(), value == null ? "" : value);
            } else {
                result.put(entry.getKey(), "");
            }
        }
        return result;
    }

    Map<String, Object> slotPacket(SlotItemRecord item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", Integer.toString(item.slot()));
        row.put("slot", item.slot());
        row.put("empty", false);
        row.put("name", item.displayName());
        row.put("amount", item.amount());
        row.put("category", item.categoryId());
        row.put("material", item.materialId());
        row.put("updated", WarehouseService.TIME_FORMATTER.format(Instant.ofEpochMilli(item.updatedAt())));
        row.put("lore", service.loreLinesFor(item));
        return row;
    }

    Map<String, Object> categoryPacket() {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        Map<String, Object> allRow = new LinkedHashMap<>();
        allRow.put("id", "all");
        allRow.put("name", service.message("ui.all"));
        allRow.put("text", service.message("ui.all-text"));
        result.put(Integer.toString(idx), allRow);
        idx++;
        for (CategoryDefinition category : service.configuration.categories().values().stream()
                .sorted(Comparator.comparingInt(CategoryDefinition::priority))
                .toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", category.id());
            row.put("name", category.displayName());
            row.put("text", service.message("ui.category-name", category.displayName()));
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    Map<String, Object> personalWarehousePacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, WarehouseRecord> records = service.personalWarehouseMap(player.getUniqueId());
        int idx = 0;
        for (WarehouseDefinition definition : service.configuration.warehouses().values()) {
            if (!records.containsKey(definition.id())) {
                continue;
            }
            WarehouseRecord record = records.get(definition.id());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("name", ChatColor.translateAlternateColorCodes('&', definition.displayName()));
            row.put("text", service.message("ui.warehouse-name", ChatColor.translateAlternateColorCodes('&', definition.displayName())));
            row.put("capacity", resolveCapacity(WarehouseService.OWNER_PERSONAL, player.getUniqueId().toString(), definition.id()));
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    Map<String, Object> previewPersonalWarehousePacket(UUID targetUuid) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, WarehouseRecord> records = service.personalWarehouseMap(targetUuid);
        int idx = 0;
        for (WarehouseDefinition definition : service.configuration.warehouses().values()) {
            if (!records.containsKey(definition.id())) {
                continue;
            }
            WarehouseRecord record = records.get(definition.id());
            if (!record.showcaseEnabled()) {
                continue;
            }
            String name = record.customName() == null || record.customName().isBlank()
                ? ChatColor.translateAlternateColorCodes('&', definition.displayName())
                : record.customName();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("name", name);
            row.put("text", service.message("ui.warehouse-name", name));
            row.put("capacity", resolveCapacity(WarehouseService.OWNER_PERSONAL, targetUuid.toString(), definition.id()));
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    Map<String, Object> previewSharedWarehousePacket(UUID targetUuid) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (SharedWarehouseRecord shared : service.repository.loadSharedWarehousesByOwner(targetUuid)) {
            if (!shared.showcaseEnabled()) {
                continue;
            }
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("id", shared.id());
            rowMap.put("name", shared.name());
            rowMap.put("role", shared.viewerRole());
            rowMap.put("roleName", service.sharedRoleName(shared.viewerRole()));
            rowMap.put("storageText", service.message("ui.shared-name", shared.name()));
            rowMap.put("manageText", service.message("ui.shared-entry", shared.name(), service.sharedRoleName(shared.viewerRole()), shared.level(), shared.capacity()));
            rowMap.put("capacity", shared.capacity());
            rowMap.put("lockedBy", "");
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    Map<String, Object> manageWarehousePacket(Map<String, Object> personalWarehouses, Map<String, Object> sharedWarehouses, WarehouseService.ViewState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (Map.Entry<String, Object> entry : personalWarehouses.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> row)) {
                continue;
            }
            String target = WarehouseTexts.safe(String.valueOf(row.get("id")));
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("ownerType", WarehouseService.OWNER_PERSONAL);
            rowMap.put("target", target);
            rowMap.put("selected", WarehouseService.OWNER_PERSONAL.equals(state.ownerType()) && target.equals(state.warehouseId()));
            rowMap.put("name", WarehouseTexts.safe(String.valueOf(row.get("name"))));
            rowMap.put("text", service.message("ui.personal-entry", WarehouseTexts.safe(String.valueOf(row.get("name"))), row.get("capacity")));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        for (Map.Entry<String, Object> entry : sharedWarehouses.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> row)) {
                continue;
            }
            String target = WarehouseTexts.safe(String.valueOf(row.get("id")));
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("ownerType", WarehouseService.OWNER_SHARED);
            rowMap.put("target", target);
            rowMap.put("selected", WarehouseService.OWNER_SHARED.equals(state.ownerType()) && target.equals(state.ownerId()));
            rowMap.put("name", WarehouseTexts.safe(String.valueOf(row.get("name"))));
            rowMap.put("text", WarehouseTexts.safe(String.valueOf(row.get("manageText"))));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    Map<String, Object> sharedWarehousePacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (SharedWarehouseRecord shared : service.repository.loadSharedWarehouses(player.getUniqueId())) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("id", shared.id());
            rowMap.put("name", shared.name());
            rowMap.put("role", shared.viewerRole());
            rowMap.put("roleName", service.sharedRoleName(shared.viewerRole()));
            rowMap.put("storageText", service.message("ui.shared-name", shared.name()));
            rowMap.put("manageText", service.message("ui.shared-entry", shared.name(), service.sharedRoleName(shared.viewerRole()), shared.level(), shared.capacity()));
            rowMap.put("capacity", shared.capacity());
            rowMap.put("lockedBy", service.lockOwnerName(WarehouseService.ViewState.shared(shared.id(), player, false, false, false, "", service.configuration.search().defaultSortId())));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    Map<String, Object> sharedMemberPacket(Player player, WarehouseService.ViewState state) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!WarehouseService.OWNER_SHARED.equals(state.ownerType())) {
            return result;
        }
        Optional<SharedWarehouseRecord> shared = service.repository.loadSharedWarehouse(player.getUniqueId(), state.ownerId());
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            return result;
        }
        UUID ownerUuid = shared.get().ownerUuid();
        int idx = 0;
        for (SharedMemberRecord member : service.repository.loadSharedMembers(state.ownerId())) {
            if (member.playerUuid().equals(ownerUuid)) {
                continue;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(member.playerUuid());
            String memberName = offline.getName() != null
                ? offline.getName() : member.playerUuid().toString();
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("uuid", member.playerUuid().toString());
            rowMap.put("name", memberName);
            rowMap.put("role", member.role());
            rowMap.put("roleName", service.sharedRoleName(member.role()));
            rowMap.put("text", memberName + " " + service.sharedRoleName(member.role()));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    Map<String, Object> backpackPacket(Player player) {
        Map<String, Object> result = new LinkedHashMap<>();
        PlayerInventory inventory = player.getInventory();
        for (int raw = 9; raw <= 44; raw++) {
            int slot = service.toBukkitBackpackSlot(raw);
            ItemStack item = slot >= 0 && slot < inventory.getSize() ? inventory.getItem(slot) : null;
            if (item == null || item.getType().isAir()) {
                result.put(Integer.toString(raw), emptyBackpackPacket(raw, slot));
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", Integer.toString(raw));
            row.put("rawSlot", raw);
            row.put("slot", slot);
            row.put("empty", false);
            row.put("name", service.resolveDisplayName(item));
            row.put("amount", item.getAmount());
            row.put("category", service.resolveCategory(item));
            row.put("material", item.getType().name());
            row.put("itemJson", service.itemJson(item));
            row.put("lore", WarehouseTexts.loreLines(item));
            row.put("locked", service.isItemLocked(item));
            result.put(Integer.toString(raw), row);
        }
        return result;
    }

    Map<String, Object> emptyBackpackPacket(int rawSlot, int slot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", Integer.toString(rawSlot));
        row.put("rawSlot", rawSlot);
        row.put("slot", slot);
        row.put("empty", true);
        row.put("name", "");
        row.put("amount", 0L);
        row.put("category", "");
        row.put("material", "");
        row.put("itemJson", "");
        row.put("lore", List.of());
        row.put("locked", false);
        return row;
    }

    Map<String, Object> emptySlotPacket(int slot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", Integer.toString(slot));
        row.put("slot", slot);
        row.put("empty", true);
        row.put("name", "");
        row.put("amount", 0L);
        row.put("category", "");
        row.put("material", "");
        row.put("updated", "");
        row.put("lore", List.of());
        return row;
    }

    Map<String, Object> emptySelectionPacket() {
        Map<String, Object> row = emptySlotPacket(-1);
        row.put("name", service.message("ui.no-selected-item"));
        row.put("lore", List.of(service.message("ui.select-slot")));
        return row;
    }

    Map<String, Object> bankBalancePacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, BigDecimal> balances = service.repository.loadBankBalances(player.getUniqueId());
        int idx = 0;
        for (String currencyId : service.configuration.bankCurrencies()) {
            BigDecimal balance = balances.getOrDefault(currencyId, BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", currencyId);
            row.put("name", service.currencyDisplayName(currencyId));
            row.put("balance", service.formatCurrency(currencyId, balance));
            row.put("text", service.currencyDisplayName(currencyId) + "  " + service.formatCurrency(currencyId, balance));
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    Map<String, Object> depositProductPacket(Player player, String selectedCurrency) {
        Map<String, Object> result = new LinkedHashMap<>();
        String currencyFilter = WarehouseTexts.normalizeId(selectedCurrency);
        int idx = 0;
        for (DepositProductDefinition product : service.configuration.depositProducts().values()) {
            if (!canUseProduct(player, product)) {
                continue;
            }
            if (!currencyFilter.isBlank() && !WarehouseTexts.normalizeId(product.currencyId()).equals(currencyFilter)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", product.id());
            row.put("name", product.displayName());
            row.put("description", product.description());
            row.put("currency", product.currencyId());
            row.put("currencyName", service.currencyDisplayName(product.currencyId()));
            row.put("duration", product.durationSeconds());
            row.put("durationText", WarehouseTexts.formatDuration(product.durationSeconds()));
            row.put("min", service.formatCurrency(product.currencyId(), product.minAmount()));
            row.put("max", product.maxAmount().compareTo(BigDecimal.ZERO) <= 0 ? "不限" : service.formatCurrency(product.currencyId(), product.maxAmount()));
            // 利率阶梯文本：如 "1%" 或 "1%~2%"
            String rateText;
            if (product.interestTiers().isEmpty()) {
                rateText = "—";
            } else if (product.interestTiers().size() == 1) {
                rateText = product.interestTiers().get(0).rate().multiply(BigDecimal.valueOf(100)).toPlainString() + "%";
            } else {
                BigDecimal minRate = product.interestTiers().stream()
                    .map(InterestTier::rate).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                BigDecimal maxRate = product.interestTiers().stream()
                    .map(InterestTier::rate).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                rateText = minRate.multiply(BigDecimal.valueOf(100)).toPlainString() + "%~" + maxRate.multiply(BigDecimal.valueOf(100)).toPlainString() + "%";
            }
            row.put("rateText", rateText);
            // 利率阶梯数据，供 UI 端根据输入金额计算预期收益
            List<Map<String, Object>> tiers = new ArrayList<>();
            for (InterestTier tier : product.interestTiers()) {
                Map<String, Object> tierMap = new LinkedHashMap<>();
                tierMap.put("min", tier.minAmount().toPlainString());
                tierMap.put("max", tier.maxAmount().toPlainString());
                tierMap.put("rate", tier.rate().toPlainString());
                tiers.add(tierMap);
            }
            row.put("tiers", tiers);
            row.put("text", product.displayName());
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    Map<String, Object> fixedDepositPacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        int idx = 0;
        for (FixedDepositRecord deposit : service.repository.loadFixedDeposits(player.getUniqueId())) {
            DepositProductDefinition productDef = service.configuration.depositProduct(deposit.productId());
            String productName = productDef != null ? productDef.displayName() : deposit.productId();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", deposit.id());
            row.put("product", deposit.productId());
            row.put("productName", productName);
            row.put("currency", deposit.currencyId());
            row.put("currencyName", service.currencyDisplayName(deposit.currencyId()));
            row.put("principal", service.formatCurrency(deposit.currencyId(), deposit.principal()));
            row.put("rate", deposit.interestRate().multiply(BigDecimal.valueOf(100)).toPlainString() + "%");
            row.put("maturesAt", WarehouseService.TIME_FORMATTER.format(Instant.ofEpochMilli(deposit.maturesAt())));
            boolean matured = deposit.maturesAt() <= now;
            row.put("matured", matured);
            row.put("claimed", deposit.claimed());
            row.put("statusText", deposit.claimed() ? "&7已领取" : (matured ? "&a可领取" : "&e未到期"));
            // 预期本息 = 本金 + 本金 × 利率
            BigDecimal payout = deposit.principal()
                .add(deposit.principal().multiply(deposit.interestRate()))
                .setScale(8, java.math.RoundingMode.DOWN)
                .stripTrailingZeros();
            row.put("payout", service.formatCurrency(deposit.currencyId(), payout));
            row.put("text", productName);
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    Map<String, Object> searchResultPacket(WarehouseService.ViewState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        String keyword = WarehouseTexts.normalizeToken(state.search());
        int index = 0;
        for (SlotItemRecord item : service.loadSlots(state.ownerType(), state.ownerId(), state.warehouseId())) {
            if (!service.slotMatches(item, state.categoryId(), keyword)) {
                continue;
            }
            result.put(Integer.toString(index++), Map.of(
                "slot", item.slot(),
                "name", item.displayName(),
                "amount", item.amount(),
                "category", item.categoryId(),
                "material", item.materialId(),
                "text", "&0#" + item.slot() + " " + item.displayName() + "\n&7x" + item.amount() + " " + item.categoryId()
            ));
        }
        return result;
    }

    void putCapacityFields(Player player, WarehouseService.ViewState state, Map<String, Object> packet) throws Exception {
        String ownerType = state.ownerType();
        long capacity = currentWarehouseCapacity(player, ownerType, state.ownerId(), state.warehouseId());
        long used = service.countSlots(ownerType, state.ownerId(), state.warehouseId());
        packet.put("capacity", capacity);
        packet.put("used", used);
        packet.put("capacityText", used + "/" + capacity);
        packet.put("slotCount", capacity);
        packet.put("pageSize", pageSize());
        packet.put("level", 1);
        packet.put("canUpgrade", false);
        packet.put("slotPriceText", "");
        packet.put("slotPriceAmount", 0.0);
        packet.put("slotCurrencyName", "");
        packet.put("nextUpgradeText", service.message("ui.upgrade-unavailable"));
        if (WarehouseService.OWNER_SHARED.equals(state.ownerType())) {
            Optional<SharedWarehouseRecord> shared = currentSharedRecord(player, state);
            if (shared.isEmpty()) {
                packet.put("nextUpgradeText", service.message("ui.select-shared"));
                return;
            }
            SharedWarehouseRecord record = shared.get();
            packet.put("level", record.level());
            Map<Integer, WarehouseLevelDefinition> levels = service.configuration.shared().levels();
            if (levels.isEmpty()) {
                packet.put("nextUpgradeText", service.message("ui.shared-upgrade-not-configured"));
                return;
            }
            WarehouseLevelDefinition next = levels.get(record.level() + 1);
            if (next == null) {
                packet.put("nextUpgradeText", service.message("ui.max-level"));
                return;
            }
            if (!"owner".equalsIgnoreCase(record.viewerRole())) {
                packet.put("nextUpgradeText", service.message("ui.shared-upgrade-owner"));
                return;
            }
            WarehouseModuleConfiguration.UpgradeCost cost = upgradeCost(levels, record.level());
            packet.put("canUpgrade", true);
            packet.put("nextUpgradeText", cost == null
                ? service.message("ui.upgrade-to", next.level(), next.capacity())
                : service.message("ui.upgrade-to-cost", next.level(), next.capacity(), service.formatCurrencyWithName(cost.currencyId(), cost.amount())));
            return;
        }
        if (!WarehouseService.OWNER_PERSONAL.equals(state.ownerType())) {
            return;
        }
        WarehouseDefinition definition = service.configuration.warehouse(state.warehouseId());
        WarehouseRecord record = service.personalWarehouseMap(player.getUniqueId()).get(state.warehouseId());
        if (definition == null || record == null) {
            return;
        }
        // 逐格解锁模型：canUpgrade → 是否还能再买一格；nextUpgradeText → 槽位价格提示。
        long unlocked = definition.baseSlots() + Math.max(0L, record.extraSlots());
        if (unlocked >= definition.maxSlots()) {
            packet.put("nextUpgradeText", service.message("ui.buy-slots-maxed"));
            return;
        }
        WarehouseModuleConfiguration.UpgradeCost cost = definition.slotPrice();
        if (cost == null) {
            packet.put("nextUpgradeText", service.message("ui.buy-slots-not-configured"));
            return;
        }
        packet.put("canUpgrade", true);
        packet.put("slotPriceText", service.formatCurrencyWithName(cost.currencyId(), cost.amount()));
        packet.put("slotPriceAmount", cost.amount().doubleValue());
        packet.put("slotCurrencyName", service.currencyDisplayName(cost.currencyId()));
        packet.put("nextUpgradeText",
            service.message("ui.buy-slots-hint",
                service.formatCurrencyWithName(cost.currencyId(), cost.amount()),
                definition.maxSlots() - unlocked));
    }

    /**
     * 槽位展示 JSON：优先使用存入时冗余写入的 item_json（零反序列化开销）；
     * 历史遗留空值时才回退到反序列化 item_data 重新生成。
     */
    String displayItemJson(SlotItemRecord item) {
        String stored = item.itemJson();
        if (stored != null && !stored.isBlank()) {
            return xuanmo.arcartxsuite.api.util.ItemPreview.singleCount(stored);
        }
        if (item.itemData() == null || item.itemData().isBlank()) {
            return stored == null ? "" : stored;
        }
        try {
            ItemStack stack = ItemSerializer.deserialize(Base64.getDecoder().decode(item.itemData()));
            String json = service.itemJson(stack);
            return json.isBlank() ? (stored == null ? "" : stored) : json;
        } catch (RuntimeException exception) {
            return stored == null ? "" : stored;
        }
    }

    Comparator<SlotItemRecord> sortComparator(String sortId) {
        Comparator<SlotItemRecord> comparator = null;
        for (SortField field : service.configuration.sortProfile(sortId).fields()) {
            Comparator<SlotItemRecord> fieldComparator = switch (field.key().toLowerCase(Locale.ROOT)) {
                case "name" -> Comparator.comparing(
                    SlotItemRecord::displayName,
                    Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)
                );
                case "amount" -> Comparator.comparingLong(SlotItemRecord::amount);
                case "updated" -> Comparator.comparingLong(SlotItemRecord::updatedAt);
                case "created" -> Comparator.comparingLong(SlotItemRecord::createdAt);
                case "material" -> Comparator.comparing(
                    SlotItemRecord::materialId,
                    Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)
                );
                case "category" -> Comparator.comparing(
                    SlotItemRecord::categoryId,
                    Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)
                );
                case "warehouse" -> Comparator.comparing(
                    SlotItemRecord::warehouseId,
                    Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)
                );
                default -> null;
            };
            if (fieldComparator == null) {
                continue;
            }
            if (field.descending()) {
                fieldComparator = fieldComparator.reversed();
            }
            comparator = comparator == null
                ? fieldComparator
                : comparator.thenComparing(fieldComparator);
        }
        Comparator<SlotItemRecord> slotComparator = Comparator.comparingInt(SlotItemRecord::slot);
        return comparator == null ? slotComparator : comparator.thenComparing(slotComparator);
    }

    String sortText(String sortId) {
        return switch (WarehouseTexts.normalizeId(sortId)) {
            case "time" -> service.message("ui.sort-time");
            case "name" -> service.message("ui.sort-name");
            case "amount" -> service.message("ui.sort-amount");
            default -> WarehouseTexts.safe(sortId);
        };
    }

    List<SlotItemRecord> visibleSlots(WarehouseService.ViewState state) {
        String normalizedSearch = WarehouseTexts.normalizeToken(state.search());
        return service.loadSlots(state.ownerType(), state.ownerId(), state.warehouseId()).stream()
            .filter(item -> service.slotMatches(item, state.categoryId(), normalizedSearch))
            .sorted(sortComparator(state.sortId()))
            .toList();
    }

    int selectedDisplaySlot(WarehouseService.ViewState state, List<SlotItemRecord> precomputedSlots) {
        if (!service.validSlot(state.selectedSlot())) {
            return -1;
        }
        int pageSize = pageSize();
        int start = (Math.max(1, state.page()) - 1) * pageSize;
        for (int index = start; index < Math.min(precomputedSlots.size(), start + pageSize); index++) {
            if (precomputedSlots.get(index).slot() == state.selectedSlot()) {
                return index - start;
            }
        }
        return -1;
    }

    int actualSlotFromDisplay(WarehouseService.ViewState state, int displaySlot) {
        int pageSize = pageSize();
        if (displaySlot < 0 || displaySlot >= pageSize) {
            return -1;
        }
        List<SlotItemRecord> visibleSlots = visibleSlots(state);
        int index = (Math.max(1, state.page()) - 1) * pageSize + displaySlot;
        return index >= 0 && index < visibleSlots.size() ? visibleSlots.get(index).slot() : -1;
    }

    void clearFilteredSelection(WarehouseService.ViewState state) throws Exception {
        if (!service.validSlot(state.selectedSlot())) {
            return;
        }
        Optional<SlotItemRecord> selected = service.repository.loadSlot(state.ownerType(), state.ownerId(), state.warehouseId(), state.selectedSlot());
        if (selected.isEmpty() || !service.slotMatches(selected.get(), state.categoryId(), WarehouseTexts.normalizeToken(state.search()))) {
            state.setSelectedSlot(-1);
        }
    }

    int pageSize() {
        return Math.max(1, service.configuration.ui().pageSize());
    }

    /** 解析仓库当前容量：个人仓库 = base-slots + 已购买 extra_slots（逐格解锁模型）。 */
    long resolveCapacity(String ownerType, String ownerId, String warehouseId) throws Exception {
        if (!WarehouseService.OWNER_PERSONAL.equals(ownerType)) {
            return WarehouseService.SLOT_COUNT;
        }
        long unlocked = service.personalUnlockedSlots(UUID.fromString(ownerId), warehouseId);
        return unlocked <= 0 ? WarehouseService.SLOT_COUNT : unlocked;
    }

    long currentWarehouseCapacity(Player player, String ownerType, String ownerId, String warehouseId) throws Exception {
        if (WarehouseService.OWNER_SHARED.equals(ownerType)) {
            return service.repository.loadSharedWarehouse(player.getUniqueId(), ownerId)
                .map(SharedWarehouseRecord::capacity)
                .orElse((long) WarehouseService.SLOT_COUNT);
        }
        return resolveCapacity(ownerType, ownerId, warehouseId);
    }

    String currentWarehouseName(Player player, WarehouseService.ViewState state) throws Exception {
        UUID lookupUuid = state.previewMode() && state.previewTargetUuid() != null ? state.previewTargetUuid() : player.getUniqueId();
        if (WarehouseService.OWNER_SHARED.equals(state.ownerType())) {
            return service.repository.loadSharedWarehousesByOwner(lookupUuid).stream()
                .filter(shared -> shared.id().equals(state.ownerId()))
                .map(SharedWarehouseRecord::name)
                .findFirst()
                .orElse("共享仓库");
        }
        WarehouseRecord record = service.personalWarehouseMap(lookupUuid).get(state.warehouseId());
        if (record != null && record.customName() != null && !record.customName().isBlank()) {
            return record.customName();
        }
        WarehouseDefinition definition = service.configuration.warehouse(state.warehouseId());
        return definition == null ? "个人仓库" : ChatColor.translateAlternateColorCodes('&', definition.displayName());
    }

    String firstPersonalWarehouseId() {
        return service.configuration.warehouses().isEmpty() ? "personal" : service.configuration.warehouses().keySet().iterator().next();
    }

    SharedPermissionTier resolveSharedTier(Player player) {
        return service.configuration.shared().permissionTiers().values().stream()
            .filter(tier -> tier.permission().isBlank() || player.hasPermission(tier.permission()))
            .max(Comparator.comparingInt(SharedPermissionTier::priority))
            .orElseGet(() -> service.configuration.shared().permissionTiers().values().iterator().next());
    }

    boolean canUseProduct(Player player, DepositProductDefinition product) {
        return product.permission().isBlank() || player.hasPermission(product.permission());
    }

    WarehouseLevelDefinition sharedInitialLevel() {
        Map<Integer, WarehouseLevelDefinition> levels = service.configuration.shared().levels();
        if (levels.isEmpty()) {
            return null;
        }
        WarehouseLevelDefinition level = levels.get(service.configuration.shared().defaultLevel());
        return level == null ? levels.values().iterator().next() : level;
    }

    WarehouseModuleConfiguration.UpgradeCost upgradeCost(Map<Integer, WarehouseLevelDefinition> levels, int currentLevel) {
        WarehouseLevelDefinition current = levels.get(currentLevel);
        WarehouseLevelDefinition next = levels.get(currentLevel + 1);
        return current == null || current.upgradeCost() == null ? next == null ? null : next.upgradeCost() : current.upgradeCost();
    }

    Optional<SharedWarehouseRecord> currentSharedRecord(Player player, WarehouseService.ViewState state) throws Exception {
        if (!WarehouseService.OWNER_SHARED.equals(state.ownerType())) {
            return Optional.empty();
        }
        return service.repository.loadSharedWarehouse(player.getUniqueId(), state.ownerId());
    }
}
