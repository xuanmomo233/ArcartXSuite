/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.bukkit.AEntityDamage;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u53d7\u4f24\u76ee\u6807", description={"\u73a9\u5bb6\u53d7\u5230\u4f24\u5bb3", "\u652f\u6301\u4f24\u5bb3\u7c7b\u578b\u3001\u4f24\u5bb3\u503c\u3001\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u53d7\u4f24\u6b21\u6570"}, alias={"\u53d7\u4f24", "\u73a9\u5bb6\u4f24\u5bb3", "\u53d7\u5230\u4f24\u5bb3"}, params={@ParamInfo(name="position", type="Location", description="\u53d7\u4f24\u4f4d\u7f6e"), @ParamInfo(name="victim", type="Entity", description="\u53d7\u4f24\u7684\u73a9\u5bb6"), @ParamInfo(name="damage", type="Number", description="\u4f24\u5bb3\u503c"), @ParamInfo(name="damage:final", type="Number", description="\u6700\u7ec8\u4f24\u5bb3\u503c"), @ParamInfo(name="cause", type="String", description="\u4f24\u5bb3\u7c7b\u578b\uff08ENTITY_ATTACK/FALL/FIRE/DROWNING\u7b49\uff09")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerDamage;", "Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage;", "Lorg/bukkit/event/entity/EntityDamageEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerDamage
extends AEntityDamage<EntityDamageEvent> {
    @NotNull
    public static final IPlayerDamage INSTANCE = new IPlayerDamage();
    @NotNull
    private static final String name = "player damage";

    private IPlayerDamage() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityDamageEvent> getEvent() {
        return EntityDamageEvent.class;
    }

    private static final Player _init_$lambda$0(EntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Entity entity = it.getEntity();
        return entity instanceof Player ? (Player)entity : null;
    }

    static {
        INSTANCE.handler(IPlayerDamage::_init_$lambda$0);
    }
}

