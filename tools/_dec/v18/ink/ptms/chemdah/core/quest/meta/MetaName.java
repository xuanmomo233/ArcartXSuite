/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.meta;

import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.meta.Meta;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Id(id="name")
@Option(type=Option.Type.TEXT)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\b\b\u0007\u0018\u0000 \f2\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u0001:\u0001\fB\u0017\u0012\b\u0010\u0003\u001a\u0004\u0018\u00010\u0002\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u001c\u0010\u0007\u001a\u0004\u0018\u00010\u0002X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\b\u0010\t\"\u0004\b\n\u0010\u000b\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/core/quest/meta/MetaName;", "Link/ptms/chemdah/core/quest/meta/Meta;", "", "source", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Ljava/lang/String;Link/ptms/chemdah/core/quest/QuestContainer;)V", "displayName", "getDisplayName", "()Ljava/lang/String;", "setDisplayName", "(Ljava/lang/String;)V", "Companion", "Chemdah"})
public final class MetaName
extends Meta<String> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @Nullable
    private String displayName;

    public MetaName(@Nullable String source, @NotNull QuestContainer questContainer) {
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        super(source, questContainer);
        this.displayName = source;
    }

    @Nullable
    public final String getDisplayName() {
        return this.displayName;
    }

    public final void setDisplayName(@Nullable String string) {
        this.displayName = string;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0014\u0010\u0003\u001a\u00020\u0004*\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/core/quest/meta/MetaName$Companion;", "", "()V", "displayName", "", "Link/ptms/chemdah/core/quest/QuestContainer;", "colored", "", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final String displayName(@NotNull QuestContainer $this$displayName, boolean colored) {
            Intrinsics.checkNotNullParameter((Object)$this$displayName, (String)"<this>");
            Object object = (MetaName)$this$displayName.meta("name");
            if (object == null || (object = ((MetaName)object).getDisplayName()) == null) {
                object = $this$displayName instanceof Task ? this.displayName(((Task)$this$displayName).getTemplate(), colored) : $this$displayName.getId();
            }
            Object displayName = object;
            return colored ? UtilKt.colored((String)displayName) : displayName;
        }

        public static /* synthetic */ String displayName$default(Companion companion, QuestContainer questContainer, boolean bl, int n, Object object) {
            if ((n & 1) != 0) {
                bl = true;
            }
            return companion.displayName(questContainer, bl);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

