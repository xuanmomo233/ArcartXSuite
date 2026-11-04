package xuanmo.arcartxsuite.abyss.design;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.script.ScriptContext;
import xuanmo.arcartxsuite.abyss.script.ScriptRunner;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.stage.StageManager;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplateLoader;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DraftTrialTest {
    @Test void exampleDraftRunsThroughRoomActionBranchAndSettlement() throws Exception {
        rehearse("example", 90);
    }

    @Test void procgenDraftRunsThroughObjectActionBranchAndSettlement() throws Exception {
        rehearse("procgen", 60);
    }

    private void rehearse(String id, int seconds) throws Exception {
        var root = Path.of("src/main/resources");
        var directory = root.resolve("abysses").resolve(id);
        var config = directory.resolve("config.yml");
        byte[] before = Files.readAllBytes(config);
        var logger = Logger.getAnonymousLogger();
        var loader = new AbyssTemplateLoader(root.toFile(), logger);
        var template = loader.load(id, directory.toFile());
        assertNotNull(template);
        var design = new DesignSession(UUID.randomUUID(), new AbyssInstance(UUID.randomUUID(), template, null));
        design.put(new DesignObject("document:stages.yml", DesignObject.Kind.DOCUMENT, "stages.yml",
            "stages.yml", "", "", null, null, "misc:files", Map.of("content", List.of(
            "entry: first", "first:", "  complete-when: 'var:draft_event'",
            "  on-complete: ['flow_if var:draft_event setvar open yes']",
            "  next: [{target: last, condition: 'var:open'}]",
            "last:", "  complete-when: 'var:map_cleared'")), true));
        var draft = loader.loadPrepared(id, directory.toFile(), DesignWriter.prepare(template, design));
        assertNotNull(draft);
        assertTrue(template.stages().isEmpty());
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        Server server = mock(Server.class);
        when(server.isPrimaryThread()).thenReturn(true);
        serverField.set(null, server);
        try {
            var scheduler = mock(SchedulerAPI.class);
            var scheduled = new ArrayDeque<Runnable>();
            when(scheduler.scheduleOnce(isNull(), any(Runnable.class), anyLong())).thenAnswer(call -> {
                scheduled.add(call.getArgument(1));
                return mock(ScheduledTask.class);
            });
            var service = mock(AbyssService.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
            var stages = new StageManager(service, null, scheduler, logger);
            var runner = new ScriptRunner(null, scheduler, () -> service, logger);
            when(service.stages()).thenReturn(stages);
            stages.setScriptLauncher((instance, lines, trigger, vars) ->
                runner.run("stage", lines, ScriptContext.withVars(instance, trigger, vars), instance));
            String event = id.equals("example") ? "on-clear" : "click-script";
            var kind = id.equals("example") ? DesignObject.Kind.ROOM : DesignObject.Kind.NPC;
            try (var trial = new DraftTrial(draft, stages, runner, Map.of(new DraftTrial.Hook("object", event),
                    List.of("setvar draft_event yes", "console prohibited", "js: prohibited")), line -> { })) {
                trial.start();
                while (!scheduled.isEmpty()) scheduled.remove().run();
                trial.event("object", event, "draft", kind);
                trial.mapCleared();
                var trace = trial.traceSnapshot();
                assertTrue(trace.contains("分支 → last"), trace::toString);
                assertTrue(trace.contains("进入阶段 last"), trace::toString);
                assertTrue(trace.contains("通关结算 COUNTDOWN " + seconds + " 秒"), trace::toString);
                assertTrue(trace.contains("记录动作 grant_reward"), trace::toString);
                assertFalse(trace.stream().anyMatch(line -> line.contains("prohibited")), trace::toString);
                verify(service, never()).completeDungeon(any());
                verify(service, never()).rewards();
            }
            assertArrayEquals(before, Files.readAllBytes(config));
        } finally {
            serverField.set(null, previous);
        }
    }
}
