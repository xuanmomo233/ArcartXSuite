package xuanmo.arcartxsuite.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Registers the exact client packet IDs handled by a module. Matching ignores case. */
public record PacketHandlerSpec(
    @Nullable ClientPacketHandler handler,
    int priority,
    @NotNull List<String> packetIds,
    @Nullable String guardModule
) {
    public static final PacketHandlerSpec NONE = new PacketHandlerSpec(null, 0, List.of(), null);

    public PacketHandlerSpec {
        var ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ids.addAll(packetIds);
        packetIds = List.copyOf(ids);
    }

    public static PacketHandlerSpec of(@NotNull ClientPacketHandler handler, int priority,
                                       @NotNull String packetId, @Nullable String guardModule,
                                       String... additionalPacketIds) {
        var ids = new java.util.ArrayList<String>(additionalPacketIds.length + 1);
        ids.add(Objects.requireNonNull(packetId));
        Collections.addAll(ids, additionalPacketIds);
        return new PacketHandlerSpec(handler, priority, ids, guardModule);
    }
}
