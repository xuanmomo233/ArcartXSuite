/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityDeathEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.bukkit.AEntityDeath;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u51fb\u6740\u76ee\u6807", description={"\u73a9\u5bb6\u51fb\u6740\u5b9e\u4f53", "\u652f\u6301\u6b66\u5668\u3001\u53d7\u5bb3\u8005\u7c7b\u578b\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u51fb\u6740\u6570\u91cf"}, alias={"\u51fb\u6740", "\u6740\u602a", "\u6218\u6597"}, params={@ParamInfo(name="weapon", type="ItemStack", description="\u4f7f\u7528\u7684\u6b66\u5668"), @ParamInfo(name="victim", type="Entity", description="\u88ab\u51fb\u6740\u7684\u5b9e\u4f53")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerKill;", "Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDeath;", "Lorg/bukkit/event/entity/EntityDeathEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerKill
extends AEntityDeath<EntityDeathEvent> {
    @NotNull
    public static final IPlayerKill INSTANCE = new IPlayerKill();
    @NotNull
    private static final String name = "player kill";

    private IPlayerKill() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityDeathEvent> getEvent() {
        return EntityDeathEvent.class;
    }

    private static final Player _init_$lambda$0(EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getKiller();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Player player2 = it.getEntity().getKiller();
        Intrinsics.checkNotNull((Object)player2);
        return player2.getInventory().getItemInMainHand();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity();
    }

    static {
        INSTANCE.handler(IPlayerKill::_init_$lambda$0);
        INSTANCE.addCondition("weapon", "ItemStack", IPlayerKill::_init_$lambda$1);
        INSTANCE.addCondition("victim", "Entity", IPlayerKill::_init_$lambda$2);
    }
}

