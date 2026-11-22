package xuanmo.arcartxsuite.title.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerTitleStateSanitizeTest {
    private final Instant now = Instant.parse("2026-11-16T12:00:00Z");
    private final UUID playerId = UUID.randomUUID();

    @Test
    void validStateReusesItsImmutableMaps() {
        var title = owned("hero", null, null);
        var state = new PlayerTitleState(playerId, Map.of(" MAIN ", " HERO "), Map.of("hero", title), " HERO ", now);
        assertSame(state, state.sanitize(now));
        assertEquals("hero", state.equippedTitleIdsByGroup().get("main"));
    }

    @Test
    void committedOwnershipKeepsItsFieldsAndAnIdenticalRecordReusesTheState() {
        var original = owned("hero", null, null);
        var state = new PlayerTitleState(playerId, Map.of("main", "hero"), Map.of("hero", original), "hero", now);
        var committed = new PlayerOwnedTitle("hero", true, now.minusSeconds(20), null, null, now.minusSeconds(1), "database");
        var applied = state.withOwnedTitle(committed, now);
        assertSame(committed, applied.ownedTitles().get("hero"));
        assertTrue(applied.ownedTitles().get("hero").hidden());
        assertSame(applied, applied.withOwnedTitle(committed, now));
        assertFalse(state.ownedTitles().get("hero").hidden());
    }

    @Test
    void expiryIsCheckedAtTheExactBoundary() {
        var state = new PlayerTitleState(playerId, Map.of("main", "hero"),
            Map.of("hero", owned("hero", null, now)), "hero", now.minusSeconds(1));
        assertSame(state, state.sanitize(now.minusNanos(1)));
        var expired = state.sanitize(now);
        assertTrue(expired.ownedTitles().isEmpty());
        assertTrue(expired.equippedTitleIdsByGroup().isEmpty());
        assertEquals("", expired.displayTitleId());
        assertTrue(state.hasOwnedTitle("hero"));
        assertSame(expired, expired.sanitize(now));
    }

    @Test
    void mixedCleanupPreservesOrderAndTheOriginalSnapshot() {
        Map<String, PlayerOwnedTitle> titles = new LinkedHashMap<>();
        titles.put("first", owned("first", null, null));
        titles.put("expired", owned("expired", null, now));
        titles.put("future", owned("future", now.plusSeconds(1), null));
        titles.put("missing", null);
        titles.put("last", owned("last", null, null));
        var state = new PlayerTitleState(playerId, Map.of("main", "expired", "secondary", "last"), titles, "expired", now);
        var cleaned = state.sanitize(now);
        assertEquals(List.of("first", "last"), new ArrayList<>(cleaned.ownedTitles().keySet()));
        assertEquals(Map.of("secondary", "last"), cleaned.equippedTitleIdsByGroup());
        assertEquals("", cleaned.displayTitleId());
        assertEquals(5, state.ownedTitles().size());
        assertThrows(UnsupportedOperationException.class, () -> cleaned.ownedTitles().clear());
    }

    @Test
    void cleanupMatchesThePreviousAlgorithmForMixedStates() {
        Random random = new Random(20361116);
        for (int sample = 0; sample < 500; sample++) {
            Map<String, PlayerOwnedTitle> titles = new LinkedHashMap<>();
            Map<String, String> equipped = new LinkedHashMap<>();
            for (int index = 0; index < 25; index++) {
                String id = "title" + index;
                Instant activation = random.nextBoolean() ? null : now.plusSeconds(random.nextInt(9) - 4);
                Instant expiry = random.nextBoolean() ? null : now.plusSeconds(random.nextInt(9) - 4);
                titles.put(id, random.nextInt(15) == 0 ? null : owned(id, activation, expiry));
                if (index % 3 == 0) equipped.put(" GROUP" + index + " ", id.toUpperCase(Locale.ROOT));
            }
            equipped.put("unknown", "absent");
            String display = random.nextBoolean() ? "title" + random.nextInt(25) : "";
            var state = new PlayerTitleState(playerId, equipped, titles, display, now.minusSeconds(10));
            var expected = previousSanitize(state);
            var actual = state.sanitize(now);
            assertEquals(expected, actual, "sample " + sample);
            assertEquals(new ArrayList<>(expected.ownedTitles().keySet()), new ArrayList<>(actual.ownedTitles().keySet()));
            assertEquals(new ArrayList<>(expected.equippedTitleIdsByGroup().keySet()),
                new ArrayList<>(actual.equippedTitleIdsByGroup().keySet()));
        }
    }

    private PlayerOwnedTitle owned(String id, Instant activation, Instant expiry) {
        return new PlayerOwnedTitle(id, false, now.minusSeconds(30), activation, expiry, now, "test");
    }

    private PlayerTitleState previousSanitize(PlayerTitleState state) {
        Map<String, PlayerOwnedTitle> titles = new LinkedHashMap<>();
        for (var entry : state.ownedTitles().entrySet()) {
            if (entry.getValue() != null && entry.getValue().isEffective(now)) titles.put(entry.getKey(), entry.getValue());
        }
        Map<String, String> equipped = new LinkedHashMap<>();
        for (var entry : state.equippedTitleIdsByGroup().entrySet()) {
            String group = entry.getKey().trim().toLowerCase(Locale.ROOT);
            String title = entry.getValue().trim().toLowerCase(Locale.ROOT);
            if (!group.isBlank() && !title.isBlank() && titles.containsKey(title)) equipped.put(group, title);
        }
        String display = state.displayTitleId().trim().toLowerCase(Locale.ROOT);
        if (!display.isBlank() && (!titles.containsKey(display) || !equipped.containsValue(display))) display = "";
        if (titles.size() == state.ownedTitles().size() && equipped.equals(state.equippedTitleIdsByGroup())
            && display.equals(state.displayTitleId())) return state;
        return new PlayerTitleState(state.playerUuid(), equipped, titles, display, now);
    }
}
