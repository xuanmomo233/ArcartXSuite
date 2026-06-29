/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.QuestDevelopment;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferBlock;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IBlockBreak;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/block/BlockBreakEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IBlockBreak
extends ObjectiveCountableI<BlockBreakEvent> {
    @NotNull
    public static final IBlockBreak INSTANCE = new IBlockBreak();
    @NotNull
    private static final String name = "block break";
    @NotNull
    private static final Class<BlockBreakEvent> event = BlockBreakEvent.class;

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
        return event;
    }

    private static final Player _init_$lambda$0(BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, BlockBreakEvent it) {
        InferArea inferArea = data2.toPosition();
        Location location = it.getBlock().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.block.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, BlockBreakEvent it) {
        InferBlock inferBlock = data2.toInferBlock();
        Block block = it.getBlock();
        Intrinsics.checkNotNullExpressionValue((Object)block, (String)"it.block");
        return inferBlock.isBlock(block);
    }

    private static final Boolean _init_$lambda$3(Data data2, BlockBreakEvent it) {
        return data2.toConditionNumber().check(it.getExpToDrop());
    }

    private static final Boolean _init_$lambda$4(Data data2, BlockBreakEvent it) {
        Boolean bl;
        if (data2.toBoolean()) {
            Block block = it.getBlock();
            Intrinsics.checkNotNullExpressionValue((Object)block, (String)"it.block");
            bl = !QuestDevelopment.INSTANCE.isPlaced(block);
        } else {
            bl = true;
        }
        return bl;
    }

    private static final Boolean _init_$lambda$5(Data data2, BlockBreakEvent it) {
        ItemMeta itemMeta = it.getPlayer().getInventory().getItemInMainHand().getItemMeta();
        return (itemMeta != null ? itemMeta.hasEnchant(Enchantment.SILK_TOUCH) : false) ? Boolean.valueOf(data2.toBoolean()) : Boolean.valueOf(true);
    }

    private static final Object _init_$lambda$6(BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExpToDrop();
    }

    static {
        INSTANCE.handler(IBlockBreak::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IBlockBreak::_init_$lambda$1);
        INSTANCE.addSimpleCondition("material", IBlockBreak::_init_$lambda$2);
        INSTANCE.addSimpleCondition("exp", IBlockBreak::_init_$lambda$3);
        INSTANCE.addSimpleCondition("unique", IBlockBreak::_init_$lambda$4);
        INSTANCE.addSimpleCondition("no-silk-touch", IBlockBreak::_init_$lambda$5);
        INSTANCE.addConditionVariable("exp", IBlockBreak::_init_$lambda$6);
    }
}

