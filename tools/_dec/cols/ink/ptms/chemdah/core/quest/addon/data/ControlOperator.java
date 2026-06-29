/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.data.Control;
import ink.ptms.chemdah.core.quest.addon.data.ControlResult;
import ink.ptms.chemdah.core.quest.addon.data.ControlTrigger;
import ink.ptms.chemdah.util.FuturesKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00008\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u000e\u0010\u0004\u001a\n\u0012\u0004\u0012\u00020\u0006\u0018\u00010\u0005\u00a2\u0006\u0002\u0010\u0007J\u0014\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000e0\r2\u0006\u0010\u000f\u001a\u00020\u0010J\u0018\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u000f\u001a\u00020\u00102\b\b\u0002\u0010\u0013\u001a\u00020\u0014R\u0019\u0010\u0004\u001a\n\u0012\u0004\u0012\u00020\u0006\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/core/quest/addon/data/ControlOperator;", "", "template", "Link/ptms/chemdah/core/quest/Template;", "control", "", "Link/ptms/chemdah/core/quest/addon/data/Control;", "(Link/ptms/chemdah/core/quest/Template;Ljava/util/List;)V", "getControl", "()Ljava/util/List;", "getTemplate", "()Link/ptms/chemdah/core/quest/Template;", "check", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/core/quest/addon/data/ControlResult;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "signature", "", "type", "Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nControlOperator.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ControlOperator.kt\nink/ptms/chemdah/core/quest/addon/data/ControlOperator\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,42:1\n766#2:43\n857#2,2:44\n1855#2,2:46\n*S KotlinDebug\n*F\n+ 1 ControlOperator.kt\nink/ptms/chemdah/core/quest/addon/data/ControlOperator\n*L\n40#1:43\n40#1:44,2\n40#1:46,2\n*E\n"})
public class ControlOperator {
    @NotNull
    private final Template template;
    @Nullable
    private final List<Control> control;

    public ControlOperator(@NotNull Template template, @Nullable List<? extends Control> control) {
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        this.template = template;
        this.control = control;
    }

    @NotNull
    public final Template getTemplate() {
        return this.template;
    }

    @Nullable
    public final List<Control> getControl() {
        return this.control;
    }

    @NotNull
    public final CompletableFuture<ControlResult> check(@NotNull PlayerProfile profile) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        CompletableFuture<ControlResult> future = new CompletableFuture<ControlResult>();
        if (this.control == null) {
            future.complete(new ControlResult(true));
            return future;
        }
        ControlOperator.check$process(this, profile, future, 0);
        return future;
    }

    /*
     * WARNING - void declaration
     */
    public final void signature(@NotNull PlayerProfile profile, @NotNull ControlTrigger type) {
        block2: {
            void $this$filterTo$iv$iv;
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
            List<Control> list2 = this.control;
            if (list2 == null) break block2;
            Iterable $this$filter$iv = list2;
            boolean $i$f$filter = false;
            Iterable iterable = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Control it = (Control)element$iv$iv;
                boolean bl = false;
                if (!(it.getTrigger() == null || it.getTrigger() == type)) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            Iterable $this$forEach$iv = (List)destination$iv$iv;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Control it = (Control)element$iv;
                boolean bl = false;
                it.signature(profile, this.template);
            }
        }
    }

    public static /* synthetic */ void signature$default(ControlOperator controlOperator, PlayerProfile playerProfile, ControlTrigger controlTrigger, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: signature");
        }
        if ((n & 2) != 0) {
            controlTrigger = ControlTrigger.COMPLETE;
        }
        controlOperator.signature(playerProfile, controlTrigger);
    }

    private static final void check$process(ControlOperator this$0, PlayerProfile $profile, CompletableFuture<ControlResult> future, int cur) {
        if (cur < this$0.control.size()) {
            FuturesKt.applyWithError(this$0.control.get(cur).check($profile, this$0.template), (Function1)new Function1<ControlResult, Unit>(cur, future, this$0, $profile){
                final /* synthetic */ int $cur;
                final /* synthetic */ CompletableFuture<ControlResult> $future;
                final /* synthetic */ ControlOperator this$0;
                final /* synthetic */ PlayerProfile $profile;
                {
                    this.$cur = $cur;
                    this.$future = $future;
                    this.this$0 = $receiver;
                    this.$profile = $profile;
                    super(1);
                }

                public final void invoke(@NotNull ControlResult it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    if (it.getPass()) {
                        ControlOperator.access$check$process(this.this$0, this.$profile, this.$future, this.$cur + 1);
                    } else {
                        this.$future.complete(it);
                    }
                }
            });
        } else {
            future.complete(new ControlResult(true));
        }
    }

    public static final /* synthetic */ void access$check$process(ControlOperator this$0, PlayerProfile $profile, CompletableFuture future, int cur) {
        ControlOperator.check$process(this$0, $profile, future, cur);
    }
}

