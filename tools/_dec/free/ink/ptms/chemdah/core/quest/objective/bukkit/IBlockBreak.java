/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.block.Block
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.Player
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.QuestDevelopment;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u65b9\u5757\u7834\u574f\u76ee\u6807", description={"\u73a9\u5bb6\u7834\u574f\u65b9\u5757", "\u652f\u6301\u6750\u8d28\u3001\u4f4d\u7f6e\u3001\u6389\u843d\u7269\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u7834\u574f\u6570\u91cf"}, alias={"\u6316\u6398", "\u7834\u574f\u65b9\u5757", "\u91c7\u96c6"}, params={@ParamInfo(name="position", type="Location", description="\u65b9\u5757\u7684\u4f4d\u7f6e"), @ParamInfo(name="material", type="Block", description="\u65b9\u5757\u7684\u6750\u8d28\u7c7b\u578b"), @ParamInfo(name="drops", type="ItemStack", description="\u65b9\u5757\u6389\u843d\u7684\u7269\u54c1"), @ParamInfo(name="exp", type="Number", description="\u65b9\u5757\u6389\u843d\u7684\u7ecf\u9a8c\u503c"), @ParamInfo(name="unique", type="Boolean", description="\u662f\u5426\u4e3a\u81ea\u7136\u751f\u6210\u7684\u65b9\u5757\uff08\u975e\u73a9\u5bb6\u653e\u7f6e\uff09"), @ParamInfo(name="no-silk-touch", type="Boolean", description="\u662f\u5426\u672a\u4f7f\u7528\u7cbe\u51c6\u91c7\u96c6\u9644\u9b54")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IBlockBreak;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/block/BlockBreakEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IBlockBreak
extends ObjectiveCountableI<BlockBreakEvent> {
    @NotNull
    public static final IBlockBreak INSTANCE = new IBlockBreak();
    @NotNull
    private static final String name = "block break";

    private IBlockBreak() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<BlockBreakEvent> getEvent() {
        return BlockBreakEvent.class;
    }

    private static final Player _init_$lambda$0(BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlock().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlock();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlock().getDrops();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExpToDrop();
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Block block = it.getBlock();
        Intrinsics.checkNotNullExpressionValue((Object)block, (String)"it.block");
        return !QuestDevelopment.INSTANCE.isPlaced(block);
    }

    private static final Object _init_$lambda$6(PlayerProfile playerProfile2, Task task, BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemMeta itemMeta = it.getPlayer().getInventory().getItemInMainHand().getItemMeta();
        return !(itemMeta != null ? itemMeta.hasEnchant(Enchantment.SILK_TOUCH) : false);
    }

    private static final Object _init_$lambda$7(BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExpToDrop();
    }

    static {
        INSTANCE.handler(IBlockBreak::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IBlockBreak::_init_$lambda$1);
        INSTANCE.addCondition("material", "Block", IBlockBreak::_init_$lambda$2);
        INSTANCE.addCondition("drops", "ItemStack", IBlockBreak::_init_$lambda$3);
        INSTANCE.addCondition("exp", "Number", IBlockBreak::_init_$lambda$4);
        INSTANCE.addCondition("unique", "Boolean", IBlockBreak::_init_$lambda$5);
        INSTANCE.addCondition("no-silk-touch", "Boolean", IBlockBreak::_init_$lambda$6);
        INSTANCE.addConditionVariable("exp", IBlockBreak::_init_$lambda$7);
    }
}

