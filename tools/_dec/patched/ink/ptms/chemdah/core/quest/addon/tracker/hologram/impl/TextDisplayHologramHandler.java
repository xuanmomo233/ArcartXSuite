/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.entity.Display$Billboard
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.TextDisplay
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.tracker.hologram.impl;

import ink.ptms.chemdah.core.quest.addon.tracker.hologram.ChemdahHologram;
import ink.ptms.chemdah.core.quest.addon.tracker.hologram.ChemdahHologramHandler;
import java.util.ArrayList;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0003\u0018\u00002\u00020\u0001:\u0001\rB\u0005\u00a2\u0006\u0002\u0010\u0002J&\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nH\u0016J\b\u0010\f\u001a\u00020\u000bH\u0016\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/hologram/impl/TextDisplayHologramHandler;", "Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologramHandler;", "()V", "createHologram", "Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologram;", "player", "Lorg/bukkit/entity/Player;", "location", "Lorg/bukkit/Location;", "content", "", "", "getName", "TextDisplayHologramImpl", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nTextDisplayHologramHandler.kt\nKotlin\n*S Kotlin\n*F\n+ 1 TextDisplayHologramHandler.kt\nink/ptms/chemdah/core/quest/addon/tracker/hologram/impl/TextDisplayHologramHandler\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,80:1\n1855#2,2:81\n*S KotlinDebug\n*F\n+ 1 TextDisplayHologramHandler.kt\nink/ptms/chemdah/core/quest/addon/tracker/hologram/impl/TextDisplayHologramHandler\n*L\n19#1:81,2\n*E\n"})
public final class TextDisplayHologramHandler
implements ChemdahHologramHandler {
    @Override
    @NotNull
    public ChemdahHologram createHologram(@NotNull Player player2, @NotNull Location location, @NotNull List<String> content) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)location, (String)"location");
        Intrinsics.checkNotNullParameter(content, (String)"content");
        List entities2 = new ArrayList();
        Location location2 = location.clone();
        Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"location.clone()");
        Location loc = location2;
        Iterable $this$forEach$iv = content;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            String line = (String)element$iv;
            boolean bl = false;
            World world = location.getWorld();
            Intrinsics.checkNotNull((Object)world);
            Entity entity = world.spawn(loc, TextDisplay.class, arg_0 -> TextDisplayHologramHandler.createHologram$lambda$1$lambda$0((Function1)new Function1<TextDisplay, Unit>(line){
                final /* synthetic */ String $line;
                {
                    this.$line = $line;
                    super(1);
                }

                public final void invoke(TextDisplay display2) {
                    display2.setText(this.$line);
                    display2.setSeeThrough(true);
                    display2.setBillboard(Display.Billboard.CENTER);
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)entity, (String)"line ->\n            val \u2026oard.CENTER\n            }");
            TextDisplay textDisplay2 = (TextDisplay)entity;
            entities2.add(textDisplay2);
            loc.setY(loc.getY() - 0.25);
        }
        return new TextDisplayHologramImpl(player2, entities2);
    }

    @Override
    @NotNull
    public String getName() {
        return "TextDisplay";
    }

    private static final void createHologram$lambda$1$lambda$0(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\u0018\u00002\u00020\u0001B\u001b\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a2\u0006\u0002\u0010\u0007J\b\u0010\b\u001a\u00020\tH\u0016J\u0010\u0010\n\u001a\u00020\t2\u0006\u0010\u000b\u001a\u00020\fH\u0016J\u0016\u0010\r\u001a\u00020\t2\f\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000fH\u0016R\u0014\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/hologram/impl/TextDisplayHologramHandler$TextDisplayHologramImpl;", "Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologram;", "player", "Lorg/bukkit/entity/Player;", "entities", "", "Lorg/bukkit/entity/TextDisplay;", "(Lorg/bukkit/entity/Player;Ljava/util/List;)V", "remove", "", "teleport", "location", "Lorg/bukkit/Location;", "update", "content", "", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nTextDisplayHologramHandler.kt\nKotlin\n*S Kotlin\n*F\n+ 1 TextDisplayHologramHandler.kt\nink/ptms/chemdah/core/quest/addon/tracker/hologram/impl/TextDisplayHologramHandler$TextDisplayHologramImpl\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,80:1\n1855#2,2:81\n1855#2,2:83\n1864#2,3:85\n1855#2,2:88\n*S KotlinDebug\n*F\n+ 1 TextDisplayHologramHandler.kt\nink/ptms/chemdah/core/quest/addon/tracker/hologram/impl/TextDisplayHologramHandler$TextDisplayHologramImpl\n*L\n43#1:81,2\n56#1:83,2\n67#1:85,3\n74#1:88,2\n*E\n"})
    public static final class TextDisplayHologramImpl
    implements ChemdahHologram {
        @NotNull
        private final Player player;
        @NotNull
        private final List<TextDisplay> entities;

        public TextDisplayHologramImpl(@NotNull Player player2, @NotNull List<TextDisplay> entities2) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter(entities2, (String)"entities");
            this.player = player2;
            this.entities = entities2;
        }

        @Override
        public void teleport(@NotNull Location location) {
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            Location location2 = location.clone();
            Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"location.clone()");
            Location loc = location2;
            Iterable $this$forEach$iv = this.entities;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                TextDisplay entity = (TextDisplay)element$iv;
                boolean bl = false;
                entity.teleport(loc);
                loc.setY(loc.getY() - 0.25);
            }
        }

        /*
         * WARNING - void declaration
         */
        @Override
        public void update(@NotNull List<String> content) {
            Intrinsics.checkNotNullParameter(content, (String)"content");
            if (content.size() != this.entities.size()) {
                TextDisplay textDisplay2 = (TextDisplay)CollectionsKt.firstOrNull(this.entities);
                if (textDisplay2 == null || (textDisplay2 = textDisplay2.getLocation()) == null) {
                    return;
                }
                TextDisplay location = textDisplay2;
                this.remove();
                this.entities.clear();
                Location location2 = location.clone();
                Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"location.clone()");
                Location loc = location2;
                Iterable $this$forEach$iv = content;
                boolean $i$f$forEach = false;
                for (Object element$iv : $this$forEach$iv) {
                    String line = (String)element$iv;
                    boolean bl = false;
                    World world = location.getWorld();
                    Intrinsics.checkNotNull((Object)world);
                    Entity entity = world.spawn(loc, TextDisplay.class, arg_0 -> TextDisplayHologramImpl.update$lambda$2$lambda$1((Function1)new Function1<TextDisplay, Unit>(line){
                        final /* synthetic */ String $line;
                        {
                            this.$line = $line;
                            super(1);
                        }

                        public final void invoke(TextDisplay display2) {
                            display2.setText(this.$line);
                            display2.setSeeThrough(true);
                            display2.setBillboard(Display.Billboard.CENTER);
                        }
                    }, arg_0));
                    Intrinsics.checkNotNullExpressionValue((Object)entity, (String)"line ->\n                \u2026TER\n                    }");
                    TextDisplay textDisplay3 = (TextDisplay)entity;
                    this.entities.add(textDisplay3);
                    loc.setY(loc.getY() - 0.25);
                }
            } else {
                Iterable $this$forEachIndexed$iv = this.entities;
                boolean $i$f$forEachIndexed = false;
                int index$iv = 0;
                for (Object item$iv : $this$forEachIndexed$iv) {
                    void entity;
                    int n;
                    if ((n = index$iv++) < 0) {
                        CollectionsKt.throwIndexOverflow();
                    }
                    TextDisplay line = (TextDisplay)item$iv;
                    int index = n;
                    boolean bl = false;
                    entity.setText(content.get(index));
                }
            }
        }

        @Override
        public void remove() {
            Iterable $this$forEach$iv = this.entities;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                TextDisplay it = (TextDisplay)element$iv;
                boolean bl = false;
                it.remove();
            }
            this.entities.clear();
        }

        private static final void update$lambda$2$lambda$1(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }
    }
}

