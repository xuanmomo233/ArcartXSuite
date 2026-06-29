/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah;

import ink.ptms.chemdah.AdyeshachChecker;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0006\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u001b\u0010\u0003\u001a\u00020\u00048FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0006\u0010\u0007\u001a\u0004\b\u0003\u0010\u0005R\u001b\u0010\b\u001a\u00020\u00048FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\t\u0010\u0007\u001a\u0004\b\b\u0010\u0005\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/AdyeshachChecker;", "", "()V", "isLegacyVersion", "", "()Z", "isLegacyVersion$delegate", "Lkotlin1822/Lazy;", "isNewVersion", "isNewVersion$delegate", "Chemdah"})
public final class AdyeshachChecker {
    @NotNull
    public static final AdyeshachChecker INSTANCE = new AdyeshachChecker();
    @NotNull
    private static final Lazy isNewVersion$delegate = LazyMakerKt.unsafeLazy((Function0)isNewVersion.2.INSTANCE);
    @NotNull
    private static final Lazy isLegacyVersion$delegate = LazyMakerKt.unsafeLazy((Function0)isLegacyVersion.2.INSTANCE);

    private AdyeshachChecker() {
    }

    public final boolean isNewVersion() {
        Lazy lazy = isNewVersion$delegate;
        return (Boolean)lazy.getValue();
    }

    public final boolean isLegacyVersion() {
        Lazy lazy = isLegacyVersion$delegate;
        return (Boolean)lazy.getValue();
    }
}

