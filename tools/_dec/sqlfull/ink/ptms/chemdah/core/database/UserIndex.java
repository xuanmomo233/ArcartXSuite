/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.database;

import ink.ptms.chemdah.core.database.UserIndex;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0005\b\u0086\u0001\u0018\u0000 \u00052\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\u0005B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/database/UserIndex;", "", "(Ljava/lang/String;I)V", "NAME", "UUID", "Companion", "Chemdah"})
public final class UserIndex
extends Enum<UserIndex> {
    @NotNull
    public static final Companion Companion;
    @NotNull
    private static final Lazy<UserIndex> INSTANCE$delegate;
    public static final /* enum */ UserIndex NAME;
    public static final /* enum */ UserIndex UUID;
    private static final /* synthetic */ UserIndex[] $VALUES;

    public static UserIndex[] values() {
        return (UserIndex[])$VALUES.clone();
    }

    public static UserIndex valueOf(String value2) {
        return Enum.valueOf(UserIndex.class, value2);
    }

    static {
        NAME = new UserIndex();
        UUID = new UserIndex();
        $VALUES = userIndexArray = new UserIndex[]{UserIndex.NAME, UserIndex.UUID};
        Companion = new Companion(null);
        INSTANCE$delegate = LazyMakerKt.unsafeLazy((Function0)Companion.INSTANCE.2.INSTANCE);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u001b\u0010\u0003\u001a\u00020\u00048FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0007\u0010\b\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/core/database/UserIndex$Companion;", "", "()V", "INSTANCE", "Link/ptms/chemdah/core/database/UserIndex;", "getINSTANCE", "()Link/ptms/chemdah/core/database/UserIndex;", "INSTANCE$delegate", "Lkotlin1822/Lazy;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final UserIndex getINSTANCE() {
            Lazy lazy = INSTANCE$delegate;
            return (UserIndex)((Object)lazy.getValue());
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

