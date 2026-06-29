/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.conversation.trigger;

import ink.ptms.chemdah.core.conversation.Source;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.ArraysKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u0018\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0011\n\u0002\u0010\u000e\n\u0002\b\u0002\u001a\u0010\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001*\u00020\u0002\u001a\u0017\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004*\u00020\u0002H\u0000\u00a2\u0006\u0002\u0010\u0006\u00a8\u0006\u0007"}, d2={"createSource", "Link/ptms/chemdah/core/conversation/Source;", "Lorg/bukkit/entity/Entity;", "getDisplayName", "", "", "(Lorg/bukkit/entity/Entity;)[Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nTriggerVanilla.kt\nKotlin\n*S Kotlin\n*F\n+ 1 TriggerVanilla.kt\nink/ptms/chemdah/core/conversation/trigger/TriggerVanillaKt\n+ 2 ArraysJVM.kt\nkotlin/collections/ArraysKt__ArraysJVMKt\n*L\n1#1,69:1\n37#2,2:70\n*S KotlinDebug\n*F\n+ 1 TriggerVanilla.kt\nink/ptms/chemdah/core/conversation/trigger/TriggerVanillaKt\n*L\n46#1:70,2\n*E\n"})
public final class TriggerVanillaKt {
    @NotNull
    public static final String[] getDisplayName(@NotNull Entity $this$getDisplayName) {
        Intrinsics.checkNotNullParameter((Object)$this$getDisplayName, (String)"<this>");
        Object[] objectArray = new String[]{$this$getDisplayName.getType().name()};
        ArrayList names = CollectionsKt.arrayListOf((Object[])objectArray);
        if ($this$getDisplayName.getCustomName() != null) {
            Collection collection = names;
            String string = $this$getDisplayName.getCustomName();
            Intrinsics.checkNotNull((Object)string);
            collection.add(string);
        }
        Collection $this$toTypedArray$iv = names;
        boolean $i$f$toTypedArray = false;
        Collection thisCollection$iv = $this$toTypedArray$iv;
        return thisCollection$iv.toArray(new String[0]);
    }

    @NotNull
    public static final Source<Entity> createSource(@NotNull Entity $this$createSource) {
        Intrinsics.checkNotNullParameter((Object)$this$createSource, (String)"<this>");
        Object[] name = TriggerVanillaKt.getDisplayName($this$createSource);
        String string = (String)ArraysKt.last((Object[])name);
        return new Source<Entity>($this$createSource, string){
            final /* synthetic */ Entity $this_createSource;
            {
                this.$this_createSource = $receiver;
                super($super_call_param$1, $receiver);
            }

            public boolean transfer(@NotNull Player player, @NotNull String newId) {
                Object v2;
                block4: {
                    Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                    Intrinsics.checkNotNullParameter((Object)newId, (String)"newId");
                    List list2 = this.$this_createSource.getNearbyEntities(10.0, 10.0, 10.0);
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"this@createSource.getNea\u2026ntities(10.0, 10.0, 10.0)");
                    List entities = list2;
                    Iterable $this$firstOrNull$iv = entities;
                    boolean $i$f$firstOrNull = false;
                    for (T element$iv : $this$firstOrNull$iv) {
                        boolean bl;
                        block3: {
                            Entity it = (Entity)element$iv;
                            boolean bl2 = false;
                            Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                            String[] $this$any$iv = TriggerVanillaKt.getDisplayName(it);
                            boolean $i$f$any = false;
                            int n = $this$any$iv.length;
                            for (int i = 0; i < n; ++i) {
                                String element$iv2;
                                String name = element$iv2 = $this$any$iv[i];
                                boolean bl3 = false;
                                if (!StringsKt.equals((String)name, (String)newId, (boolean)true)) continue;
                                bl = true;
                                break block3;
                            }
                            bl = false;
                        }
                        if (!bl) continue;
                        v2 = element$iv;
                        break block4;
                    }
                    v2 = null;
                }
                Entity entity = v2;
                if (entity == null) {
                    return false;
                }
                Entity nearby = entity;
                this.update((String)ArraysKt.last((Object[])TriggerVanillaKt.getDisplayName(nearby)), nearby);
                return true;
            }

            @NotNull
            public Location getOriginLocation(@NotNull Entity entity) {
                Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                Location location = entity.getLocation().add(0.0, entity.getHeight(), 0.0);
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"entity.location.add(0.0, entity.height, 0.0)");
                return location;
            }
        };
    }
}

