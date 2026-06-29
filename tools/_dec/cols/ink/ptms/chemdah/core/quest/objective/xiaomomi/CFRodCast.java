/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.xiaomomi;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.xiaomomi.GearsKt;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import net.momirealms.customfishing.api.event.RodCastEvent;
import net.momirealms.customfishing.api.mechanic.fishing.FishingGears;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CustomFishing")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/xiaomomi/CFRodCast;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/momirealms/customfishing/api/event/RodCastEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nCFRodCast.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CFRodCast.kt\nink/ptms/chemdah/core/quest/objective/xiaomomi/CFRodCast\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,35:1\n1747#2,3:36\n1747#2,3:39\n1747#2,3:42\n1747#2,3:45\n*S KotlinDebug\n*F\n+ 1 CFRodCast.kt\nink/ptms/chemdah/core/quest/objective/xiaomomi/CFRodCast\n*L\n23#1:36,3\n26#1:39,3\n29#1:42,3\n32#1:45,3\n*E\n"})
public final class CFRodCast
extends ObjectiveCountableI<RodCastEvent> {
    @NotNull
    public static final CFRodCast INSTANCE = new CFRodCast();
    @NotNull
    private static final String name = "customfishing rod cast";
    @NotNull
    private static final Class<RodCastEvent> event = RodCastEvent.class;

    private CFRodCast() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<RodCastEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(RodCastEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, RodCastEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getBukkitPlayerFishEvent().getHook().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.bukkitPlayerFishEvent.hook.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$3(Data data2, RodCastEvent e) {
        boolean bl;
        block3: {
            FishingGears fishingGears = e.getGears();
            Intrinsics.checkNotNullExpressionValue((Object)fishingGears, (String)"e.gears");
            FishingGears.GearType gearType = FishingGears.GearType.ROD;
            Intrinsics.checkNotNullExpressionValue((Object)gearType, (String)"ROD");
            Iterable $this$any$iv = GearsKt.getItems(fishingGears, gearType);
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    ItemStack item2 = (ItemStack)element$iv;
                    boolean bl2 = false;
                    if (!data2.toInferItem().isItem(item2)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$5(Data data2, RodCastEvent e) {
        boolean bl;
        block3: {
            FishingGears fishingGears = e.getGears();
            Intrinsics.checkNotNullExpressionValue((Object)fishingGears, (String)"e.gears");
            FishingGears.GearType gearType = FishingGears.GearType.BAIT;
            Intrinsics.checkNotNullExpressionValue((Object)gearType, (String)"BAIT");
            Iterable $this$any$iv = GearsKt.getItems(fishingGears, gearType);
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    ItemStack item2 = (ItemStack)element$iv;
                    boolean bl2 = false;
                    if (!data2.toInferItem().isItem(item2)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$7(Data data2, RodCastEvent e) {
        boolean bl;
        block3: {
            FishingGears fishingGears = e.getGears();
            Intrinsics.checkNotNullExpressionValue((Object)fishingGears, (String)"e.gears");
            FishingGears.GearType gearType = FishingGears.GearType.HOOK;
            Intrinsics.checkNotNullExpressionValue((Object)gearType, (String)"HOOK");
            Iterable $this$any$iv = GearsKt.getItems(fishingGears, gearType);
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    ItemStack item2 = (ItemStack)element$iv;
                    boolean bl2 = false;
                    if (!data2.toInferItem().isItem(item2)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$9(Data data2, RodCastEvent e) {
        boolean bl;
        block3: {
            FishingGears fishingGears = e.getGears();
            Intrinsics.checkNotNullExpressionValue((Object)fishingGears, (String)"e.gears");
            FishingGears.GearType gearType = FishingGears.GearType.UTIL;
            Intrinsics.checkNotNullExpressionValue((Object)gearType, (String)"UTIL");
            Iterable $this$any$iv = GearsKt.getItems(fishingGears, gearType);
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    ItemStack item2 = (ItemStack)element$iv;
                    boolean bl2 = false;
                    if (!data2.toInferItem().isItem(item2)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    static {
        INSTANCE.handler(CFRodCast::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", CFRodCast::_init_$lambda$1);
        INSTANCE.addSimpleCondition("rod", CFRodCast::_init_$lambda$3);
        INSTANCE.addSimpleCondition("bait", CFRodCast::_init_$lambda$5);
        INSTANCE.addSimpleCondition("hook", CFRodCast::_init_$lambda$7);
        INSTANCE.addSimpleCondition("util", CFRodCast::_init_$lambda$9);
    }
}

