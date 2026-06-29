/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.util.LazyMakerKt
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.jvm.functions.Function0
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah;

import ink.ptms.chemdah.AdyeshachChecker;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u001b\u0010\u0003\u001a\u00020\u00048FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0006\u0010\u0007\u001a\u0004\b\u0003\u0010\u0005\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/AdyeshachChecker;", "", "()V", "isLoaded", "", "()Z", "isLoaded$delegate", "Lkotlin1822/Lazy;", "Chemdah"})
public final class AdyeshachChecker {
    @NotNull
    public static final AdyeshachChecker INSTANCE = new AdyeshachChecker();
    @NotNull
    private static final Lazy isLoaded$delegate = LazyMakerKt.unsafeLazy((Function0)isLoaded.2.INSTANCE);

    private AdyeshachChecker() {
    }

    public final boolean isLoaded() {
        Lazy lazy = isLoaded$delegate;
        return (Boolean)lazy.getValue();
    }
}

