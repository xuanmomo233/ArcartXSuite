/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u7269\u54c1\u4ea4\u4e92\u76ee\u6807", description={"\u73a9\u5bb6\u4f7f\u7528\u7269\u54c1\u4ea4\u4e92\u65f6\u89e6\u53d1", "\u652f\u6301\u53f3\u952e\u3001\u5de6\u952e\u7b49\u4ea4\u4e92\u52a8\u4f5c", "\u53ef\u5224\u65ad\u624b\u6301\u4f4d\u7f6e\u548c\u4ea4\u4e92\u7c7b\u578b"}, alias={"\u4ea4\u4e92", "\u4f7f\u7528\u7269\u54c1", "\u53f3\u952e\u7269\u54c1"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u7684\u4f4d\u7f6e"), @ParamInfo(name="action", type="String", description="\u4ea4\u4e92\u7c7b\u578b\uff08\u5982RIGHT_CLICK_AIR\uff09"), @ParamInfo(name="hand", type="String", description="\u4f7f\u7528\u7684\u624b"), @ParamInfo(name="item", type="ItemStack", description="\u4ea4\u4e92\u4f7f\u7528\u7684\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0014\u0010\f\u001a\u00020\r8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemInteract;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerInteractEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "priority", "Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "getPriority", "()Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "Chemdah"})
public final class IItemInteract
extends ObjectiveCountableI<PlayerInteractEvent> {
    @NotNull
    public static final IItemInteract INSTANCE = new IItemInteract();
    @NotNull
    private static final String name = "item interact";

    private IItemInteract() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerInteractEvent> getEvent() {
        return PlayerInteractEvent.class;
    }

    @Override
    @NotNull
    public EventPriority getPriority() {
        return EventPriority.LOWEST;
    }

    private static final Player _init_$lambda$0(PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAction() != Action.PHYSICAL && ItemModifierKt.isNotAir((ItemStack)it.getItem()) ? it.getPlayer() : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAction().name();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        EquipmentSlot equipmentSlot = it.getHand();
        return equipmentSlot != null ? equipmentSlot.name() : null;
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getItem();
        Intrinsics.checkNotNull((Object)itemStack);
        return itemStack;
    }

    static {
        INSTANCE.handler(IItemInteract::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IItemInteract::_init_$lambda$1);
        INSTANCE.addCondition("action", "String", IItemInteract::_init_$lambda$2);
        INSTANCE.addCondition("hand", "String", IItemInteract::_init_$lambda$3);
        INSTANCE.addCondition("item", "ItemStack", IItemInteract::_init_$lambda$4);
    }
}

