/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.data.ControlResult;
import ink.ptms.chemdah.core.quest.addon.data.ControlTrigger;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000<\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0000\b&\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u001e\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH&J\u0018\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH&J\u0014\u0010\u0010\u001a\u00020\t*\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0013H\u0004R\u0014\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/core/quest/addon/data/Control;", "", "()V", "trigger", "Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "getTrigger", "()Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "check", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/core/quest/addon/data/ControlResult;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "template", "Link/ptms/chemdah/core/quest/Template;", "signature", "", "toResult", "", "reason", "", "Chemdah"})
public abstract class Control {
    @Nullable
    public abstract ControlTrigger getTrigger();

    @NotNull
    public abstract CompletableFuture<ControlResult> check(@NotNull PlayerProfile var1, @NotNull Template var2);

    public abstract void signature(@NotNull PlayerProfile var1, @NotNull Template var2);

    @NotNull
    protected final ControlResult toResult(boolean $this$toResult, @NotNull String reason) {
        Intrinsics.checkNotNullParameter((Object)reason, (String)"reason");
        return new ControlResult($this$toResult, reason);
    }
}

