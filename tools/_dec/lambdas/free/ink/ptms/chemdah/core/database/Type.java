/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.util.LazyMakerKt
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.database;

import ink.ptms.chemdah.core.database.Type;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0006\b\u0086\u0001\u0018\u0000 \u00062\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\u0006B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/core/database/Type;", "", "(Ljava/lang/String;I)V", "LOCAL", "SQL", "CUSTOM", "Companion", "Chemdah"})
public final class Type
extends Enum<Type> {
    @NotNull
    public static final Companion Companion;
    @NotNull
    private static final Lazy<Type> INSTANCE$delegate;
    public static final /* enum */ Type LOCAL;
    public static final /* enum */ Type SQL;
    public static final /* enum */ Type CUSTOM;
    private static final /* synthetic */ Type[] $VALUES;

    public static Type[] values() {
        return (Type[])$VALUES.clone();
    }

    public static Type valueOf(String value2) {
        return Enum.valueOf(Type.class, value2);
    }

    static {
        LOCAL = new Type();
        SQL = new Type();
        CUSTOM = new Type();
        $VALUES = typeArray = new Type[]{Type.LOCAL, Type.SQL, Type.CUSTOM};
        Companion = new Companion(null);
        INSTANCE$delegate = LazyMakerKt.unsafeLazy((Function0)Companion.INSTANCE.2.INSTANCE);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u001b\u0010\u0003\u001a\u00020\u00048FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0007\u0010\b\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/core/database/Type$Companion;", "", "()V", "INSTANCE", "Link/ptms/chemdah/core/database/Type;", "getINSTANCE", "()Link/ptms/chemdah/core/database/Type;", "INSTANCE$delegate", "Lkotlin1822/Lazy;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Type getINSTANCE() {
            Lazy lazy = INSTANCE$delegate;
            return (Type)((Object)lazy.getValue());
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

