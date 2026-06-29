/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerItemHeldEvent
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
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u5207\u6362\u624b\u6301\u7269\u54c1\u76ee\u6807", description={"\u73a9\u5bb6\u5207\u6362\u5feb\u6377\u680f\u7269\u54c1\u65f6\u89e6\u53d1", "\u652f\u6301\u65b0\u65e7\u7269\u54c1\u3001\u65b0\u65e7\u69fd\u4f4d\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u5207\u6362\u6b21\u6570"}, alias={"\u5207\u6362\u7269\u54c1", "\u5feb\u6377\u680f", "\u624b\u6301\u7269\u54c1"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="slot:new", type="Number", description="\u65b0\u69fd\u4f4d\u7f16\u53f7\uff080-8\uff09"), @ParamInfo(name="slot:previous", type="Number", description="\u65e7\u69fd\u4f4d\u7f16\u53f7\uff080-8\uff09"), @ParamInfo(name="item:new", type="ItemStack", description="\u65b0\u69fd\u4f4d\u7684\u7269\u54c1"), @ParamInfo(name="item:previous", type="ItemStack", description="\u65e7\u69fd\u4f4d\u7684\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerItemHeld;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerItemHeldEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerItemHeld
extends ObjectiveCountableI<PlayerItemHeldEvent> {
    @NotNull
    public static final IPlayerItemHeld INSTANCE = new IPlayerItemHeld();
    @NotNull
    private static final String name = "item held";

    private IPlayerItemHeld() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerItemHeldEvent> getEvent() {
        return PlayerItemHeldEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerItemHeldEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerItemHeldEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerItemHeldEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewSlot();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerItemHeldEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPreviousSlot();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerItemHeldEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getPlayer().getInventory().getItem(it.getNewSlot());
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        return itemStack;
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, PlayerItemHeldEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getPlayer().getInventory().getItem(it.getPreviousSlot());
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        return itemStack;
    }

    static {
        INSTANCE.handler(IPlayerItemHeld::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerItemHeld::_init_$lambda$1);
        INSTANCE.addCondition("slot:new", "Number", IPlayerItemHeld::_init_$lambda$2);
        INSTANCE.addCondition("slot:previous", "Number", IPlayerItemHeld::_init_$lambda$3);
        INSTANCE.addCondition("item:new", "ItemStack", IPlayerItemHeld::_init_$lambda$4);
        INSTANCE.addCondition("item:previous", "ItemStack", IPlayerItemHeld::_init_$lambda$5);
    }
}

