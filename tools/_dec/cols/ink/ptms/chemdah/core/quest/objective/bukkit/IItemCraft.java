/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt;
import java.util.NoSuchElementException;
import kotlin.Metadata;
import kotlin1822.collections.ArraysKt;
import kotlin1822.collections.IntIterator;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.IntRange;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemCraft;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/inventory/CraftItemEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIItemCraft.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IItemCraft.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IItemCraft\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 3 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n*L\n1#1,42:1\n1#2:43\n12744#3,2:44\n*S KotlinDebug\n*F\n+ 1 IItemCraft.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IItemCraft\n*L\n35#1:44,2\n*E\n"})
public final class IItemCraft
extends ObjectiveCountableI<CraftItemEvent> {
    @NotNull
    public static final IItemCraft INSTANCE = new IItemCraft();
    @NotNull
    private static final String name = "craft item";
    @NotNull
    private static final Class<CraftItemEvent> event = CraftItemEvent.class;

    private IItemCraft() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<CraftItemEvent> getEvent() {
        return event;
    }

    @Override
    public int getCount(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull CraftItemEvent event) {
        int n;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        if (event.isShiftClick()) {
            ItemStack[] itemStackArray = event.getInventory().getMatrix();
            Intrinsics.checkNotNullExpressionValue((Object)itemStackArray, (String)"event.inventory.matrix");
            Object[] objectArray = itemStackArray;
            if (objectArray.length == 0) {
                throw new NoSuchElementException();
            }
            ItemStack it = (ItemStack)objectArray[0];
            boolean bl = false;
            ItemStack itemStack = it;
            int n2 = itemStack != null ? itemStack.getAmount() : 1;
            IntIterator intIterator = new IntRange(1, ArraysKt.getLastIndex((Object[])objectArray)).iterator();
            while (intIterator.hasNext()) {
                int n3 = intIterator.nextInt();
                ItemStack it2 = (ItemStack)objectArray[n3];
                $i$a$-minOf-IItemCraft$getCount$1 = false;
                ItemStack itemStack2 = it2;
                int n4 = itemStack2 != null ? itemStack2.getAmount() : 1;
                if (n2 <= n4) continue;
                n2 = n4;
            }
            n = n2;
        } else {
            n = 1;
        }
        return n;
    }

    private static final Player _init_$lambda$0(CraftItemEvent it) {
        Player player;
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        if (ItemModifierKt.isNotAir((ItemStack)it.getInventory().getResult())) {
            HumanEntity humanEntity = it.getWhoClicked();
            Intrinsics.checkNotNull((Object)humanEntity, (String)"null cannot be cast to non-null type org.bukkit.entity.Player");
            player = (Player)humanEntity;
        } else {
            player = null;
        }
        return player;
    }

    private static final Boolean _init_$lambda$1(Data data2, CraftItemEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getWhoClicked().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.whoClicked.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, CraftItemEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getInventory().getResult();
        Intrinsics.checkNotNull((Object)itemStack);
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$5(Data data2, CraftItemEvent e) {
        boolean bl;
        block1: {
            InferItem $this$lambda_u245_u24lambda_u244 = data2.toInferItem();
            boolean bl2 = false;
            ItemStack[] itemStackArray = e.getInventory().getMatrix();
            Intrinsics.checkNotNullExpressionValue((Object)itemStackArray, (String)"e.inventory.matrix");
            Object[] $this$any$iv = itemStackArray;
            boolean $i$f$any = false;
            for (Object element$iv : $this$any$iv) {
                ItemStack item2 = (ItemStack)element$iv;
                boolean bl3 = false;
                Intrinsics.checkNotNullExpressionValue((Object)item2, (String)"item");
                if (!$this$lambda_u245_u24lambda_u244.isItem(item2)) continue;
                bl = true;
                break block1;
            }
            bl = false;
        }
        return bl;
    }

    static {
        INSTANCE.handler(IItemCraft::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IItemCraft::_init_$lambda$1);
        INSTANCE.addSimpleCondition("item", IItemCraft::_init_$lambda$2);
        INSTANCE.addSimpleCondition("item:matrix", IItemCraft::_init_$lambda$5);
    }
}

