/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.DataContainerEventFactory;
import ink.ptms.chemdah.core.database.ChangeTracker;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.module.nms.ItemTag;
import ink.ptms.chemdah.taboolib.module.nms.ItemTagData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import java.util.function.Function;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u008a\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010#\n\u0002\u0010'\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010&\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0016\u0018\u00002\u00020\u0001B\u001b\b\u0016\u0012\u0012\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003\u00a2\u0006\u0002\u0010\u0006B#\b\u0016\u0012\u0012\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003\u0012\u0006\u0010\u0007\u001a\u00020\b\u00a2\u0006\u0002\u0010\tB\r\u0012\u0006\u0010\n\u001a\u00020\b\u00a2\u0006\u0002\u0010\u000bJ\b\u0010\u0016\u001a\u00020\u0017H\u0016J\u0010\u0010\u0018\u001a\u00020\r2\u0006\u0010\u0019\u001a\u00020\u0004H\u0016J\u0010\u0010\u001a\u001a\u00020\r2\u0006\u0010\u001b\u001a\u00020\u001cH\u0016J\b\u0010\u001d\u001a\u00020\u0000H\u0016J\u001a\u0010\u001e\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050 0\u001fH\u0016J\u0013\u0010!\u001a\u00020\r2\b\u0010\"\u001a\u0004\u0018\u00010\u001cH\u0096\u0002J\b\u0010#\u001a\u00020$H\u0016J\"\u0010%\u001a\u00020\u00172\u0018\u0010&\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050(0'H\u0016J\u0013\u0010)\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0019\u001a\u00020\u0004H\u0096\u0002J\u0019\u0010)\u001a\u00020\u00052\u0006\u0010\u0019\u001a\u00020\u00042\u0006\u0010*\u001a\u00020\u001cH\u0096\u0002J\b\u0010+\u001a\u00020,H\u0016J\b\u0010-\u001a\u00020\rH\u0016J\b\u0010.\u001a\u00020\rH\u0016J\u000e\u0010/\u001a\b\u0012\u0004\u0012\u00020\u000400H\u0016J\u0010\u00101\u001a\u00020\u00172\u0006\u0010\u0019\u001a\u00020\u0004H\u0016J(\u00102\u001a\u00020\u00172\u001e\u00103\u001a\u001a\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050(\u0012\u0004\u0012\u00020\r04H\u0016J\u0019\u00105\u001a\u00020\u00172\u0006\u0010\u0019\u001a\u00020\u00042\u0006\u0010\u001b\u001a\u00020\u001cH\u0096\u0002J\b\u00106\u001a\u00020\u0004H\u0016J\u0014\u00107\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u001c0\u0003H\u0016J\b\u00108\u001a\u000209H\u0016J\b\u0010:\u001a\u00020\u0004H\u0016J\n\u0010;\u001a\u00020\u0005*\u00020\u0005R\u0014\u0010\f\u001a\u00020\r8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\f\u0010\u000eR\u0014\u0010\u000f\u001a\u00020\u0010X\u0084\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R \u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0013X\u0084\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015\u00a8\u0006<"}, d2={"Link/ptms/chemdah/core/SimpleDataContainer;", "Link/ptms/chemdah/core/DataContainer;", "map", "", "", "Link/ptms/chemdah/core/Data;", "(Ljava/util/Map;)V", "factory", "Link/ptms/chemdah/core/DataContainerEventFactory;", "(Ljava/util/Map;Link/ptms/chemdah/core/DataContainerEventFactory;)V", "eventFactory", "(Link/ptms/chemdah/core/DataContainerEventFactory;)V", "isChanged", "", "()Z", "lock", "Ljava/util/concurrent/locks/StampedLock;", "getLock", "()Ljava/util/concurrent/locks/StampedLock;", "Ljava/util/concurrent/ConcurrentHashMap;", "getMap", "()Ljava/util/concurrent/ConcurrentHashMap;", "clear", "", "containsKey", "key", "containsValue", "value", "", "copy", "entries", "", "", "equals", "other", "flush", "Link/ptms/chemdah/core/database/ChangeTracker;", "forEach", "consumer", "Ljava/util/function/Consumer;", "", "get", "def", "hashCode", "", "isEmpty", "isNotEmpty", "keys", "", "remove", "removeIf", "predicate", "Ljava/util/function/Function;", "set", "toJson", "toMap", "toNBT", "Link/ptms/chemdah/taboolib/module/nms/ItemTag;", "toString", "makeChanged", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nSimpleDataContainer.kt\nKotlin\n*S Kotlin\n*F\n+ 1 SimpleDataContainer.kt\nink/ptms/chemdah/core/SimpleDataContainer\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 4 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,231:1\n187#2,3:232\n187#2,3:235\n215#2,2:238\n215#2,2:247\n215#2,2:251\n215#2,2:259\n215#2,2:261\n515#3:240\n500#3,6:241\n442#3:253\n392#3:254\n1855#4,2:249\n1238#4,4:255\n*S KotlinDebug\n*F\n+ 1 SimpleDataContainer.kt\nink/ptms/chemdah/core/SimpleDataContainer\n*L\n37#1:232,3\n45#1:235,3\n115#1:238,2\n151#1:247,2\n170#1:251,2\n181#1:259,2\n187#1:261,2\n149#1:240\n149#1:241,6\n175#1:253\n175#1:254\n161#1:249,2\n175#1:255,4\n*E\n"})
public class SimpleDataContainer
extends DataContainer {
    @NotNull
    private final StampedLock lock;
    @NotNull
    private final ConcurrentHashMap<String, Data> map;

    public SimpleDataContainer(@NotNull DataContainerEventFactory eventFactory) {
        Intrinsics.checkNotNullParameter((Object)eventFactory, (String)"eventFactory");
        super(eventFactory);
        this.lock = new StampedLock();
        this.map = new ConcurrentHashMap();
    }

    @NotNull
    protected final StampedLock getLock() {
        return this.lock;
    }

    @NotNull
    protected final ConcurrentHashMap<String, Data> getMap() {
        return this.map;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Unable to fully structure code
     */
    @Override
    public boolean isChanged() {
        block17: {
            block16: {
                block13: {
                    stamp = this.lock.tryOptimisticRead();
                    if (((Collection)this.getDrops()).isEmpty() == false) ** GOTO lbl-1000
                    $this$any$iv = this.map;
                    $i$f$any = false;
                    if ($this$any$iv.isEmpty()) {
                        v0 = false;
                    } else {
                        var6_5 = $this$any$iv.entrySet().iterator();
                        while (var6_5.hasNext()) {
                            it = element$iv = var6_5.next();
                            $i$a$-any-SimpleDataContainer$isChanged$result$1 = false;
                            if (!((Data)it.getValue()).getChanged()) continue;
                            v0 = true;
                            break block13;
                        }
                        v0 = false;
                    }
                }
                if (v0) lbl-1000:
                // 2 sources

                {
                    v1 = true;
                } else {
                    v1 = result = false;
                }
                if (!this.lock.validate(stamp)) break block16;
                v2 = result;
                break block17;
            }
            readStamp = this.lock.readLock();
            try {
                block14: {
                    if (((Collection)this.getDrops()).isEmpty() == false) ** GOTO lbl-1000
                    $this$any$iv = this.map;
                    $i$f$any = false;
                    if ($this$any$iv.isEmpty()) {
                        v3 = false;
                    } else {
                        var8_10 = $this$any$iv.entrySet().iterator();
                        while (var8_10.hasNext()) {
                            it = element$iv = var8_10.next();
                            $i$a$-any-SimpleDataContainer$isChanged$1 = false;
                            if (!((Data)it.getValue()).getChanged()) continue;
                            v3 = true;
                            break block14;
                        }
                        v3 = false;
                    }
                }
                if (v3) lbl-1000:
                // 2 sources

                {
                    v4 = true;
                } else {
                    v4 = false;
                }
                var6_6 = v4;
            }
            finally {
                this.lock.unlockRead(readStamp);
            }
            v2 = var6_6;
        }
        return v2;
    }

    public SimpleDataContainer(@NotNull Map<String, ? extends Data> map) {
        Intrinsics.checkNotNullParameter(map, (String)"map");
        this(DataContainerEventFactory.Companion.getEMPTY());
        this.map.putAll(map);
    }

    public SimpleDataContainer(@NotNull Map<String, ? extends Data> map, @NotNull DataContainerEventFactory factory) {
        Intrinsics.checkNotNullParameter(map, (String)"map");
        Intrinsics.checkNotNullParameter((Object)factory, (String)"factory");
        this(factory);
        this.map.putAll(map);
    }

    @Override
    @Nullable
    public Data get(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return this.map.get(key);
    }

    @Override
    @NotNull
    public Data get(@NotNull String key, @NotNull Object def) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)def, (String)"def");
        Data data2 = this.map.get(key);
        if (data2 == null) {
            data2 = Data.Companion.unsafe(def);
        }
        return data2;
    }

    @Override
    public void set(@NotNull String key, @NotNull Object value2) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        Data data2 = value2 instanceof Data ? (Data)value2 : null;
        if (data2 == null) {
            data2 = Data.Companion.unsafe(value2);
        }
        Data data3 = data2;
        this.makeChanged(data3);
        if (!this.isSilence()) {
            Data data4 = this.getEventFactory().callPreSet(this, key, data3);
            if (data4 == null) {
                return;
            }
            data3 = data4;
        }
        ((Map)this.map).put(key, data3);
        if (!this.isSilence()) {
            this.getDrops().remove((Object)key);
            this.getEventFactory().callPostSet(this, key, data3);
        }
    }

    @Override
    public void remove(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        if (!this.isSilence() && !this.getEventFactory().callPreRemove(this, key)) {
            return;
        }
        this.map.remove(key);
        if (!this.isSilence()) {
            this.getDrops().add((Object)key);
            this.getEventFactory().callPostRemove(this, key);
        }
    }

    @Override
    public void clear() {
        Map $this$forEach$iv = this.map;
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry it = element$iv = iterator.next();
            boolean bl = false;
            this.remove((String)it.getKey());
        }
    }

    @Override
    public boolean containsKey(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return this.map.containsKey(key);
    }

    @Override
    public boolean containsValue(@NotNull Object value2) {
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        return this.map.containsValue(Data.Companion.unsafe(value2));
    }

    @Override
    @NotNull
    public Set<Map.Entry<String, Data>> entries() {
        Set<Map.Entry<String, Data>> set2 = this.map.entrySet();
        Intrinsics.checkNotNullExpressionValue(set2, (String)"map.entries");
        return set2;
    }

    @Override
    @NotNull
    public List<String> keys() {
        Enumeration<String> enumeration = this.map.keys();
        Intrinsics.checkNotNullExpressionValue(enumeration, (String)"map.keys()");
        ArrayList<String> arrayList = Collections.list(enumeration);
        Intrinsics.checkNotNullExpressionValue(arrayList, (String)"list(this)");
        return arrayList;
    }

    @Override
    @NotNull
    public SimpleDataContainer copy() {
        return new SimpleDataContainer((Map<String, ? extends Data>)this.map, this.getEventFactory());
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * WARNING - void declaration
     */
    @Override
    @NotNull
    public ChangeTracker flush() {
        ChangeTracker tracker = null;
        long writeLock = this.lock.writeLock();
        try {
            void $this$filterTo$iv$iv;
            Map $this$filter$iv = this.map;
            boolean $i$f$filter = false;
            Object object = $this$filter$iv;
            Map destination$iv$iv = new LinkedHashMap();
            boolean $i$f$filterTo = false;
            Iterator iterator = $this$filterTo$iv$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry element$iv$iv;
                Map.Entry entry = element$iv$iv = iterator.next();
                boolean bl = false;
                String k = (String)entry.getKey();
                Data v = (Data)entry.getValue();
                if (!(v.getChanged() && !StringsKt.startsWith$default((String)k, (String)"__", (boolean)false, (int)2, null))) continue;
                destination$iv$iv.put(element$iv$iv.getKey(), element$iv$iv.getValue());
            }
            Set set2 = CollectionsKt.toSet((Iterable)((Iterable)this.getDrops()));
            Map map = destination$iv$iv;
            tracker = new ChangeTracker(map, set2);
            Map $this$forEach$iv = this.map;
            boolean $i$f$forEach = false;
            object = $this$forEach$iv.entrySet().iterator();
            while (object.hasNext()) {
                Map.Entry element$iv;
                Map.Entry it = element$iv = (Map.Entry)object.next();
                boolean bl = false;
                ((Data)it.getValue()).setChanged(false);
            }
            this.getDrops().clear();
        }
        finally {
            this.lock.unlockWrite(writeLock);
        }
        return tracker;
    }

    @Override
    public void removeIf(@NotNull Function<Map.Entry<String, Data>, Boolean> predicate) {
        Intrinsics.checkNotNullParameter(predicate, (String)"predicate");
        Set<Map.Entry<String, Data>> set2 = this.map.entrySet();
        Intrinsics.checkNotNullExpressionValue(set2, (String)"map.entries");
        Iterable $this$forEach$iv = set2;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Map.Entry it = (Map.Entry)element$iv;
            boolean bl = false;
            Boolean bl2 = predicate.apply(it);
            Intrinsics.checkNotNullExpressionValue((Object)bl2, (String)"predicate.apply(it)");
            if (!bl2.booleanValue()) continue;
            Object k = it.getKey();
            Intrinsics.checkNotNullExpressionValue(k, (String)"it.key");
            this.remove((String)k);
        }
    }

    @Override
    public void forEach(@NotNull Consumer<Map.Entry<String, Data>> consumer) {
        Intrinsics.checkNotNullParameter(consumer, (String)"consumer");
        Map $this$forEach$iv = this.map;
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry it = element$iv = iterator.next();
            boolean bl = false;
            consumer.accept(it);
        }
    }

    /*
     * WARNING - void declaration
     */
    @Override
    @NotNull
    public Map<String, Object> toMap() {
        void $this$mapValuesTo$iv$iv;
        Map $this$mapValues$iv = this.map;
        boolean $i$f$mapValues = false;
        Map map = $this$mapValues$iv;
        Map destination$iv$iv = new LinkedHashMap(MapsKt.mapCapacity((int)$this$mapValues$iv.size()));
        boolean $i$f$mapValuesTo = false;
        Iterable $this$associateByTo$iv$iv$iv = $this$mapValuesTo$iv$iv.entrySet();
        boolean $i$f$associateByTo = false;
        for (Object element$iv$iv$iv : $this$associateByTo$iv$iv$iv) {
            void it;
            void it$iv$iv;
            Map.Entry entry = (Map.Entry)element$iv$iv$iv;
            Map map2 = destination$iv$iv;
            boolean bl = false;
            Map.Entry entry2 = (Map.Entry)element$iv$iv$iv;
            Object k = it$iv$iv.getKey();
            Map map3 = map2;
            boolean bl2 = false;
            Object object = ((Data)it.getValue()).getData();
            map3.put(k, object);
        }
        return destination$iv$iv;
    }

    @Override
    @NotNull
    public ItemTag toNBT() {
        ItemTag itemTag;
        ItemTag it = itemTag = new ItemTag();
        boolean bl = false;
        Map $this$forEach$iv = this.map;
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry entry = element$iv = iterator.next();
            boolean bl2 = false;
            String k = (String)entry.getKey();
            Data v = (Data)entry.getValue();
            ((Map)it).put(k, ItemTagData.Companion.toNBT(v.getData()));
        }
        return itemTag;
    }

    /*
     * WARNING - void declaration
     */
    @Override
    @NotNull
    public String toJson() {
        JsonObject jsonObject;
        JsonObject it = jsonObject = new JsonObject();
        boolean bl = false;
        Map $this$forEach$iv = this.map;
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            void json;
            JsonPrimitive jsonPrimitive;
            Map.Entry element$iv;
            Map.Entry entry = element$iv = iterator.next();
            boolean bl2 = false;
            String k = (String)entry.getKey();
            Data v = (Data)entry.getValue();
            JsonPrimitive jsonPrimitive2 = jsonPrimitive = new JsonPrimitive((Number)0);
            String string = k;
            JsonObject jsonObject2 = it;
            boolean bl3 = false;
            Reflex.Companion.setProperty$default((Reflex.Companion)Reflex.Companion, (Object)json, (String)"value", (Object)v.getData(), (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
            Unit unit = Unit.INSTANCE;
            jsonObject2.add(string, (JsonElement)jsonPrimitive);
        }
        String string = jsonObject.toString();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"JsonObject().also {\n    \u2026       }\n    }.toString()");
        return string;
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    @Override
    public boolean isNotEmpty() {
        return !((Map)this.map).isEmpty();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @NotNull
    public final Data makeChanged(@NotNull Data $this$makeChanged) {
        Intrinsics.checkNotNullParameter((Object)$this$makeChanged, (String)"<this>");
        if (!this.isSilence()) {
            long writeLock = this.lock.writeLock();
            try {
                $this$makeChanged.setChanged(true);
            }
            finally {
                this.lock.unlockWrite(writeLock);
            }
        }
        return $this$makeChanged;
    }

    @NotNull
    public String toString() {
        return "DataCenter(map=" + this.map + ')';
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SimpleDataContainer)) {
            return false;
        }
        return Intrinsics.areEqual(this.map, ((SimpleDataContainer)other).map);
    }

    public int hashCode() {
        return this.map.hashCode();
    }
}

