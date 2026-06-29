/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.dre.brewery.api.events.PlayerPushEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.brewery;

import com.dre.brewery.api.events.PlayerPushEvent;
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

@Dependency(plugin="Brewery")
@MetaInfo(source="Brewery", name="Brewery \u63a8\u6324\u76ee\u6807", description={"\u56e0\u9189\u9152\u800c\u88ab\u63a8\u6324\u6216\u63a8\u52a8", "\u652f\u6301\u4f4d\u7f6e\u3001\u63a8\u529b\u5411\u91cf (x, y, z) \u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 Brewery \u63d2\u4ef6\u652f\u6301"}, alias={"brewery\u63a8\u6324", "\u9189\u9152\u63a8\u52a8", "\u9189\u9152\u63a8\u6324"}, params={@ParamInfo(name="position", type="section", description="\u63a8\u6324\u4f4d\u7f6e\u6761\u4ef6"), @ParamInfo(name="x", type="section", description="\u63a8\u529b X \u8f74\u65b9\u5411\u6761\u4ef6"), @ParamInfo(name="y", type="section", description="\u63a8\u529b Y \u8f74\u65b9\u5411\u6761\u4ef6"), @ParamInfo(name="z", type="section", description="\u63a8\u529b Z \u8f74\u65b9\u5411\u6761\u4ef6")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/brewery/BPush;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/dre/brewery/api/events/PlayerPushEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class BPush
extends ObjectiveCountableI<PlayerPushEvent> {
    @NotNull
    public static final BPush INSTANCE = new BPush();
    @NotNull
    private static final String name = "brewery push";

    private BPush() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerPushEvent> getEvent() {
        return PlayerPushEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerPushEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerPushEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerPushEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPush().getX();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerPushEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPush().getY();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerPushEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPush().getZ();
    }

    private static final Object _init_$lambda$5(PlayerPushEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPush().getX();
    }

    private static final Object _init_$lambda$6(PlayerPushEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPush().getY();
    }

    private static final Object _init_$lambda$7(PlayerPushEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPush().getZ();
    }

    static {
        INSTANCE.handler(BPush::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", BPush::_init_$lambda$1);
        INSTANCE.addCondition("x", "Number", BPush::_init_$lambda$2);
        INSTANCE.addCondition("y", "Number", BPush::_init_$lambda$3);
        INSTANCE.addCondition("z", "Number", BPush::_init_$lambda$4);
        INSTANCE.addConditionVariable("x", BPush::_init_$lambda$5);
        INSTANCE.addConditionVariable("y", BPush::_init_$lambda$6);
        INSTANCE.addConditionVariable("z", BPush::_init_$lambda$7);
    }
}

