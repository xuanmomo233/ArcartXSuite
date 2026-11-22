package xuanmo.arcartxsuite.vanilla.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.banner.PatternType;
import org.junit.jupiter.api.Test;

class NativeWorkbenchSnapshotsTest {
    @Test
    void loomKeepsChoicesForSelectionChangesAndDecodesAReorderedNativeList() throws Exception {
        var holderValue = PatternHolder.class.getMethod("value");
        var patternCode = NativePattern.class.getMethod("code");
        var bottom = new PatternHolder(new NativePattern("bs"));
        var top = new PatternHolder(new NativePattern("ts"));
        var patterns = new ArrayList<>(List.of(bottom, top));

        var first = ArtisanNativeAccess.snapshotLoom(patterns, 0, null, holderValue, patternCode);
        assertEquals(PatternType.STRIPE_BOTTOM, first.choices().get(0).pattern());
        assertEquals(PatternType.STRIPE_TOP, first.choices().get(1).pattern());
        assertSame(first, ArtisanNativeAccess.snapshotLoom(List.copyOf(patterns), 0, first, holderValue, patternCode));

        var selection = ArtisanNativeAccess.snapshotLoom(patterns, 1, first, holderValue, patternCode);
        assertEquals(1, selection.selected());
        assertSame(first.choices(), selection.choices());
        assertEquals(1, bottom.reads);
        assertEquals(1, top.reads);

        patterns.set(0, top);
        patterns.set(1, bottom);
        var reordered = ArtisanNativeAccess.snapshotLoom(patterns, 0, selection, holderValue, patternCode);
        assertEquals(List.of("ts", "bs"), reordered.choices().stream().map(ArtisanNativeAccess.Choice::code).toList());
        assertEquals(0, reordered.choices().get(0).index());
        assertEquals(2, bottom.reads);
        assertEquals(2, top.reads);
    }

    @Test
    void enchantingReusesStableOffersAndDetectsChangesToLiveArraysAndSeed() {
        int[] costs = {1, 8, 30};
        int[] clues = {1, 2, 3};
        int[] levels = {1, 2, 3};
        var first = EquipmentNativeAccess.snapshotOffers(costs, clues, levels, 7, null);
        assertSame(first, EquipmentNativeAccess.snapshotOffers(costs, clues, levels, 7, first));

        costs[0] = 2;
        clues[1] = 3;
        levels[2] = 4;
        var updated = EquipmentNativeAccess.snapshotOffers(costs, clues, levels, 7, first);
        assertNotSame(first, updated);
        assertArrayEquals(new int[]{1, 8, 30}, first.costs());
        assertArrayEquals(new int[]{1, 2, 3}, first.clues());
        assertArrayEquals(new int[]{1, 2, 3}, first.levels());
        assertArrayEquals(costs, updated.costs());
        assertArrayEquals(clues, updated.clues());
        assertArrayEquals(levels, updated.levels());
        assertNotSame(updated, EquipmentNativeAccess.snapshotOffers(costs, clues, levels, 8, updated));
    }

    public record NativePattern(String code) {}

    public static final class PatternHolder {
        private final NativePattern pattern;
        private int reads;

        PatternHolder(NativePattern pattern) {
            this.pattern = pattern;
        }

        public NativePattern value() {
            reads++;
            return pattern;
        }
    }
}
