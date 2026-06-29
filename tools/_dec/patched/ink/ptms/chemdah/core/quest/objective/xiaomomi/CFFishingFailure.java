/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.momirealms.customfishing.api.event.FishingResultEvent
 *  net.momirealms.customfishing.api.event.FishingResultEvent$Result
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
import net.momirealms.customfishing.api.event.FishingResultEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CustomFishing")
@MetaInfo(source="CustomFishing", name="CustomFishing \u9493\u9c7c\u5931\u8d25\u76ee\u6807", description={"CustomFishing \u9493\u9c7c\u5931\u8d25", "\u652f\u6301\u4f4d\u7f6e\u3001\u9c7c\u94a9\u5b9e\u4f53\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 CustomFishing \u63d2\u4ef6\u652f\u6301"}, alias={"customfishing\u5931\u8d25", "\u9493\u9c7c\u5931\u8d25", "\u9493\u9c7c\u672a\u6210\u529f"}, params={@ParamInfo(name="position", type="Location", description="\u9493\u9c7c\u5931\u8d25\u7684\u4f4d\u7f6e"), @ParamInfo(name="hook", type="Entity", description="\u9c7c\u94a9\u5b9e\u4f53")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/xiaomomi/CFFishingFailure;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/momirealms/customfishing/api/event/FishingResultEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CFFishingFailure
extends ObjectiveCountableI<FishingResultEvent> {
    @NotNull
    public static final CFFishingFailure INSTANCE = new CFFishingFailure();
    @NotNull
    private static final String name = "customfishing failure";

    private CFFishingFailure() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<FishingResultEvent> getEvent() {
        return FishingResultEvent.class;
    }

    private static final Player _init_$lambda$0(FishingResultEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getResult() == FishingResultEvent.Result.FAILURE ? it.getPlayer() : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, FishingResultEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, FishingResultEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFishHook();
    }

    static {
        INSTANCE.handler(CFFishingFailure::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", CFFishingFailure::_init_$lambda$1);
        INSTANCE.addCondition("hook", "Entity", CFFishingFailure::_init_$lambda$2);
    }
}

