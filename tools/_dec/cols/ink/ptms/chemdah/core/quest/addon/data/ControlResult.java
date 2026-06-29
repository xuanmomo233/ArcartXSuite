/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u000b\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u000f\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\u0002\u0010\u0007J\t\u0010\f\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\r\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003J\u001f\u0010\u000e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00c6\u0001J\u0013\u0010\u000f\u001a\u00020\u00032\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0011\u001a\u00020\u0012H\u00d6\u0001J\t\u0010\u0013\u001a\u00020\u0006H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0013\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/core/quest/addon/data/ControlResult;", "", "pass", "", "(Z)V", "reason", "", "(ZLjava/lang/String;)V", "getPass", "()Z", "getReason", "()Ljava/lang/String;", "component1", "component2", "copy", "equals", "other", "hashCode", "", "toString", "Chemdah"})
public final class ControlResult {
    private final boolean pass;
    @Nullable
    private final String reason;

    public ControlResult(boolean pass, @Nullable String reason) {
        this.pass = pass;
        this.reason = reason;
    }

    public final boolean getPass() {
        return this.pass;
    }

    @Nullable
    public final String getReason() {
        return this.reason;
    }

    public ControlResult(boolean pass) {
        this(pass, null);
    }

    public final boolean component1() {
        return this.pass;
    }

    @Nullable
    public final String component2() {
        return this.reason;
    }

    @NotNull
    public final ControlResult copy(boolean pass, @Nullable String reason) {
        return new ControlResult(pass, reason);
    }

    public static /* synthetic */ ControlResult copy$default(ControlResult controlResult, boolean bl, String string, int n, Object object) {
        if ((n & 1) != 0) {
            bl = controlResult.pass;
        }
        if ((n & 2) != 0) {
            string = controlResult.reason;
        }
        return controlResult.copy(bl, string);
    }

    @NotNull
    public String toString() {
        return "ControlResult(pass=" + this.pass + ", reason=" + this.reason + ')';
    }

    public int hashCode() {
        int n = this.pass ? 1 : 0;
        if (n != 0) {
            n = 1;
        }
        int result = n;
        result = result * 31 + (this.reason == null ? 0 : this.reason.hashCode());
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ControlResult)) {
            return false;
        }
        ControlResult controlResult = (ControlResult)other;
        if (this.pass != controlResult.pass) {
            return false;
        }
        return Intrinsics.areEqual((Object)this.reason, (Object)controlResult.reason);
    }
}

