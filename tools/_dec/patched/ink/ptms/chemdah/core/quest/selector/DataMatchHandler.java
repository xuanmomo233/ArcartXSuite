/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.selector;

import ink.ptms.chemdah.core.quest.selector.DataMatch;
import ink.ptms.chemdah.core.quest.selector.Flags;
import ink.ptms.chemdah.core.quest.selector.InferAreaParser;
import ink.ptms.chemdah.core.quest.selector.InferBlockParser;
import ink.ptms.chemdah.core.quest.selector.InferEntityParser;
import ink.ptms.chemdah.core.quest.selector.InferItemParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001!B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001e2\b\b\u0002\u0010\u001f\u001a\u00020 R\u001a\u0010\u0003\u001a\u00020\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u001a\u0010\t\u001a\u00020\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u001a\u0010\u000f\u001a\u00020\u0010X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u001a\u0010\u0015\u001a\u00020\u0016X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0017\u0010\u0018\"\u0004\b\u0019\u0010\u001a\u00a8\u0006\""}, d2={"Link/ptms/chemdah/core/quest/selector/DataMatchHandler;", "", "()V", "areaParser", "Link/ptms/chemdah/core/quest/selector/InferAreaParser;", "getAreaParser", "()Link/ptms/chemdah/core/quest/selector/InferAreaParser;", "setAreaParser", "(Link/ptms/chemdah/core/quest/selector/InferAreaParser;)V", "blockParser", "Link/ptms/chemdah/core/quest/selector/InferBlockParser;", "getBlockParser", "()Link/ptms/chemdah/core/quest/selector/InferBlockParser;", "setBlockParser", "(Link/ptms/chemdah/core/quest/selector/InferBlockParser;)V", "entityParser", "Link/ptms/chemdah/core/quest/selector/InferEntityParser;", "getEntityParser", "()Link/ptms/chemdah/core/quest/selector/InferEntityParser;", "setEntityParser", "(Link/ptms/chemdah/core/quest/selector/InferEntityParser;)V", "itemParser", "Link/ptms/chemdah/core/quest/selector/InferItemParser;", "getItemParser", "()Link/ptms/chemdah/core/quest/selector/InferItemParser;", "setItemParser", "(Link/ptms/chemdah/core/quest/selector/InferItemParser;)V", "parseMatcher", "Link/ptms/chemdah/core/quest/selector/DataMatchHandler$Matcher;", "source", "", "readData", "", "Matcher", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nDataMatchHandler.kt\nKotlin\n*S Kotlin\n*F\n+ 1 DataMatchHandler.kt\nink/ptms/chemdah/core/quest/selector/DataMatchHandler\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,45:1\n1549#2:46\n1620#2,3:47\n*S KotlinDebug\n*F\n+ 1 DataMatchHandler.kt\nink/ptms/chemdah/core/quest/selector/DataMatchHandler\n*L\n28#1:46\n28#1:47,3\n*E\n"})
public final class DataMatchHandler {
    @NotNull
    public static final DataMatchHandler INSTANCE = new DataMatchHandler();
    @NotNull
    private static InferAreaParser areaParser = new InferAreaParser();
    @NotNull
    private static InferBlockParser blockParser = new InferBlockParser();
    @NotNull
    private static InferEntityParser entityParser = new InferEntityParser();
    @NotNull
    private static InferItemParser itemParser = new InferItemParser();

    private DataMatchHandler() {
    }

    @NotNull
    public final InferAreaParser getAreaParser() {
        return areaParser;
    }

    public final void setAreaParser(@NotNull InferAreaParser inferAreaParser) {
        Intrinsics.checkNotNullParameter((Object)inferAreaParser, (String)"<set-?>");
        areaParser = inferAreaParser;
    }

    @NotNull
    public final InferBlockParser getBlockParser() {
        return blockParser;
    }

    public final void setBlockParser(@NotNull InferBlockParser inferBlockParser) {
        Intrinsics.checkNotNullParameter((Object)inferBlockParser, (String)"<set-?>");
        blockParser = inferBlockParser;
    }

    @NotNull
    public final InferEntityParser getEntityParser() {
        return entityParser;
    }

    public final void setEntityParser(@NotNull InferEntityParser inferEntityParser) {
        Intrinsics.checkNotNullParameter((Object)inferEntityParser, (String)"<set-?>");
        entityParser = inferEntityParser;
    }

    @NotNull
    public final InferItemParser getItemParser() {
        return itemParser;
    }

    public final void setItemParser(@NotNull InferItemParser inferItemParser) {
        Intrinsics.checkNotNullParameter((Object)inferItemParser, (String)"<set-?>");
        itemParser = inferItemParser;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final Matcher parseMatcher(@NotNull String source, boolean readData) {
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        String type = null;
        ArrayList data2 = new ArrayList();
        ArrayList flag = new ArrayList();
        if (StringsKt.contains$default((CharSequence)source, (char)'[', (boolean)false, (int)2, null) && StringsKt.endsWith$default((CharSequence)source, (char)']', (boolean)false, (int)2, null)) {
            type = StringsKt.substringBefore$default((String)source, (char)'[', null, (int)2, null);
            if (readData) {
                void $this$mapTo$iv$iv;
                Collection collection = data2;
                Object object = new char[]{','};
                Iterable $this$map$iv = StringsKt.split$default((CharSequence)StringsKt.dropLast((String)StringsKt.substringAfter$default((String)source, (char)'[', null, (int)2, null), (int)1), (char[])object, (boolean)false, (int)0, (int)6, null);
                boolean $i$f$map = false;
                Iterable iterable = $this$map$iv;
                Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                boolean $i$f$mapTo = false;
                for (Object item$iv$iv : $this$mapTo$iv$iv) {
                    void it;
                    String string = (String)item$iv$iv;
                    Collection collection2 = destination$iv$iv;
                    boolean bl = false;
                    collection2.add(DataMatch.Companion.fromString(((Object)StringsKt.trim((CharSequence)((CharSequence)it))).toString()));
                }
                object = (List)destination$iv$iv;
                CollectionsKt.addAll((Collection)collection, (Iterable)object);
            }
        } else {
            type = source;
        }
        return StringsKt.contains$default((CharSequence)type, (char)':', (boolean)false, (int)2, null) ? new Matcher(StringsKt.substringBefore$default((String)type, (char)':', null, (int)2, null), Flags.Companion.parseFlags(StringsKt.substringAfter$default((String)type, (char)':', null, (int)2, null), flag), data2, flag) : new Matcher("minecraft", Flags.Companion.parseFlags(type, flag), data2, flag);
    }

    public static /* synthetic */ Matcher parseMatcher$default(DataMatchHandler dataMatchHandler, String string, boolean bl, int n, Object object) {
        if ((n & 2) != 0) {
            bl = true;
        }
        return dataMatchHandler.parseMatcher(string, bl);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B1\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u0012\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0006\u00a2\u0006\u0002\u0010\nJ\t\u0010\u0011\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u00c6\u0003J\u000f\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\t0\u0006H\u00c6\u0003J=\u0010\u0015\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\u000e\b\u0002\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u000e\b\u0002\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0006H\u00c6\u0001J\u0013\u0010\u0016\u001a\u00020\u00172\b\u0010\u0018\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0019\u001a\u00020\u001aH\u00d6\u0001J\b\u0010\u001b\u001a\u00020\u0003H\u0016R\u0017\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0017\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u000f\u00a8\u0006\u001c"}, d2={"Link/ptms/chemdah/core/quest/selector/DataMatchHandler$Matcher;", "", "namespace", "", "key", "dataMatch", "", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "flags", "Link/ptms/chemdah/core/quest/selector/Flags;", "(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "getDataMatch", "()Ljava/util/List;", "getFlags", "getKey", "()Ljava/lang/String;", "getNamespace", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "", "toString", "Chemdah"})
    public static final class Matcher {
        @NotNull
        private final String namespace;
        @NotNull
        private final String key;
        @NotNull
        private final List<DataMatch> dataMatch;
        @NotNull
        private final List<Flags> flags;

        public Matcher(@NotNull String namespace, @NotNull String key, @NotNull List<DataMatch> dataMatch, @NotNull List<? extends Flags> flags) {
            Intrinsics.checkNotNullParameter((Object)namespace, (String)"namespace");
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Intrinsics.checkNotNullParameter(dataMatch, (String)"dataMatch");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            this.namespace = namespace;
            this.key = key;
            this.dataMatch = dataMatch;
            this.flags = flags;
        }

        @NotNull
        public final String getNamespace() {
            return this.namespace;
        }

        @NotNull
        public final String getKey() {
            return this.key;
        }

        @NotNull
        public final List<DataMatch> getDataMatch() {
            return this.dataMatch;
        }

        @NotNull
        public final List<Flags> getFlags() {
            return this.flags;
        }

        @NotNull
        public String toString() {
            return this.namespace + ':' + this.key + '[' + this.dataMatch + "] (" + this.flags + ')';
        }

        @NotNull
        public final String component1() {
            return this.namespace;
        }

        @NotNull
        public final String component2() {
            return this.key;
        }

        @NotNull
        public final List<DataMatch> component3() {
            return this.dataMatch;
        }

        @NotNull
        public final List<Flags> component4() {
            return this.flags;
        }

        @NotNull
        public final Matcher copy(@NotNull String namespace, @NotNull String key, @NotNull List<DataMatch> dataMatch, @NotNull List<? extends Flags> flags) {
            Intrinsics.checkNotNullParameter((Object)namespace, (String)"namespace");
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Intrinsics.checkNotNullParameter(dataMatch, (String)"dataMatch");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            return new Matcher(namespace, key, dataMatch, flags);
        }

        public static /* synthetic */ Matcher copy$default(Matcher matcher2, String string, String string2, List list2, List list3, int n, Object object) {
            if ((n & 1) != 0) {
                string = matcher2.namespace;
            }
            if ((n & 2) != 0) {
                string2 = matcher2.key;
            }
            if ((n & 4) != 0) {
                list2 = matcher2.dataMatch;
            }
            if ((n & 8) != 0) {
                list3 = matcher2.flags;
            }
            return matcher2.copy(string, string2, list2, list3);
        }

        public int hashCode() {
            int result = this.namespace.hashCode();
            result = result * 31 + this.key.hashCode();
            result = result * 31 + ((Object)this.dataMatch).hashCode();
            result = result * 31 + ((Object)this.flags).hashCode();
            return result;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Matcher)) {
                return false;
            }
            Matcher matcher2 = (Matcher)other;
            if (!Intrinsics.areEqual((Object)this.namespace, (Object)matcher2.namespace)) {
                return false;
            }
            if (!Intrinsics.areEqual((Object)this.key, (Object)matcher2.key)) {
                return false;
            }
            if (!Intrinsics.areEqual(this.dataMatch, matcher2.dataMatch)) {
                return false;
            }
            return Intrinsics.areEqual(this.flags, matcher2.flags);
        }
    }
}

