/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.data.Control;
import ink.ptms.chemdah.core.quest.addon.data.ControlResult;
import ink.ptms.chemdah.core.quest.addon.data.ControlTrigger;
import ink.ptms.chemdah.core.quest.meta.MetaType;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000>\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0019\u0012\u0012\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003\u00a2\u0006\u0002\u0010\u0006J\u001e\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014H\u0016J\u0018\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014H\u0016R\u0016\u0010\u0007\u001a\u0004\u0018\u00010\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR&\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u0006\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/core/quest/addon/data/ControlCoexist;", "Link/ptms/chemdah/core/quest/addon/data/Control;", "type", "", "", "", "(Ljava/util/Map;)V", "trigger", "Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "getTrigger", "()Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "getType", "()Ljava/util/Map;", "setType", "check", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/core/quest/addon/data/ControlResult;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "template", "Link/ptms/chemdah/core/quest/Template;", "signature", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nControlCoexist.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ControlCoexist.kt\nink/ptms/chemdah/core/quest/addon/data/ControlCoexist\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,46:1\n187#2,2:47\n189#2:53\n1774#3,4:49\n*S KotlinDebug\n*F\n+ 1 ControlCoexist.kt\nink/ptms/chemdah/core/quest/addon/data/ControlCoexist\n*L\n37#1:47,2\n37#1:53\n37#1:49,4\n*E\n"})
public final class ControlCoexist
extends Control {
    @NotNull
    private Map<String, Integer> type;

    public ControlCoexist(@NotNull Map<String, Integer> type) {
        Intrinsics.checkNotNullParameter(type, (String)"type");
        this.type = type;
    }

    @NotNull
    public final Map<String, Integer> getType() {
        return this.type;
    }

    public final void setType(@NotNull Map<String, Integer> map) {
        Intrinsics.checkNotNullParameter(map, (String)"<set-?>");
        this.type = map;
    }

    @Override
    @Nullable
    public ControlTrigger getTrigger() {
        return null;
    }

    @Override
    @NotNull
    public CompletableFuture<ControlResult> check(@NotNull PlayerProfile profile, @NotNull Template template) {
        CompletableFuture<ControlResult> completableFuture;
        boolean bl;
        block8: {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)template, (String)"template");
            Map<String, Integer> $this$any$iv = this.type;
            boolean $i$f$any = false;
            if ($this$any$iv.isEmpty()) {
                bl = false;
            } else {
                Iterator<Map.Entry<String, Integer>> iterator = $this$any$iv.entrySet().iterator();
                while (iterator.hasNext()) {
                    int n;
                    Map.Entry<String, Integer> element$iv;
                    Map.Entry<String, Integer> label = element$iv = iterator.next();
                    boolean bl2 = false;
                    Iterable $this$count$iv = PlayerProfile.getQuests$default(profile, false, 1, null);
                    boolean $i$f$count = false;
                    if ($this$count$iv instanceof Collection && ((Collection)$this$count$iv).isEmpty()) {
                        n = 0;
                    } else {
                        int count$iv = 0;
                        for (Object element$iv2 : $this$count$iv) {
                            Quest it = (Quest)element$iv2;
                            boolean bl3 = false;
                            if (!MetaType.Companion.type(it.getTemplate()).contains(label.getKey()) || ++count$iv >= 0) continue;
                            CollectionsKt.throwCountOverflow();
                        }
                        n = count$iv;
                    }
                    if (!(n >= ((Number)label.getValue()).intValue())) continue;
                    bl = true;
                    break block8;
                }
                bl = false;
            }
        }
        if (bl) {
            CompletableFuture<ControlResult> completableFuture2 = CompletableFuture.completedFuture(new ControlResult(false, "coexist"));
            completableFuture = completableFuture2;
            Intrinsics.checkNotNullExpressionValue(completableFuture2, (String)"{\n            Completabl\u2026se, \"coexist\"))\n        }");
        } else {
            CompletableFuture<ControlResult> completableFuture3 = CompletableFuture.completedFuture(new ControlResult(true, "coexist"));
            completableFuture = completableFuture3;
            Intrinsics.checkNotNullExpressionValue(completableFuture3, (String)"{\n            Completabl\u2026ue, \"coexist\"))\n        }");
        }
        return completableFuture;
    }

    @Override
    public void signature(@NotNull PlayerProfile profile, @NotNull Template template) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
    }
}

