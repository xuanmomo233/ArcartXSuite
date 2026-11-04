package xuanmo.arcartxsuite.map.service;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapPreloadGeometryTest {
    @Test void includesThinDiagonalWithNoVertexMidpointOrCenterInside() {
        List<int[]> polygon = List.of(new int[]{-1000, -990}, new int[]{100, 110},
            new int[]{101, 109}, new int[]{-999, -991});
        assertFalse(MapPreloadService.pointInPolygon(polygon, 32, 32));
        assertTrue(MapPreloadService.chunkTouchesPolygon(polygon, 0, 0));
        var chunks = new HashSet<Long>();
        MapPreloadService.collectPolygonChunks(chunks, polygon);
        assertTrue(chunks.contains(0L));
        assertFalse(MapPreloadService.chunkTouchesPolygon(polygon, 0, 128));
    }

    @Test void supportsNegativeCoordinatesAndBoundaryContact() {
        assertTrue(MapPreloadService.chunkTouchesPolygon(
            List.of(new int[]{-128, -64}, new int[]{0, -64}, new int[]{0, -63}), -64, -64));
        assertFalse(MapPreloadService.chunkTouchesPolygon(
            List.of(new int[]{-200, -200}, new int[]{-180, -200}, new int[]{-180, -180}), -64, -64));
    }
}
