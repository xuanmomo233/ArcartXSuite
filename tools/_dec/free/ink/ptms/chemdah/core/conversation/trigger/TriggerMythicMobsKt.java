/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation.trigger;

import ink.ptms.chemdah.core.conversation.Source;
import ink.ptms.chemdah.um.Mob;
import ink.ptms.chemdah.um.Mythic;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\f\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u001a\u0010\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001*\u00020\u0002\u00a8\u0006\u0003"}, d2={"createSource", "Link/ptms/chemdah/core/conversation/Source;", "Link/ptms/chemdah/um/Mob;", "Chemdah"})
public final class TriggerMythicMobsKt {
    @NotNull
    public static final Source<Mob> createSource(@NotNull Mob $this$createSource) {
        Intrinsics.checkNotNullParameter((Object)$this$createSource, (String)"<this>");
        Entity entity = $this$createSource.getEntity();
        String string = $this$createSource.getDisplayName();
        return new Source<Mob>($this$createSource, entity, string){
            final /* synthetic */ Entity $entity;
            {
                this.$entity = $entity;
                super($super_call_param$1, $receiver);
            }

            /*
             * WARNING - void declaration
             */
            public boolean transfer(@NotNull Player player2, @NotNull String newId) {
                Object v2;
                block3: {
                    void $this$mapNotNullTo$iv$iv;
                    Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                    Intrinsics.checkNotNullParameter((Object)newId, (String)"newId");
                    List list2 = this.$entity.getNearbyEntities(10.0, 10.0, 10.0);
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"entity.getNearbyEntities(10.0, 10.0, 10.0)");
                    List entities2 = list2;
                    Iterable $this$mapNotNull$iv = entities2;
                    boolean $i$f$mapNotNull = false;
                    Iterable iterable = $this$mapNotNull$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$mapNotNullTo = false;
                    void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                    boolean $i$f$forEach = false;
                    Iterator<T> iterator = $this$forEach$iv$iv$iv.iterator();
                    while (iterator.hasNext()) {
                        Mob it$iv$iv;
                        T element$iv$iv$iv;
                        T element$iv$iv = element$iv$iv$iv = iterator.next();
                        boolean bl = false;
                        Entity it = (Entity)element$iv$iv;
                        boolean bl2 = false;
                        Mythic mythic = Mythic.Companion.getAPI();
                        Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                        if (mythic.getMob(it) == null) continue;
                        boolean bl3 = false;
                        destination$iv$iv.add(it$iv$iv);
                    }
                    Iterable $this$firstOrNull$iv = (List)destination$iv$iv;
                    boolean $i$f$firstOrNull = false;
                    for (E element$iv : $this$firstOrNull$iv) {
                        Mob it = (Mob)element$iv;
                        boolean bl = false;
                        if (!Intrinsics.areEqual((Object)it.getId(), (Object)newId)) continue;
                        v2 = element$iv;
                        break block3;
                    }
                    v2 = null;
                }
                Mob mob = v2;
                if (mob == null) {
                    return false;
                }
                Mob nearby = mob;
                this.update(nearby.getDisplayName(), nearby);
                return true;
            }

            @NotNull
            public Location getOriginLocation(@NotNull Mob entity) {
                Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                Entity be = entity.getEntity();
                Location location = be.getLocation().add(0.0, be.getHeight(), 0.0);
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"be.location.add(0.0, be.height, 0.0)");
                return location;
            }
        };
    }
}

