/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sk89q.worldedit.EditSession
 *  com.sk89q.worldedit.EditSessionFactory
 *  com.sk89q.worldedit.WorldEdit
 *  com.sk89q.worldedit.bukkit.BukkitWorld
 *  com.sk89q.worldedit.extent.Extent
 *  com.sk89q.worldedit.extent.clipboard.Clipboard
 *  com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat
 *  com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
 *  com.sk89q.worldedit.extent.clipboard.io.ClipboardReader
 *  com.sk89q.worldedit.function.operation.Operation
 *  com.sk89q.worldedit.function.operation.Operations
 *  com.sk89q.worldedit.math.BlockVector3
 *  com.sk89q.worldedit.math.transform.AffineTransform
 *  com.sk89q.worldedit.math.transform.Transform
 *  com.sk89q.worldedit.session.ClipboardHolder
 *  com.sk89q.worldedit.util.io.Closer
 *  com.sk89q.worldedit.world.World
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.library.kether.ParsedAction
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.io.CloseableKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether.compat;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EditSessionFactory;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.world.World;
import ink.ptms.chemdah.module.kether.compat.ActionSchematic;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.kether.ParsedAction;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.io.CloseableKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u0000 \u00152\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\u0015B1\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\n\u0010\u0006\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\u0006\u0010\u0007\u001a\u00020\b\u00a2\u0006\u0002\u0010\tJ\u001a\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00020\u00112\n\u0010\u0012\u001a\u00060\u0013j\u0002`\u0014H\u0016R\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\rR\u0015\u0010\u0006\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\r\u00a8\u0006\u0016"}, d2={"Link/ptms/chemdah/module/kether/compat/ActionSchematic;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "name", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "location", "rotation", "ignoreAir", "", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Z)V", "getIgnoreAir", "()Z", "getLocation", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getName", "getRotation", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Companion", "Chemdah"})
public final class ActionSchematic
extends ScriptAction<Void> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final ParsedAction<?> name;
    @NotNull
    private final ParsedAction<?> location;
    @NotNull
    private final ParsedAction<?> rotation;
    private final boolean ignoreAir;

    public ActionSchematic(@NotNull ParsedAction<?> name, @NotNull ParsedAction<?> location, @NotNull ParsedAction<?> rotation, boolean ignoreAir) {
        Intrinsics.checkNotNullParameter(name, (String)"name");
        Intrinsics.checkNotNullParameter(location, (String)"location");
        Intrinsics.checkNotNullParameter(rotation, (String)"rotation");
        this.name = name;
        this.location = location;
        this.rotation = rotation;
        this.ignoreAir = ignoreAir;
    }

    @NotNull
    public final ParsedAction<?> getName() {
        return this.name;
    }

    @NotNull
    public final ParsedAction<?> getLocation() {
        return this.location;
    }

    @NotNull
    public final ParsedAction<?> getRotation() {
        return this.rotation;
    }

    public final boolean getIgnoreAir() {
        return this.ignoreAir;
    }

    @NotNull
    public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
        Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
        CompletionStage completionStage = frame.newFrame(this.name).run().thenAccept(arg_0 -> ActionSchematic.run$lambda$1(frame, this, arg_0));
        Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"frame.newFrame(name).run\u2026}\n            }\n        }");
        return completionStage;
    }

    private static final void run$lambda$1$lambda$0(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void run$lambda$1(QuestContext.Frame $frame, ActionSchematic this$0, Object name) {
        Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
        Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
        $frame.newFrame(this$0.location).run().thenAccept(arg_0 -> ActionSchematic.run$lambda$1$lambda$0((Function1)new Function1<Location, Unit>($frame, this$0, name){
            final /* synthetic */ QuestContext.Frame $frame;
            final /* synthetic */ ActionSchematic this$0;
            final /* synthetic */ Object $name;
            {
                this.$frame = $frame;
                this.this$0 = $receiver;
                this.$name = $name;
                super(1);
            }

            public final void invoke(Location location) {
                this.$frame.newFrame(this.this$0.getRotation()).run().thenAccept(arg_0 -> run.1.1.invoke$lambda$2(this.$name, location, this.this$0, arg_0));
            }

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            private static final void invoke$lambda$2(Object $name, Location $location, ActionSchematic this$0, Object rotation) {
                Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
                File f = null;
                f = new File("plugins/WorldEdit/schematics/" + $name + ".schematic");
                if (!f.exists()) {
                    f = new File("plugins/WorldEdit/schematics/" + $name + ".schem");
                }
                if (!f.exists()) {
                    Object[] objectArray = new Object[]{"Schematic " + $name + " does not exist!"};
                    IOKt.warning((Object[])objectArray);
                } else {
                    ClipboardFormat format = ClipboardFormats.findByAlias((String)"schematic");
                    if (format == null) {
                        Object[] objectArray = new Object[]{"Unknown schematic format: schematic"};
                        IOKt.warning((Object[])objectArray);
                    } else {
                        try {
                            Closeable closeable = (Closeable)Closer.create();
                            Throwable throwable = null;
                            try {
                                AffineTransform affineTransform;
                                Closer closer = (Closer)closeable;
                                boolean bl = false;
                                EditSessionFactory editSessionFactory = WorldEdit.getInstance().getEditSessionFactory();
                                org.bukkit.World world = $location.getWorld();
                                Intrinsics.checkNotNull((Object)world);
                                EditSession session = editSessionFactory.getEditSession((World)new BukkitWorld(world), -1);
                                FileInputStream fis = (FileInputStream)closer.register((Closeable)new FileInputStream(f));
                                BufferedInputStream bis = (BufferedInputStream)closer.register((Closeable)new BufferedInputStream(fis));
                                ClipboardReader reader = format.getReader((InputStream)bis);
                                Clipboard clipboard = reader.read();
                                AffineTransform it = affineTransform = new AffineTransform();
                                boolean bl2 = false;
                                if (rotation != null) {
                                    it.rotateY(Coerce.toDouble((Object)rotation));
                                }
                                AffineTransform transform = affineTransform;
                                ClipboardHolder holder = new ClipboardHolder(clipboard);
                                holder.setTransform(holder.getTransform().combine((Transform)transform));
                                Operations.complete((Operation)holder.createPaste((Extent)session).to(BlockVector3.at((int)$location.getBlockX(), (int)$location.getBlockY(), (int)$location.getBlockZ())).ignoreAirBlocks(this$0.getIgnoreAir()).build());
                                Unit unit = Unit.INSTANCE;
                            }
                            catch (Throwable throwable2) {
                                throwable = throwable2;
                                throw throwable2;
                            }
                            finally {
                                CloseableKt.closeFinally((Closeable)closeable, (Throwable)throwable);
                            }
                        }
                        catch (IOException t) {
                            Object[] objectArray = new Object[]{"Schematic could not read or it does not exist: " + t.getMessage()};
                            IOKt.warning((Object[])objectArray);
                        }
                    }
                }
            }
        }, arg_0));
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0007\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/compat/ActionSchematic$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Ljava/lang/Void;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"schematic", "schem"}, shared=true)
        @NotNull
        public final ScriptActionParser<Void> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

