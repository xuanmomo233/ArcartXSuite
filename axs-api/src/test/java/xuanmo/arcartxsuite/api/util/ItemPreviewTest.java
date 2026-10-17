package xuanmo.arcartxsuite.api.util;

import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;

import static org.junit.jupiter.api.Assertions.*;

class ItemPreviewTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 64, 65, 127, 128, 256, Integer.MAX_VALUE})
    void previewUsesOneWithoutChangingRewardOrLegacySerialization(int amount) {
        ItemStack reward = new ItemStack(Material.PAPER, amount);
        ItemBridgeAPI bridge = new TestBridge();
        assertEquals("Count:1", bridge.itemToPreviewJson(reward).orElseThrow());
        assertEquals("Count:1", new IconResolver(bridge, null).itemToPreviewJson(reward));
        assertEquals("Count:" + amount, bridge.itemToJson(reward).orElseThrow());
        assertEquals(amount, reward.getAmount());
    }

    @Test
    void rawLegacyCountPreservesNestedItemsAndEscapedStrings() {
        String input = "{id:'mod:gun',Count:128b,tag:{Items:[{Count:64b}],lore:'a \\'Count:999b\\' b'}}";
        assertEquals(input.replace("Count:128b", "Count:1b"), ItemPreview.singleCount(input));
    }

    @Test
    void modernJsonPreservesComponentsAndOnlyChangesTopLevelCount() {
        String input = "{\"id\":\"mod:gun\",\"components\":{\"mod:data\":{\"count\":128}},\"count\":256}";
        assertEquals(input.substring(0, input.length() - 4) + "1}", ItemPreview.singleCount(input));
        assertEquals("{Count:1b,count:1}", ItemPreview.singleCount("{Count:128b,count:256}"));
    }

    @Test
    void rawIconResolverNormalizesWithoutRebuildingModNbt() {
        String input = "{\"id\":\"tacz:modern_kinetic_gun\",\"Count\":128b,\"tag\":{\"GunId\":\"tacz:ak47\"}}";
        IconDefinition icon = IconDefinition.load(input);
        assertNotNull(icon);
        assertEquals(input.replace("128b", "1b"), new IconResolver(null, null).resolveItemJson(null, icon));
    }

    @Test
    void leavesAbsentCountsAndInvalidCountValuesUntouched() {
        for (String value : new String[]{"", "{}", "{tag:{Count:128b}}", "{Count:128broken}", "{Count:'128'}"}) {
            assertEquals(value, ItemPreview.singleCount(value));
        }
        assertNull(ItemPreview.singleCount(null));
    }

    private static final class TestBridge implements ItemBridgeAPI {
        public boolean isAvailable() { return true; }
        public boolean initialize() { return true; }
        public void shutdown() { }
        public Optional<String> itemToJson(ItemStack item) { return Optional.of("Count:" + item.getAmount()); }
    }
}
