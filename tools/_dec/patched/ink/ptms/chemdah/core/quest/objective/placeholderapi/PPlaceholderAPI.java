/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.compat.PlaceholderExpansionKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Event
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.placeholderapi;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.other.APlayerData;
import ink.ptms.chemdah.taboolib.platform.compat.PlaceholderExpansionKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="PlaceholderAPI")
@MetaInfo(source="PlaceholderAPI", name="PlaceholderAPI \u53d8\u91cf\u76ee\u6807", description={"\u83b7\u53d6 PlaceholderAPI \u53d8\u91cf\u503c", "\u652f\u6301\u6240\u6709 PAPI \u53d8\u91cf", "\u9700\u8981 PlaceholderAPI \u63d2\u4ef6\u652f\u6301"}, alias={"papi", "\u53d8\u91cf", "\u5360\u4f4d\u7b26"}, params={@ParamInfo(name="key", type="string", required=true, description="PlaceholderAPI \u53d8\u91cf\u540d\u79f0")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\u0010\u001a\u00020\r2\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\rH\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\nR\u0014\u0010\u000b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\nR\u0014\u0010\f\u001a\u00020\rX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0016"}, d2={"Link/ptms/chemdah/core/quest/objective/placeholderapi/PPlaceholderAPI;", "Link/ptms/chemdah/core/quest/objective/other/APlayerData;", "Lorg/bukkit/event/Event;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "isListener", "", "()Z", "isTickable", "name", "", "getName", "()Ljava/lang/String;", "getValue", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "key", "Chemdah"})
public final class PPlaceholderAPI
extends APlayerData<Event> {
    @NotNull
    public static final PPlaceholderAPI INSTANCE = new PPlaceholderAPI();
    @NotNull
    private static final String name = "placeholder api";
    private static final boolean isListener;
    private static final boolean isTickable;

    private PPlaceholderAPI() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<Event> getEvent() {
        return Event.class;
    }

    @Override
    public boolean isListener() {
        return isListener;
    }

    @Override
    public boolean isTickable() {
        return isTickable;
    }

    @Override
    @NotNull
    public String getValue(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Player player2 = profile.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"profile.player");
        return PlaceholderExpansionKt.replacePlaceholder((String)key, (Player)player2);
    }

    static {
        isTickable = true;
    }
}

