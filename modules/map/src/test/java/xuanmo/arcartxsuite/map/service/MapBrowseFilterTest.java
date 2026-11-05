package xuanmo.arcartxsuite.map.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapBrowseFilterTest {
    private Map<String, Object> rows() {
        Map<String, Object> rows = new LinkedHashMap<>();
        rows.put("0", Map.of("id", "b", "displayName", "&a北门", "unlocked", true, "x", 100, "z", 0));
        rows.put("1", Map.of("id", "a", "displayName", "Alpha", "unlocked", false, "x", 2, "z", 0));
        rows.put("2", Map.of("id", "c", "displayName", "远程", "unlocked", true, "x", 0, "z", 0, "remote", true));
        return rows;
    }
    @Test void searchesChineseNamesAndIgnoresFormatting() {
        var result = MapBrowseFilter.apply(rows(), new MapBrowseFilter.Options("北", false, false), 0, 0, true, true);
        assertEquals("b", ((Map<?, ?>) result.get("0")).get("id"));
        assertEquals(1, result.size());
    }
    @Test void searchesNamesWithoutCaseAndIds() {
        assertEquals(1, MapBrowseFilter.apply(rows(), new MapBrowseFilter.Options("ALPHA", false, false), 0, 0, true, true).size());
        assertEquals(1, MapBrowseFilter.apply(rows(), new MapBrowseFilter.Options("b", false, false), 0, 0, true, true).size());
    }
    @Test void onlyFiltersUnlockStateForAnchors() {
        var options = new MapBrowseFilter.Options("", true, false);
        assertEquals(2, MapBrowseFilter.apply(rows(), options, 0, 0, true, true).size());
        assertEquals(3, MapBrowseFilter.apply(rows(), options, 0, 0, true, false).size());
    }
    @Test void sortsDistanceOnlyWithinSameWorldAndRemoteLast() {
        var options = new MapBrowseFilter.Options("", false, true);
        var result = MapBrowseFilter.apply(rows(), options, 0, 0, true, true);
        assertEquals("a", ((Map<?, ?>) result.get("0")).get("id"));
        assertEquals("c", ((Map<?, ?>) result.get("2")).get("id"));
        assertEquals(MapBrowseFilter.apply(rows(), MapBrowseFilter.Options.DEFAULT, 0, 0, false, true),
            MapBrowseFilter.apply(rows(), options, 0, 0, false, true));
    }
    @Test void doesNotMutateMarkerSnapshotAndBoundsQuery() {
        var rows = rows(); var original = new LinkedHashMap<>(rows);
        assertTrue(MapBrowseFilter.apply(rows, new MapBrowseFilter.Options("不存在", false, false), 0, 0, true, true).isEmpty());
        assertEquals(original, rows);
        assertEquals(64, new MapBrowseFilter.Options("a".repeat(200), false, false).query().length());
    }
}
