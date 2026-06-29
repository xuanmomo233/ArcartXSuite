/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit.paper;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u76d4\u7532\u66f4\u6362\u76ee\u6807", description={"\u73a9\u5bb6\u66f4\u6362\u76d4\u7532\u88c5\u5907\uff08\u9700\u8981 Paper \u670d\u52a1\u7aef\uff09", "\u652f\u6301\u88c5\u5907\u4f4d\u7f6e\u3001\u7269\u54c1\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u66f4\u6362\u6b21\u6570"}, alias={"\u88c5\u5907\u76d4\u7532", "\u66f4\u6362\u88c5\u5907", "\u7a7f\u6234\u76d4\u7532"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="ItemStack", description="\u66f4\u6362\u540e\u7684\u76d4\u7532\u7269\u54c1"), @ParamInfo(name="slot", type="String", description="\u88c5\u5907\u7684\u69fd\u4f4d\uff08\u5982HEAD\u3001CHEST\u3001LEGS\u3001FEET\uff09")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/paper/IPlayerArmorChange;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/destroystokyo/paper/event/player/PlayerArmorChangeEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
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

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerArmorChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerArmorChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewItem();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerArmorChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getSlotType().name();
    }

    static {
        INSTANCE.handler(IPlayerArmorChange::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerArmorChange::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", IPlayerArmorChange::_init_$lambda$2);
        INSTANCE.addCondition("slot", "String", IPlayerArmorChange::_init_$lambda$3);
    }
}

