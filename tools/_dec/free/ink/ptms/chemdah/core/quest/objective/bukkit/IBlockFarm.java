/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Material
 *  org.bukkit.block.Block
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
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u8015\u5730\u76ee\u6807", description={"\u73a9\u5bb6\u4f7f\u7528\u9504\u5934\u8015\u5730", "\u652f\u6301\u6750\u8d28\u3001\u4f4d\u7f6e\u3001\u9504\u5934\u7c7b\u578b\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u8015\u5730\u6b21\u6570"}, alias={"\u5f00\u57a6", "\u8015\u4f5c", "\u7ffb\u571f"}, params={@ParamInfo(name="position", type="Location", description="\u8015\u5730\u7684\u4f4d\u7f6e"), @ParamInfo(name="material", type="Block", description="\u8015\u5730\u524d\u7684\u65b9\u5757\u6750\u8d28"), @ParamInfo(name="action", type="String", description="\u4ea4\u4e92\u7c7b\u578b"), @ParamInfo(name="face", type="String", description="\u70b9\u51fb\u7684\u65b9\u5757\u9762"), @ParamInfo(name="hand", type="String", description="\u4f7f\u7528\u7684\u624b"), @ParamInfo(name="item", type="ItemStack", description="\u4f7f\u7528\u7684\u9504\u5934")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\f\u0010\f\u001a\u00020\r*\u00020\u000eH\u0002R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IBlockFarm;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerInteractEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "isFarmable", "", "Lorg/bukkit/Material;", "Chemdah"})
public final class IBlockFarm
extends ObjectiveCountableI<PlayerInteractEvent> {
    @NotNull
    public static final IBlockFarm INSTANCE = new IBlockFarm();
    @NotNull
    private static final String name = "block farm";

    private IBlockFarm() {
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

    private final boolean isFarmable(Material $this$isFarmable) {
        return Intrinsics.areEqual((Object)$this$isFarmable.name(), (Object)"DIRT") || Intrinsics.areEqual((Object)$this$isFarmable.name(), (Object)"GRASS_BLOCK") || Intrinsics.areEqual((Object)$this$isFarmable.name(), (Object)"GRASS_PATH");
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private static final Player _init_$lambda$0(PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        if (it.getAction() != Action.RIGHT_CLICK_BLOCK) return null;
        Block block = it.getClickedBlock();
        Intrinsics.checkNotNull((Object)block);
        Material material = block.getType();
        Intrinsics.checkNotNullExpressionValue((Object)material, (String)"it.clickedBlock!!.type");
        if (!INSTANCE.isFarmable(material)) return null;
        ItemStack itemStack = it.getItem();
        Object object = itemStack;
        if (itemStack == null) return null;
        Material material2 = object.getType();
        object = material2;
        if (material2 == null) return null;
        String string = object.name();
        object = string;
        if (string == null) return null;
        if (!StringsKt.endsWith$default((String)object, (String)"_HOE", (boolean)false, (int)2, null)) return null;
        boolean bl = true;
        if (!bl) return null;
        Player player2 = it.getPlayer();
        return player2;
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
        Intrinsics.checkNotNull((Object)itemStack);
        return itemStack;
    }

    static {
        INSTANCE.handler(IBlockFarm::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IBlockFarm::_init_$lambda$1);
        INSTANCE.addCondition("material", "Block", IBlockFarm::_init_$lambda$2);
        INSTANCE.addCondition("action", "String", IBlockFarm::_init_$lambda$3);
        INSTANCE.addCondition("face", "String", IBlockFarm::_init_$lambda$4);
        INSTANCE.addCondition("hand", "String", IBlockFarm::_init_$lambda$5);
        INSTANCE.addCondition("item", "ItemStack", IBlockFarm::_init_$lambda$6);
    }
}

