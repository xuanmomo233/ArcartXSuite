/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyParticle
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.util.Location
 *  ink.ptms.chemdah.taboolib.common.util.Vector
 *  ink.ptms.chemdah.taboolib.common5.Baffle
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLocationKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.ranges.RangesKt
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.taboolib.common.platform.ProxyParticle;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.util.Location;
import ink.ptms.chemdah.taboolib.common.util.Vector;
import ink.ptms.chemdah.taboolib.common5.Baffle;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLocationKt;
import ink.ptms.chemdah.util.ConfigurationKt;
import java.util.Locale;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.ranges.RangesKt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000P\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0010\u0006\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\u0018\u00101\u001a\u0002022\u0006\u00103\u001a\u0002042\u0006\u00105\u001a\u000206H\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u001a\u0010\b\u001a\u00020\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u001a\u0010\u000e\u001a\u00020\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0011\"\u0004\b\u0012\u0010\u0013R\u001a\u0010\u0014\u001a\u00020\u0015X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0016\u0010\u0017\"\u0004\b\u0018\u0010\u0019R\u001a\u0010\u001a\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001b\u0010\u0007\"\u0004\b\u001c\u0010\u001dR\u001a\u0010\u001e\u001a\u00020\u0015X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001e\u0010\u0017\"\u0004\b\u001f\u0010\u0019R\u001f\u0010 \u001a\u00070!\u00a2\u0006\u0002\b\"X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b#\u0010$\"\u0004\b%\u0010&R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b'\u0010\u0007R\u001a\u0010(\u001a\u00020\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b)\u0010\u0011\"\u0004\b*\u0010\u0013R\u001a\u0010+\u001a\u00020,X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b-\u0010.\"\u0004\b/\u00100\u00a8\u00067"}, d2={"Link/ptms/chemdah/core/quest/addon/data/TrackBeacon;", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "root", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "count", "", "getCount", "()I", "setCount", "(I)V", "distance", "", "getDistance", "()D", "setDistance", "(D)V", "enable", "", "getEnable", "()Z", "setEnable", "(Z)V", "extraOptions", "getExtraOptions", "setExtraOptions", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "isFixed", "setFixed", "period", "Link/ptms/chemdah/taboolib/common5/Baffle;", "Lorg/jetbrains/annotations/NotNull;", "getPeriod", "()Link/ptms/chemdah/taboolib/common5/Baffle;", "setPeriod", "(Link/ptms/chemdah/taboolib/common5/Baffle;)V", "getRoot", "size", "getSize", "setSize", "type", "Link/ptms/chemdah/taboolib/common/platform/ProxyParticle;", "getType", "()Link/ptms/chemdah/taboolib/common/platform/ProxyParticle;", "setType", "(Link/ptms/chemdah/taboolib/common/platform/ProxyParticle;)V", "display", "", "player", "Lorg/bukkit/entity/Player;", "center", "Lorg/bukkit/Location;", "Chemdah"})
public class TrackBeacon {
    @NotNull
    private final ConfigurationSection config;
    @NotNull
    private final ConfigurationSection root;
    private boolean enable;
    @NotNull
    private ConfigurationSection extraOptions;
    @NotNull
    private ProxyParticle type;
    private double size;
    private int count;
    private double distance;
    private boolean isFixed;
    @NotNull
    private Baffle period;

    public TrackBeacon(@NotNull ConfigurationSection config, @NotNull ConfigurationSection root2) {
        ProxyParticle proxyParticle;
        TrackBeacon trackBeacon;
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.config = config;
        this.root = root2;
        this.enable = this.config.getBoolean("beacon", this.root.getBoolean("value"));
        ConfigurationSection configurationSection = this.config.getConfigurationSection("beacon-option");
        if (configurationSection == null) {
            configurationSection = (ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null);
        }
        this.extraOptions = ConfigurationKt.mergeTo(this.root, configurationSection, false);
        TrackBeacon trackBeacon2 = this;
        try {
            trackBeacon = trackBeacon2;
            String string = this.extraOptions.getString("type");
            Intrinsics.checkNotNull((Object)string);
            String string2 = string.toUpperCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"this as java.lang.String).toUpperCase(Locale.ROOT)");
            proxyParticle = ProxyParticle.valueOf((String)string2);
        }
        catch (Throwable throwable) {
            trackBeacon = trackBeacon2;
            proxyParticle = ProxyParticle.HAPPY_VILLAGER;
        }
        trackBeacon.type = proxyParticle;
        this.size = this.extraOptions.getDouble("size");
        this.count = this.extraOptions.getInt("count");
        this.distance = this.extraOptions.getDouble("distance");
        this.isFixed = this.extraOptions.getBoolean("fixed");
        Baffle baffle = Baffle.of((int)this.extraOptions.getInt("period"));
        Intrinsics.checkNotNullExpressionValue((Object)baffle, (String)"of(extraOptions.getInt(\"period\"))");
        this.period = baffle;
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

    @NotNull
    public final ProxyParticle getType() {
        return this.type;
    }

    public final void setType(@NotNull ProxyParticle proxyParticle) {
        Intrinsics.checkNotNullParameter((Object)proxyParticle, (String)"<set-?>");
        this.type = proxyParticle;
    }

    public final double getSize() {
        return this.size;
    }

    public final void setSize(double d) {
        this.size = d;
    }

    public final int getCount() {
        return this.count;
    }

    public final void setCount(int n) {
        this.count = n;
    }

    public final double getDistance() {
        return this.distance;
    }

    public final void setDistance(double d) {
        this.distance = d;
    }

    public final boolean isFixed() {
        return this.isFixed;
    }

    public final void setFixed(boolean bl) {
        this.isFixed = bl;
    }

    @NotNull
    public final Baffle getPeriod() {
        return this.period;
    }

    public final void setPeriod(@NotNull Baffle baffle) {
        Intrinsics.checkNotNullParameter((Object)baffle, (String)"<set-?>");
        this.period = baffle;
    }

    public void display(@NotNull Player player, @NotNull org.bukkit.Location center2) {
        org.bukkit.Location location;
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
        if (this.isFixed) {
            location = center2;
        } else {
            org.bukkit.util.Vector vector = center2.toVector().subtract(player.getLocation().toVector()).normalize();
            Intrinsics.checkNotNullExpressionValue((Object)vector, (String)"center.toVector().subtra\u2026n.toVector()).normalize()");
            org.bukkit.util.Vector direction = vector;
            org.bukkit.Location location2 = player.getLocation().add(direction.multiply(RangesKt.coerceAtMost((double)this.distance, (double)this.distance)));
            Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"{\n            val direct\u2026ost(distance)))\n        }");
            location = location2;
        }
        org.bukkit.Location pos = location;
        ProxyParticle.sendTo$default((ProxyParticle)this.type, (ProxyPlayer)AdapterKt.adaptPlayer((Object)player), (Location)BukkitLocationKt.toProxyLocation((org.bukkit.Location)pos), (Vector)new Vector(this.size, 128.0, this.size), (int)this.count, (double)0.0, null, (int)48, null);
    }
}

