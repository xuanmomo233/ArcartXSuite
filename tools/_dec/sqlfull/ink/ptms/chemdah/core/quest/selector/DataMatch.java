/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.selector;

import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherMathKt;
import ink.ptms.chemdah.taboolib.module.kether.action.transform.CheckType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.Regex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0010\u000b\n\u0002\b\b\n\u0002\u0010\b\n\u0002\b\u0004\b\u0086\b\u0018\u0000 \u001b2\u00020\u0001:\u0001\u001bB\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0001\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u0018\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00012\b\b\u0002\u0010\u0005\u001a\u00020\u0006J\t\u0010\u0011\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0001H\u00c6\u0003J\t\u0010\u0013\u001a\u00020\u0006H\u00c6\u0003J'\u0010\u0014\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00012\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u00c6\u0001J\u0013\u0010\u0015\u001a\u00020\u000f2\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0017\u001a\u00020\u0018H\u00d6\u0001J\u0006\u0010\u0019\u001a\u00020\u000fJ\b\u0010\u001a\u001a\u00020\u0003H\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0004\u001a\u00020\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\r\u00a8\u0006\u001c"}, d2={"Link/ptms/chemdah/core/quest/selector/DataMatch;", "", "key", "", "value", "type", "Link/ptms/chemdah/taboolib/module/kether/action/transform/CheckType;", "(Ljava/lang/String;Ljava/lang/Object;Link/ptms/chemdah/taboolib/module/kether/action/transform/CheckType;)V", "getKey", "()Ljava/lang/String;", "getType", "()Link/ptms/chemdah/taboolib/module/kether/action/transform/CheckType;", "getValue", "()Ljava/lang/Object;", "check", "", "target", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "isInt", "toString", "Companion", "Chemdah"})
public final class DataMatch {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final String key;
    @NotNull
    private final Object value;
    @NotNull
    private final CheckType type;

    public DataMatch(@NotNull String key, @NotNull Object value2, @NotNull CheckType type) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        Intrinsics.checkNotNullParameter((Object)type, (String)"type");
        this.key = key;
        this.value = value2;
        this.type = type;
    }

    @NotNull
    public final String getKey() {
        return this.key;
    }

    @NotNull
    public final Object getValue() {
        return this.value;
    }

    @NotNull
    public final CheckType getType() {
        return this.type;
    }

    public final boolean isInt() {
        return KetherMathKt.isInt((Object)this.value);
    }

    public final boolean check(@NotNull Object target, @NotNull CheckType type) {
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        Intrinsics.checkNotNullParameter((Object)type, (String)"type");
        return (Boolean)type.getCheck().invoke(this.value, target);
    }

    public static /* synthetic */ boolean check$default(DataMatch dataMatch, Object object, CheckType checkType, int n, Object object2) {
        if ((n & 2) != 0) {
            checkType = dataMatch.type;
        }
        return dataMatch.check(object, checkType);
    }

    @NotNull
    public String toString() {
        return '[' + this.key + "] " + this.type + " [" + this.value + ']';
    }

    @NotNull
    public final String component1() {
        return this.key;
    }

    @NotNull
    public final Object component2() {
        return this.value;
    }

    @NotNull
    public final CheckType component3() {
        return this.type;
    }

    @NotNull
    public final DataMatch copy(@NotNull String key, @NotNull Object value2, @NotNull CheckType type) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        Intrinsics.checkNotNullParameter((Object)type, (String)"type");
        return new DataMatch(key, value2, type);
    }

    public static /* synthetic */ DataMatch copy$default(DataMatch dataMatch, String string, Object object, CheckType checkType, int n, Object object2) {
        if ((n & 1) != 0) {
            string = dataMatch.key;
        }
        if ((n & 2) != 0) {
            object = dataMatch.value;
        }
        if ((n & 4) != 0) {
            checkType = dataMatch.type;
        }
        return dataMatch.copy(string, object, checkType);
    }

    public int hashCode() {
        int result = this.key.hashCode();
        result = result * 31 + this.value.hashCode();
        result = result * 31 + this.type.hashCode();
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DataMatch)) {
            return false;
        }
        DataMatch dataMatch = (DataMatch)other;
        if (!Intrinsics.areEqual((Object)this.key, (Object)dataMatch.key)) {
            return false;
        }
        if (!Intrinsics.areEqual((Object)this.value, (Object)dataMatch.value)) {
            return false;
        }
        return this.type == dataMatch.type;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/core/quest/selector/DataMatch$Companion;", "", "()V", "fromString", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "str", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nDataMatch.kt\nKotlin\n*S Kotlin\n*F\n+ 1 DataMatch.kt\nink/ptms/chemdah/core/quest/selector/DataMatch$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,114:1\n1549#2:115\n1620#2,3:116\n*S KotlinDebug\n*F\n+ 1 DataMatch.kt\nink/ptms/chemdah/core/quest/selector/DataMatch$Companion\n*L\n99#1:115\n99#1:116,3\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        /*
         * WARNING - void declaration
         */
        @NotNull
        public final DataMatch fromString(@NotNull String str) {
            Object object;
            Intrinsics.checkNotNullParameter((Object)str, (String)"str");
            char[] cArray = str.toCharArray();
            Intrinsics.checkNotNullExpressionValue((Object)cArray, (String)"this as java.lang.String).toCharArray()");
            char[] chars = cArray;
            int index = 0;
            CheckType type = null;
            int n = chars.length;
            for (int i = 0; i < n; ++i) {
                if (type != null) continue;
                char c = chars[i];
                if (c == '!') {
                    index = i;
                    type = CheckType.EQUALS_NOT;
                    continue;
                }
                if (c == '=') {
                    index = i;
                    type = CheckType.EQUALS;
                    continue;
                }
                if (c == '>') {
                    index = i;
                    type = i + 1 < chars.length && chars[i + 1] == '=' ? CheckType.GTE : CheckType.GT;
                    continue;
                }
                if (c == '<') {
                    index = i;
                    type = i + 1 < chars.length && chars[i + 1] == '=' ? CheckType.LTE : CheckType.LT;
                    continue;
                }
                if (c != '(') continue;
                index = i;
                type = CheckType.CONTAINS;
            }
            if (type == null) {
                throw new IllegalStateException(("Invalid data match: " + str).toString());
            }
            String string = str.substring(0, index);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String\u2026ing(startIndex, endIndex)");
            String key = string;
            switch (WhenMappings.$EnumSwitchMapping$0[type.ordinal()]) {
                case 1: 
                case 2: 
                case 3: {
                    String string2 = str.substring(index + 2);
                    object = string2;
                    Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"this as java.lang.String).substring(startIndex)");
                    break;
                }
                case 4: {
                    void $this$mapTo$iv$iv;
                    void $this$map$iv;
                    String string3 = str.substring(index + 1, str.length() - 1);
                    Intrinsics.checkNotNullExpressionValue((Object)string3, (String)"this as java.lang.String\u2026ing(startIndex, endIndex)");
                    Object object2 = string3;
                    Regex regex = new Regex("[/|]");
                    int n2 = 0;
                    object2 = regex.split((CharSequence)object2, n2);
                    boolean $i$f$map = false;
                    void var11_14 = $this$map$iv;
                    Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                    boolean $i$f$mapTo = false;
                    for (Object item$iv$iv : $this$mapTo$iv$iv) {
                        void it;
                        String string4 = (String)item$iv$iv;
                        Collection collection = destination$iv$iv;
                        boolean bl = false;
                        Object object3 = KetherHelperKt.inferType((Object)it);
                        Intrinsics.checkNotNull((Object)object3);
                        collection.add(object3);
                    }
                    List array = (List)destination$iv$iv;
                    if (array.size() == 1) {
                        type = CheckType.IN;
                        object = array.get(0);
                        break;
                    }
                    object = array;
                    break;
                }
                default: {
                    String string5 = str.substring(index + 1);
                    object = string5;
                    Intrinsics.checkNotNullExpressionValue((Object)string5, (String)"this as java.lang.String).substring(startIndex)");
                }
            }
            String value2 = object;
            return new DataMatch(key, value2, type);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }

        @Metadata(mv={1, 8, 0}, k=3, xi=48)
        public final class WhenMappings {
            public static final /* synthetic */ int[] $EnumSwitchMapping$0;

            static {
                int[] nArray = new int[CheckType.values().length];
                try {
                    nArray[CheckType.EQUALS_NOT.ordinal()] = 1;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[CheckType.GTE.ordinal()] = 2;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[CheckType.LTE.ordinal()] = 3;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[CheckType.CONTAINS.ordinal()] = 4;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                $EnumSwitchMapping$0 = nArray;
            }
        }
    }
}

