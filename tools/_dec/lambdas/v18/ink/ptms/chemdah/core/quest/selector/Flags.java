/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.collections.SetsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Tag
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.selector;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.collections.SetsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010\u000b\n\u0002\b\u000b\b\u0086\u0001\u0018\u0000 \u000f2\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\u000fB!\b\u0002\u0012\u0018\u0010\u0002\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003\u00a2\u0006\u0002\u0010\u0006R#\u0010\u0002\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bj\u0002\b\tj\u0002\b\nj\u0002\b\u000bj\u0002\b\fj\u0002\b\rj\u0002\b\u000e\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/selector/Flags;", "", "match", "Lkotlin1822/Function2;", "", "", "(Ljava/lang/String;ILkotlin1822/jvm/functions/Function2;)V", "getMatch", "()Lkotlin1822/jvm/functions/Function2;", "DEFAULT", "STARTS_WITH", "ENDS_WITH", "CONTAINS", "TAG", "ALL", "Companion", "Chemdah"})
public final class Flags
extends Enum<Flags> {
    @NotNull
    public static final Companion Companion;
    @NotNull
    private final Function2<String, String, Boolean> match;
    @NotNull
    private static final HashMap<String, Set<Material>> tagsMap;
    public static final /* enum */ Flags DEFAULT;
    public static final /* enum */ Flags STARTS_WITH;
    public static final /* enum */ Flags ENDS_WITH;
    public static final /* enum */ Flags CONTAINS;
    public static final /* enum */ Flags TAG;
    public static final /* enum */ Flags ALL;
    private static final /* synthetic */ Flags[] $VALUES;

    private Flags(Function2<? super String, ? super String, Boolean> match) {
        this.match = match;
    }

    @NotNull
    public final Function2<String, String, Boolean> getMatch() {
        return this.match;
    }

    public static Flags[] values() {
        return (Flags[])$VALUES.clone();
    }

    public static Flags valueOf(String value2) {
        return Enum.valueOf(Flags.class, value2);
    }

    static {
        DEFAULT = new Flags((Function2<? super String, ? super String, Boolean>)((Function2)1.INSTANCE));
        STARTS_WITH = new Flags((Function2<? super String, ? super String, Boolean>)((Function2)2.INSTANCE));
        ENDS_WITH = new Flags((Function2<? super String, ? super String, Boolean>)((Function2)3.INSTANCE));
        CONTAINS = new Flags((Function2<? super String, ? super String, Boolean>)((Function2)4.INSTANCE));
        TAG = new Flags((Function2<? super String, ? super String, Boolean>)((Function2)5.INSTANCE));
        ALL = new Flags((Function2<? super String, ? super String, Boolean>)((Function2)6.INSTANCE));
        $VALUES = flagsArray = new Flags[]{Flags.DEFAULT, Flags.STARTS_WITH, Flags.ENDS_WITH, Flags.CONTAINS, Flags.TAG, Flags.ALL};
        Companion = new Companion(null);
        tagsMap = new HashMap();
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006*\u00020\u0005J\u0018\u0010\n\u001a\u00020\u0005*\u00020\u00052\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\fR6\u0010\u0003\u001a*\u0012\u0004\u0012\u00020\u0005\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00070\u00060\u0004j\u0014\u0012\u0004\u0012\u00020\u0005\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00070\u0006`\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/core/quest/selector/Flags$Companion;", "", "()V", "tagsMap", "Ljava/util/HashMap;", "", "", "Lorg/bukkit/Material;", "Lkotlin1822/collections/HashMap;", "asTags", "parseFlags", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Set<Material> asTags(@NotNull String $this$asTags) {
            Intrinsics.checkNotNullParameter((Object)$this$asTags, (String)"<this>");
            Set set2 = tagsMap.computeIfAbsent($this$asTags, arg_0 -> Companion.asTags$lambda$0((Function1)new Function1<String, Set<? extends Material>>($this$asTags){
                final /* synthetic */ String $this_asTags;
                {
                    this.$this_asTags = $receiver;
                    super(1);
                }

                @NotNull
                public final Set<Material> invoke(@NotNull String it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    HashSet<E> set2 = new HashSet<E>();
                    Tag tag = Bukkit.getTag((String)"blocks", (NamespacedKey)NamespacedKey.minecraft((String)this.$this_asTags), Material.class);
                    Set<E> set3 = tag != null ? tag.getValues() : null;
                    set2.addAll(set3 == null ? (Collection)SetsKt.emptySet() : (Collection)set3);
                    Tag tag2 = Bukkit.getTag((String)"items", (NamespacedKey)NamespacedKey.minecraft((String)this.$this_asTags), Material.class);
                    Set<E> set4 = tag2 != null ? tag2.getValues() : null;
                    set2.addAll(set4 == null ? (Collection)SetsKt.emptySet() : (Collection)set4);
                    return set2;
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)set2, (String)"String.asTags(): Set<Mat\u2026        set\n            }");
            return set2;
        }

        @NotNull
        public final String parseFlags(@NotNull String $this$parseFlags, @NotNull List<Flags> flags) {
            Intrinsics.checkNotNullParameter((Object)$this$parseFlags, (String)"<this>");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            String result = $this$parseFlags;
            if (Intrinsics.areEqual((Object)result, (Object)"*")) {
                flags.add(ALL);
            } else if (StringsKt.startsWith$default((CharSequence)result, (char)'%', (boolean)false, (int)2, null) && StringsKt.endsWith$default((CharSequence)result, (char)'%', (boolean)false, (int)2, null)) {
                String string = result.substring(1, result.length() - 1);
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String\u2026ing(startIndex, endIndex)");
                result = string;
                flags.add(TAG);
            } else {
                if (StringsKt.startsWith$default((CharSequence)result, (char)'(', (boolean)false, (int)2, null) && StringsKt.endsWith$default((CharSequence)result, (char)')', (boolean)false, (int)2, null)) {
                    String string = result.substring(1, result.length() - 1);
                    Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String\u2026ing(startIndex, endIndex)");
                    result = string;
                    flags.add(CONTAINS);
                }
                if (StringsKt.startsWith$default((CharSequence)result, (char)'^', (boolean)false, (int)2, null)) {
                    String string = result.substring(1);
                    Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).substring(startIndex)");
                    result = string;
                    flags.add(STARTS_WITH);
                }
                if (StringsKt.endsWith$default((CharSequence)result, (char)'$', (boolean)false, (int)2, null)) {
                    String string = result.substring(0, result.length() - 1);
                    Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String\u2026ing(startIndex, endIndex)");
                    result = string;
                    flags.add(ENDS_WITH);
                }
            }
            flags.add(DEFAULT);
            return result;
        }

        private static final Set asTags$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (Set)$tmp0.invoke(p0);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

