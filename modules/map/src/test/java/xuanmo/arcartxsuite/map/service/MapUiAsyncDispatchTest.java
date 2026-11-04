package xuanmo.arcartxsuite.map.service;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapUiAsyncDispatchTest {
    private final Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
        (proxy,method,args)->switch(method.getName()) { case "isOnline","hasPermission"->true; default->null; });

    @Test void pendingOperationRetainsTokenAndFailureAllowsRetry() {
        AtomicBoolean busy=new AtomicBoolean(); AtomicInteger dispatched=new AtomicInteger();
        CompletableFuture<Boolean> pending=new CompletableFuture<>();
        var target=target((name,args)->switch(name) {
            case "beginActionToken"->busy.compareAndSet(false,true);
            case "dispatchAsyncAction"->{ dispatched.incrementAndGet(); pending.whenComplete((result,error)->busy.set(false)); yield true; }
            case "finishActionToken"-> { fail("router must not release the asynchronous owner's token"); yield null; }
            default->null;
        });
        MapUiPacketHandler handler=new MapUiPacketHandler(target,"AXS_MAP");
        assertTrue(handler.handleClientPacket(player,"AXS_MAP",List.of("create_waypoint","token")));
        assertTrue(busy.get());
        handler.handleClientPacket(player,"AXS_MAP",List.of("create_waypoint","token"));
        assertEquals(1,dispatched.get());
        pending.complete(false);
        handler.handleClientPacket(player,"AXS_MAP",List.of("create_waypoint","token"));
        assertEquals(2,dispatched.get());
    }

    @Test void synchronousHandoffFailureReleasesToken() {
        AtomicBoolean released=new AtomicBoolean();
        var target=target((name,args)->switch(name) {
            case "beginActionToken"->true;
            case "dispatchAsyncAction"->throw new IllegalStateException("handoff failed");
            case "finishActionToken"->{assertEquals(false,args[1]);released.set(true);yield null;}
            default->null;
        });
        assertThrows(IllegalStateException.class,()->new MapUiPacketHandler(target,"AXS_MAP")
            .handleClientPacket(player,"AXS_MAP",List.of("unlock_anchor","anchor","token")));
        assertTrue(released.get());
    }

    @Test void invalidTokenNeverReachesTheStoragePipeline() {
        var target=target((name,args)->switch(name) {
            case "beginActionToken"->false;
            case "dispatchAsyncAction"->{fail("invalid token reached storage");yield false;}
            default->null;
        });
        assertTrue(new MapUiPacketHandler(target,"AXS_MAP").handleClientPacket(player,"AXS_MAP",List.of("create_waypoint","bad")));
    }

    interface Call {Object invoke(String name,Object[] args);}
    private static MapUiPacketHandler.ActionTarget target(Call call) {
        return (MapUiPacketHandler.ActionTarget)Proxy.newProxyInstance(MapUiPacketHandler.ActionTarget.class.getClassLoader(),
            new Class<?>[]{MapUiPacketHandler.ActionTarget.class},(proxy,method,args)->call.invoke(method.getName(),args));
    }
}
