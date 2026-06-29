/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerInteractAtEntityEvent
 *  org.bukkit.inventory.EntityEquipment
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u5b9e\u4f53\u4ea4\u4e92\u76ee\u6807", description={"\u73a9\u5bb6\u53f3\u952e\u4ea4\u4e92\u5b9e\u4f53", "\u652f\u6301\u5b9e\u4f53\u7c7b\u578b\u3001\u4f4d\u7f6e\u3001\u624b\u6301\u7269\u54c1\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u4ea4\u4e92\u6b21\u6570"}, alias={"\u4ea4\u4e92", "\u53f3\u952e\u5b9e\u4f53", "\u70b9\u51fb\u5b9e\u4f53"}, params={@ParamInfo(name="position", type="Location", description="\u88ab\u4ea4\u4e92\u5b9e\u4f53\u7684\u4f4d\u7f6e"), @ParamInfo(name="position:clicked", type="Vector", description="\u70b9\u51fb\u7684\u7cbe\u786e\u4f4d\u7f6e"), @ParamInfo(name="entity", type="Entity", description="\u88ab\u4ea4\u4e92\u7684\u5b9e\u4f53"), @ParamInfo(name="hand", type="String", description="\u4f7f\u7528\u7684\u624b"), @ParamInfo(name="item", type="ItemStack", description="\u624b\u6301\u7684\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IEntityInteract;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerInteractAtEntityEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IEntityInteract
extends ObjectiveCountableI<PlayerInteractAtEntityEvent> {
    @NotNull
    public static final IEntityInteract INSTANCE = new IEntityInteract();
    @NotNull
    private static final String name = "entity interact";

    private IEntityInteract() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerInteractAtEntityEvent> getEvent() {
        return PlayerInteractAtEntityEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerInteractAtEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerInteractAtEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getRightClicked().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerInteractAtEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getClickedPosition();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerInteractAtEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getRightClicked();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerInteractAtEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getHand().name();
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, PlayerInteractAtEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        EntityEquipment entityEquipment = it.getPlayer().getEquipment();
        Intrinsics.checkNotNull((Object)entityEquipment);
        return entityEquipment.getItem(it.getHand());
    }

    static {
        INSTANCE.handler(IEntityInteract::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IEntityInteract::_init_$lambda$1);
        INSTANCE.addCondition("position:clicked", "Vector", IEntityInteract::_init_$lambda$2);
        INSTANCE.addCondition("entity", "Entity", IEntityInteract::_init_$lambda$3);
        INSTANCE.addCondition("hand", "String", IEntityInteract::_init_$lambda$4);
        INSTANCE.addCondition("item", "ItemStack", IEntityInteract::_init_$lambda$5);
    }
}

