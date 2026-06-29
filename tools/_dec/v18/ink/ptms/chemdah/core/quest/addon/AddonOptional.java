/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.addon.Addon;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Id(id="optional")
@Option(type=Option.Type.BOOLEAN)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0007\u0018\u0000 \t2\u00020\u0001:\u0001\tB\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonOptional;", "Link/ptms/chemdah/core/quest/addon/Addon;", "value", "", "task", "Link/ptms/chemdah/core/quest/Task;", "(ZLink/ptms/chemdah/core/quest/Task;)V", "getValue", "()Z", "Companion", "Chemdah"})
public final class AddonOptional
extends Addon {
    @NotNull
    public static final Companion Companion = new Companion(null);
    private final boolean value;

    public AddonOptional(boolean value2, @NotNull Task task) {
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        super(value2, task);
        this.value = value2;
    }

    public final boolean getValue() {
        return this.value;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\n\u0010\u0003\u001a\u00020\u0004*\u00020\u0005\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonOptional$Companion;", "", "()V", "isOptional", "", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        public final boolean isOptional(@NotNull Task $this$isOptional) {
            Intrinsics.checkNotNullParameter((Object)$this$isOptional, (String)"<this>");
            AddonOptional addonOptional = (AddonOptional)$this$isOptional.addon("optional");
            return addonOptional != null ? addonOptional.getValue() : false;
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

