/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Item
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerFishEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u9493\u9c7c\u76ee\u6807", description={"\u73a9\u5bb6\u9493\u9c7c", "\u652f\u6301\u9493\u9c7c\u72b6\u6001\u3001\u7269\u54c1\u7c7b\u578b\u3001\u7ecf\u9a8c\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u9493\u9c7c\u6b21\u6570"}, alias={"\u9493\u9c7c", "\u6355\u9c7c", "\u5782\u9493"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u7684\u4f4d\u7f6e"), @ParamInfo(name="entity", type="Entity", description="\u9493\u4e0a\u6765\u7684\u5b9e\u4f53"), @ParamInfo(name="entity:hook", type="Entity", description="\u9c7c\u94a9\u5b9e\u4f53"), @ParamInfo(name="item", type="ItemStack", description="\u9493\u4e0a\u6765\u7684\u7269\u54c1"), @ParamInfo(name="state", type="String", description="\u9493\u9c7c\u72b6\u6001\uff08\u5982CAUGHT_FISH\uff09"), @ParamInfo(name="exp", type="Number", description="\u83b7\u5f97\u7684\u7ecf\u9a8c\u503c")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerFish;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerFishEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerFish
extends ObjectiveCountableI<PlayerFishEvent> {
    @NotNull
    public static final IPlayerFish INSTANCE = new IPlayerFish();
    @NotNull
    private static final String name = "player fish";

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
        return PlayerFishEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerFishEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerFishEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerFishEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCaught();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerFishEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getHook();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerFishEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Entity entity = it.getCaught();
        Item item2 = entity instanceof Item ? (Item)entity : null;
        return item2 != null ? item2.getItemStack() : null;
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, PlayerFishEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getState().name();
    }

    private static final Object _init_$lambda$6(PlayerProfile playerProfile2, Task task, PlayerFishEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExpToDrop();
    }

    private static final Object _init_$lambda$7(PlayerFishEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExpToDrop();
    }

    static {
        INSTANCE.handler(IPlayerFish::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerFish::_init_$lambda$1);
        INSTANCE.addCondition("entity", "Entity", IPlayerFish::_init_$lambda$2);
        INSTANCE.addCondition("entity:hook", "Entity", IPlayerFish::_init_$lambda$3);
        INSTANCE.addCondition("item", "ItemStack", IPlayerFish::_init_$lambda$4);
        INSTANCE.addCondition("state", "String", IPlayerFish::_init_$lambda$5);
        INSTANCE.addCondition("exp", "Number", IPlayerFish::_init_$lambda$6);
        INSTANCE.addConditionVariable("exp", IPlayerFish::_init_$lambda$7);
    }
}

