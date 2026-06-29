/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.PrepareAnvilEvent
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
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u94c1\u7827\u4f7f\u7528\u76ee\u6807", description={"\u73a9\u5bb6\u4f7f\u7528\u94c1\u7827\u65f6\u89e6\u53d1", "\u652f\u6301\u91cd\u547d\u540d\u6587\u672c\u3001\u4fee\u7406\u6210\u672c\u3001\u7ed3\u679c\u7269\u54c1\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u4f7f\u7528\u6b21\u6570"}, alias={"\u94c1\u7827", "\u91cd\u547d\u540d", "\u4fee\u7406"}, params={@ParamInfo(name="position", type="Location", description="\u94c1\u7827\u7684\u4f4d\u7f6e"), @ParamInfo(name="text", type="String", description="\u91cd\u547d\u540d\u7684\u6587\u672c"), @ParamInfo(name="cost", type="Number", description="\u4fee\u7406\u6210\u672c"), @ParamInfo(name="item", type="ItemStack", description="\u5408\u6210\u7ed3\u679c\u7269\u54c1"), @ParamInfo(name="item:matrix", type="ItemStack", description="\u94c1\u7827\u4e2d\u7684\u6750\u6599\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemAnvil;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/inventory/PrepareAnvilEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IItemAnvil
extends ObjectiveCountableI<PrepareAnvilEvent> {
    @NotNull
    public static final IItemAnvil INSTANCE = new IItemAnvil();
    @NotNull
    private static final String name = "player anvil";

    private IItemAnvil() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PrepareAnvilEvent> getEvent() {
        return PrepareAnvilEvent.class;
    }

    private static final Player _init_$lambda$0(PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Object e = it.getViewers().get(0);
        Intrinsics.checkNotNull(e, (String)"null cannot be cast to non-null type org.bukkit.entity.Player");
        return (Player)e;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Location location = it.getInventory().getLocation();
        if (location == null) {
            location = UnitsKt.getEMPTY_LOCATION();
        }
        return location;
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return String.valueOf(it.getInventory().getRenameText());
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getInventory().getRepairCost();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getInventory().getItem(2);
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        return itemStack;
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Object[] objectArray = new ItemStack[]{it.getInventory().getItem(0), it.getInventory().getItem(1)};
        return CollectionsKt.listOfNotNull((Object[])objectArray);
    }

    private static final Object _init_$lambda$6(PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return String.valueOf(it.getInventory().getRenameText());
    }

    private static final Object _init_$lambda$7(PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getInventory().getRepairCost();
    }

    static {
        INSTANCE.handler(IItemAnvil::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IItemAnvil::_init_$lambda$1);
        INSTANCE.addCondition("text", "String", IItemAnvil::_init_$lambda$2);
        INSTANCE.addCondition("cost", "Number", IItemAnvil::_init_$lambda$3);
        INSTANCE.addCondition("item", "ItemStack", IItemAnvil::_init_$lambda$4);
        INSTANCE.addCondition("item:matrix", "ItemStack", IItemAnvil::_init_$lambda$5);
        INSTANCE.addConditionVariable("text", IItemAnvil::_init_$lambda$6);
        INSTANCE.addConditionVariable("cost", IItemAnvil::_init_$lambda$7);
    }
}

