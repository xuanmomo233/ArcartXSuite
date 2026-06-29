/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.momirealms.customcrops.api.event.ScarecrowPlaceEvent
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.xiaomomi;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import net.momirealms.customcrops.api.event.ScarecrowPlaceEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CustomCrops")
@MetaInfo(source="CustomCrops", name="CustomCrops \u7a3b\u8349\u4eba\u653e\u7f6e\u76ee\u6807", description={"\u653e\u7f6e CustomCrops \u7a3b\u8349\u4eba", "\u652f\u6301\u4f4d\u7f6e\u3001\u7a3b\u8349\u4eba\u7269\u54c1\u7c7b\u578b\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 CustomCrops \u63d2\u4ef6\u652f\u6301"}, alias={"customcrops\u7a3b\u8349\u4eba", "\u653e\u7f6e\u7a3b\u8349\u4eba", "\u8bbe\u7f6e\u7a3b\u8349\u4eba"}, params={@ParamInfo(name="position", type="Location", description="\u7a3b\u8349\u4eba\u653e\u7f6e\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="String", description="\u7a3b\u8349\u4eba\u7684\u7269\u54c1 ID")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/xiaomomi/CCScarecrowPlace;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/momirealms/customcrops/api/event/ScarecrowPlaceEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CCScarecrowPlace
extends ObjectiveCountableI<ScarecrowPlaceEvent> {
    @NotNull
    public static final CCScarecrowPlace INSTANCE = new CCScarecrowPlace();
    @NotNull
    private static final String name = "customcrops scarecrow place";

    private CCScarecrowPlace() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<ScarecrowPlaceEvent> getEvent() {
        return ScarecrowPlaceEvent.class;
    }

    private static final Player _init_$lambda$0(ScarecrowPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, ScarecrowPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.location();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, ScarecrowPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.scarecrowItemID();
    }

    static {
        INSTANCE.handler(CCScarecrowPlace::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", CCScarecrowPlace::_init_$lambda$1);
        INSTANCE.addCondition("item", "String", CCScarecrowPlace::_init_$lambda$2);
    }
}

