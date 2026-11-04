package xuanmo.arcartxsuite.questgps.chemdah;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeViewLifecycleTest {
    @Test
    void enableDisableAndReenableReleaseOnlyTheOwnedType() {
        Map<String, Object> registry = new HashMap<>();
        Object type = new Object();
        var registration = new OwnedAddonRegistration<>(registry, "axs-view", type);
        registration.register();
        registration.register();
        assertSame(type, registry.get("axs-view"));
        registration.close();
        registration.close();
        assertFalse(registry.containsKey("axs-view"));
        registration.register();
        assertSame(type, registry.get("axs-view"));
    }

    @Test
    void registrationConflictNeverReplacesAnExistingAddon() {
        Object foreign = new Object();
        Map<String, Object> registry = new HashMap<>(Map.of("axs-view", foreign));
        var registration = new OwnedAddonRegistration<>(registry, "axs-view", new Object());
        assertThrows(IllegalStateException.class, registration::register);
        registration.close();
        assertSame(foreign, registry.get("axs-view"));
    }

    @Test
    void failedSecondOwnerAndStaleCleanupCannotRemoveTheCurrentOwner() {
        Map<String, Object> registry = new HashMap<>();
        Object first = new Object(), replacement = new Object();
        var owner = new OwnedAddonRegistration<>(registry, "axs-view", first);
        owner.register();
        var second = new OwnedAddonRegistration<>(registry, "axs-view", first);
        assertThrows(IllegalStateException.class, second::register);
        second.close();
        assertSame(first, registry.get("axs-view"));
        registry.put("axs-view", replacement);
        owner.close();
        assertSame(replacement, registry.get("axs-view"));
    }

    @Test
    void aRegistryThatRejectsThePutCannotBeReportedAsRegistered() {
        Map<String, Object> registry = new HashMap<>() {
            @Override public Object put(String key, Object value) { return null; }
        };
        var registration = new OwnedAddonRegistration<>(registry, "axs-view", new Object());
        assertThrows(IllegalStateException.class, registration::register);
        registration.close();
        assertTrue(registry.isEmpty());
    }

    @Test
    void reloadedAndRemovedTemplatesAreDetectedByIdentity() {
        Object first = new Object();
        assertTrue(QuestGpsNativeViews.sameTemplates(Map.of("quest", first), Map.of("quest", first)));
        assertFalse(QuestGpsNativeViews.sameTemplates(Map.of("quest", first), Map.of("quest", new Object())));
        assertFalse(QuestGpsNativeViews.sameTemplates(Map.of("quest", first), Map.of()));
    }

    @Test
    void publishedSnapshotsDoNotExposeMutableInputOrSurviveShutdown() {
        var index = new QuestViewIndex();
        var categories = new HashMap<String, xuanmo.arcartxsuite.questgps.QuestGpsCategory>();
        categories.put("daily", new xuanmo.arcartxsuite.questgps.QuestGpsCategory("daily", "日常", 1));
        index.publish(Map.of(), categories);
        categories.clear();
        assertTrue(index.snapshot().categories().containsKey("daily"));
        assertThrows(UnsupportedOperationException.class, () -> index.snapshot().categories().clear());
        index.clear();
        assertTrue(index.snapshot().categories().isEmpty());
        assertTrue(index.snapshot().quests().isEmpty());
    }
}
