/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt
 *  kotlin.Metadata
 *  kotlin1822.collections.ArraysKt
 *  kotlin1822.collections.IntIterator
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.IntRange
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.CraftItemEvent
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
import ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt;
import java.util.NoSuchElementException;
import kotlin.Metadata;
import kotlin1822.collections.ArraysKt;
import kotlin1822.collections.IntIterator;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.IntRange;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u7269\u54c1\u5408\u6210\u76ee\u6807", description={"\u73a9\u5bb6\u5728\u5de5\u4f5c\u53f0\u5408\u6210\u7269\u54c1", "\u652f\u6301\u7269\u54c1\u3001\u914d\u65b9\u6750\u6599\u3001\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u652f\u6301\u6279\u91cf\u5408\u6210\u6570\u91cf\u7edf\u8ba1"}, alias={"\u5408\u6210", "\u5236\u4f5c\u7269\u54c1", "\u5de5\u4f5c\u53f0"}, params={@ParamInfo(name="position", type="Location", description="\u5408\u6210\u65f6\u73a9\u5bb6\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="ItemStack", description="\u5408\u6210\u7684\u7269\u54c1"), @ParamInfo(name="item:matrix", type="ItemStack", description="\u5408\u6210\u914d\u65b9\u7684\u6750\u6599")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemCraft;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/inventory/CraftItemEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIItemCraft.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IItemCraft.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IItemCraft\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,64:1\n1#2:65\n*E\n"})
public final class IItemCraft
extends ObjectiveCountableI<CraftItemEvent> {
    @NotNull
    public static final IItemCraft INSTANCE = new IItemCraft();
    @NotNull
    private static final String name = "craft item";

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
        return CraftItemEvent.class;
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
        Player player2;
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        if (ItemModifierKt.isNotAir((ItemStack)it.getInventory().getResult())) {
            HumanEntity humanEntity = it.getWhoClicked();
            Intrinsics.checkNotNull((Object)humanEntity, (String)"null cannot be cast to non-null type org.bukkit.entity.Player");
            player2 = (Player)humanEntity;
        } else {
            player2 = null;
        }
        return player2;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, CraftItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getWhoClicked().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, CraftItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getInventory().getResult();
        Intrinsics.checkNotNull((Object)itemStack);
        return itemStack;
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, CraftItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getInventory().getMatrix();
    }

    static {
        INSTANCE.handler(IItemCraft::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IItemCraft::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", IItemCraft::_init_$lambda$2);
        INSTANCE.addCondition("item:matrix", "ItemStack", IItemCraft::_init_$lambda$3);
    }
}

