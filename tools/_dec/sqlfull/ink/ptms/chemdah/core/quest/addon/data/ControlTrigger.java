/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0006\b\u0086\u0001\u0018\u0000 \u00062\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\u0006B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "", "(Ljava/lang/String;I)V", "ACCEPT", "FAIL", "COMPLETE", "Companion", "Chemdah"})
public final class ControlTrigger
extends Enum<ControlTrigger> {
    @NotNull
    public static final Companion Companion;
    public static final /* enum */ ControlTrigger ACCEPT;
    public static final /* enum */ ControlTrigger FAIL;
    public static final /* enum */ ControlTrigger COMPLETE;
    private static final /* synthetic */ ControlTrigger[] $VALUES;

    public static ControlTrigger[] values() {
        return (ControlTrigger[])$VALUES.clone();
    }

    public static ControlTrigger valueOf(String value2) {
        return Enum.valueOf(ControlTrigger.class, value2);
    }

    static {
        ACCEPT = new ControlTrigger();
        FAIL = new ControlTrigger();
        COMPLETE = new ControlTrigger();
        $VALUES = controlTriggerArray = new ControlTrigger[]{ControlTrigger.ACCEPT, ControlTrigger.FAIL, ControlTrigger.COMPLETE};
        Companion = new Companion(null);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/core/quest/addon/data/ControlTrigger$Companion;", "", "()V", "fromName", "Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "name", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nControlTrigger.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ControlTrigger.kt\nink/ptms/chemdah/core/quest/addon/data/ControlTrigger$Companion\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,17:1\n1#2:18\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final ControlTrigger fromName(@NotNull String name) {
            ControlTrigger controlTrigger;
            ControlTrigger controlTrigger2;
            block2: {
                Intrinsics.checkNotNullParameter((Object)name, (String)"name");
                ControlTrigger[] controlTriggerArray = ControlTrigger.values();
                int n = controlTriggerArray.length;
                for (int i = 0; i < n; ++i) {
                    ControlTrigger controlTrigger3;
                    ControlTrigger it = controlTrigger3 = controlTriggerArray[i];
                    boolean bl = false;
                    if (!CoerceExtensionsKt.eqic((String)it.name(), (String)name)) continue;
                    controlTrigger2 = controlTrigger3;
                    break block2;
                }
                controlTrigger2 = controlTrigger = null;
            }
            if (controlTrigger2 == null) {
                controlTrigger = ACCEPT;
            }
            return controlTrigger;
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

