/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.quest.addon.data.NavPoint;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.navigation.Node;
import ink.ptms.chemdah.taboolib.module.navigation.NodeEntity;
import ink.ptms.chemdah.taboolib.module.navigation.Path;
import ink.ptms.chemdah.taboolib.module.navigation.PathFinder;
import ink.ptms.chemdah.taboolib.module.navigation.UtilsKt;
import ink.ptms.chemdah.util.ConfigurationKt;
import java.util.List;
import java.util.Locale;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\u0018\u0010'\u001a\u00020(2\u0006\u0010)\u001a\u00020*2\u0006\u0010+\u001a\u00020,H\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u001a\u0010\b\u001a\u00020\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u001a\u0010\u000e\u001a\u00020\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0011\"\u0004\b\u0012\u0010\u0013R\u001a\u0010\u0014\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0007\"\u0004\b\u0016\u0010\u0017R\u001a\u0010\u0018\u001a\u00020\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0018\u0010\u0011\"\u0004\b\u0019\u0010\u0013R\u001c\u0010\u001a\u001a\u0004\u0018\u00010\u001bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001c\u0010\u001d\"\u0004\b\u001e\u0010\u001fR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u0007R\u001a\u0010!\u001a\u00020\"X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b#\u0010$\"\u0004\b%\u0010&\u00a8\u0006-"}, d2={"Link/ptms/chemdah/core/quest/addon/data/TrackNavigation;", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "root", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "distance", "", "getDistance", "()F", "setDistance", "(F)V", "enable", "", "getEnable", "()Z", "setEnable", "(Z)V", "extraOptions", "getExtraOptions", "setExtraOptions", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "isRunInPrimaryThread", "setRunInPrimaryThread", "naviPoint", "Link/ptms/chemdah/core/quest/addon/data/NavPoint;", "getNaviPoint", "()Link/ptms/chemdah/core/quest/addon/data/NavPoint;", "setNaviPoint", "(Link/ptms/chemdah/core/quest/addon/data/NavPoint;)V", "getRoot", "type", "", "getType", "()Ljava/lang/String;", "setType", "(Ljava/lang/String;)V", "display", "", "player", "Lorg/bukkit/entity/Player;", "center", "Lorg/bukkit/Location;", "Chemdah"})
public class TrackNavigation {
    @NotNull
    private final ConfigurationSection config;
    @NotNull
    private final ConfigurationSection root;
    private boolean enable;
    @NotNull
    private ConfigurationSection extraOptions;
    private boolean isRunInPrimaryThread;
    private float distance;
    @NotNull
    private String type;
    @Nullable
    private NavPoint naviPoint;

    public TrackNavigation(@NotNull ConfigurationSection config, @NotNull ConfigurationSection root2) {
        NavPoint navPoint;
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.config = config;
        this.root = root2;
        this.enable = this.config.getBoolean("navigation", this.root.getBoolean("value"));
        ConfigurationSection configurationSection = this.config.getConfigurationSection("navigation-option");
        if (configurationSection == null) {
            configurationSection = (ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null);
        }
        this.extraOptions = ConfigurationKt.mergeTo$default(this.root, configurationSection, false, 2, null);
        this.isRunInPrimaryThread = this.extraOptions.getBoolean("sync");
        this.distance = (float)this.extraOptions.getDouble("distance");
        String string = String.valueOf(this.extraOptions.getString("type")).toUpperCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toUpperCase(Locale.ROOT)");
        String string2 = this.type = string;
        if (Intrinsics.areEqual((Object)string2, (Object)"POINT")) {
            ConfigurationSection configurationSection2 = this.extraOptions.getConfigurationSection("point");
            if (configurationSection2 == null) {
                configurationSection2 = (ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null);
            }
            navPoint = new NavPoint.Normal(configurationSection2);
        } else if (Intrinsics.areEqual((Object)string2, (Object)"ARROW")) {
            ConfigurationSection configurationSection3 = this.extraOptions.getConfigurationSection("arrow");
            if (configurationSection3 == null) {
                configurationSection3 = (ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null);
            }
            navPoint = new NavPoint.Arrow(configurationSection3);
        } else {
            navPoint = null;
        }
        this.naviPoint = navPoint;
    }

    @NotNull
    public final ConfigurationSection getConfig() {
        return this.config;
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    public final boolean getEnable() {
        return this.enable;
    }

    public final void setEnable(boolean bl) {
        this.enable = bl;
    }

    @NotNull
    public final ConfigurationSection getExtraOptions() {
        return this.extraOptions;
    }

    public final void setExtraOptions(@NotNull ConfigurationSection configurationSection) {
        Intrinsics.checkNotNullParameter((Object)configurationSection, (String)"<set-?>");
        this.extraOptions = configurationSection;
    }

    public final boolean isRunInPrimaryThread() {
        return this.isRunInPrimaryThread;
    }

    public final void setRunInPrimaryThread(boolean bl) {
        this.isRunInPrimaryThread = bl;
    }

    public final float getDistance() {
        return this.distance;
    }

    public final void setDistance(float f) {
        this.distance = f;
    }

    @NotNull
    public final String getType() {
        return this.type;
    }

    public final void setType(@NotNull String string) {
        Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
        this.type = string;
    }

    @Nullable
    public final NavPoint getNaviPoint() {
        return this.naviPoint;
    }

    public final void setNaviPoint(@Nullable NavPoint navPoint) {
        this.naviPoint = navPoint;
    }

    public void display(@NotNull Player player, @NotNull Location center2) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
        if (this.naviPoint == null) {
            return;
        }
        ExecutorKt.submit$default((boolean)false, (!this.isRunInPrimaryThread ? 1 : 0) != 0, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(player, center2, this){
            final /* synthetic */ Player $player;
            final /* synthetic */ Location $center;
            final /* synthetic */ TrackNavigation this$0;
            {
                this.$player = $player;
                this.$center = $center;
                this.this$0 = $receiver;
                super(1);
            }

            public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                Location location = this.$player.getLocation();
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"location");
                PathFinder pathFinder = UtilsKt.createPathfinder((NodeEntity)new NodeEntity(location, 2.0, 1.0, 0.0, true, true, false, null, null, 0.0f, 968, null));
                Path path = PathFinder.findPath$default((PathFinder)pathFinder, (Location)this.$center, (float)this.this$0.getDistance(), (int)0, (float)0.0f, (int)12, null);
                Object object = path;
                if (object == null || (object = object.getNodes()) == null) {
                    return;
                }
                Object pathNodes = object;
                NavPoint navPoint = this.this$0.getNaviPoint();
                Intrinsics.checkNotNull((Object)navPoint);
                navPoint.display(this.$player, (List<? extends Node>)pathNodes);
            }
        }), (int)13, null);
    }
}

