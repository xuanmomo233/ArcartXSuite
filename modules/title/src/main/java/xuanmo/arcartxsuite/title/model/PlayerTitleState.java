package xuanmo.arcartxsuite.title.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家称号的完整运行时状态。
 * <p>
 * 包含玩家拥有的全部称号、各分组已装备的称号、展示称号 ID，
 * 提供授予、回收、隐藏、装备、卸下等不可变状态转换方法。
 *
 * @param playerUuid            玩家 UUID
 * @param equippedTitleIdsByGroup 各分组已装备的称号 ID 映射（分组 ID → 称号 ID）
 * @param ownedTitles           玩家拥有的称号映射（称号 ID → 拥有记录）
 * @param displayTitleId        展示称号 ID，为空表示无展示称号
 * @param updatedAt             最后更新时间
 */
public record PlayerTitleState(
    UUID playerUuid,
    Map<String, String> equippedTitleIdsByGroup,
    Map<String, PlayerOwnedTitle> ownedTitles,
    String displayTitleId,
    Instant updatedAt
) {
    public PlayerTitleState {
        equippedTitleIdsByGroup = immutableStringCopy(equippedTitleIdsByGroup);
        ownedTitles = immutableCopy(ownedTitles);
        displayTitleId = normalize(displayTitleId);
    }

    /**
     * @param playerUuid 玩家 UUID
     * @return 无任何称号的空状态
     */
    public static PlayerTitleState empty(UUID playerUuid) {
        return new PlayerTitleState(playerUuid, Map.of(), Map.of(), "", Instant.EPOCH);
    }

    /**
     * @param titleId 称号 ID
     * @return 是否拥有该称号
     */
    public boolean hasOwnedTitle(String titleId) {
        return ownedTitles.containsKey(titleId);
    }

    /**
     * 清理过期/未激活称号及无效装备记录，返回清理后的状态。
     *
     * @param now 当前时间
     * @return 清理后的状态；若无变化则返回 {@code this}
     */
    public PlayerTitleState sanitize(Instant now) {
        LinkedHashMap<String, PlayerOwnedTitle> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerOwnedTitle> entry : ownedTitles.entrySet()) {
            PlayerOwnedTitle ownedTitle = entry.getValue();
            if (ownedTitle == null || !ownedTitle.isEffective(now)) {
                continue;
            }
            sanitized.put(entry.getKey(), ownedTitle);
        }

        LinkedHashMap<String, String> sanitizedEquipped = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : equippedTitleIdsByGroup.entrySet()) {
            String groupId = normalize(entry.getKey());
            String titleId = normalize(entry.getValue());
            if (groupId.isBlank() || titleId.isBlank() || !sanitized.containsKey(titleId)) {
                continue;
            }
            sanitizedEquipped.put(groupId, titleId);
        }

        String sanitizedDisplayTitleId = normalize(displayTitleId);
        if (!sanitizedDisplayTitleId.isBlank()) {
            PlayerOwnedTitle displayOwned = sanitized.get(sanitizedDisplayTitleId);
            boolean stillEquipped = sanitizedEquipped.containsValue(sanitizedDisplayTitleId);
            if (displayOwned == null || !displayOwned.isEffective(now) || !stillEquipped) {
                sanitizedDisplayTitleId = "";
            }
        }

        if (sanitized.size() == ownedTitles.size()
            && sanitizedEquipped.equals(equippedTitleIdsByGroup)
            && sanitizedDisplayTitleId.equals(displayTitleId)) {
            return this;
        }
        return new PlayerTitleState(playerUuid, sanitizedEquipped, sanitized, sanitizedDisplayTitleId, now);
    }

    /**
     * 授予称号，若已拥有则刷新发放信息（保留原隐藏状态）。
     *
     * @param titleId     称号 ID
     * @param grantedAt   发放时间
     * @param activatesAt 激活时间
     * @param expiresAt   过期时间
     * @param now         更新时间
     * @param grantedBy   发放者
     * @return 授予后的新状态
     */
    public PlayerTitleState grant(String titleId, Instant grantedAt, Instant activatesAt, Instant expiresAt, Instant now, String grantedBy) {
        LinkedHashMap<String, PlayerOwnedTitle> updatedTitles = new LinkedHashMap<>(ownedTitles);
        boolean hidden = updatedTitles.containsKey(titleId) && updatedTitles.get(titleId).hidden();
        updatedTitles.put(titleId, new PlayerOwnedTitle(titleId, hidden, grantedAt, activatesAt, expiresAt, now, grantedBy));
        return new PlayerTitleState(playerUuid, equippedTitleIdsByGroup, updatedTitles, displayTitleId, now).sanitize(now);
    }

    /**
     * 回收称号，同时移除对应的装备记录与展示称号。
     *
     * @param titleId 称号 ID
     * @param now     更新时间
     * @return 回收后的新状态；若未拥有则返回 {@code this}
     */
    public PlayerTitleState revoke(String titleId, Instant now) {
        if (!ownedTitles.containsKey(titleId)) {
            return this;
        }
        LinkedHashMap<String, PlayerOwnedTitle> updatedTitles = new LinkedHashMap<>(ownedTitles);
        updatedTitles.remove(titleId);
        LinkedHashMap<String, String> updatedEquipped = new LinkedHashMap<>(equippedTitleIdsByGroup);
        updatedEquipped.entrySet().removeIf(entry -> titleId.equals(entry.getValue()));
        String updatedDisplayTitleId = titleId.equals(displayTitleId) ? "" : displayTitleId;
        return new PlayerTitleState(playerUuid, updatedEquipped, updatedTitles, updatedDisplayTitleId, now);
    }

    /**
     * 设置称号的隐藏状态。
     *
     * @param titleId 称号 ID
     * @param hidden  是否隐藏
     * @param now     更新时间
     * @return 修改后的新状态；若未拥有或状态未变则返回 {@code this}
     */
    public PlayerTitleState setHidden(String titleId, boolean hidden, Instant now) {
        PlayerOwnedTitle ownedTitle = ownedTitles.get(titleId);
        if (ownedTitle == null || ownedTitle.hidden() == hidden) {
            return this;
        }
        LinkedHashMap<String, PlayerOwnedTitle> updatedTitles = new LinkedHashMap<>(ownedTitles);
        updatedTitles.put(titleId, ownedTitle.withHidden(hidden, now));
        return new PlayerTitleState(playerUuid, equippedTitleIdsByGroup, updatedTitles, displayTitleId, now);
    }

    /**
     * 在指定分组装备称号。
     *
     * @param groupId 分组 ID
     * @param titleId 称号 ID
     * @param now     更新时间
     * @return 装备后的新状态；若分组或称号无效则返回 {@code this}
     */
    public PlayerTitleState equip(String groupId, String titleId, Instant now) {
        String normalizedGroupId = normalize(groupId);
        String normalizedTitleId = normalize(titleId);
        if (normalizedGroupId.isBlank() || normalizedTitleId.isBlank()) {
            return this;
        }
        if (!ownedTitles.containsKey(normalizedTitleId)) {
            return this;
        }
        LinkedHashMap<String, String> updatedEquipped = new LinkedHashMap<>(equippedTitleIdsByGroup);
        updatedEquipped.put(normalizedGroupId, normalizedTitleId);
        return new PlayerTitleState(playerUuid, updatedEquipped, ownedTitles, displayTitleId, now);
    }

    /**
     * 卸下指定分组的已装备称号。
     *
     * @param groupId 分组 ID
     * @param now     更新时间
     * @return 卸下后的新状态；若分组无效则返回 {@code this}
     */
    public PlayerTitleState unequipGroup(String groupId, Instant now) {
        String normalizedGroupId = normalize(groupId);
        if (normalizedGroupId.isBlank() || !equippedTitleIdsByGroup.containsKey(normalizedGroupId)) {
            return this;
        }
        LinkedHashMap<String, String> updatedEquipped = new LinkedHashMap<>(equippedTitleIdsByGroup);
        String removedTitleId = updatedEquipped.remove(normalizedGroupId);
        String updatedDisplayTitleId = removedTitleId != null && removedTitleId.equals(displayTitleId) ? "" : displayTitleId;
        return new PlayerTitleState(playerUuid, updatedEquipped, ownedTitles, updatedDisplayTitleId, now);
    }

    /**
     * 卸下所有分组的已装备称号。
     *
     * @param now 更新时间
     * @return 卸下后的新状态；若无装备则返回 {@code this}
     */
    public PlayerTitleState unequipAll(Instant now) {
        if (equippedTitleIdsByGroup.isEmpty()) {
            return this;
        }
        return new PlayerTitleState(playerUuid, Map.of(), ownedTitles, "", now);
    }

    /**
     * 设置展示称号 ID。
     *
     * @param titleId 称号 ID，为空表示清除展示称号
     * @param now     更新时间
     * @return 修改后的新状态；若未拥有该称号则返回 {@code this}
     */
    public PlayerTitleState withDisplayTitle(String titleId, Instant now) {
        String normalizedTitleId = normalize(titleId);
        if (normalizedTitleId.isBlank()) {
            return new PlayerTitleState(playerUuid, equippedTitleIdsByGroup, ownedTitles, "", now);
        }
        if (!ownedTitles.containsKey(normalizedTitleId)) {
            return this;
        }
        return new PlayerTitleState(playerUuid, equippedTitleIdsByGroup, ownedTitles, normalizedTitleId, now);
    }

    /**
     * @return 已隐藏的称号数量
     */
    public int hiddenCount() {
        int count = 0;
        for (PlayerOwnedTitle ownedTitle : ownedTitles.values()) {
            if (ownedTitle.hidden()) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, PlayerOwnedTitle> immutableCopy(Map<String, PlayerOwnedTitle> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, String> immutableStringCopy(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = normalize(entry.getKey());
            String value = normalize(entry.getValue());
            if (!key.isBlank() && !value.isBlank()) {
                sanitized.put(key, value);
            }
        }
        if (sanitized.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
