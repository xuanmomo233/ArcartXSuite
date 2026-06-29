/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.io.FilesKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.wizard;

import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.io.FilesKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0015\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001e\u0010\u0005\u001a\n\u0012\u0004\u0012\u00020\u0007\u0018\u00010\u00062\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\tJ$\u0010\u000b\u001a\u00020\f2\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\t2\f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000e0\u0006R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/module/wizard/WizardPathCache;", "", "()V", "cacheDir", "Ljava/io/File;", "load", "", "", "id", "", "cacheKey", "save", "", "pathList", "Lorg/bukkit/Location;", "Chemdah"})
public final class WizardPathCache {
    @NotNull
    public static final WizardPathCache INSTANCE = new WizardPathCache();
    @NotNull
    private static final File cacheDir = new File(IOKt.getDataFolder(), "module/wizard/.pathcache");

    private WizardPathCache() {
    }

    @Nullable
    public final List<int[]> load(@NotNull String id2, @NotNull String cacheKey) {
        List list2;
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter((Object)cacheKey, (String)"cacheKey");
        File file = new File(cacheDir, id2 + ".txt");
        if (!file.exists()) {
            return null;
        }
        try {
            list2 = FilesKt.readLines$default((File)file, null, (int)1, null);
        }
        catch (Exception e) {
            Object[] objectArray = new Object[]{"[Wizard] Failed to read path cache: " + id2 + " (" + e.getMessage() + ')'};
            IOKt.warning((Object[])objectArray);
            return null;
        }
        List lines = list2;
        if (lines.size() < 2) {
            return null;
        }
        if (!Intrinsics.areEqual(lines.get(0), (Object)cacheKey)) {
            return null;
        }
        ArrayList coords = new ArrayList(lines.size() - 1);
        int n = lines.size();
        for (int i = 1; i < n; ++i) {
            String line = (String)lines.get(i);
            if (((CharSequence)line).length() == 0) continue;
            Object object = new char[]{' '};
            List parts = StringsKt.split$default((CharSequence)line, (char[])object, (boolean)false, (int)0, (int)6, null);
            if (parts.size() != 3) {
                return null;
            }
            object = coords;
            int[] nArray = new int[]{Integer.parseInt((String)parts.get(0)), Integer.parseInt((String)parts.get(1)), Integer.parseInt((String)parts.get(2))};
            object.add(nArray);
        }
        return coords;
    }

    public final void save(@NotNull String id2, @NotNull String cacheKey, @NotNull List<? extends Location> pathList) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter((Object)cacheKey, (String)"cacheKey");
        Intrinsics.checkNotNullParameter(pathList, (String)"pathList");
        ExecutorKt.submit$default((boolean)false, (boolean)true, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(cacheKey, pathList, id2){
            final /* synthetic */ String $cacheKey;
            final /* synthetic */ List<Location> $pathList;
            final /* synthetic */ String $id;
            {
                this.$cacheKey = $cacheKey;
                this.$pathList = $pathList;
                this.$id = $id;
                super(1);
            }

            public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                WizardPathCache.access$getCacheDir$p().mkdirs();
                StringBuilder sb = new StringBuilder(this.$cacheKey.length() + this.$pathList.size() * 16);
                StringBuilder stringBuilder = sb.append(this.$cacheKey);
                Intrinsics.checkNotNullExpressionValue((Object)stringBuilder, (String)"append(value)");
                Intrinsics.checkNotNullExpressionValue((Object)stringBuilder.append('\n'), (String)"append('\\n')");
                for (Location loc : this.$pathList) {
                    StringBuilder stringBuilder2;
                    Intrinsics.checkNotNullExpressionValue((Object)sb.append(loc.getBlockX()).append(' ').append(loc.getBlockY()).append(' '), (String)"sb.append(loc.blockX).ap\u2026d(loc.blockY).append(' ')");
                    int n = loc.getBlockZ();
                    StringBuilder stringBuilder3 = stringBuilder2.append(n);
                    Intrinsics.checkNotNullExpressionValue((Object)stringBuilder3, (String)"append(value)");
                    Intrinsics.checkNotNullExpressionValue((Object)stringBuilder3.append('\n'), (String)"append('\\n')");
                }
                try {
                    File file = new File(WizardPathCache.access$getCacheDir$p(), this.$id + ".txt");
                    String string = sb.toString();
                    Intrinsics.checkNotNullExpressionValue((Object)string, (String)"sb.toString()");
                    FilesKt.writeText$default((File)file, (String)string, null, (int)2, null);
                }
                catch (Exception e) {
                    Object[] objectArray = new Object[]{"[Wizard] Failed to write path cache: " + this.$id + " (" + e.getMessage() + ')'};
                    IOKt.warning((Object[])objectArray);
                }
            }
        }), (int)13, null);
    }

    public static final /* synthetic */ File access$getCacheDir$p() {
        return cacheDir;
    }
}

