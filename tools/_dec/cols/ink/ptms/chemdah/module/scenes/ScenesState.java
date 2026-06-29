/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.scenes;

import ink.ptms.chemdah.module.kether.ActionScenes;
import ink.ptms.chemdah.module.scenes.BlockList;
import ink.ptms.chemdah.module.scenes.BlockListArea;
import ink.ptms.chemdah.module.scenes.BlockListSingle;
import ink.ptms.chemdah.module.scenes.ScenesBlockData;
import ink.ptms.chemdah.module.scenes.ScenesFile;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common.util.Location;
import ink.ptms.chemdah.taboolib.common.util.Vector;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.xseries.XItemStackKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLocationKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\"\n\u0002\b\u0005\b&\u0018\u0000 \u001e2\u00020\u0001:\u0003\u001d\u001e\u001fB\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0010\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019H&J\u000e\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00110\u001bH&J\u0010\u0010\u001c\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019H&R\u0017\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\f\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u000eR\u0011\u0010\u0010\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015\u00a8\u0006 "}, d2={"Link/ptms/chemdah/module/scenes/ScenesState;", "", "index", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(ILink/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "agent", "", "", "getAgent", "()Ljava/util/List;", "autoNext", "getAutoNext", "()I", "getIndex", "relative", "Link/ptms/chemdah/taboolib/common/util/Vector;", "getRelative", "()Link/ptms/chemdah/taboolib/common/util/Vector;", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "cancel", "", "player", "Lorg/bukkit/entity/Player;", "getAffectPosition", "", "send", "Block", "Companion", "Copy", "Chemdah"})
public abstract class ScenesState {
    @NotNull
    public static final Companion Companion = new Companion(null);
    private final int index;
    @NotNull
    private final ConfigurationSection root;
    @NotNull
    private final List<String> agent;
    @NotNull
    private final Vector relative;
    private final int autoNext;

    public ScenesState(int index, @NotNull ConfigurationSection root2) {
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.index = index;
        this.root = root2;
        Object object = this.root.get("$");
        if (object == null || (object = CollectionKt.asList((Object)object)) == null) {
            object = CollectionsKt.emptyList();
        }
        this.agent = object;
        String string = this.root.getString("relative", "");
        Intrinsics.checkNotNull((Object)string);
        this.relative = ink.ptms.chemdah.module.scenes.ScenesState$Companion.toVector$default(Companion, string, null, 1, null);
        this.autoNext = this.root.getInt("auto-next");
    }

    public final int getIndex() {
        return this.index;
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    @NotNull
    public final List<String> getAgent() {
        return this.agent;
    }

    @NotNull
    public final Vector getRelative() {
        return this.relative;
    }

    public final int getAutoNext() {
        return this.autoNext;
    }

    public abstract void send(@NotNull Player var1);

    public abstract void cancel(@NotNull Player var1);

    @NotNull
    public abstract Set<Vector> getAffectPosition();

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000F\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\"\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0010\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014H\u0016J\u000e\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u00170\u0016H\u0016J\u0010\u0010\u0018\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014H\u0016R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u001d\u0010\u000b\u001a\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u000e0\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/module/scenes/ScenesState$Block;", "Link/ptms/chemdah/module/scenes/ScenesState;", "index", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "file", "Link/ptms/chemdah/module/scenes/ScenesFile;", "(ILink/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/module/scenes/ScenesFile;)V", "getFile", "()Link/ptms/chemdah/module/scenes/ScenesFile;", "set", "", "Link/ptms/chemdah/module/scenes/BlockList;", "Link/ptms/chemdah/module/scenes/ScenesBlockData;", "getSet", "()Ljava/util/Map;", "cancel", "", "player", "Lorg/bukkit/entity/Player;", "getAffectPosition", "", "Link/ptms/chemdah/taboolib/common/util/Vector;", "send", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nScenesState.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ScenesState.kt\nink/ptms/chemdah/module/scenes/ScenesState$Block\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 4 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,183:1\n1603#2,9:184\n1855#2:193\n1549#2:194\n1620#2,3:195\n1549#2:198\n1620#2,3:199\n1856#2:203\n1612#2:204\n1855#2,2:206\n1855#2,2:210\n1#3:202\n215#4:205\n216#4:208\n215#4:209\n216#4:212\n76#4:213\n96#4,5:214\n*S KotlinDebug\n*F\n+ 1 ScenesState.kt\nink/ptms/chemdah/module/scenes/ScenesState$Block\n*L\n44#1:184,9\n44#1:193\n45#1:194\n45#1:195,3\n47#1:198\n47#1:199,3\n44#1:203\n44#1:204\n75#1:206,2\n92#1:210,2\n44#1:202\n74#1:205\n74#1:208\n91#1:209\n91#1:212\n104#1:213\n104#1:214,5\n*E\n"})
    public static final class Block
    extends ScenesState {
        @NotNull
        private final ScenesFile file;
        @NotNull
        private final Map<BlockList, ScenesBlockData> set;

        /*
         * WARNING - void declaration
         */
        public Block(int index, @NotNull ConfigurationSection root2, @NotNull ScenesFile file) {
            void $this$mapNotNullTo$iv$iv;
            void $this$mapNotNull$iv;
            Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
            Intrinsics.checkNotNullParameter((Object)file, (String)"file");
            super(index, root2);
            this.file = file;
            Iterable iterable = root2.getStringList("set");
            Block block = this;
            boolean $i$f$mapNotNull = false;
            void var6_7 = $this$mapNotNull$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$mapNotNullTo = false;
            void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
            boolean $i$f$forEach = false;
            Iterator iterator = $this$forEach$iv$iv$iv.iterator();
            while (iterator.hasNext()) {
                Pair pair;
                Collection collection;
                void $this$mapTo$iv$iv;
                Object element$iv$iv$iv;
                Object element$iv$iv = element$iv$iv$iv = iterator.next();
                boolean bl = false;
                String it = (String)element$iv$iv;
                boolean bl2 = false;
                String[] stringArray = new String[]{">"};
                Iterable $this$map$iv = StringsKt.split$default((CharSequence)it, (String[])stringArray, (boolean)false, (int)0, (int)6, null);
                boolean $i$f$map2 = false;
                Iterable iterable2 = $this$map$iv;
                Iterable destination$iv$iv2 = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                boolean $i$f$mapTo = false;
                for (Object item$iv$iv : $this$mapTo$iv$iv) {
                    void i;
                    String string = (String)item$iv$iv;
                    collection = destination$iv$iv2;
                    boolean bl3 = false;
                    collection.add(((Object)StringsKt.trim((CharSequence)((CharSequence)i))).toString());
                }
                List args = (List)destination$iv$iv2;
                if (args.size() == 2) {
                    void $this$mapTo$iv$iv2;
                    String[] $i$f$map2 = new String[]{"~"};
                    Iterable $this$map$iv2 = StringsKt.split$default((CharSequence)((CharSequence)args.get(0)), (String[])$i$f$map2, (boolean)false, (int)0, (int)6, null);
                    boolean $i$f$map3 = false;
                    destination$iv$iv2 = $this$map$iv2;
                    Collection destination$iv$iv3 = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv2, (int)10));
                    boolean $i$f$mapTo2 = false;
                    for (Object item$iv$iv : $this$mapTo$iv$iv2) {
                        void i;
                        String bl3 = (String)item$iv$iv;
                        collection = destination$iv$iv3;
                        boolean bl4 = false;
                        collection.add(((Object)StringsKt.trim((CharSequence)((CharSequence)i))).toString());
                    }
                    List area = (List)destination$iv$iv3;
                    BlockList blockList = area.size() == 1 ? (BlockList)new BlockListSingle(Companion.toVector((String)area.get(0), this.getRelative())) : (BlockList)new BlockListArea(Companion.toVector((String)area.get(0), this.getRelative()), Companion.toVector((String)area.get(1), this.getRelative()));
                    String[] stringArray2 = new String[]{" "};
                    List block2 = StringsKt.split$default((CharSequence)((CharSequence)args.get(1)), (String[])stringArray2, (boolean)false, (int)0, (int)6, null);
                    if (block2.size() == 1) {
                        stringArray2 = new String[]{":"};
                        Material material = XItemStackKt.parseToMaterial((String)((String)StringsKt.split$default((CharSequence)((CharSequence)block2.get(0)), (String[])stringArray2, (boolean)false, (int)0, (int)6, null).get(0)));
                        stringArray2 = new String[]{":"};
                        pair = TuplesKt.to((Object)blockList, (Object)new ScenesBlockData(material, Coerce.toByte((Object)CollectionsKt.getOrNull((List)StringsKt.split$default((CharSequence)((CharSequence)block2.get(0)), (String[])stringArray2, (boolean)false, (int)0, (int)6, null), (int)1)), false, 4, null));
                    } else if (Intrinsics.areEqual(block2.get(0), (Object)"falling")) {
                        stringArray2 = new String[]{":"};
                        Material material = XItemStackKt.parseToMaterial((String)((String)StringsKt.split$default((CharSequence)((CharSequence)block2.get(1)), (String[])stringArray2, (boolean)false, (int)0, (int)6, null).get(0)));
                        stringArray2 = new String[]{":"};
                        pair = TuplesKt.to((Object)blockList, (Object)new ScenesBlockData(material, Coerce.toByte((Object)CollectionsKt.getOrNull((List)StringsKt.split$default((CharSequence)((CharSequence)block2.get(1)), (String[])stringArray2, (boolean)false, (int)0, (int)6, null), (int)1)), true));
                    } else {
                        pair = null;
                    }
                } else {
                    pair = null;
                }
                if (pair == null) continue;
                Pair it$iv$iv = pair;
                boolean bl5 = false;
                destination$iv$iv.add(it$iv$iv);
            }
            block.set = MapsKt.toMap((Iterable)((List)destination$iv$iv));
        }

        @NotNull
        public final ScenesFile getFile() {
            return this.file;
        }

        @NotNull
        public final Map<BlockList, ScenesBlockData> getSet() {
            return this.set;
        }

        @Override
        public void send(@NotNull Player player) {
            Object object;
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            try {
                KetherShell ketherShell = KetherShell.INSTANCE;
                List<String> list2 = this.getAgent();
                object = AdapterKt.adaptCommandSender((Object)player);
                List<String> list3 = UtilsForKetherKt.getNamespaceQuest();
                KetherShell.eval$default((KetherShell)ketherShell, list2, (boolean)false, list3, null, (ProxyCommandSender)object, null, null, (int)106, null);
            }
            catch (Exception ex) {
                KetherHelperKt.printKetherErrorMessage((Throwable)ex, (boolean)true);
            }
            Map<BlockList, ScenesBlockData> $this$forEach$iv = this.set;
            boolean $i$f$forEach = false;
            object = $this$forEach$iv.entrySet().iterator();
            while (object.hasNext()) {
                Map.Entry element$iv;
                Map.Entry entry = element$iv = (Map.Entry)object.next();
                boolean bl = false;
                BlockList k = (BlockList)entry.getKey();
                ScenesBlockData v = (ScenesBlockData)entry.getValue();
                Iterable $this$forEach$iv2 = k.getList();
                boolean $i$f$forEach2 = false;
                for (Object element$iv2 : $this$forEach$iv2) {
                    Vector pos = (Vector)element$iv2;
                    boolean bl2 = false;
                    if (v.getFalling()) {
                        Location location = pos.toLocation(player.getWorld().getName());
                        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"pos.toLocation(player.world.name)");
                        ActionScenes.Companion.createScenesFallingBlock$default(ActionScenes.Companion, player, BukkitLocationKt.toBukkitLocation((Location)location), v.getMaterial(), v.getData(), false, 8, null);
                        continue;
                    }
                    Location location = pos.toLocation(player.getWorld().getName());
                    Intrinsics.checkNotNullExpressionValue((Object)location, (String)"pos.toLocation(player.world.name)");
                    ActionScenes.Companion.createScenesBlock(player, BukkitLocationKt.toBukkitLocation((Location)location), v.getMaterial(), v.getData());
                }
            }
            if (this.getAutoNext() > 0) {
                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)this.getAutoNext(), (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this, player){
                    final /* synthetic */ Block this$0;
                    final /* synthetic */ Player $player;
                    {
                        this.this$0 = $receiver;
                        this.$player = $player;
                        super(1);
                    }

                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                        block0: {
                            Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                            ScenesState scenesState = (ScenesState)CollectionsKt.getOrNull(this.this$0.getFile().getState(), (int)(this.this$0.getIndex() + 1));
                            if (scenesState == null) break block0;
                            scenesState.send(this.$player);
                        }
                    }
                }), (int)11, null);
            }
        }

        @Override
        public void cancel(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            Map<BlockList, ScenesBlockData> $this$forEach$iv = this.set;
            boolean $i$f$forEach = false;
            Iterator<Map.Entry<BlockList, ScenesBlockData>> iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockList, ScenesBlockData> element$iv;
                Map.Entry<BlockList, ScenesBlockData> entry = element$iv = iterator.next();
                boolean bl = false;
                BlockList k = entry.getKey();
                Iterable $this$forEach$iv2 = k.getList();
                boolean $i$f$forEach2 = false;
                for (Object element$iv2 : $this$forEach$iv2) {
                    Vector pos = (Vector)element$iv2;
                    boolean bl2 = false;
                    Location location = pos.toLocation(player.getWorld().getName());
                    Intrinsics.checkNotNullExpressionValue((Object)location, (String)"pos.toLocation(player.world.name)");
                    ActionScenes.Companion.removeScenesBlock(player, BukkitLocationKt.toBukkitLocation((Location)location));
                }
            }
            if (this.getAutoNext() > 0) {
                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)this.getAutoNext(), (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this, player){
                    final /* synthetic */ Block this$0;
                    final /* synthetic */ Player $player;
                    {
                        this.this$0 = $receiver;
                        this.$player = $player;
                        super(1);
                    }

                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                        block0: {
                            Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                            ScenesState scenesState = (ScenesState)CollectionsKt.getOrNull(this.this$0.getFile().getState(), (int)(this.this$0.getIndex() + 1));
                            if (scenesState == null) break block0;
                            scenesState.cancel(this.$player);
                        }
                    }
                }), (int)11, null);
            }
        }

        /*
         * WARNING - void declaration
         */
        @Override
        @NotNull
        public Set<Vector> getAffectPosition() {
            void $this$flatMapTo$iv$iv;
            Map<BlockList, ScenesBlockData> $this$flatMap$iv = this.set;
            boolean $i$f$flatMap = false;
            Map<BlockList, ScenesBlockData> map = $this$flatMap$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$flatMapTo = false;
            Iterator iterator = $this$flatMapTo$iv$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry element$iv$iv;
                Map.Entry entry = element$iv$iv = iterator.next();
                boolean bl = false;
                BlockList k = (BlockList)entry.getKey();
                Iterable list$iv$iv = k.getList();
                CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
            }
            return CollectionsKt.toSet((Iterable)((List)destination$iv$iv));
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\"\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00050\u00042\u0006\u0010\u0006\u001a\u00020\u00052\u0006\u0010\u0007\u001a\u00020\u0005J\u0016\u0010\b\u001a\u00020\u0005*\u00020\t2\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u0005\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/module/scenes/ScenesState$Companion;", "", "()V", "getArea", "Lkotlin1822/Pair;", "Link/ptms/chemdah/taboolib/common/util/Vector;", "pos1", "pos2", "toVector", "", "relative", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nScenesState.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ScenesState.kt\nink/ptms/chemdah/module/scenes/ScenesState$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,183:1\n1549#2:184\n1620#2,3:185\n*S KotlinDebug\n*F\n+ 1 ScenesState.kt\nink/ptms/chemdah/module/scenes/ScenesState$Companion\n*L\n162#1:184\n162#1:185,3\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        /*
         * WARNING - void declaration
         */
        @NotNull
        public final Vector toVector(@NotNull String $this$toVector, @Nullable Vector relative) {
            Vector vector;
            void $this$mapTo$iv$iv;
            Intrinsics.checkNotNullParameter((Object)$this$toVector, (String)"<this>");
            String[] stringArray = new String[]{" "};
            Iterable $this$map$iv = StringsKt.split$default((CharSequence)$this$toVector, (String[])stringArray, (boolean)false, (int)0, (int)6, null);
            boolean $i$f$map = false;
            Iterable iterable = $this$map$iv;
            Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                void it;
                String string = (String)item$iv$iv;
                Collection collection = destination$iv$iv;
                boolean bl = false;
                collection.add(Coerce.toInteger((Object)it));
            }
            List args = (List)destination$iv$iv;
            Integer n = (Integer)CollectionsKt.getOrNull((List)args, (int)1);
            Integer n2 = (Integer)CollectionsKt.getOrNull((List)args, (int)2);
            Vector position2 = new Vector(((Number)args.get(0)).intValue(), n != null ? n.intValue() : ((Number)args.get(0)).intValue(), n2 != null ? n2.intValue() : ((Number)args.get(0)).intValue());
            if (relative != null) {
                Vector vector2 = position2.add(relative);
                vector = vector2;
                Intrinsics.checkNotNullExpressionValue((Object)vector2, (String)"{\n                positi\u2026d(relative)\n            }");
            } else {
                vector = position2;
            }
            return vector;
        }

        public static /* synthetic */ Vector toVector$default(Companion companion, String string, Vector vector, int n, Object object) {
            if ((n & 1) != 0) {
                vector = null;
            }
            return companion.toVector(string, vector);
        }

        @NotNull
        public final Pair<Vector, Vector> getArea(@NotNull Vector pos1, @NotNull Vector pos2) {
            Intrinsics.checkNotNullParameter((Object)pos1, (String)"pos1");
            Intrinsics.checkNotNullParameter((Object)pos2, (String)"pos2");
            return TuplesKt.to((Object)new Vector(Integer.min(pos1.getBlockX(), pos2.getBlockX()), Integer.min(pos1.getBlockY(), pos2.getBlockY()), Integer.min(pos1.getBlockZ(), pos2.getBlockZ())), (Object)new Vector(Integer.max(pos1.getBlockX(), pos2.getBlockX()), Integer.max(pos1.getBlockY(), pos2.getBlockY()), Integer.max(pos1.getBlockZ(), pos2.getBlockZ())));
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\"\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0010\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001eH\u0016J\u000e\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u00180 H\u0016J\u0010\u0010!\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001eH\u0016R\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u000f\u001a\u00020\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0013\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0017\u001a\u00020\u0018\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001a\u00a8\u0006\""}, d2={"Link/ptms/chemdah/module/scenes/ScenesState$Copy;", "Link/ptms/chemdah/module/scenes/ScenesState;", "index", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "file", "Link/ptms/chemdah/module/scenes/ScenesFile;", "(ILink/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/module/scenes/ScenesFile;)V", "falling", "", "getFalling", "()Z", "getFile", "()Link/ptms/chemdah/module/scenes/ScenesFile;", "from", "Link/ptms/chemdah/module/scenes/BlockListArea;", "getFrom", "()Link/ptms/chemdah/module/scenes/BlockListArea;", "fromWorld", "", "getFromWorld", "()Ljava/lang/String;", "to", "Link/ptms/chemdah/taboolib/common/util/Vector;", "getTo", "()Link/ptms/chemdah/taboolib/common/util/Vector;", "cancel", "", "player", "Lorg/bukkit/entity/Player;", "getAffectPosition", "", "send", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nScenesState.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ScenesState.kt\nink/ptms/chemdah/module/scenes/ScenesState$Copy\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,183:1\n1864#2,3:184\n1855#2,2:187\n*S KotlinDebug\n*F\n+ 1 ScenesState.kt\nink/ptms/chemdah/module/scenes/ScenesState$Copy\n*L\n125#1:184,3\n140#1:187,2\n*E\n"})
    public static final class Copy
    extends ScenesState {
        @NotNull
        private final ScenesFile file;
        @NotNull
        private final String fromWorld;
        @NotNull
        private final BlockListArea from;
        @NotNull
        private final Vector to;
        private final boolean falling;

        /*
         * WARNING - void declaration
         */
        public Copy(int index, @NotNull ConfigurationSection root2, @NotNull ScenesFile file) {
            void $this$from_u24lambda_u240;
            Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
            Intrinsics.checkNotNullParameter((Object)file, (String)"file");
            super(index, root2);
            this.file = file;
            String string = root2.getString("copy.from-world");
            if (string == null) {
                string = this.file.getWorld();
            }
            this.fromWorld = string;
            String string2 = root2.getString("copy.from", "");
            Intrinsics.checkNotNull((Object)string2);
            String string3 = string2;
            Copy copy = this;
            boolean bl = false;
            String[] stringArray = new String[]{"~"};
            Vector vector = ink.ptms.chemdah.module.scenes.ScenesState$Companion.toVector$default(Companion, ((Object)StringsKt.trim((CharSequence)((String)StringsKt.split$default((CharSequence)((CharSequence)$this$from_u24lambda_u240), (String[])stringArray, (boolean)false, (int)0, (int)6, null).get(0)))).toString(), null, 1, null);
            stringArray = new String[]{"~"};
            copy.from = new BlockListArea(vector, ink.ptms.chemdah.module.scenes.ScenesState$Companion.toVector$default(Companion, ((Object)StringsKt.trim((CharSequence)String.valueOf(CollectionsKt.getOrNull((List)StringsKt.split$default((CharSequence)((CharSequence)$this$from_u24lambda_u240), (String[])stringArray, (boolean)false, (int)0, (int)6, null), (int)1)))).toString(), null, 1, null));
            String string4 = root2.getString("copy.to", "");
            Intrinsics.checkNotNull((Object)string4);
            this.to = Companion.toVector(string4, this.getRelative());
            this.falling = root2.getBoolean("copy.falling");
        }

        @NotNull
        public final ScenesFile getFile() {
            return this.file;
        }

        @NotNull
        public final String getFromWorld() {
            return this.fromWorld;
        }

        @NotNull
        public final BlockListArea getFrom() {
            return this.from;
        }

        @NotNull
        public final Vector getTo() {
            return this.to;
        }

        public final boolean getFalling() {
            return this.falling;
        }

        /*
         * WARNING - void declaration
         */
        @Override
        public void send(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            try {
                KetherShell ketherShell = KetherShell.INSTANCE;
                List<String> list2 = this.getAgent();
                ProxyCommandSender proxyCommandSender = AdapterKt.adaptCommandSender((Object)player);
                List<String> list3 = UtilsForKetherKt.getNamespaceQuest();
                KetherShell.eval$default((KetherShell)ketherShell, list2, (boolean)false, list3, null, (ProxyCommandSender)proxyCommandSender, null, null, (int)106, null);
            }
            catch (Exception ex) {
                KetherHelperKt.printKetherErrorMessage((Throwable)ex, (boolean)true);
            }
            World world = Bukkit.getWorld((String)this.fromWorld);
            if (world == null) {
                return;
            }
            List<org.bukkit.block.Block> blocksFrom = this.from.getList(world);
            Vector vector = this.to.clone().add(this.from.getMax().clone().subtract(this.from.getMin()));
            Intrinsics.checkNotNullExpressionValue((Object)vector, (String)"to.clone().add(from.max.\u2026one().subtract(from.min))");
            List<Vector> blocksTo = new BlockListArea(this.to, vector).getList();
            Iterable $this$forEachIndexed$iv = blocksFrom;
            boolean $i$f$forEachIndexed = false;
            int index$iv = 0;
            for (Object item$iv : $this$forEachIndexed$iv) {
                void block;
                int n;
                if ((n = index$iv++) < 0) {
                    CollectionsKt.throwIndexOverflow();
                }
                org.bukkit.block.Block block2 = (org.bukkit.block.Block)item$iv;
                int index = n;
                boolean bl = false;
                if (this.falling) {
                    Location location = blocksTo.get(index).toLocation(player.getWorld().getName());
                    Intrinsics.checkNotNullExpressionValue((Object)location, (String)"blocksTo[index].toLocation(player.world.name)");
                    org.bukkit.Location location2 = BukkitLocationKt.toBukkitLocation((Location)location);
                    Material material = block.getType();
                    Intrinsics.checkNotNullExpressionValue((Object)material, (String)"block.type");
                    ActionScenes.Companion.createScenesFallingBlock$default(ActionScenes.Companion, player, location2, material, block.getData(), false, 8, null);
                    continue;
                }
                Location location = blocksTo.get(index).toLocation(player.getWorld().getName());
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"blocksTo[index].toLocation(player.world.name)");
                org.bukkit.Location location3 = BukkitLocationKt.toBukkitLocation((Location)location);
                Material material = block.getType();
                Intrinsics.checkNotNullExpressionValue((Object)material, (String)"block.type");
                ActionScenes.Companion.createScenesBlock(player, location3, material, block.getData());
            }
            if (this.getAutoNext() > 0) {
                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)this.getAutoNext(), (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this, player){
                    final /* synthetic */ Copy this$0;
                    final /* synthetic */ Player $player;
                    {
                        this.this$0 = $receiver;
                        this.$player = $player;
                        super(1);
                    }

                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                        block0: {
                            Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                            ScenesState scenesState = (ScenesState)CollectionsKt.getOrNull(this.this$0.getFile().getState(), (int)(this.this$0.getIndex() + 1));
                            if (scenesState == null) break block0;
                            scenesState.send(this.$player);
                        }
                    }
                }), (int)11, null);
            }
        }

        @Override
        public void cancel(@NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            Iterable $this$forEach$iv = this.getAffectPosition();
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Vector it = (Vector)element$iv;
                boolean bl = false;
                if (this.falling) {
                    Location location = it.toLocation(player.getWorld().getName());
                    Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.toLocation(player.world.name)");
                    ActionScenes.Companion.removeScenesFallingBlock$default(ActionScenes.Companion, player, BukkitLocationKt.toBukkitLocation((Location)location), false, 2, null);
                    continue;
                }
                Location location = it.toLocation(player.getWorld().getName());
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.toLocation(player.world.name)");
                ActionScenes.Companion.removeScenesBlock(player, BukkitLocationKt.toBukkitLocation((Location)location));
            }
            if (this.getAutoNext() > 0) {
                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)this.getAutoNext(), (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this, player){
                    final /* synthetic */ Copy this$0;
                    final /* synthetic */ Player $player;
                    {
                        this.this$0 = $receiver;
                        this.$player = $player;
                        super(1);
                    }

                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                        block0: {
                            Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                            ScenesState scenesState = (ScenesState)CollectionsKt.getOrNull(this.this$0.getFile().getState(), (int)(this.this$0.getIndex() + 1));
                            if (scenesState == null) break block0;
                            scenesState.cancel(this.$player);
                        }
                    }
                }), (int)11, null);
            }
        }

        @Override
        @NotNull
        public Set<Vector> getAffectPosition() {
            Vector vector = this.to.clone().add(this.from.getMax().clone().subtract(this.from.getMin()));
            Intrinsics.checkNotNullExpressionValue((Object)vector, (String)"to.clone().add(from.max.\u2026one().subtract(from.min))");
            return CollectionsKt.toSet((Iterable)new BlockListArea(this.to, vector).getList());
        }
    }
}

