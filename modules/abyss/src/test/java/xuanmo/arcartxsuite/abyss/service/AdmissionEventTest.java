package xuanmo.arcartxsuite.abyss.service;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstanceManager;
import xuanmo.arcartxsuite.abyss.party.PartyManager;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.abyss.world.AbyssWorldPool;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionEventTest {
    static void set(Object object,String name,Object value)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    @Test void preparingAnAdmissionDoesNotPublishProgressBeforePaymentAndTeleport()throws Exception {
        var cfg=AbyssModuleConfiguration.load(new YamlConfiguration());
        Constructor<?> constructor=AbyssService.class.getDeclaredConstructors()[0];Object[] args=new Object[constructor.getParameterCount()];
        for(int i=0;i<args.length;i++) {
            if(constructor.getParameterTypes()[i]==AbyssModuleConfiguration.class)args[i]=cfg;
            if(constructor.getParameterTypes()[i]==Logger.class)args[i]=Logger.getAnonymousLogger();
        }
        var service=(AbyssService)constructor.newInstance(args);
        var bus=mock(EventBusCapability.class);service.setEventBusProvider(()->bus);
        var instances=new AbyssInstanceManager();set(service,"instances",instances);set(service,"started",true);
        set(service,"partyManager",new PartyManager(cfg.party(),s->{},null));
        var template=mock(AbyssTemplate.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(template.id()).thenReturn("test");when(template.mode()).thenReturn(AbyssTemplate.Mode.CLASSIC);when(template.plans()).thenReturn(Map.of());
        instances.registerTemplate(template);
        var checker=mock(RequirementChecker.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(checker.check(eq(template),anyList(),isNull())).thenReturn(RequirementChecker.CheckResult.pass());set(service,"requirementChecker",checker);
        var pool=mock(AbyssWorldPool.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(pool.acquireWorld(eq(template),any())).thenReturn(new CompletableFuture<World>());set(service,"worldPool",pool);
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);
        assertTrue(service.enterDungeon(List.of(player),"test").ok());verifyNoInteractions(bus);
    }
}
