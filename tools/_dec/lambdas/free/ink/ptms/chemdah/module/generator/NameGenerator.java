/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.IntIterator
 *  kotlin1822.io.FilesKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.IntRange
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.generator;

import ink.ptms.chemdah.module.Module;
import ink.ptms.chemdah.module.generator.NameGenerator;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.IntIterator;
import kotlin1822.io.FilesKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.IntRange;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Awake
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001e\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\u0006\u0010\u000b\u001a\u00020\u00052\b\b\u0002\u0010\f\u001a\u00020\rJ\f\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004J\u0010\u0010\u000f\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00110\u0010H\u0003J\b\u0010\u0012\u001a\u00020\u0013H\u0016R\u0017\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0017\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\u0007\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/module/generator/NameGenerator;", "Link/ptms/chemdah/module/Module;", "()V", "def", "", "", "getDef", "()Ljava/util/List;", "namespace", "getNamespace", "generate", "name", "amount", "", "generatorKeys", "nameParser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "", "reload", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nNameGenerator.kt\nKotlin\n*S Kotlin\n*F\n+ 1 NameGenerator.kt\nink/ptms/chemdah/module/generator/NameGenerator\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n*L\n1#1,71:1\n766#2:72\n857#2,2:73\n1855#2,2:79\n11335#3:75\n11670#3,3:76\n*S KotlinDebug\n*F\n+ 1 NameGenerator.kt\nink/ptms/chemdah/module/generator/NameGenerator\n*L\n41#1:72\n41#1:73,2\n58#1:79,2\n52#1:75\n52#1:76,3\n*E\n"})
public final class NameGenerator
implements Module {
    @NotNull
    public static final NameGenerator INSTANCE = new NameGenerator();
    @NotNull
    private static final List<String> namespace;
    @NotNull
    private static final List<String> def;

    private NameGenerator() {
    }

    @NotNull
    public final List<String> getNamespace() {
        return namespace;
    }

    @NotNull
    public final List<String> getDef() {
        return def;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final List<String> generate(@NotNull String name, int amount) {
        List list2;
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        File file = new File(IOKt.getDataFolder(), "module/generator/" + name + ".ks");
        if (file.exists()) {
            void $this$filterTo$iv$iv;
            Iterable $this$filter$iv = FilesKt.readLines$default((File)file, null, (int)1, null);
            boolean $i$f$filter = false;
            Iterable iterable = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                String it = (String)element$iv$iv;
                boolean bl = false;
                if (!(!StringsKt.startsWith$default((CharSequence)((Object)StringsKt.trimStart((CharSequence)it)).toString(), (char)'#', (boolean)false, (int)2, null))) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            List readText = (List)destination$iv$iv;
            Object object = KetherHelperKt.runKether$default(null, (boolean)false, (Function0)((Function0)new Function0<List<? extends String>>(amount, (List<String>)readText){
                final /* synthetic */ int $amount;
                final /* synthetic */ List<String> $readText;
                {
                    this.$amount = $amount;
                    this.$readText = $readText;
                    super(0);
                }

                /*
                 * WARNING - void declaration
                 */
                @NotNull
                public final List<String> invoke() {
                    void $this$mapTo$iv$iv;
                    void $this$map$iv;
                    Iterable iterable = (Iterable)new IntRange(1, this.$amount);
                    List<String> list2 = this.$readText;
                    boolean $i$f$map = false;
                    void var4_4 = $this$map$iv;
                    Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                    boolean $i$f$mapTo = false;
                    Iterator<T> iterator = $this$mapTo$iv$iv.iterator();
                    while (iterator.hasNext()) {
                        int item$iv$iv;
                        int n = item$iv$iv = ((IntIterator)iterator).nextInt();
                        Collection collection = destination$iv$iv;
                        boolean bl = false;
                        collection.add(String.valueOf(KetherShell.eval$default((KetherShell)KetherShell.INSTANCE, list2, (boolean)false, NameGenerator.INSTANCE.getNamespace(), null, null, null, null, (int)122, null).getNow("null")));
                    }
                    return (List)destination$iv$iv;
                }
            }), (int)3, null);
            Intrinsics.checkNotNull((Object)object);
            list2 = (List)object;
        } else {
            list2 = CollectionsKt.emptyList();
        }
        return list2;
    }

    public static /* synthetic */ List generate$default(NameGenerator nameGenerator, String string, int n, int n2, Object object) {
        if ((n2 & 2) != 0) {
            n = 1;
        }
        return nameGenerator.generate(string, n);
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final List<String> generatorKeys() {
        List list2;
        File[] fileArray = new File(IOKt.getDataFolder(), "module/generator").listFiles();
        if (fileArray != null) {
            void $this$mapTo$iv$iv;
            File[] $this$map$iv = fileArray;
            boolean $i$f$map = false;
            File[] fileArray2 = $this$map$iv;
            Collection destination$iv$iv = new ArrayList($this$map$iv.length);
            boolean $i$f$mapTo = false;
            int n = ((void)$this$mapTo$iv$iv).length;
            for (int i = 0; i < n; ++i) {
                void it;
                void item$iv$iv;
                void var9_9 = item$iv$iv = $this$mapTo$iv$iv[i];
                Collection collection = destination$iv$iv;
                boolean bl = false;
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                collection.add(FilesKt.getNameWithoutExtension((File)it));
            }
            list2 = (List)destination$iv$iv;
        } else {
            list2 = CollectionsKt.emptyList();
        }
        return list2;
    }

    @Override
    public void reload() {
        File folder = new File(IOKt.getDataFolder(), "module/generator");
        if (!folder.exists()) {
            Iterable $this$forEach$iv = def;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                String it = (String)element$iv;
                boolean bl = false;
                IOKt.releaseResourceFile$default((String)("module/generator/" + it + ".ks"), (boolean)false, null, (int)4, null);
            }
        }
    }

    @KetherParser(value={"name"}, namespace="chemdah_name_generator")
    private final ScriptActionParser<Object> nameParser() {
        return KetherHelperKt.scriptParser((Function1)nameParser.1.INSTANCE);
    }

    static {
        Object[] objectArray = new String[]{"adyeshach", "chemdah", "chemdah_name_generator"};
        namespace = CollectionsKt.listOf((Object[])objectArray);
        objectArray = new String[]{"city", "dragon_1", "dragon_2", "dragon_3", "dwarf_1", "dwarf_2", "elf_1", "elf_2", "elf_3", "human", "item", "kingdom", "town"};
        def = CollectionsKt.listOf((Object[])objectArray);
        Module.Companion.register(INSTANCE);
    }
}

