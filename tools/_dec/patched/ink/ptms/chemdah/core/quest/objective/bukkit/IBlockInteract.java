/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
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
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u65b9\u5757\u4ea4\u4e92\u76ee\u6807", description={"\u73a9\u5bb6\u4e0e\u65b9\u5757\u4ea4\u4e92", "\u652f\u6301\u6750\u8d28\u3001\u4ea4\u4e92\u7c7b\u578b\u3001\u65b9\u5411\u3001\u624b\u6301\u7269\u54c1\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u4ea4\u4e92\u6b21\u6570"}, alias={"\u70b9\u51fb\u65b9\u5757", "\u53f3\u952e\u65b9\u5757", "\u4ea4\u4e92\u65b9\u5757"}, params={@ParamInfo(name="position", type="Location", description="\u88ab\u4ea4\u4e92\u65b9\u5757\u7684\u4f4d\u7f6e"), @ParamInfo(name="material", type="Block", description="\u88ab\u4ea4\u4e92\u7684\u65b9\u5757\u6750\u8d28"), @ParamInfo(name="action", type="String", description="\u4ea4\u4e92\u7c7b\u578b\uff08\u5982RIGHT_CLICK_BLOCK\uff09"), @ParamInfo(name="face", type="String", description="\u70b9\u51fb\u7684\u65b9\u5757\u9762"), @ParamInfo(name="hand", type="String", description="\u4f7f\u7528\u7684\u624b"), @ParamInfo(name="item", type="ItemStack", description="\u624b\u6301\u7684\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IBlockInteract;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerInteractEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IBlockInteract
extends ObjectiveCountableI<PlayerInteractEvent> {
    @NotNull
    public static final IBlockInteract INSTANCE = new IBlockInteract();
    @NotNull
    private static final String name = "block interact";

    private IBlockInteract() {
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

    private static final Player _init_$lambda$0(PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getClickedBlock() != null ? it.getPlayer() : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Block block = it.getClickedBlock();
        Intrinsics.checkNotNull((Object)block);
        return block.getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Block block = it.getClickedBlock();
        Intrinsics.checkNotNull((Object)block);
        return block;
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAction().name();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlockFace().name();
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        EquipmentSlot equipmentSlot = it.getHand();
        return equipmentSlot != null ? equipmentSlot.name() : null;
    }

    private static final Object _init_$lambda$6(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getItem();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        return itemStack;
    }

    static {
        INSTANCE.handler(IBlockInteract::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IBlockInteract::_init_$lambda$1);
        INSTANCE.addCondition("material", "Block", IBlockInteract::_init_$lambda$2);
        INSTANCE.addCondition("action", "String", IBlockInteract::_init_$lambda$3);
        INSTANCE.addCondition("face", "String", IBlockInteract::_init_$lambda$4);
        INSTANCE.addCondition("hand", "String", IBlockInteract::_init_$lambda$5);
        INSTANCE.addCondition("item", "ItemStack", IBlockInteract::_init_$lambda$6);
    }
}

