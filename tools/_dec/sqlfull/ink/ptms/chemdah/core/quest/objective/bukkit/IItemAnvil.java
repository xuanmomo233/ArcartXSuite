/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemAnvil;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/inventory/PrepareAnvilEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IItemAnvil
extends ObjectiveCountableI<PrepareAnvilEvent> {
    @NotNull
    public static final IItemAnvil INSTANCE = new IItemAnvil();
    @NotNull
    private static final String name = "player anvil";
    @NotNull
    private static final Class<PrepareAnvilEvent> event = PrepareAnvilEvent.class;

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
        return event;
    }

    private static final Player _init_$lambda$0(PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Object e = it.getViewers().get(0);
        Intrinsics.checkNotNull(e, (String)"null cannot be cast to non-null type org.bukkit.entity.Player");
        return (Player)e;
    }

    private static final Boolean _init_$lambda$1(Data data2, PrepareAnvilEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getInventory().getLocation();
        if (location == null) {
            location = UnitsKt.getEMPTY_LOCATION();
        }
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.inventory.location ?: EMPTY_LOCATION");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PrepareAnvilEvent e) {
        return StringsKt.contains$default((CharSequence)String.valueOf(e.getInventory().getRenameText()), (CharSequence)data2.toString(), (boolean)false, (int)2, null);
    }

    private static final Boolean _init_$lambda$3(Data data2, PrepareAnvilEvent e) {
        return data2.toConditionNumber().check(e.getInventory().getRepairCost());
    }

    private static final Boolean _init_$lambda$4(Data data2, PrepareAnvilEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getInventory().getItem(2);
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.inventory.getItem(2) ?: EMPTY_ITEM");
        return inferItem.isItem(itemStack);
    }

    /*
     * Enabled aggressive block sorting
     */
    private static final Boolean _init_$lambda$6(Data data2, PrepareAnvilEvent e) {
        boolean bl;
        InferItem $this$lambda_u246_u24lambda_u245 = data2.toInferItem();
        boolean bl2 = false;
        ItemStack itemStack = e.getInventory().getItem(0);
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.inventory.getItem(0) ?: EMPTY_ITEM");
        if (!$this$lambda_u246_u24lambda_u245.isItem(itemStack)) {
            ItemStack itemStack2 = e.getInventory().getItem(1);
            if (itemStack2 == null) {
                itemStack2 = UnitsKt.getEMPTY_ITEM();
            }
            Intrinsics.checkNotNullExpressionValue((Object)itemStack2, (String)"e.inventory.getItem(1) ?: EMPTY_ITEM");
            if (!$this$lambda_u246_u24lambda_u245.isItem(itemStack2)) {
                bl = false;
                return bl;
            }
        }
        bl = true;
        return bl;
    }

    private static final Object _init_$lambda$7(PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return String.valueOf(it.getInventory().getRenameText());
    }

    private static final Object _init_$lambda$8(PrepareAnvilEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getInventory().getRepairCost();
    }

    static {
        INSTANCE.handler(IItemAnvil::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IItemAnvil::_init_$lambda$1);
        INSTANCE.addSimpleCondition("text", IItemAnvil::_init_$lambda$2);
        INSTANCE.addSimpleCondition("cost", IItemAnvil::_init_$lambda$3);
        INSTANCE.addSimpleCondition("item", IItemAnvil::_init_$lambda$4);
        INSTANCE.addSimpleCondition("item:matrix", IItemAnvil::_init_$lambda$6);
        INSTANCE.addConditionVariable("text", IItemAnvil::_init_$lambda$7);
        INSTANCE.addConditionVariable("cost", IItemAnvil::_init_$lambda$8);
    }
}

