/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.util.debug;

import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.util.debug.Debug;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000J\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001\u001fB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001e\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\r2\u0006\u0010\u0017\u001a\u00020\u0018J\u0016\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u0016\u001a\u00020\rJ\u001e\u0010\u001d\u001a\u00020\u00132\u0006\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u0016\u001a\u00020\r2\u0006\u0010\u001e\u001a\u00020\u001aR\u001c\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR#\u0010\t\u001a\u0014\u0012\u0004\u0012\u00020\u000b\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u001d\u0010\u0010\u001a\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\u00040\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u000f\u00a8\u0006 "}, d2={"Link/ptms/chemdah/util/debug/DebugHandler;", "", "()V", "consoleWatchState", "Link/ptms/chemdah/util/debug/DebugHandler$WatchState;", "getConsoleWatchState", "()Link/ptms/chemdah/util/debug/DebugHandler$WatchState;", "setConsoleWatchState", "(Link/ptms/chemdah/util/debug/DebugHandler$WatchState;)V", "players", "Ljava/util/concurrent/ConcurrentHashMap;", "Ljava/util/UUID;", "", "Link/ptms/chemdah/util/debug/Debug;", "getPlayers", "()Ljava/util/concurrent/ConcurrentHashMap;", "watchStates", "getWatchStates", "forwardToWatchers", "", "target", "Lorg/bukkit/entity/Entity;", "debug", "message", "", "isDebugMode", "", "player", "Lorg/bukkit/entity/Player;", "setDebugMode", "enabled", "WatchState", "Chemdah"})
public final class DebugHandler {
    @NotNull
    public static final DebugHandler INSTANCE = new DebugHandler();
    @NotNull
    private static final ConcurrentHashMap<UUID, List<Debug>> players = new ConcurrentHashMap();
    @NotNull
    private static final ConcurrentHashMap<UUID, WatchState> watchStates = new ConcurrentHashMap();
    @Nullable
    private static WatchState consoleWatchState;

    private DebugHandler() {
    }

    @NotNull
    public final ConcurrentHashMap<UUID, List<Debug>> getPlayers() {
        return players;
    }

    @NotNull
    public final ConcurrentHashMap<UUID, WatchState> getWatchStates() {
        return watchStates;
    }

    @Nullable
    public final WatchState getConsoleWatchState() {
        return consoleWatchState;
    }

    public final void setConsoleWatchState(@Nullable WatchState watchState) {
        consoleWatchState = watchState;
    }

    public final boolean isDebugMode(@NotNull Player player2, @NotNull Debug debug2) {
        List<Debug> types;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)((Object)debug2), (String)"debug");
        List<Debug> list2 = types = players.get(player2.getUniqueId());
        return list2 != null ? list2.contains((Object)debug2) : false;
    }

    public final void setDebugMode(@NotNull Player player2, @NotNull Debug debug2, boolean enabled) {
        List currentTypes;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)((Object)debug2), (String)"debug");
        List list2 = players.get(player2.getUniqueId());
        if (list2 == null || (list2 = CollectionsKt.toMutableList((Collection)list2)) == null) {
            list2 = currentTypes = (List)new ArrayList();
        }
        if (enabled) {
            if (!currentTypes.contains((Object)debug2)) {
                currentTypes.add(debug2);
                Map map = players;
                UUID uUID = player2.getUniqueId();
                Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"player.uniqueId");
                map.put(uUID, currentTypes);
            }
        } else {
            currentTypes.remove((Object)debug2);
            if (currentTypes.isEmpty()) {
                players.remove(player2.getUniqueId());
            } else {
                Map map = players;
                UUID uUID = player2.getUniqueId();
                Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"player.uniqueId");
                map.put(uUID, currentTypes);
            }
        }
    }

    public final void forwardToWatchers(@NotNull Entity target, @NotNull Debug debug2, @NotNull String message2) {
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        Intrinsics.checkNotNullParameter((Object)((Object)debug2), (String)"debug");
        Intrinsics.checkNotNullParameter((Object)message2, (String)"message");
        UUID uUID = target.getUniqueId();
        Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"target.uniqueId");
        UUID targetId = uUID;
        String formatted = UtilKt.colored((String)("&c[#][" + debug2.getDisplay() + "] &e[" + target.getName() + "] &7" + message2));
        for (Map.Entry entry : ((Map)watchStates).entrySet()) {
            Player watcher;
            UUID watcherId = (UUID)entry.getKey();
            WatchState state = (WatchState)entry.getValue();
            if (!Intrinsics.areEqual((Object)state.getTarget(), (Object)targetId) || !state.getTypes().contains((Object)debug2) || Bukkit.getPlayer((UUID)watcherId) == null) continue;
            watcher.sendMessage(formatted);
        }
        WatchState cs = consoleWatchState;
        if (cs != null && Intrinsics.areEqual((Object)cs.getTarget(), (Object)targetId) && cs.getTypes().contains((Object)debug2)) {
            AdapterKt.console().sendMessage(formatted);
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u000e\b\u0002\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a2\u0006\u0002\u0010\u0007R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0017\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/util/debug/DebugHandler$WatchState;", "", "target", "Ljava/util/UUID;", "types", "", "Link/ptms/chemdah/util/debug/Debug;", "(Ljava/util/UUID;Ljava/util/List;)V", "getTarget", "()Ljava/util/UUID;", "getTypes", "()Ljava/util/List;", "Chemdah"})
    public static final class WatchState {
        @NotNull
        private final UUID target;
        @NotNull
        private final List<Debug> types;

        public WatchState(@NotNull UUID target, @NotNull List<Debug> types) {
            Intrinsics.checkNotNullParameter((Object)target, (String)"target");
            Intrinsics.checkNotNullParameter(types, (String)"types");
            this.target = target;
            this.types = types;
        }

        public /* synthetic */ WatchState(UUID uUID, List list2, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 2) != 0) {
                list2 = new ArrayList();
            }
            this(uUID, list2);
        }

        @NotNull
        public final UUID getTarget() {
            return this.target;
        }

        @NotNull
        public final List<Debug> getTypes() {
            return this.types;
        }
    }
}

