/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  net.citizensnpcs.api.event.NPCDamageByEntityEvent
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.citizens;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import net.citizensnpcs.api.event.NPCDamageByEntityEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Citizens")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/citizens/CNPCDamage;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/citizensnpcs/api/event/NPCDamageByEntityEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nCNPCDamage.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CNPCDamage.kt\nink/ptms/chemdah/core/quest/objective/citizens/CNPCDamage\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,44:1\n1747#2,3:45\n1747#2,3:48\n*S KotlinDebug\n*F\n+ 1 CNPCDamage.kt\nink/ptms/chemdah/core/quest/objective/citizens/CNPCDamage\n*L\n22#1:45,3\n28#1:48,3\n*E\n"})
public final class CNPCDamage
extends ObjectiveCountableI<NPCDamageByEntityEvent> {
    @NotNull
    public static final CNPCDamage INSTANCE = new CNPCDamage();
    @NotNull
    private static final String name = "cnpc damage";
    @NotNull
    private static final Class<NPCDamageByEntityEvent> event = NPCDamageByEntityEvent.class;

    private CNPCDamage() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<NPCDamageByEntityEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Entity entity = it.getDamager();
        return entity instanceof Player ? (Player)entity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, NPCDamageByEntityEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getDamager().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.damager.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$3(Data data2, NPCDamageByEntityEvent e) {
        boolean bl;
        block3: {
            Iterable $this$any$iv = data2.asList();
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    String it = (String)element$iv;
                    boolean bl2 = false;
                    if (!StringsKt.equals((String)it, (String)e.getNPC().getName(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$4(Data data2, NPCDamageByEntityEvent e) {
        return data2.toInt() == e.getNPC().getId();
    }

    private static final Boolean _init_$lambda$6(Data data2, NPCDamageByEntityEvent e) {
        boolean bl;
        block3: {
            Iterable $this$any$iv = data2.asList();
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    String it = (String)element$iv;
                    boolean bl2 = false;
                    if (!StringsKt.equals((String)it, (String)e.getNPC().getEntity().getType().name(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$7(Data data2, NPCDamageByEntityEvent it) {
        return data2.toConditionNumber().check(it.getDamage());
    }

    private static final Object _init_$lambda$8(NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getName();
    }

    private static final Object _init_$lambda$9(NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDamage();
    }

    private static final Object _init_$lambda$10(NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getId();
    }

    static {
        INSTANCE.handler(CNPCDamage::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", CNPCDamage::_init_$lambda$1);
        INSTANCE.addSimpleCondition("name", CNPCDamage::_init_$lambda$3);
        INSTANCE.addSimpleCondition("id", CNPCDamage::_init_$lambda$4);
        INSTANCE.addSimpleCondition("type", CNPCDamage::_init_$lambda$6);
        INSTANCE.addSimpleCondition("damage", CNPCDamage::_init_$lambda$7);
        INSTANCE.addConditionVariable("name", CNPCDamage::_init_$lambda$8);
        INSTANCE.addConditionVariable("damage", CNPCDamage::_init_$lambda$9);
        INSTANCE.addConditionVariable("id", CNPCDamage::_init_$lambda$10);
    }
}

