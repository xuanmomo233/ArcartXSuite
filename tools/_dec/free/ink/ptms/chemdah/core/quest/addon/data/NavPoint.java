/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.common.util.Location
 *  ink.ptms.chemdah.taboolib.common.util.OptionalKt
 *  ink.ptms.chemdah.taboolib.common5.Baffle
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.library.xseries.particles.XParticle
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  ink.ptms.chemdah.taboolib.module.navigation.Node
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt
 *  ink.ptms.chemdah.taboolib.module.nms.NMSParticleKt
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLocationKt
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.IntIterator
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.IntProgression
 *  kotlin1822.ranges.RangesKt
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.entity.Player
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common.util.Location;
import ink.ptms.chemdah.taboolib.common.util.OptionalKt;
import ink.ptms.chemdah.taboolib.common5.Baffle;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.xseries.particles.XParticle;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.navigation.Node;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt;
import ink.ptms.chemdah.taboolib.module.nms.NMSParticleKt;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLocationKt;
import ink.ptms.chemdah.util.Effects;
import ink.ptms.chemdah.util.UtilsKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.IntIterator;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.IntProgression;
import kotlin1822.ranges.RangesKt;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\b&\u0018\u00002\u00020\u0001:\u0002\u0015\u0016B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00140\u0013H&R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u001f\u0010\u0007\u001a\u00070\b\u00a2\u0006\u0002\b\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\r\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/core/quest/addon/data/NavPoint;", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "period", "Link/ptms/chemdah/taboolib/common5/Baffle;", "Lorg/jetbrains/annotations/NotNull;", "getPeriod", "()Link/ptms/chemdah/taboolib/common5/Baffle;", "setPeriod", "(Link/ptms/chemdah/taboolib/common5/Baffle;)V", "display", "", "player", "Lorg/bukkit/entity/Player;", "nodes", "", "Link/ptms/chemdah/taboolib/module/navigation/Node;", "Arrow", "Normal", "Chemdah"})
public abstract class NavPoint {
    @NotNull
    private final ConfigurationSection config;
    @NotNull
    private Baffle period;

    public NavPoint(@NotNull ConfigurationSection config) {
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        this.config = config;
        Baffle baffle = Baffle.of((int)this.config.getInt("period"));
        Intrinsics.checkNotNullExpressionValue((Object)baffle, (String)"of(config.getInt(\"period\"))");
        this.period = baffle;
    }

    @NotNull
    public final ConfigurationSection getConfig() {
        return this.config;
    }

    @NotNull
    public final Baffle getPeriod() {
        return this.period;
    }

    public final void setPeriod(@NotNull Baffle baffle) {
        Intrinsics.checkNotNullParameter((Object)baffle, (String)"<set-?>");
        this.period = baffle;
    }

    public abstract void display(@NotNull Player var1, @NotNull List<? extends Node> var2);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000D\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0016\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B?\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0005\u0012\u0006\u0010\t\u001a\u00020\u0005\u0012\u0006\u0010\n\u001a\u00020\u000b\u0012\u0006\u0010\f\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\rB\r\u0012\u0006\u0010\u000e\u001a\u00020\u000f\u00a2\u0006\u0002\u0010\u0010J\u001e\u0010%\u001a\u00020&2\u0006\u0010'\u001a\u00020(2\f\u0010)\u001a\b\u0012\u0004\u0012\u00020+0*H\u0016R\u001a\u0010\t\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u001a\u0010\u0006\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018R\u001a\u0010\b\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0019\u0010\u0012\"\u0004\b\u001a\u0010\u0014R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001b\u0010\u001c\"\u0004\b\u001d\u0010\u001eR\u001a\u0010\n\u001a\u00020\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001f\u0010 \"\u0004\b!\u0010\"R\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b#\u0010\u0012\"\u0004\b$\u0010\u0014\u00a8\u0006,"}, d2={"Link/ptms/chemdah/core/quest/addon/data/NavPoint$Arrow;", "Link/ptms/chemdah/core/quest/addon/data/NavPoint;", "particle", "Lorg/bukkit/Particle;", "y", "", "density", "", "length", "angle", "speed", "", "periodTicks", "(Lorg/bukkit/Particle;DIDDJI)V", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getAngle", "()D", "setAngle", "(D)V", "getDensity", "()I", "setDensity", "(I)V", "getLength", "setLength", "getParticle", "()Lorg/bukkit/Particle;", "setParticle", "(Lorg/bukkit/Particle;)V", "getSpeed", "()J", "setSpeed", "(J)V", "getY", "setY", "display", "", "player", "Lorg/bukkit/entity/Player;", "nodes", "", "Link/ptms/chemdah/taboolib/module/navigation/Node;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nNavPoint.kt\nKotlin\n*S Kotlin\n*F\n+ 1 NavPoint.kt\nink/ptms/chemdah/core/quest/addon/data/NavPoint$Arrow\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,155:1\n1855#2,2:156\n*S KotlinDebug\n*F\n+ 1 NavPoint.kt\nink/ptms/chemdah/core/quest/addon/data/NavPoint$Arrow\n*L\n139#1:156,2\n*E\n"})
    public static final class Arrow
    extends NavPoint {
        @NotNull
        private Particle particle;
        private double y;
        private int density;
        private double length;
        private double angle;
        private long speed;

        public Arrow(@NotNull ConfigurationSection config) {
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            super(config);
            Optional optional = XParticle.of((String)String.valueOf(config.getString("type")));
            if (optional == null || (optional = (XParticle)OptionalKt.orNull((Optional)optional)) == null || (optional = optional.get()) == null) {
                Particle particle = XParticle.CRIT.get();
                optional = particle;
                Intrinsics.checkNotNull((Object)particle);
            }
            Intrinsics.checkNotNullExpressionValue((Object)optional, (String)"XParticle.of(config.getS\u2026?: XParticle.CRIT.get()!!");
            this.particle = optional;
            this.y = config.getDouble("y");
            this.density = config.getInt("density");
            this.length = config.getDouble("length", config.getDouble("len"));
            this.angle = config.getDouble("angle");
            this.speed = config.getLong("speed");
        }

        @NotNull
        public final Particle getParticle() {
            return this.particle;
        }

        public final void setParticle(@NotNull Particle particle) {
            Intrinsics.checkNotNullParameter((Object)particle, (String)"<set-?>");
            this.particle = particle;
        }

        public final double getY() {
            return this.y;
        }

        public final void setY(double d) {
            this.y = d;
        }

        public final int getDensity() {
            return this.density;
        }

        public final void setDensity(int n) {
            this.density = n;
        }

        public final double getLength() {
            return this.length;
        }

        public final void setLength(double d) {
            this.length = d;
        }

        public final double getAngle() {
            return this.angle;
        }

        public final void setAngle(double d) {
            this.angle = d;
        }

        public final long getSpeed() {
            return this.speed;
        }

        public final void setSpeed(long l) {
            this.speed = l;
        }

        public Arrow(@NotNull Particle particle, double y, int density, double length, double angle, long speed, int periodTicks) {
            Intrinsics.checkNotNullParameter((Object)particle, (String)"particle");
            this((ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null));
            this.particle = particle;
            this.y = y;
            this.density = density;
            this.length = length;
            this.angle = angle;
            this.speed = speed;
            Baffle baffle = Baffle.of((int)periodTicks);
            Intrinsics.checkNotNullExpressionValue((Object)baffle, (String)"of(periodTicks)");
            this.setPeriod(baffle);
        }

        @Override
        public void display(@NotNull Player player2, @NotNull List<? extends Node> nodes) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter(nodes, (String)"nodes");
            Iterable $this$forEach$iv = (Iterable)RangesKt.step((IntProgression)((IntProgression)RangesKt.until((int)0, (int)(nodes.size() - 1))), (int)2);
            boolean $i$f$forEach = false;
            Iterator iterator = $this$forEach$iv.iterator();
            while (iterator.hasNext()) {
                int element$iv;
                int it = element$iv = ((IntIterator)iterator).nextInt();
                boolean bl = false;
                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)((long)it * this.speed), (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(nodes, it, player2, this){
                    final /* synthetic */ List<Node> $nodes;
                    final /* synthetic */ int $it;
                    final /* synthetic */ Player $player;
                    final /* synthetic */ Arrow this$0;
                    {
                        this.$nodes = $nodes;
                        this.$it = $it;
                        this.$player = $player;
                        this.this$0 = $receiver;
                        super(1);
                    }

                    /*
                     * WARNING - void declaration
                     */
                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                        void $this$mapTo$iv$iv;
                        void $this$map$iv;
                        Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                        org.bukkit.Location location = this.$nodes.get(this.$it).asBlockPos().toLocation(this.$player.getWorld()).add(0.5, this.this$0.getY(), 0.5);
                        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"nodes[it].asBlockPos().t\u2026r.world).add(0.5, y, 0.5)");
                        Location start = BukkitLocationKt.toProxyLocation((org.bukkit.Location)location);
                        org.bukkit.Location location2 = this.$nodes.get(this.$it + 1).asBlockPos().toLocation(this.$player.getWorld()).add(0.5, this.this$0.getY(), 0.5);
                        Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"nodes[it + 1].asBlockPos\u2026r.world).add(0.5, y, 0.5)");
                        Location target = BukkitLocationKt.toProxyLocation((org.bukkit.Location)location2);
                        Iterable iterable = Effects.INSTANCE.drawArrow(start, target, this.this$0.getDensity(), this.this$0.getLength(), this.this$0.getAngle());
                        Arrow arrow = this.this$0;
                        Player player2 = this.$player;
                        boolean $i$f$map = false;
                        void var7_8 = $this$map$iv;
                        Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                        boolean $i$f$mapTo = false;
                        for (T item$iv$iv : $this$mapTo$iv$iv) {
                            void pos;
                            Location location3 = (Location)item$iv$iv;
                            Collection collection = destination$iv$iv;
                            boolean bl = false;
                            collection.add(NMSParticleKt.createPacket$default((Particle)arrow.getParticle(), (org.bukkit.Location)BukkitLocationKt.toBukkitLocation((Location)pos), (Vector)new Vector(0, 0, 0), (double)0.0, (int)0, null, (int)28, null));
                        }
                        MinecraftServerUtilKt.sendBundlePacket((Player)player2, (List)((List)destination$iv$iv));
                    }
                }), (int)11, null);
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000D\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0016\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B?\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\u0005\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u0012\u0006\u0010\f\u001a\u00020\t\u00a2\u0006\u0002\u0010\rB\r\u0012\u0006\u0010\u000e\u001a\u00020\u000f\u00a2\u0006\u0002\u0010\u0010J\u001e\u0010%\u001a\u00020&2\u0006\u0010'\u001a\u00020(2\f\u0010)\u001a\b\u0012\u0004\u0012\u00020+0*H\u0016R\u001a\u0010\b\u001a\u00020\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018R\u001a\u0010\u0006\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0019\u0010\u001a\"\u0004\b\u001b\u0010\u001cR\u001a\u0010\u0007\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001d\u0010\u001a\"\u0004\b\u001e\u0010\u001cR\u001a\u0010\n\u001a\u00020\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001f\u0010 \"\u0004\b!\u0010\"R\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b#\u0010\u001a\"\u0004\b$\u0010\u001c\u00a8\u0006,"}, d2={"Link/ptms/chemdah/core/quest/addon/data/NavPoint$Normal;", "Link/ptms/chemdah/core/quest/addon/data/NavPoint;", "particle", "Lorg/bukkit/Particle;", "y", "", "sizeX", "sizeY", "count", "", "speed", "", "periodTicks", "(Lorg/bukkit/Particle;DDDIJI)V", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getCount", "()I", "setCount", "(I)V", "getParticle", "()Lorg/bukkit/Particle;", "setParticle", "(Lorg/bukkit/Particle;)V", "getSizeX", "()D", "setSizeX", "(D)V", "getSizeY", "setSizeY", "getSpeed", "()J", "setSpeed", "(J)V", "getY", "setY", "display", "", "player", "Lorg/bukkit/entity/Player;", "nodes", "", "Link/ptms/chemdah/taboolib/module/navigation/Node;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nNavPoint.kt\nKotlin\n*S Kotlin\n*F\n+ 1 NavPoint.kt\nink/ptms/chemdah/core/quest/addon/data/NavPoint$Normal\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,155:1\n1864#2,3:156\n*S KotlinDebug\n*F\n+ 1 NavPoint.kt\nink/ptms/chemdah/core/quest/addon/data/NavPoint$Normal\n*L\n78#1:156,3\n*E\n"})
    public static final class Normal
    extends NavPoint {
        @NotNull
        private Particle particle;
        private double y;
        private double sizeX;
        private double sizeY;
        private int count;
        private long speed;

        public Normal(@NotNull ConfigurationSection config) {
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            super(config);
            Optional optional = XParticle.of((String)String.valueOf(config.getString("type")));
            if (optional == null || (optional = (XParticle)OptionalKt.orNull((Optional)optional)) == null || (optional = optional.get()) == null) {
                Particle particle = XParticle.CRIT.get();
                optional = particle;
                Intrinsics.checkNotNull((Object)particle);
            }
            Intrinsics.checkNotNullExpressionValue((Object)optional, (String)"XParticle.of(config.getS\u2026?: XParticle.CRIT.get()!!");
            this.particle = optional;
            this.y = config.getDouble("y");
            this.sizeX = config.getDouble("size.x");
            this.sizeY = config.getDouble("size.y");
            this.count = config.getInt("count");
            this.speed = config.getLong("speed");
        }

        @NotNull
        public final Particle getParticle() {
            return this.particle;
        }

        public final void setParticle(@NotNull Particle particle) {
            Intrinsics.checkNotNullParameter((Object)particle, (String)"<set-?>");
            this.particle = particle;
        }

        public final double getY() {
            return this.y;
        }

        public final void setY(double d) {
            this.y = d;
        }

        public final double getSizeX() {
            return this.sizeX;
        }

        public final void setSizeX(double d) {
            this.sizeX = d;
        }

        public final double getSizeY() {
            return this.sizeY;
        }

        public final void setSizeY(double d) {
            this.sizeY = d;
        }

        public final int getCount() {
            return this.count;
        }

        public final void setCount(int n) {
            this.count = n;
        }

        public final long getSpeed() {
            return this.speed;
        }

        public final void setSpeed(long l) {
            this.speed = l;
        }

        public Normal(@NotNull Particle particle, double y, double sizeX, double sizeY, int count2, long speed, int periodTicks) {
            Intrinsics.checkNotNullParameter((Object)particle, (String)"particle");
            this((ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null));
            this.particle = particle;
            this.y = y;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.count = count2;
            this.speed = speed;
            Baffle baffle = Baffle.of((int)periodTicks);
            Intrinsics.checkNotNullExpressionValue((Object)baffle, (String)"of(periodTicks)");
            this.setPeriod(baffle);
        }

        /*
         * WARNING - void declaration
         */
        @Override
        public void display(@NotNull Player player2, @NotNull List<? extends Node> nodes) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter(nodes, (String)"nodes");
            Iterable $this$forEachIndexed$iv = nodes;
            boolean $i$f$forEachIndexed = false;
            int index$iv = 0;
            for (Object item$iv : $this$forEachIndexed$iv) {
                void node;
                int n;
                if ((n = index$iv++) < 0) {
                    CollectionsKt.throwIndexOverflow();
                }
                Node node2 = (Node)item$iv;
                int index = n;
                boolean bl = false;
                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)((long)index * this.speed), (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(player2, this, (Node)node){
                    final /* synthetic */ Player $player;
                    final /* synthetic */ Normal this$0;
                    final /* synthetic */ Node $node;
                    {
                        this.$player = $player;
                        this.this$0 = $receiver;
                        this.$node = $node;
                        super(1);
                    }

                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                        Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                        Particle particle = this.this$0.getParticle();
                        org.bukkit.Location location = this.$node.asBlockPos().toLocation(this.$player.getWorld()).add(0.5, this.this$0.getY(), 0.5);
                        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"node.asBlockPos().toLoca\u2026r.world).add(0.5, y, 0.5)");
                        UtilsKt.sendTo$default(this.$player, particle, location, new Vector(this.this$0.getSizeX(), this.this$0.getSizeY(), this.this$0.getSizeX()), this.this$0.getCount(), 0.0, null, 32, null);
                    }
                }), (int)11, null);
            }
        }
    }
}

