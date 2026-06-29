/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.selector;

import ink.ptms.adyeshach.core.util.UtilsKt;
import ink.ptms.chemdah.core.quest.selector.DataMatchHandler;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.module.navigation.BoundingBox;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\b&\u0018\u0000 \u00112\u00020\u0001:\u0005\u0010\u0011\u0012\u0013\u0014B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0010\u0010\u000b\u001a\u00020\u00052\u0006\u0010\f\u001a\u00020\rH&J\u000e\u0010\u000b\u001a\u00020\u00052\u0006\u0010\u000e\u001a\u00020\u000fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/core/quest/selector/InferArea;", "", "source", "", "noWorld", "", "(Ljava/lang/String;Z)V", "getNoWorld", "()Z", "getSource", "()Ljava/lang/String;", "inside", "location", "Lorg/bukkit/Location;", "vector", "Lorg/bukkit/util/Vector;", "Area", "Companion", "Range", "Single", "Unrecognized", "Chemdah"})
public abstract class InferArea {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final String source;
    private final boolean noWorld;

    public InferArea(@NotNull String source, boolean noWorld) {
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        this.source = source;
        this.noWorld = noWorld;
    }

    @NotNull
    public final String getSource() {
        return this.source;
    }

    public final boolean getNoWorld() {
        return this.noWorld;
    }

    public final boolean inside(@NotNull Vector vector) {
        Intrinsics.checkNotNullParameter((Object)vector, (String)"vector");
        Location location = vector.toLocation((World)Bukkit.getWorlds().get(0));
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"vector.toLocation(Bukkit.getWorlds()[0])");
        return this.inside(location);
    }

    public abstract boolean inside(@NotNull Location var1);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0010\u0010\u000e\u001a\u00020\u00052\u0006\u0010\u000f\u001a\u00020\u0010H\u0016R\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u000b\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\r\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/core/quest/selector/InferArea$Area;", "Link/ptms/chemdah/core/quest/selector/InferArea;", "source", "", "noWorld", "", "(Ljava/lang/String;Z)V", "box", "Link/ptms/chemdah/taboolib/module/navigation/BoundingBox;", "getBox", "()Link/ptms/chemdah/taboolib/module/navigation/BoundingBox;", "world", "getWorld", "()Ljava/lang/String;", "inside", "location", "Lorg/bukkit/Location;", "Chemdah"})
    public static final class Area
    extends InferArea {
        @NotNull
        private final String world;
        @NotNull
        private final BoundingBox box;

        public Area(@NotNull String source, boolean noWorld) {
            Intrinsics.checkNotNullParameter((Object)source, (String)"source");
            super(source, noWorld);
            int index = noWorld ? 0 : 1;
            String[] stringArray = new String[]{" "};
            List args = StringsKt.split$default((CharSequence)source, (String[])stringArray, (boolean)false, (int)0, (int)6, null);
            this.world = (String)args.get(0);
            double x1 = Coerce.toDouble(args.get(index + 0));
            double y1 = Coerce.toDouble(args.get(index + 1));
            double z1 = Coerce.toDouble(args.get(index + 2));
            double x2 = Coerce.toDouble(args.get(index + 4));
            double y2 = Coerce.toDouble(args.get(index + 5));
            double z2 = Coerce.toDouble(args.get(index + 6));
            this.box = new BoundingBox(Double.min(x1, x2), Double.min(y1, y2), Double.min(z1, z2), Double.max(x1, x2), Double.max(y1, y2), Double.max(z1, z2));
        }

        @NotNull
        public final String getWorld() {
            return this.world;
        }

        @NotNull
        public final BoundingBox getBox() {
            return this.box;
        }

        /*
         * Enabled force condition propagation
         * Lifted jumps to return sites
         */
        @Override
        public boolean inside(@NotNull Location location) {
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            if (!this.getNoWorld()) {
                World world = location.getWorld();
                if (!Intrinsics.areEqual((Object)this.world, (Object)(world != null ? world.getName() : null))) return false;
            }
            if (!this.box.contains(location.getX(), location.getY(), location.getZ())) return false;
            return true;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0014\u0010\u0003\u001a\u00020\u0004*\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/core/quest/selector/InferArea$Companion;", "", "()V", "toInferArea", "Link/ptms/chemdah/core/quest/selector/InferArea;", "", "noWorld", "", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final InferArea toInferArea(@NotNull String $this$toInferArea, boolean noWorld) {
            Intrinsics.checkNotNullParameter((Object)$this$toInferArea, (String)"<this>");
            return DataMatchHandler.INSTANCE.getAreaParser().parse($this$toInferArea, noWorld);
        }

        public static /* synthetic */ InferArea toInferArea$default(Companion companion, String string, boolean bl, int n, Object object) {
            if ((n & 1) != 0) {
                bl = false;
            }
            return companion.toInferArea(string, bl);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0010\u0010\u000f\u001a\u00020\u00052\u0006\u0010\u0010\u001a\u00020\bH\u0016R\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u000b\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/core/quest/selector/InferArea$Range;", "Link/ptms/chemdah/core/quest/selector/InferArea;", "source", "", "noWorld", "", "(Ljava/lang/String;Z)V", "position", "Lorg/bukkit/Location;", "getPosition", "()Lorg/bukkit/Location;", "r", "", "getR", "()D", "inside", "location", "Chemdah"})
    public static final class Range
    extends InferArea {
        @NotNull
        private final Location position;
        private final double r;

        public Range(@NotNull String source, boolean noWorld) {
            Intrinsics.checkNotNullParameter((Object)source, (String)"source");
            super(source, noWorld);
            String[] stringArray = new String[]{" "};
            List args = StringsKt.split$default((CharSequence)source, (String[])stringArray, (boolean)false, (int)0, (int)6, null);
            if (noWorld) {
                this.r = Coerce.toDouble(args.get(4));
                this.position = new Location((World)Bukkit.getWorlds().get(0), Coerce.toDouble(args.get(0)), Coerce.toDouble(args.get(1)), Coerce.toDouble(args.get(2)));
            } else {
                this.r = Coerce.toDouble(args.get(5));
                this.position = new Location(Bukkit.getWorld((String)((String)args.get(0))), Coerce.toDouble(args.get(1)), Coerce.toDouble(args.get(2)), Coerce.toDouble(args.get(3)));
            }
        }

        @NotNull
        public final Location getPosition() {
            return this.position;
        }

        public final double getR() {
            return this.r;
        }

        @Override
        public boolean inside(@NotNull Location location) {
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            return (this.getNoWorld() || Intrinsics.areEqual((Object)this.position.getWorld(), (Object)location.getWorld())) && UtilsKt.safeDistance((Location)this.position, (Location)location) <= this.r;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0010\u0010\r\u001a\u00020\u00052\u0006\u0010\u000e\u001a\u00020\tH\u0016R!\u0010\u0007\u001a\u0012\u0012\u0004\u0012\u00020\t0\bj\b\u0012\u0004\u0012\u00020\t`\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/selector/InferArea$Single;", "Link/ptms/chemdah/core/quest/selector/InferArea;", "source", "", "noWorld", "", "(Ljava/lang/String;Z)V", "positions", "Ljava/util/ArrayList;", "Lorg/bukkit/Location;", "Lkotlin1822/collections/ArrayList;", "getPositions", "()Ljava/util/ArrayList;", "inside", "location", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nInferArea.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferArea.kt\nink/ptms/chemdah/core/quest/selector/InferArea$Single\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,140:1\n1855#2,2:141\n1747#2,3:143\n*S KotlinDebug\n*F\n+ 1 InferArea.kt\nink/ptms/chemdah/core/quest/selector/InferArea$Single\n*L\n91#1:141,2\n113#1:143,3\n*E\n"})
    public static final class Single
    extends InferArea {
        @NotNull
        private final ArrayList<Location> positions;

        public Single(@NotNull String source, boolean noWorld) {
            Intrinsics.checkNotNullParameter((Object)source, (String)"source");
            super(source, noWorld);
            this.positions = new ArrayList();
            String[] stringArray = new String[]{"&"};
            Iterable $this$forEach$iv = StringsKt.split$default((CharSequence)source, (String[])stringArray, (boolean)false, (int)0, (int)6, null);
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Location location;
                String it = (String)element$iv;
                boolean bl = false;
                Object object = new String[]{" "};
                List args = StringsKt.split$default((CharSequence)((Object)StringsKt.trim((CharSequence)it)).toString(), (String[])object, (boolean)false, (int)0, (int)6, null);
                if (args.isEmpty()) continue;
                object = this.positions;
                switch (args.size()) {
                    case 3: {
                        location = new Location(null, Coerce.toDouble(args.get(0)), Coerce.toDouble(args.get(1)), Coerce.toDouble(args.get(2)));
                        break;
                    }
                    case 4: {
                        location = new Location(Bukkit.getWorld((String)((String)args.get(0))), Coerce.toDouble(args.get(1)), Coerce.toDouble(args.get(2)), Coerce.toDouble(args.get(3)));
                        break;
                    }
                    default: {
                        throw new IllegalStateException(("Unsupported " + ((Object)StringsKt.trim((CharSequence)it)).toString()).toString());
                    }
                }
                Location location2 = location;
                object.add(location2);
            }
        }

        @NotNull
        public final ArrayList<Location> getPositions() {
            return this.positions;
        }

        @Override
        public boolean inside(@NotNull Location location) {
            boolean bl;
            block3: {
                Intrinsics.checkNotNullParameter((Object)location, (String)"location");
                Iterable $this$any$iv = this.positions;
                boolean $i$f$any = false;
                if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                    bl = false;
                } else {
                    for (Object element$iv : $this$any$iv) {
                        Location it = (Location)element$iv;
                        boolean bl2 = false;
                        boolean bl3 = (it.getWorld() == null || Intrinsics.areEqual((Object)it.getWorld(), (Object)location.getWorld())) && it.getBlockX() == location.getBlockX() && it.getBlockY() == location.getBlockY() && it.getBlockZ() == location.getBlockZ();
                        if (!bl3) continue;
                        bl = true;
                        break block3;
                    }
                    bl = false;
                }
            }
            return bl;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\u0010\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bH\u0016R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/selector/InferArea$Unrecognized;", "Link/ptms/chemdah/core/quest/selector/InferArea;", "source", "", "message", "(Ljava/lang/String;Ljava/lang/String;)V", "getMessage", "()Ljava/lang/String;", "inside", "", "location", "Lorg/bukkit/Location;", "Chemdah"})
    public static final class Unrecognized
    extends InferArea {
        @NotNull
        private final String message;

        public Unrecognized(@NotNull String source, @NotNull String message2) {
            Intrinsics.checkNotNullParameter((Object)source, (String)"source");
            Intrinsics.checkNotNullParameter((Object)message2, (String)"message");
            super(source, false);
            this.message = message2;
        }

        @NotNull
        public final String getMessage() {
            return this.message;
        }

        @Override
        public boolean inside(@NotNull Location location) {
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            Object[] objectArray = new Object[]{"Unrecognized area format: " + this.getSource() + " (" + this.message + ')'};
            IOKt.warning((Object[])objectArray);
            return false;
        }
    }
}

