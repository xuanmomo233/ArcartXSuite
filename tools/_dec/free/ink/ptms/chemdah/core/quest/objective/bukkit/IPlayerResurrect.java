/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.util.LazyMakerKt
 *  ink.ptms.chemdah.taboolib.platform.util.PlayerUtilKt
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Material
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityResurrectEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.IPlayerResurrect;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.platform.util.PlayerUtilKt;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u590d\u6d3b\u76ee\u6807", description={"\u73a9\u5bb6\u4f7f\u7528\u4e0d\u6b7b\u56fe\u817e\u590d\u6d3b\u65f6\u89e6\u53d1", "\u652f\u6301\u4f7f\u7528\u7269\u54c1\u7c7b\u578b\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u590d\u6d3b\u6b21\u6570"}, alias={"\u590d\u6d3b", "\u4e0d\u6b7b\u56fe\u817e", "\u91cd\u751f"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="item", type="ItemStack", description="\u4f7f\u7528\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR \u0010\f\u001a\u00070\r\u00a2\u0006\u0002\b\u000e8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0011\u0010\u0012\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerResurrect;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/EntityResurrectEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "totem", "Lorg/bukkit/Material;", "Lorg/jetbrains/annotations/Nullable;", "getTotem", "()Lorg/bukkit/Material;", "totem$delegate", "Lkotlin1822/Lazy;", "Chemdah"})
public final class IPlayerResurrect
extends ObjectiveCountableI<EntityResurrectEvent> {
    @NotNull
    public static final IPlayerResurrect INSTANCE = new IPlayerResurrect();
    @NotNull
    private static final String name = "player resurrect";
    @NotNull
    private static final Lazy totem$delegate = LazyMakerKt.unsafeLazy((Function0)totem.2.INSTANCE);

    private IPlayerResurrect() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityResurrectEvent> getEvent() {
        return EntityResurrectEvent.class;
    }

    @NotNull
    public final Material getTotem() {
        Lazy lazy = totem$delegate;
        return (Material)lazy.getValue();
    }

    private static final Player _init_$lambda$0(EntityResurrectEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = it.getEntity();
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityResurrectEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EntityResurrectEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = it.getEntity();
        Intrinsics.checkNotNull((Object)livingEntity, (String)"null cannot be cast to non-null type org.bukkit.entity.Player");
        HumanEntity humanEntity = (HumanEntity)((Player)livingEntity);
        Material material = INSTANCE.getTotem();
        Intrinsics.checkNotNullExpressionValue((Object)material, (String)"totem");
        return PlayerUtilKt.getUsingItem((HumanEntity)humanEntity, (Material)material);
    }

    static {
        INSTANCE.handler(IPlayerResurrect::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerResurrect::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", IPlayerResurrect::_init_$lambda$2);
    }
}

