/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.selector;

import ink.ptms.chemdah.core.quest.selector.DataMatch;
import ink.ptms.chemdah.core.quest.selector.DataMatchHandler;
import ink.ptms.chemdah.core.quest.selector.Flags;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.module.nms.NMSTranslateKt;
import ink.ptms.chemdah.um.Mob;
import ink.ptms.chemdah.um.Mythic;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u0000 \r2\u00020\u0001:\u0004\f\r\u000e\u000fB\u0013\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\u0002\u0010\u0005J\u0010\u0010\b\u001a\u00020\t2\b\u0010\n\u001a\u0004\u0018\u00010\u000bR\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/selector/InferEntity;", "", "entities", "", "Link/ptms/chemdah/core/quest/selector/InferEntity$Entity;", "(Ljava/util/List;)V", "getEntities", "()Ljava/util/List;", "isEntity", "", "entity", "Lorg/bukkit/entity/Entity;", "CitizensEntity", "Companion", "Entity", "MythicMobsEntity", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nInferEntity.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferEntity.kt\nink/ptms/chemdah/core/quest/selector/InferEntity\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,102:1\n1747#2,3:103\n*S KotlinDebug\n*F\n+ 1 InferEntity.kt\nink/ptms/chemdah/core/quest/selector/InferEntity\n*L\n19#1:103,3\n*E\n"})
public final class InferEntity {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final List<Entity> entities;

    public InferEntity(@NotNull List<? extends Entity> entities) {
        Intrinsics.checkNotNullParameter(entities, (String)"entities");
        this.entities = entities;
    }

    @NotNull
    public final List<Entity> getEntities() {
        return this.entities;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public final boolean isEntity(@Nullable org.bukkit.entity.Entity entity) {
        Entity it;
        if (entity == null) return false;
        Iterable $this$any$iv = this.entities;
        boolean $i$f$any = false;
        if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
            return false;
        }
        Iterator iterator = $this$any$iv.iterator();
        do {
            if (!iterator.hasNext()) return false;
            Object element$iv = iterator.next();
            it = (Entity)element$iv;
            boolean bl = false;
        } while (!it.match(entity));
        return true;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\u0010\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\n\u0010\u000f\u001a\u00020\u0003*\u00020\r\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/selector/InferEntity$CitizensEntity;", "Link/ptms/chemdah/core/quest/selector/InferEntity$Entity;", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "match", "", "entity", "Lorg/bukkit/entity/Entity;", "matchData", "citizensId", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nInferEntity.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferEntity.kt\nink/ptms/chemdah/core/quest/selector/InferEntity$CitizensEntity\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,102:1\n1726#2,3:103\n*S KotlinDebug\n*F\n+ 1 InferEntity.kt\nink/ptms/chemdah/core/quest/selector/InferEntity$CitizensEntity\n*L\n49#1:103,3\n*E\n"})
    public static final class CitizensEntity
    extends Entity {
        public CitizensEntity(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            super(material, flags, data2);
        }

        @Override
        public boolean match(@NotNull org.bukkit.entity.Entity entity) {
            Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
            return this.matchType(this.citizensId(entity)) && this.matchData(entity);
        }

        @Override
        public boolean matchData(@NotNull org.bukkit.entity.Entity entity) {
            boolean bl;
            block7: {
                Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
                Iterable $this$all$iv = this.getData();
                boolean $i$f$all = false;
                if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
                    bl = true;
                } else {
                    for (Object element$iv : $this$all$iv) {
                        boolean bl2;
                        DataMatch it = (DataMatch)element$iv;
                        boolean bl3 = false;
                        String string = it.getKey();
                        if (Intrinsics.areEqual((Object)string, (Object)"type")) {
                            bl2 = DataMatch.check$default(it, npc.getEntity().getType().name(), null, 2, null);
                        } else if (Intrinsics.areEqual((Object)string, (Object)"name")) {
                            String string2 = npc.getFullName();
                            Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"npc.fullName");
                            bl2 = DataMatch.check$default(it, string2, null, 2, null);
                        } else {
                            Object[] objectArray = new Object[]{this.getName() + '[' + it.getKey() + '=' + it.getValue() + "] not supported."};
                            IOKt.warning((Object[])objectArray);
                            bl2 = false;
                        }
                        if (bl2) continue;
                        bl = false;
                        break block7;
                    }
                    bl = true;
                }
            }
            return bl;
        }

        @NotNull
        public final String citizensId(@NotNull org.bukkit.entity.Entity $this$citizensId) {
            Intrinsics.checkNotNullParameter((Object)$this$citizensId, (String)"<this>");
            Object object = CitizensAPI.getNPCRegistry().getNPC($this$citizensId);
            if (object == null || (object = Integer.valueOf(object.getId()).toString()) == null) {
                object = "@vanilla";
            }
            return object;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\n\u0010\u0003\u001a\u00020\u0004*\u00020\u0005J\u0010\u0010\u0003\u001a\u00020\u0006*\b\u0012\u0004\u0012\u00020\u00050\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/core/quest/selector/InferEntity$Companion;", "", "()V", "toInferEntity", "Link/ptms/chemdah/core/quest/selector/InferEntity$Entity;", "", "Link/ptms/chemdah/core/quest/selector/InferEntity;", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nInferEntity.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferEntity.kt\nink/ptms/chemdah/core/quest/selector/InferEntity$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,102:1\n1549#2:103\n1620#2,3:104\n*S KotlinDebug\n*F\n+ 1 InferEntity.kt\nink/ptms/chemdah/core/quest/selector/InferEntity$Companion\n*L\n99#1:103\n99#1:104,3\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Entity toInferEntity(@NotNull String $this$toInferEntity) {
            Intrinsics.checkNotNullParameter((Object)$this$toInferEntity, (String)"<this>");
            return DataMatchHandler.INSTANCE.getEntityParser().parse($this$toInferEntity);
        }

        /*
         * WARNING - void declaration
         */
        @NotNull
        public final InferEntity toInferEntity(@NotNull List<String> $this$toInferEntity) {
            void $this$mapTo$iv$iv;
            Intrinsics.checkNotNullParameter($this$toInferEntity, (String)"<this>");
            Iterable $this$map$iv = $this$toInferEntity;
            boolean $i$f$map = false;
            Iterable iterable = $this$map$iv;
            Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                void it;
                String string = (String)item$iv$iv;
                Collection collection = destination$iv$iv;
                boolean bl = false;
                collection.add(Companion.toInferEntity((String)it));
            }
            List list2 = (List)destination$iv$iv;
            return new InferEntity(list2);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0016\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u0017H\u0016J\u0010\u0010\u0018\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u0017H\u0016J\u0010\u0010\u0019\u001a\u00020\u00152\u0006\u0010\u001a\u001a\u00020\u0003H\u0016R \u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR \u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000e\u0010\u000b\"\u0004\b\u000f\u0010\rR\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0011\"\u0004\b\u0012\u0010\u0013\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/core/quest/selector/InferEntity$Entity;", "", "name", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "getData", "()Ljava/util/List;", "setData", "(Ljava/util/List;)V", "getFlags", "setFlags", "getName", "()Ljava/lang/String;", "setName", "(Ljava/lang/String;)V", "match", "", "entity", "Lorg/bukkit/entity/Entity;", "matchData", "matchType", "type", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nInferEntity.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferEntity.kt\nink/ptms/chemdah/core/quest/selector/InferEntity$Entity\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,102:1\n1747#2,3:103\n1726#2,3:106\n*S KotlinDebug\n*F\n+ 1 InferEntity.kt\nink/ptms/chemdah/core/quest/selector/InferEntity$Entity\n*L\n25#1:103,3\n28#1:106,3\n*E\n"})
    public static class Entity {
        @NotNull
        private String name;
        @NotNull
        private List<? extends Flags> flags;
        @NotNull
        private List<DataMatch> data;

        public Entity(@NotNull String name, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            this.name = name;
            this.flags = flags;
            this.data = data2;
        }

        @NotNull
        public final String getName() {
            return this.name;
        }

        public final void setName(@NotNull String string) {
            Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
            this.name = string;
        }

        @NotNull
        public final List<Flags> getFlags() {
            return this.flags;
        }

        public final void setFlags(@NotNull List<? extends Flags> list2) {
            Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
            this.flags = list2;
        }

        @NotNull
        public final List<DataMatch> getData() {
            return this.data;
        }

        public final void setData(@NotNull List<DataMatch> list2) {
            Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
            this.data = list2;
        }

        public boolean match(@NotNull org.bukkit.entity.Entity entity) {
            Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
            String string = entity.getType().name().toLowerCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
            return this.matchType(string) && this.matchData(entity);
        }

        public boolean matchType(@NotNull String type) {
            boolean bl;
            block3: {
                Intrinsics.checkNotNullParameter((Object)type, (String)"type");
                Iterable $this$any$iv = this.flags;
                boolean $i$f$any = false;
                if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                    bl = false;
                } else {
                    for (Object element$iv : $this$any$iv) {
                        Flags it = (Flags)((Object)element$iv);
                        boolean bl2 = false;
                        if (!((Boolean)it.getMatch().invoke((Object)type, (Object)this.name)).booleanValue()) continue;
                        bl = true;
                        break block3;
                    }
                    bl = false;
                }
            }
            return bl;
        }

        public boolean matchData(@NotNull org.bukkit.entity.Entity entity) {
            boolean bl;
            block5: {
                Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                Iterable $this$all$iv = this.data;
                boolean $i$f$all = false;
                if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
                    bl = true;
                } else {
                    for (Object element$iv : $this$all$iv) {
                        boolean bl2;
                        DataMatch it = (DataMatch)element$iv;
                        boolean bl3 = false;
                        if (Intrinsics.areEqual((Object)it.getKey(), (Object)"name")) {
                            bl2 = DataMatch.check$default(it, NMSTranslateKt.getI18nName$default((org.bukkit.entity.Entity)entity, null, (int)1, null), null, 2, null);
                        } else {
                            Object[] objectArray = new Object[]{this.name + '[' + it.getKey() + '=' + it.getValue() + "] not supported."};
                            IOKt.warning((Object[])objectArray);
                            bl2 = false;
                        }
                        if (bl2) continue;
                        bl = false;
                        break block5;
                    }
                    bl = true;
                }
            }
            return bl;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\u0010\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0016J\n\u0010\u000f\u001a\u00020\u0003*\u00020\r\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/selector/InferEntity$MythicMobsEntity;", "Link/ptms/chemdah/core/quest/selector/InferEntity$Entity;", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "match", "", "entity", "Lorg/bukkit/entity/Entity;", "matchData", "mythicMobId", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nInferEntity.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferEntity.kt\nink/ptms/chemdah/core/quest/selector/InferEntity$MythicMobsEntity\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,102:1\n1726#2,3:103\n*S KotlinDebug\n*F\n+ 1 InferEntity.kt\nink/ptms/chemdah/core/quest/selector/InferEntity$MythicMobsEntity\n*L\n78#1:103,3\n*E\n"})
    public static final class MythicMobsEntity
    extends Entity {
        public MythicMobsEntity(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            super(material, flags, data2);
        }

        @NotNull
        public final String mythicMobId(@NotNull org.bukkit.entity.Entity $this$mythicMobId) {
            Intrinsics.checkNotNullParameter((Object)$this$mythicMobId, (String)"<this>");
            Object object = Mythic.Companion.getAPI().getMob($this$mythicMobId);
            if (object == null || (object = object.getId()) == null) {
                object = "@vanilla";
            }
            return object;
        }

        @Override
        public boolean match(@NotNull org.bukkit.entity.Entity entity) {
            Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
            return this.matchType(this.mythicMobId(entity)) && this.matchData(entity);
        }

        @Override
        public boolean matchData(@NotNull org.bukkit.entity.Entity entity) {
            boolean bl;
            block23: {
                Iterable $this$all$iv;
                Mob mob;
                block24: {
                    Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                    Mob mob2 = Mythic.Companion.getAPI().getMob(entity);
                    if (mob2 == null) {
                        return false;
                    }
                    mob = mob2;
                    $this$all$iv = this.getData();
                    boolean $i$f$all = false;
                    if (!($this$all$iv instanceof Collection) || !((Collection)$this$all$iv).isEmpty()) break block24;
                    bl = true;
                    break block23;
                }
                for (Object element$iv : $this$all$iv) {
                    boolean bl2;
                    DataMatch it = (DataMatch)element$iv;
                    boolean bl3 = false;
                    switch (it.getKey()) {
                        case "type": {
                            bl2 = DataMatch.check$default(it, mob.getEntityType().name(), null, 2, null);
                            break;
                        }
                        case "name": {
                            bl2 = DataMatch.check$default(it, NMSTranslateKt.getI18nName$default((org.bukkit.entity.Entity)entity, null, (int)1, null), null, 2, null);
                            break;
                        }
                        case "level": {
                            if (Coerce.toDouble((Object)it.getValue()) <= mob.getLevel()) {
                                bl2 = true;
                                break;
                            }
                            bl2 = false;
                            break;
                        }
                        case "stance": {
                            bl2 = Intrinsics.areEqual((Object)it.getValue(), (Object)mob.getStance());
                            break;
                        }
                        case "faction": {
                            bl2 = Intrinsics.areEqual((Object)it.getValue(), (Object)mob.getFaction());
                            break;
                        }
                        default: {
                            String string = mob.getConfig().getString(it.getKey());
                            if (string == null) {
                                return false;
                            }
                            bl2 = DataMatch.check$default(it, string, null, 2, null);
                        }
                    }
                    if (bl2) continue;
                    bl = false;
                    break block23;
                }
                bl = true;
            }
            return bl;
        }
    }
}

