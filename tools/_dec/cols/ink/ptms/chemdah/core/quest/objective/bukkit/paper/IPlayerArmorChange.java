/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit.paper;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/paper/IPlayerArmorChange;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/destroystokyo/paper/event/player/PlayerArmorChangeEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIPlayerArmorChange.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IPlayerArmorChange.kt\nink/ptms/chemdah/core/quest/objective/bukkit/paper/IPlayerArmorChange\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,28:1\n1747#2,3:29\n*S KotlinDebug\n*F\n+ 1 IPlayerArmorChange.kt\nink/ptms/chemdah/core/quest/objective/bukkit/paper/IPlayerArmorChange\n*L\n24#1:29,3\n*E\n"})
public final class IPlayerArmorChange
extends ObjectiveCountableI<PlayerArmorChangeEvent> {
    @NotNull
    public static final IPlayerArmorChange INSTANCE = new IPlayerArmorChange();
    @NotNull
    private static final String name = "armor change";
    @NotNull
    private static final Class<PlayerArmorChangeEvent> event = PlayerArmorChangeEvent.class;

    private IPlayerArmorChange() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerArmorChangeEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(PlayerArmorChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerArmorChangeEvent it) {
        InferArea inferArea = data2.toPosition();
        Location location = it.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerArmorChangeEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getNewItem();
        Intrinsics.checkNotNull((Object)itemStack);
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$4(Data data2, PlayerArmorChangeEvent e) {
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
                    if (!StringsKt.equals((String)it, (String)e.getSlotType().name(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    static {
        INSTANCE.handler(IPlayerArmorChange::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerArmorChange::_init_$lambda$1);
        INSTANCE.addSimpleCondition("item", IPlayerArmorChange::_init_$lambda$2);
        INSTANCE.addSimpleCondition("slot", IPlayerArmorChange::_init_$lambda$4);
    }
}

