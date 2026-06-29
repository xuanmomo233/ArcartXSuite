/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  org.bukkit.event.player.PlayerBucketEmptyEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.bukkit.APlayerBucket;
import kotlin.Metadata;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u503e\u5012\u6876\u76ee\u6807", description={"\u73a9\u5bb6\u503e\u5012\u6876\u4e2d\u7684\u6d41\u4f53", "\u652f\u6301\u4f4d\u7f6e\u3001\u6750\u8d28\u3001\u7269\u54c1\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u503e\u5012\u6b21\u6570"}, alias={"\u5012\u6876", "\u653e\u6c34", "\u503e\u5012"}, params={@ParamInfo(name="position", type="Location", description="\u70b9\u51fb\u7684\u65b9\u5757\u4f4d\u7f6e"), @ParamInfo(name="material", type="Block", description="\u503e\u5012\u540e\u7684\u65b9\u5757"), @ParamInfo(name="material:clicked", type="Block", description="\u70b9\u51fb\u7684\u65b9\u5757"), @ParamInfo(name="item", type="ItemStack", description="\u624b\u6301\u7269\u54c1"), @ParamInfo(name="item:bucket", type="ItemStack", description="\u503e\u5012\u7684\u6876"), @ParamInfo(name="face", type="String", description="\u70b9\u51fb\u7684\u65b9\u5757\u9762"), @ParamInfo(name="hand", type="String", description="\u4f7f\u7528\u7684\u624b\uff08HAND/OFF_HAND\uff09")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerBucketEmpty;", "Link/ptms/chemdah/core/quest/objective/bukkit/APlayerBucket;", "Lorg/bukkit/event/player/PlayerBucketEmptyEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerBucketEmpty
extends APlayerBucket<PlayerBucketEmptyEvent> {
    @NotNull
    public static final IPlayerBucketEmpty INSTANCE = new IPlayerBucketEmpty();
    @NotNull
    private static final String name = "bucket empty";

    private IPlayerBucketEmpty() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerBucketEmptyEvent> getEvent() {
        return PlayerBucketEmptyEvent.class;
    }
}

