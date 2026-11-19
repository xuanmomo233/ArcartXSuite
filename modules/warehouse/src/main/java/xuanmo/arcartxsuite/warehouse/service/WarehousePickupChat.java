package xuanmo.arcartxsuite.warehouse.service;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.bukkit.entity.Player;

/** The Paper component API avoids reparsing a repeated legacy chat line for every recipient. */
final class WarehousePickupChat {
    private record PaperChat(MethodHandle deserialize, MethodHandle send) {}
    private static final PaperChat PAPER = findPaperChat();

    private WarehousePickupChat() {}

    static Object prepare(String legacy, String body) {
        // Keep CraftBukkit's handling of custom multiline text and links.
        if (PAPER == null || body.indexOf('.') >= 0 || body.indexOf(':') >= 0
            || body.indexOf('\n') >= 0 || body.indexOf('\r') >= 0) return null;
        try {
            return PAPER.deserialize().invokeExact(legacy);
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError error) throw error;
            if (failure instanceof ThreadDeath death) throw death;
            return null;
        }
    }

    static void send(Player player, String legacy, Object component) {
        if (component == null) {
            player.sendMessage(legacy);
            return;
        }
        try {
            PAPER.send().invokeExact(player, component);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static PaperChat findPaperChat() {
        try {
            Class<?> component = Class.forName("net.kyori.adventure.text.Component");
            Class<?> serializerType = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle send = lookup.unreflect(Player.class.getMethod("sendMessage", component))
                .asType(MethodType.methodType(void.class, Player.class, Object.class));
            Object serializer = serializerType.getMethod("legacySection").invoke(null);
            MethodHandle deserialize = lookup.unreflect(serializerType.getMethod("deserialize", String.class))
                .bindTo(serializer).asType(MethodType.methodType(Object.class, String.class));
            return new PaperChat(deserialize, send);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return null;
        }
    }
}
