/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.TimeCycle
 *  ink.ptms.chemdah.taboolib.common5.util.String2TimeCycleKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.taboolib.common5.TimeCycle;
import ink.ptms.chemdah.taboolib.common5.util.String2TimeCycleKt;
import java.text.SimpleDateFormat;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Id(id="timeout")
@Option(type=Option.Type.TEXT)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0007\u0018\u0000 \u00132\u00020\u0001:\u0001\u0013B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u001a\u0010\u0007\u001a\u00020\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u001a\u0010\r\u001a\u00020\u000eX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\u0010\"\u0004\b\u0011\u0010\u0012\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonTimeout;", "Link/ptms/chemdah/core/quest/addon/Addon;", "root", "", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Ljava/lang/String;Link/ptms/chemdah/core/quest/QuestContainer;)V", "real", "", "getReal", "()J", "setReal", "(J)V", "timeout", "Link/ptms/chemdah/taboolib/common5/TimeCycle;", "getTimeout", "()Link/ptms/chemdah/taboolib/common5/TimeCycle;", "setTimeout", "(Link/ptms/chemdah/taboolib/common5/TimeCycle;)V", "Companion", "Chemdah"})
public final class AddonTimeout
extends Addon {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private TimeCycle timeout;
    private long real;

    public AddonTimeout(@NotNull String root2, @NotNull QuestContainer questContainer) {
        long l;
        AddonTimeout addonTimeout;
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        super(root2, questContainer);
        this.timeout = String2TimeCycleKt.parseTimeCycle((String)root2);
        AddonTimeout addonTimeout2 = this;
        try {
            addonTimeout = addonTimeout2;
            l = new SimpleDateFormat("yyyy/M/d H:m").parse(root2).getTime();
        }
        catch (Throwable throwable) {
            addonTimeout = addonTimeout2;
            l = 0L;
        }
        addonTimeout.real = l;
    }

    @NotNull
    public final TimeCycle getTimeout() {
        return this.timeout;
    }

    public final void setTimeout(@NotNull TimeCycle timeCycle) {
        Intrinsics.checkNotNullParameter((Object)timeCycle, (String)"<set-?>");
        this.timeout = timeCycle;
    }

    public final long getReal() {
        return this.real;
    }

    public final void setReal(long l) {
        this.real = l;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u00020\u0004*\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonTimeout$Companion;", "", "()V", "isTimeout", "", "Link/ptms/chemdah/core/quest/QuestContainer;", "startTime", "", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        /*
         * Enabled force condition propagation
         * Lifted jumps to return sites
         */
        public final boolean isTimeout(@NotNull QuestContainer $this$isTimeout, long startTime) {
            Intrinsics.checkNotNullParameter((Object)$this$isTimeout, (String)"<this>");
            AddonTimeout addonTimeout = (AddonTimeout)$this$isTimeout.addon("timeout");
            if (addonTimeout == null) {
                return false;
            }
            AddonTimeout meta = addonTimeout;
            if (meta.getReal() > 0L) {
                if (meta.getReal() < System.currentTimeMillis()) return true;
            }
            TimeCycle timeCycle = meta.getTimeout().start(startTime);
            if (timeCycle == null) return false;
            if (!timeCycle.isTimeout(startTime)) return false;
            return true;
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

