/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.util.UtilsKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u00008\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0011\n\u0002\b\u0002\n\u0002\u0010 \n\u0000\n\u0002\u0010\f\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\f\u001a\u0012\u0010\u0000\u001a\u00020\u0001*\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0001\u001a#\u0010\u0003\u001a\u00020\u0004*\u00020\u00012\u0012\u0010\u0005\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00010\u0006\"\u00020\u0001\u00a2\u0006\u0002\u0010\u0007\u001a\u0016\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00010\t*\b\u0012\u0004\u0012\u00020\u00010\t\u001a\n\u0010\n\u001a\u00020\u0004*\u00020\u000b\u001a\n\u0010\f\u001a\u00020\r*\u00020\u0001\u001a;\u0010\u000e\u001a\u00020\u0001*\u00020\u00012*\u0010\u000f\u001a\u0016\u0012\u0012\b\u0001\u0012\u000e\u0012\u0004\u0012\u00020\u0001\u0012\u0004\u0012\u00020\u00110\u00100\u0006\"\u000e\u0012\u0004\u0012\u00020\u0001\u0012\u0004\u0012\u00020\u00110\u0010\u00a2\u0006\u0002\u0010\u0012\u001a+\u0010\u000e\u001a\u00020\u0001*\u00020\u00012\u0012\u0010\u0013\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00010\u0006\"\u00020\u00012\u0006\u0010\u0014\u001a\u00020\u0011\u00a2\u0006\u0002\u0010\u0015\u001a\u001e\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u00010\t*\b\u0012\u0004\u0012\u00020\u00010\t2\u0006\u0010\u0017\u001a\u00020\r\u001a#\u0010\u0018\u001a\u00020\u0004*\u00020\u00012\u0012\u0010\u0002\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00010\u0006\"\u00020\u0001\u00a2\u0006\u0002\u0010\u0007\u001a#\u0010\u0019\u001a\u00020\u0001*\u00020\u00012\u0012\u0010\u001a\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00010\u0006\"\u00020\u0001\u00a2\u0006\u0002\u0010\u001b\u001a\u0016\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u00010\t*\b\u0012\u0004\u0012\u00020\u00010\t\u00a8\u0006\u001d"}, d2={"addPrefix", "", "prefix", "contains", "", "value", "", "(Ljava/lang/String;[Ljava/lang/String;)Z", "flatLines", "", "isFullWidth", "", "realLength", "", "replace", "vars", "Lkotlin1822/Pair;", "", "(Ljava/lang/String;[Lkotlin1822/Pair;)Ljava/lang/String;", "key", "rep", "(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;", "splitBy", "size", "startsWithAny", "substringAfterAny", "morePrefix", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/String;", "trim", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nString.kt\nKotlin\n*S Kotlin\n*F\n+ 1 String.kt\nink/ptms/chemdah/util/StringKt\n+ 2 _Strings.kt\nkotlin/text/StringsKt___StringsKt\n+ 3 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n+ 4 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,125:1\n2104#2,5:126\n13579#3,2:131\n13579#3,2:133\n12744#3,2:135\n1282#3,2:137\n12744#3,2:139\n1360#4:141\n1446#4,5:142\n1549#4:147\n1620#4,3:148\n1360#4:151\n1446#4,5:152\n*S KotlinDebug\n*F\n+ 1 String.kt\nink/ptms/chemdah/util/StringKt\n*L\n16#1:126,5\n54#1:131,2\n60#1:133,2\n65#1:135,2\n69#1:137,2\n73#1:139,2\n86#1:141\n86#1:142,5\n95#1:147\n95#1:148,3\n105#1:151\n105#1:152,5\n*E\n"})
public final class StringKt {
    /*
     * WARNING - void declaration
     */
    public static final int realLength(@NotNull String $this$realLength) {
        void var3_3;
        Intrinsics.checkNotNullParameter((Object)$this$realLength, (String)"<this>");
        CharSequence $this$sumBy$iv = $this$realLength;
        boolean $i$f$sumBy = false;
        int sum$iv = 0;
        for (int i = 0; i < $this$sumBy$iv.length(); ++i) {
            void char_;
            char element$iv;
            char c = element$iv = $this$sumBy$iv.charAt(i);
            int n = sum$iv;
            boolean bl = false;
            int n2 = char_ <= 127 ? 1 : (StringKt.isFullWidth((char)char_) ? 2 : 1);
            sum$iv = n + n2;
        }
        return (int)var3_3;
    }

    public static final boolean isFullWidth(char $this$isFullWidth) {
        boolean bl;
        block1: {
            block5: {
                block4: {
                    Character.UnicodeScript code;
                    block3: {
                        block2: {
                            block0: {
                                code = Character.UnicodeScript.of($this$isFullWidth);
                                if (code != Character.UnicodeScript.HAN) break block0;
                                bl = true;
                                break block1;
                            }
                            if (code != Character.UnicodeScript.HIRAGANA) break block2;
                            bl = true;
                            break block1;
                        }
                        if (code != Character.UnicodeScript.KATAKANA) break block3;
                        bl = true;
                        break block1;
                    }
                    if (code != Character.UnicodeScript.HANGUL) break block4;
                    bl = true;
                    break block1;
                }
                boolean bl2 = '\u2e80' <= $this$isFullWidth ? $this$isFullWidth < '\ufe50' : false;
                if (!bl2) break block5;
                bl = true;
                break block1;
            }
            bl = ('\uff00' <= $this$isFullWidth ? $this$isFullWidth < '\ufff0' : false) ? true : ('\u4e00' <= $this$isFullWidth ? $this$isFullWidth < '\ua000' : false);
        }
        return bl;
    }

    @NotNull
    public static final String replace(@NotNull String $this$replace, Pair<String, ? extends Object> ... vars2) {
        Intrinsics.checkNotNullParameter((Object)$this$replace, (String)"<this>");
        Intrinsics.checkNotNullParameter(vars2, (String)"vars");
        String r = null;
        r = $this$replace;
        Pair<String, ? extends Object>[] $this$forEach$iv = vars2;
        boolean $i$f$forEach = false;
        int n = $this$forEach$iv.length;
        for (int i = 0; i < n; ++i) {
            Pair<String, ? extends Object> element$iv;
            Pair<String, ? extends Object> it = element$iv = $this$forEach$iv[i];
            boolean bl = false;
            Object[] objectArray = new Object[]{UtilsKt.reversed(it)};
            r = ink.ptms.chemdah.taboolib.common.util.StringKt.replaceWithOrder((String)r, (Object[])objectArray);
        }
        return r;
    }

    @NotNull
    public static final String replace(@NotNull String $this$replace, @NotNull String[] key, @NotNull Object rep) {
        Intrinsics.checkNotNullParameter((Object)$this$replace, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)rep, (String)"rep");
        String r = null;
        r = $this$replace;
        String[] $this$forEach$iv = key;
        boolean $i$f$forEach = false;
        int n = $this$forEach$iv.length;
        for (int i = 0; i < n; ++i) {
            String element$iv;
            String it = element$iv = $this$forEach$iv[i];
            boolean bl = false;
            Object[] objectArray = new Object[]{TuplesKt.to((Object)rep, (Object)it)};
            r = ink.ptms.chemdah.taboolib.common.util.StringKt.replaceWithOrder((String)r, (Object[])objectArray);
        }
        return r;
    }

    public static final boolean startsWithAny(@NotNull String $this$startsWithAny, String ... prefix) {
        boolean bl;
        block1: {
            Intrinsics.checkNotNullParameter((Object)$this$startsWithAny, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)prefix, (String)"prefix");
            String[] $this$any$iv = prefix;
            boolean $i$f$any = false;
            int n = $this$any$iv.length;
            for (int i = 0; i < n; ++i) {
                String element$iv;
                String it = element$iv = $this$any$iv[i];
                boolean bl2 = false;
                if (!StringsKt.startsWith$default((String)$this$startsWithAny, (String)it, (boolean)false, (int)2, null)) continue;
                bl = true;
                break block1;
            }
            bl = false;
        }
        return bl;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public static final String substringAfterAny(@NotNull String $this$substringAfterAny, String ... morePrefix) {
        String string;
        String string2;
        block2: {
            void $this$firstOrNull$iv;
            Intrinsics.checkNotNullParameter((Object)$this$substringAfterAny, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)morePrefix, (String)"morePrefix");
            String[] stringArray = morePrefix;
            string2 = $this$substringAfterAny;
            boolean $i$f$firstOrNull = false;
            int n = ((void)$this$firstOrNull$iv).length;
            for (int i = 0; i < n; ++i) {
                void element$iv;
                void it = element$iv = $this$firstOrNull$iv[i];
                boolean bl = false;
                String[] stringArray2 = new String[]{it};
                if (!StringKt.startsWithAny($this$substringAfterAny, stringArray2)) continue;
                string = element$iv;
                break block2;
            }
            string = null;
        }
        if (string == null) {
            return $this$substringAfterAny;
        }
        return StringsKt.substringAfter$default((String)string2, string, null, (int)2, null);
    }

    public static final boolean contains(@NotNull String $this$contains, String ... value2) {
        boolean bl;
        block1: {
            Intrinsics.checkNotNullParameter((Object)$this$contains, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            String[] $this$any$iv = value2;
            boolean $i$f$any = false;
            int n = $this$any$iv.length;
            for (int i = 0; i < n; ++i) {
                String element$iv;
                String it = element$iv = $this$any$iv[i];
                boolean bl2 = false;
                if (!(StringsKt.indexOf$default((CharSequence)$this$contains, (String)it, (int)0, (boolean)false, (int)6, null) != -1)) continue;
                bl = true;
                break block1;
            }
            bl = false;
        }
        return bl;
    }

    @NotNull
    public static final String addPrefix(@NotNull String $this$addPrefix, @NotNull String prefix) {
        Intrinsics.checkNotNullParameter((Object)$this$addPrefix, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)prefix, (String)"prefix");
        return prefix + $this$addPrefix;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public static final List<String> flatLines(@NotNull List<String> $this$flatLines) {
        void $this$flatMapTo$iv$iv;
        Intrinsics.checkNotNullParameter($this$flatLines, (String)"<this>");
        Iterable $this$flatMap$iv = $this$flatLines;
        boolean $i$f$flatMap = false;
        Iterable iterable = $this$flatMap$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$flatMapTo = false;
        for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
            String it = (String)element$iv$iv;
            boolean bl = false;
            Iterable list$iv$iv = StringsKt.lines((CharSequence)it);
            CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
        }
        return (List)destination$iv$iv;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public static final List<String> trim(@NotNull List<String> $this$trim) {
        void $this$mapTo$iv$iv;
        Intrinsics.checkNotNullParameter($this$trim, (String)"<this>");
        Iterable $this$map$iv = $this$trim;
        boolean $i$f$map = false;
        Iterable iterable = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            String string = (String)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(((Object)StringsKt.trim((CharSequence)((CharSequence)it))).toString());
        }
        return (List)destination$iv$iv;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public static final List<String> splitBy(@NotNull List<String> $this$splitBy, int size) {
        void $this$flatMapTo$iv$iv;
        Intrinsics.checkNotNullParameter($this$splitBy, (String)"<this>");
        Iterable $this$flatMap$iv = UtilKt.colored($this$splitBy);
        boolean $i$f$flatMap = false;
        Iterable iterable = $this$flatMap$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$flatMapTo = false;
        for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
            List list2;
            String line = (String)element$iv$iv;
            boolean bl = false;
            if (line.length() > size) {
                ArrayList<String> arr = new ArrayList<String>();
                String s = line;
                while (s.length() > size) {
                    String string;
                    String c;
                    Intrinsics.checkNotNullExpressionValue((Object)s.substring(0, size), (String)"this as java.lang.String\u2026ing(startIndex, endIndex)");
                    int i = StringsKt.lastIndexOf$default((CharSequence)c, (String)"\u00a7", (int)0, (boolean)false, (int)6, null);
                    arr.add(c);
                    if (i != -1 && i + 2 < c.length()) {
                        StringBuilder stringBuilder = new StringBuilder();
                        String string2 = s.substring(i, i + 2);
                        Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"this as java.lang.String\u2026ing(startIndex, endIndex)");
                        StringBuilder stringBuilder2 = stringBuilder.append(string2);
                        String string3 = s.substring(size);
                        Intrinsics.checkNotNullExpressionValue((Object)string3, (String)"this as java.lang.String).substring(startIndex)");
                        string = stringBuilder2.append(string3).toString();
                    } else {
                        String string4 = s.substring(size);
                        string = string4;
                        Intrinsics.checkNotNullExpressionValue((Object)string4, (String)"this as java.lang.String).substring(startIndex)");
                    }
                    s = string;
                }
                arr.add(s);
                list2 = arr;
            } else {
                list2 = CollectionKt.asList((Object)line);
            }
            Iterable list$iv$iv = list2;
            CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
        }
        return (List)destination$iv$iv;
    }
}

