/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Item
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerFishEvent
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerFish;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerFishEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIPlayerFish.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IPlayerFish.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IPlayerFish\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,47:1\n1747#2,3:48\n*S KotlinDebug\n*F\n+ 1 IPlayerFish.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IPlayerFish\n*L\n38#1:48,3\n*E\n"})
public final class IPlayerFish
extends ObjectiveCountableI<PlayerFishEvent> {
    @NotNull
    public static final IPlayerFish INSTANCE = new IPlayerFish();
    @NotNull
    private static final String name = "player fish";
    @NotNull
    private static final Class<PlayerFishEvent> event = PlayerFishEvent.class;

    private IPlayerFish() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerFishEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(PlayerFishEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerFishEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerFishEvent e) {
        return data2.toInferEntity().isEntity(e.getCaught());
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerFishEvent e) {
        return data2.toInferEntity().isEntity((Entity)e.getHook());
    }

    private static final Boolean _init_$lambda$4(Data data2, PlayerFishEvent e) {
        Entity entity = e.getCaught();
        Item item2 = entity instanceof Item ? (Item)entity : null;
        if (item2 == null || (item2 = item2.getItemStack()) == null) {
            return false;
        }
        return data2.toInferItem().isItem((ItemStack)item2);
    }

    private static final Boolean _init_$lambda$6(Data data2, PlayerFishEvent e) {
        boolean bl;
        block3: {
            Iterable $this$any$iv = data2.asList();
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    String it = (String)element$iv;
                    boolean bl2 = false;
                    if (!StringsKt.equals((String)it, (String)e.getState().name(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$7(Data data2, PlayerFishEvent e) {
        return data2.toConditionNumber().check(e.getExpToDrop());
    }

    private static final Object _init_$lambda$8(PlayerFishEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExpToDrop();
    }

    static {
        INSTANCE.handler(IPlayerFish::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerFish::_init_$lambda$1);
        INSTANCE.addSimpleCondition("entity", IPlayerFish::_init_$lambda$2);
        INSTANCE.addSimpleCondition("entity:hook", IPlayerFish::_init_$lambda$3);
        INSTANCE.addSimpleCondition("item", IPlayerFish::_init_$lambda$4);
        INSTANCE.addSimpleCondition("state", IPlayerFish::_init_$lambda$6);
        INSTANCE.addSimpleCondition("exp", IPlayerFish::_init_$lambda$7);
        INSTANCE.addConditionVariable("exp", IPlayerFish::_init_$lambda$8);
    }
}

