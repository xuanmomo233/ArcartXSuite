/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.util.debug;

import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.util.debug.Debug;
import ink.ptms.chemdah.util.debug.DebugHandler;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u0016\n\u0000\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\u001a\u001a\u0010\u0000\u001a\u00020\u0001*\u00020\u00022\u0006\u0010\u0000\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005\u00a8\u0006\u0006"}, d2={"debug", "", "Lorg/bukkit/entity/Entity;", "Link/ptms/chemdah/util/debug/Debug;", "message", "", "Chemdah"})
public final class DebugHandlerKt {
    public static final boolean debug(@NotNull Entity $this$debug, @NotNull Debug debug2, @NotNull String message2) {
        Intrinsics.checkNotNullParameter((Object)$this$debug, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)((Object)debug2), (String)"debug");
        Intrinsics.checkNotNullParameter((Object)message2, (String)"message");
        boolean sent = false;
        if ($this$debug instanceof Player && DebugHandler.INSTANCE.isDebugMode((Player)$this$debug, debug2)) {
            $this$debug.sendMessage(UtilKt.colored((String)("&c[#][" + debug2.getDisplay() + "] &7" + message2)));
            sent = true;
        }
        if (!((Map)DebugHandler.INSTANCE.getWatchStates()).isEmpty() || DebugHandler.INSTANCE.getConsoleWatchState() != null) {
            DebugHandler.INSTANCE.forwardToWatchers($this$debug, debug2, message2);
        }
        return sent;
    }
}

