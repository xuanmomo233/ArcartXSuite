/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.citizensnpcs.api.event.NPCDamageByEntityEvent
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.citizens;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import net.citizensnpcs.api.event.NPCDamageByEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Citizens")
@MetaInfo(source="Citizens", name="Citizens NPC \u4f24\u5bb3\u76ee\u6807", description={"\u5bf9 Citizens NPC \u9020\u6210\u4f24\u5bb3", "\u652f\u6301 NPC ID\u3001\u540d\u79f0\u3001\u7c7b\u578b\u3001\u4f4d\u7f6e\u3001\u4f24\u5bb3\u503c\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 Citizens \u63d2\u4ef6\u652f\u6301"}, alias={"cnpc\u4f24\u5bb3", "\u4f24\u5bb3\u516c\u6c11", "\u653b\u51fb\u516c\u6c11"}, params={@ParamInfo(name="position", type="Location", description="\u4f24\u5bb3\u8005\u7684\u4f4d\u7f6e"), @ParamInfo(name="name", type="String", description="NPC \u7684\u540d\u79f0"), @ParamInfo(name="id", type="Number", description="NPC \u7684\u552f\u4e00 ID"), @ParamInfo(name="type", type="String", description="NPC \u7684\u5b9e\u4f53\u7c7b\u578b"), @ParamInfo(name="damage", type="Number", description="\u9020\u6210\u7684\u4f24\u5bb3\u503c")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/citizens/CNPCDamage;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/citizensnpcs/api/event/NPCDamageByEntityEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CNPCDamage
extends ObjectiveCountableI<NPCDamageByEntityEvent> {
    @NotNull
    public static final CNPCDamage INSTANCE = new CNPCDamage();
    @NotNull
    private static final String name = "cnpc damage";

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
        return NPCDamageByEntityEvent.class;
    }

    private static final Player _init_$lambda$0(NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Entity entity = it.getDamager();
        return entity instanceof Player ? (Player)entity : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDamager().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getName();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getId();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getEntity().getType().name();
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDamage();
    }

    private static final Object _init_$lambda$6(NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getName();
    }

    private static final Object _init_$lambda$7(NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDamage();
    }

    private static final Object _init_$lambda$8(NPCDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getId();
    }

    static {
        INSTANCE.handler(CNPCDamage::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", CNPCDamage::_init_$lambda$1);
        INSTANCE.addCondition("name", "String", CNPCDamage::_init_$lambda$2);
        INSTANCE.addCondition("id", "Number", CNPCDamage::_init_$lambda$3);
        INSTANCE.addCondition("type", "String", CNPCDamage::_init_$lambda$4);
        INSTANCE.addCondition("damage", "Number", CNPCDamage::_init_$lambda$5);
        INSTANCE.addConditionVariable("name", CNPCDamage::_init_$lambda$6);
        INSTANCE.addConditionVariable("damage", CNPCDamage::_init_$lambda$7);
        INSTANCE.addConditionVariable("id", CNPCDamage::_init_$lambda$8);
    }
}

