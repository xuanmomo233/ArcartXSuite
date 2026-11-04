package xuanmo.arcartxsuite.abyss.stage;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.FailReason;
import xuanmo.arcartxsuite.abyss.script.ScriptSession;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.abyss.template.StageConfig;
import xuanmo.arcartxsuite.abyss.template.StageConfig.Branch;
import xuanmo.arcartxsuite.abyss.template.StageConfig.Stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class StageProgressionTest {
    private final AbyssService service = inline(AbyssService.class);
    private final StageManager manager = new StageManager(service, null, null, Logger.getAnonymousLogger());

    @Test
    void waitsForActionsAndKeepsUnmatchedBranchesPendingWithoutRepeatingActions() {
        var first = new Stage(null, null, 0, List.of(), List.of("wait 1"), List.of(),
            List.of(new Branch("var:route == 1", "end")));
        var instance = instance(Map.of("start", first, "end", terminal()));
        var actions = new ScriptSession("completion", List.of(), null, null);
        var launches = new java.util.concurrent.atomic.AtomicInteger();
        manager.setScriptLauncher((inst, lines, player, vars) -> {
            launches.incrementAndGet();
            manager.completeCurrent(inst, null);
            return actions;
        });
        manager.start(instance);
        manager.completeCurrent(instance, null);
        manager.onEvent(instance);
        assertEquals("start", manager.currentStage(instance));
        actions.completion().complete(true);
        verify(service, never()).completeDungeon(instance);
        instance.getVariables().put("route", 1);
        manager.onEvent(instance);
        assertEquals("end", manager.currentStage(instance));
        assertEquals(1, launches.get());
        manager.completeCurrent(instance, null);
        verify(service).completeDungeon(instance);
    }

    @Test
    void staleCompletionCannotOverrideAnExplicitJump() {
        var first = new Stage(null, null, 0, List.of(), List.of("wait 1"), List.of(),
            List.of(new Branch(null, "end")));
        var instance = instance(Map.of("start", first, "end", terminal(), "override", terminal()));
        var actions = new ScriptSession("completion", List.of(), null, null);
        manager.setScriptLauncher((inst, lines, player, vars) -> actions);
        manager.start(instance);
        manager.completeCurrent(instance, null);
        manager.jump(instance, "override", null);
        actions.completion().complete(true);
        assertEquals("override", manager.currentStage(instance));
    }

    @Test
    void immediatelySatisfiedCycleFailsWithoutUnboundedRecursion() {
        var loop = new Stage(null, "var:ready", 0, List.of(), List.of(), List.of(),
            List.of(new Branch(null, "start")));
        var instance = instance(Map.of("start", loop));
        instance.getVariables().put("ready", true);
        manager.start(instance);
        verify(service).failDungeon(instance, FailReason.SCRIPTED);
        verify(service, never()).completeDungeon(instance);
    }

    private AbyssInstance instance(Map<String, Stage> stages) {
        AbyssTemplate template = inline(AbyssTemplate.class);
        when(template.stages()).thenReturn(new StageConfig("start", stages));
        return new AbyssInstance(UUID.randomUUID(), template, null);
    }

    private static Stage terminal() {
        return new Stage(null, null, 0, List.of(), List.of(), List.of(), List.of());
    }

    private static <T> T inline(Class<T> type) {
        return mock(type, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
    }
}
