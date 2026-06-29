/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.bukkit.AEntityDamage;
import ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u653b\u51fb\u76ee\u6807", description={"\u73a9\u5bb6\u653b\u51fb\u5b9e\u4f53", "\u652f\u6301\u76ee\u6807\u5b9e\u4f53\u3001\u6b66\u5668\u3001\u4f24\u5bb3\u503c\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u653b\u51fb\u6b21\u6570"}, alias={"\u653b\u51fb", "\u73a9\u5bb6\u653b\u51fb", "\u6253\u51fb"}, params={@ParamInfo(name="position", type="Location", description="\u653b\u51fb\u4f4d\u7f6e\uff08\u76ee\u6807\u4f4d\u7f6e\uff09"), @ParamInfo(name="victim", type="Entity", description="\u88ab\u653b\u51fb\u7684\u5b9e\u4f53"), @ParamInfo(name="damage", type="Number", description="\u4f24\u5bb3\u503c"), @ParamInfo(name="damage:final", type="Number", description="\u6700\u7ec8\u4f24\u5bb3\u503c"), @ParamInfo(name="cause", type="String", description="\u4f24\u5bb3\u7c7b\u578b"), @ParamInfo(name="weapon", type="ItemStack", description="\u4f7f\u7528\u7684\u6b66\u5668")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerAttack;", "Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage;", "Lorg/bukkit/event/entity/EntityDamageByEntityEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerAttack
extends AEntityDamage<EntityDamageByEntityEvent> {
    @NotNull
    public static final IPlayerAttack INSTANCE = new IPlayerAttack();
    @NotNull
    private static final String name = "player attack";

    private IPlayerAttack() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityDamageByEntityEvent> getEvent() {
        return EntityDamageByEntityEvent.class;
    }

    private static final Player _init_$lambda$0(EntityDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = BukkitEventKt.getAttacker((EntityDamageByEntityEvent)it);
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = BukkitEventKt.getAttacker((EntityDamageByEntityEvent)it);
        Intrinsics.checkNotNull((Object)livingEntity, (String)"null cannot be cast to non-null type org.bukkit.entity.Player");
        return ((Player)livingEntity).getInventory().getItemInMainHand();
    }

    static {
        INSTANCE.handler(IPlayerAttack::_init_$lambda$0);
        INSTANCE.addCondition("weapon", "ItemStack", IPlayerAttack::_init_$lambda$1);
    }
}

