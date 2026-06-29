/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.util.OptionalKt
 *  ink.ptms.chemdah.taboolib.common5.Baffle
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.library.xseries.particles.XParticle
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.ranges.RangesKt
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.entity.Player
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.taboolib.common.util.OptionalKt;
import ink.ptms.chemdah.taboolib.common5.Baffle;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.xseries.particles.XParticle;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.util.ConfigurationKt;
import ink.ptms.chemdah.util.UtilsKt;
import java.util.Optional;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.ranges.RangesKt;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000L\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B7\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0005\u0012\u0006\u0010\t\u001a\u00020\n\u0012\u0006\u0010\u000b\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\fB\u0015\u0012\u0006\u0010\r\u001a\u00020\u000e\u0012\u0006\u0010\u000f\u001a\u00020\u000e\u00a2\u0006\u0002\u0010\u0010J\u0018\u00105\u001a\u0002062\u0006\u00107\u001a\u0002082\u0006\u00109\u001a\u00020:H\u0016R\u0011\u0010\r\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u001a\u0010\u0006\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0013\u0010\u0014\"\u0004\b\u0015\u0010\u0016R\u001a\u0010\b\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0017\u0010\u0018\"\u0004\b\u0019\u0010\u001aR\u001a\u0010\u001b\u001a\u00020\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001c\u0010\u001d\"\u0004\b\u001e\u0010\u001fR\u001a\u0010 \u001a\u00020\u000eX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b!\u0010\u0012\"\u0004\b\"\u0010#R\u001a\u0010$\u001a\u00020\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b$\u0010\u001d\"\u0004\b%\u0010\u001fR\u001f\u0010&\u001a\u00070'\u00a2\u0006\u0002\b(X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b)\u0010*\"\u0004\b+\u0010,R\u0011\u0010\u000f\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b-\u0010\u0012R\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b.\u0010\u0018\"\u0004\b/\u0010\u001aR\u001a\u00100\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b1\u00102\"\u0004\b3\u00104\u00a8\u0006;"}, d2={"Link/ptms/chemdah/core/quest/addon/data/TrackBeacon;", "", "particle", "Lorg/bukkit/Particle;", "size", "", "count", "", "distance", "fixed", "", "periodTicks", "(Lorg/bukkit/Particle;DIDZI)V", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "root", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getCount", "()I", "setCount", "(I)V", "getDistance", "()D", "setDistance", "(D)V", "enable", "getEnable", "()Z", "setEnable", "(Z)V", "extraOptions", "getExtraOptions", "setExtraOptions", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "isFixed", "setFixed", "period", "Link/ptms/chemdah/taboolib/common5/Baffle;", "Lorg/jetbrains/annotations/NotNull;", "getPeriod", "()Link/ptms/chemdah/taboolib/common5/Baffle;", "setPeriod", "(Link/ptms/chemdah/taboolib/common5/Baffle;)V", "getRoot", "getSize", "setSize", "type", "getType", "()Lorg/bukkit/Particle;", "setType", "(Lorg/bukkit/Particle;)V", "display", "", "player", "Lorg/bukkit/entity/Player;", "center", "Lorg/bukkit/Location;", "Chemdah"})
public class TrackBeacon {
    @NotNull
    private final ConfigurationSection config;
    @NotNull
    private final ConfigurationSection root;
    private boolean enable;
    @NotNull
    private ConfigurationSection extraOptions;
    @NotNull
    private Particle type;
    private double size;
    private int count;
    private double distance;
    private boolean isFixed;
    @NotNull
    private Baffle period;

    public TrackBeacon(@NotNull ConfigurationSection config, @NotNull ConfigurationSection root2) {
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
        Optional optional = XParticle.of((String)String.valueOf(this.extraOptions.getString("type")));
        if (optional == null || (optional = (XParticle)OptionalKt.orNull((Optional)optional)) == null || (optional = optional.get()) == null) {
            Particle particle = XParticle.HAPPY_VILLAGER.get();
            optional = particle;
            Intrinsics.checkNotNull((Object)particle);
        }
        Intrinsics.checkNotNullExpressionValue((Object)optional, (String)"XParticle.of(extraOption\u2026le.HAPPY_VILLAGER.get()!!");
        this.type = optional;
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
    public final Particle getType() {
        return this.type;
    }

    public final void setType(@NotNull Particle particle) {
        Intrinsics.checkNotNullParameter((Object)particle, (String)"<set-?>");
        this.type = particle;
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

    public TrackBeacon(@NotNull Particle particle, double size, int count2, double distance, boolean fixed, int periodTicks) {
        Intrinsics.checkNotNullParameter((Object)particle, (String)"particle");
        this((ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null), (ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null));
        this.enable = true;
        this.type = particle;
        this.size = size;
        this.count = count2;
        this.distance = distance;
        this.isFixed = fixed;
        Baffle baffle = Baffle.of((int)periodTicks);
        Intrinsics.checkNotNullExpressionValue((Object)baffle, (String)"of(periodTicks)");
        this.period = baffle;
    }

    public void display(@NotNull Player player2, @NotNull Location center2) {
        Location location;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
        if (this.isFixed) {
            location = center2;
        } else {
            Vector vector = center2.toVector().subtract(player2.getLocation().toVector()).normalize();
            Intrinsics.checkNotNullExpressionValue((Object)vector, (String)"center.toVector().subtra\u2026n.toVector()).normalize()");
            Vector direction = vector;
            Location location2 = player2.getLocation().add(direction.multiply(RangesKt.coerceAtMost((double)this.distance, (double)this.distance)));
            Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"{\n            val direct\u2026ost(distance)))\n        }");
            location = location2;
        }
        Location pos = location;
        UtilsKt.sendTo$default(player2, this.type, pos, new Vector(this.size, 128.0, this.size), this.count, 0.0, null, 32, null);
    }
}

