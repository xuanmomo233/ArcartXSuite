/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerItemConsumeEvent
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemConsume;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerItemConsumeEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IItemConsume
extends ObjectiveCountableI<PlayerItemConsumeEvent> {
    @NotNull
    public static final IItemConsume INSTANCE = new IItemConsume();
    @NotNull
    private static final String name = "item consume";
    @NotNull
    private static final Class<PlayerItemConsumeEvent> event = PlayerItemConsumeEvent.class;

    private IItemConsume() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerItemConsumeEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(PlayerItemConsumeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerItemConsumeEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerItemConsumeEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getItem();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.item");
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerItemConsumeEvent e) {
        InferItem inferItem = data2.toInferItem();
        Intrinsics.checkNotNullExpressionValue((Object)e, (String)"e");
        ItemStack itemStack = (ItemStack)Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)e, (String)"getReplacement", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        return inferItem.isItem(itemStack);
    }

    static {
        INSTANCE.handler(IItemConsume::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IItemConsume::_init_$lambda$1);
        INSTANCE.addSimpleCondition("item", IItemConsume::_init_$lambda$2);
        INSTANCE.addSimpleCondition("item:replacement", IItemConsume::_init_$lambda$3);
    }
}

