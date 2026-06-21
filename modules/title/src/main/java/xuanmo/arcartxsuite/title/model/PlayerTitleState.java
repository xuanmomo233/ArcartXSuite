package xuanmo.arcartxsuite.title.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

    public static PlayerTitleState empty(UUID playerUuid) {
        return new PlayerTitleState(playerUuid, Map.of(), Map.of(), "", Instant.EPOCH);
    }

    public boolean hasOwnedTitle(String titleId) {
        return ownedTitles.containsKey(titleId);
    }

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

    public PlayerTitleState grant(String titleId, Instant grantedAt, Instant activatesAt, Instant expiresAt, Instant now, String grantedBy) {
        LinkedHashMap<String, PlayerOwnedTitle> updatedTitles = new LinkedHashMap<>(ownedTitles);
        boolean hidden = updatedTitles.containsKey(titleId) && updatedTitles.get(titleId).hidden();
        updatedTitles.put(titleId, new PlayerOwnedTitle(titleId, hidden, grantedAt, activatesAt, expiresAt, now, grantedBy));
        return new PlayerTitleState(playerUuid, equippedTitleIdsByGroup, updatedTitles, displayTitleId, now).sanitize(now);
    }

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

    public PlayerTitleState setHidden(String titleId, boolean hidden, Instant now) {
        PlayerOwnedTitle ownedTitle = ownedTitles.get(titleId);
        if (ownedTitle == null || ownedTitle.hidden() == hidden) {
            return this;
        }
        LinkedHashMap<String, PlayerOwnedTitle> updatedTitles = new LinkedHashMap<>(ownedTitles);
        updatedTitles.put(titleId, ownedTitle.withHidden(hidden, now));
        return new PlayerTitleState(playerUuid, equippedTitleIdsByGroup, updatedTitles, displayTitleId, now);
    }

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

    public PlayerTitleState unequipAll(Instant now) {
        if (equippedTitleIdsByGroup.isEmpty()) {
            return this;
        }
        return new PlayerTitleState(playerUuid, Map.of(), ownedTitles, "", now);
    }

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
