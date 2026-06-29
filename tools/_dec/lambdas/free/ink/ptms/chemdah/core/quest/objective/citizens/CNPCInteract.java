/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.citizensnpcs.api.event.NPCRightClickEvent
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
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Citizens")
@MetaInfo(source="Citizens", name="Citizens NPC \u4ea4\u4e92\u76ee\u6807", description={"\u4e0e Citizens NPC \u53f3\u952e\u4ea4\u4e92", "\u652f\u6301 NPC ID\u3001\u540d\u79f0\u3001\u7c7b\u578b\u3001\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 Citizens \u63d2\u4ef6\u652f\u6301"}, alias={"cnpc\u4ea4\u4e92", "\u70b9\u51fb\u516c\u6c11", "\u4e92\u52a8\u516c\u6c11"}, params={@ParamInfo(name="position", type="Location", description="NPC \u5b9e\u4f53\u7684\u4f4d\u7f6e"), @ParamInfo(name="id", type="Number", description="NPC \u7684\u552f\u4e00 ID"), @ParamInfo(name="name", type="String", description="NPC \u7684\u540d\u79f0"), @ParamInfo(name="type", type="String", description="NPC \u7684\u5b9e\u4f53\u7c7b\u578b")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/citizens/CNPCInteract;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/citizensnpcs/api/event/NPCRightClickEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CNPCInteract
extends ObjectiveCountableI<NPCRightClickEvent> {
    @NotNull
    public static final CNPCInteract INSTANCE = new CNPCInteract();
    @NotNull
    private static final String name = "cnpc interact";

    private CNPCInteract() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<NPCRightClickEvent> getEvent() {
        return NPCRightClickEvent.class;
    }

    private static final Player _init_$lambda$0(NPCRightClickEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getClicker();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, NPCRightClickEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getEntity().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, NPCRightClickEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getId();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, NPCRightClickEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getName();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, NPCRightClickEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getEntity().getType().name();
    }

    private static final Object _init_$lambda$5(NPCRightClickEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getId();
    }

    private static final Object _init_$lambda$6(NPCRightClickEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNPC().getName();
    }

    static {
        INSTANCE.handler(CNPCInteract::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", CNPCInteract::_init_$lambda$1);
        INSTANCE.addCondition("id", "Number", CNPCInteract::_init_$lambda$2);
        INSTANCE.addCondition("name", "String", CNPCInteract::_init_$lambda$3);
        INSTANCE.addCondition("type", "String", CNPCInteract::_init_$lambda$4);
        INSTANCE.addConditionVariable("id", CNPCInteract::_init_$lambda$5);
        INSTANCE.addConditionVariable("name", CNPCInteract::_init_$lambda$6);
    }
}

