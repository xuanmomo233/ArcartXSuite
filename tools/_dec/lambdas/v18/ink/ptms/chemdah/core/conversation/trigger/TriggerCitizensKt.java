/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.citizensnpcs.api.npc.NPC
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation.trigger;

import ink.ptms.chemdah.core.conversation.Source;
import ink.ptms.chemdah.core.conversation.trigger.TriggerCitizens;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\f\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u001a\u0010\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001*\u00020\u0002\u00a8\u0006\u0003"}, d2={"createSource", "Link/ptms/chemdah/core/conversation/Source;", "Lnet/citizensnpcs/api/npc/NPC;", "Chemdah"})
public final class TriggerCitizensKt {
    @NotNull
    public static final Source<NPC> createSource(@NotNull NPC $this$createSource) {
        Intrinsics.checkNotNullParameter((Object)$this$createSource, (String)"<this>");
        Entity entity = $this$createSource.getEntity();
        String string = $this$createSource.getFullName();
        return new Source<NPC>($this$createSource, entity, string){
            final /* synthetic */ NPC $this_createSource;
            final /* synthetic */ Entity $entity;
            {
                this.$this_createSource = $receiver;
                this.$entity = $entity;
                Intrinsics.checkNotNullExpressionValue((Object)$super_call_param$1, (String)"fullName");
                super($super_call_param$1, $receiver);
            }

            /*
             * WARNING - void declaration
             */
            public boolean transfer(@NotNull Player player, @NotNull String newId) {
                Object v1;
                block3: {
                    void $this$mapNotNullTo$iv$iv;
                    Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                    Intrinsics.checkNotNullParameter((Object)newId, (String)"newId");
                    int newIdInt = Coerce.toInteger((Object)newId);
                    List list2 = this.$entity.getNearbyEntities(10.0, 10.0, 10.0);
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"entity.getNearbyEntities(10.0, 10.0, 10.0)");
                    List entities = list2;
                    Iterable $this$mapNotNull$iv = entities;
                    boolean $i$f$mapNotNull = false;
                    Iterable iterable = $this$mapNotNull$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$mapNotNullTo = false;
                    void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                    boolean $i$f$forEach = false;
                    Iterator<T> iterator = $this$forEach$iv$iv$iv.iterator();
                    while (iterator.hasNext()) {
                        NPC it$iv$iv;
                        T element$iv$iv$iv;
                        T element$iv$iv = element$iv$iv$iv = iterator.next();
                        boolean bl = false;
                        Entity it = (Entity)element$iv$iv;
                        boolean bl2 = false;
                        Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                        if (TriggerCitizens.INSTANCE.toNPC(it) == null) continue;
                        boolean bl3 = false;
                        destination$iv$iv.add(it$iv$iv);
                    }
                    Iterable $this$firstOrNull$iv = (List)destination$iv$iv;
                    boolean $i$f$firstOrNull = false;
                    for (E element$iv : $this$firstOrNull$iv) {
                        NPC it = (NPC)element$iv;
                        boolean bl = false;
                        if (!(it.getId() == newIdInt)) continue;
                        v1 = element$iv;
                        break block3;
                    }
                    v1 = null;
                }
                NPC nPC = v1;
                if (nPC == null) {
                    return false;
                }
                NPC nearby = nPC;
                String string = this.$this_createSource.getFullName();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"fullName");
                this.update(string, nearby);
                return true;
            }

            @NotNull
            public Location getOriginLocation(@NotNull NPC entity) {
                Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                Location location = entity.getEntity().getLocation().add(0.0, entity.getEntity().getHeight(), 0.0);
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"entity.entity.location.a\u2026ntity.entity.height, 0.0)");
                return location;
            }
        };
    }
}

