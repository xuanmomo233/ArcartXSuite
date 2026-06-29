/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.level;

import ink.ptms.chemdah.module.level.Algorithm;
import ink.ptms.chemdah.module.level.Level;
import ink.ptms.chemdah.module.level.LevelReward;
import ink.ptms.chemdah.module.level.PlayerLevel;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import java.util.ArrayList;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.IntRange;
import kotlin1822.ranges.RangesKt;
import kotlin1822.text.Regex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0010\u0010\u0015\u001a\u0004\u0018\u00010\u00132\u0006\u0010\u0019\u001a\u00020\u0005J\u000e\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u001dJ\u0016\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u0019\u001a\u00020\u00052\u0006\u0010\u001e\u001a\u00020\u0005R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u000b\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R!\u0010\u0011\u001a\u0012\u0012\u0004\u0012\u00020\u00130\u0012j\b\u0012\u0004\u0012\u00020\u0013`\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018\u00a8\u0006\u001f"}, d2={"Link/ptms/chemdah/module/level/LevelOption;", "", "algorithm", "Link/ptms/chemdah/module/level/Algorithm;", "min", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/module/level/Algorithm;ILink/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getAlgorithm", "()Link/ptms/chemdah/module/level/Algorithm;", "id", "", "getId", "()Ljava/lang/String;", "getMin", "()I", "reward", "Ljava/util/ArrayList;", "Link/ptms/chemdah/module/level/LevelReward;", "Lkotlin1822/collections/ArrayList;", "getReward", "()Ljava/util/ArrayList;", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "level", "toLevel", "Link/ptms/chemdah/module/level/Level;", "playerLevel", "Link/ptms/chemdah/module/level/PlayerLevel;", "experience", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nLevelOption.kt\nKotlin\n*S Kotlin\n*F\n+ 1 LevelOption.kt\nink/ptms/chemdah/module/level/LevelOption\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,53:1\n1855#2:54\n1856#2:56\n288#2,2:57\n1#3:55\n*S KotlinDebug\n*F\n+ 1 LevelOption.kt\nink/ptms/chemdah/module/level/LevelOption\n*L\n24#1:54\n24#1:56\n52#1:57,2\n*E\n"})
public final class LevelOption {
    @NotNull
    private final Algorithm algorithm;
    private final int min;
    @NotNull
    private final ConfigurationSection root;
    @NotNull
    private final String id;
    @NotNull
    private final ArrayList<LevelReward> reward;

    public LevelOption(@NotNull Algorithm algorithm, int min2, @NotNull ConfigurationSection root2) {
        block3: {
            Intrinsics.checkNotNullParameter((Object)algorithm, (String)"algorithm");
            Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
            this.algorithm = algorithm;
            this.min = min2;
            this.root = root2;
            this.id = this.root.getName();
            this.reward = new ArrayList();
            Object object = this.root.getConfigurationSection("reward");
            if (object == null || (object = object.getKeys(false)) == null) break block3;
            Iterable $this$forEach$iv = (Iterable)object;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Object object2;
                String node = (String)element$iv;
                boolean bl = false;
                CharSequence charSequence = node;
                Object object3 = new Regex("[-~]");
                int n = 0;
                List args = object3.split(charSequence, n);
                int n2 = Coerce.toInteger(args.get(0));
                n = 1;
                object3 = args;
                if (n <= CollectionsKt.getLastIndex((List)object3)) {
                    object2 = object3.get(n);
                } else {
                    int n3 = n;
                    int n4 = n2;
                    boolean bl2 = false;
                    String string = (String)args.get(0);
                    n2 = n4;
                    object2 = string;
                }
                int n5 = Coerce.toInteger(object2);
                int n6 = n2;
                List range = CollectionsKt.toList((Iterable)((Iterable)new IntRange(n6, n5)));
                Object object4 = this.root.get("reward." + node);
                Intrinsics.checkNotNull((Object)object4);
                this.reward.add(new LevelReward(range, CollectionKt.asList((Object)object4)));
            }
        }
    }

    @NotNull
    public final Algorithm getAlgorithm() {
        return this.algorithm;
    }

    public final int getMin() {
        return this.min;
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    @NotNull
    public final ArrayList<LevelReward> getReward() {
        return this.reward;
    }

    @NotNull
    public final Level toLevel(int level, int experience) {
        return new Level(this.algorithm, RangesKt.coerceAtLeast((int)level, (int)this.min), experience);
    }

    @NotNull
    public final Level toLevel(@NotNull PlayerLevel playerLevel) {
        Intrinsics.checkNotNullParameter((Object)playerLevel, (String)"playerLevel");
        return new Level(this.algorithm, RangesKt.coerceAtLeast((int)playerLevel.getLevel(), (int)this.min), playerLevel.getExperience());
    }

    @Nullable
    public final LevelReward getReward(int level) {
        Object v0;
        block1: {
            Iterable $this$firstOrNull$iv = this.reward;
            boolean $i$f$firstOrNull = false;
            for (Object element$iv : $this$firstOrNull$iv) {
                LevelReward it = (LevelReward)element$iv;
                boolean bl = false;
                if (!it.getLevel().contains(level)) continue;
                v0 = element$iv;
                break block1;
            }
            v0 = null;
        }
        return v0;
    }
}

