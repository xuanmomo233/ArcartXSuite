/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.skill;

import ink.ptms.chemdah.um.skill.data.PlaceholderDouble;
import ink.ptms.chemdah.um.skill.data.PlaceholderFloat;
import ink.ptms.chemdah.um.skill.data.PlaceholderInt;
import ink.ptms.chemdah.um.skill.data.PlaceholderString;
import java.awt.Color;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000n\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\"\n\u0002\u0010&\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0011\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\bf\u0018\u00002\u00020\u0001J\u001a\u0010\u0002\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00010\u00040\u0003H&J#\u0010\u0006\u001a\u00020\u00072\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u0007H&\u00a2\u0006\u0002\u0010\u000bJ\u0010\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\u0005H\u0016J\u0018\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u0007H\u0016J#\u0010\f\u001a\u00020\r2\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u0005H&\u00a2\u0006\u0002\u0010\u000eJ\u0018\u0010\f\u001a\u00020\r2\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u0005H&J#\u0010\u000f\u001a\u00020\u00102\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u0010H&\u00a2\u0006\u0002\u0010\u0011J\u0010\u0010\u000f\u001a\u00020\u00102\u0006\u0010\b\u001a\u00020\u0005H\u0016J\u0018\u0010\u000f\u001a\u00020\u00102\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u0010H\u0016J#\u0010\u0012\u001a\u00020\u00132\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u0013H&\u00a2\u0006\u0002\u0010\u0014J\u0010\u0010\u0012\u001a\u00020\u00132\u0006\u0010\b\u001a\u00020\u0005H\u0016J\u0018\u0010\u0012\u001a\u00020\u00132\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u0013H\u0016J#\u0010\u0015\u001a\u00020\u00162\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u0016H&\u00a2\u0006\u0002\u0010\u0017J\u0010\u0010\u0015\u001a\u00020\u00162\u0006\u0010\b\u001a\u00020\u0005H\u0016J\u0018\u0010\u0015\u001a\u00020\u00162\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u0016H\u0016J\b\u0010\u0018\u001a\u00020\u0005H&J\u0010\u0010\u0018\u001a\u00020\u00052\u0006\u0010\u0019\u001a\u00020\u0005H&J#\u0010\u001a\u001a\u00020\u001b2\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u001bH&\u00a2\u0006\u0002\u0010\u001cJ\u0010\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\b\u001a\u00020\u0005H\u0016J\u0018\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u001bH\u0016J7\u0010\u001d\u001a\u00020\u001e2\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u00102\u0012\u0010\u001f\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00050\t\"\u00020\u0005H&\u00a2\u0006\u0002\u0010 J1\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u00102\u0012\u0010\u001f\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00050\t\"\u00020\u0005H\u0016\u00a2\u0006\u0002\u0010!J7\u0010\"\u001a\u00020#2\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u00132\u0012\u0010\u001f\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00050\t\"\u00020\u0005H&\u00a2\u0006\u0002\u0010$J1\u0010\"\u001a\u00020#2\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u00132\u0012\u0010\u001f\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00050\t\"\u00020\u0005H\u0016\u00a2\u0006\u0002\u0010%J7\u0010&\u001a\u00020'2\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u00162\u0012\u0010\u001f\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00050\t\"\u00020\u0005H&\u00a2\u0006\u0002\u0010(J1\u0010&\u001a\u00020'2\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u00162\u0012\u0010\u001f\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00050\t\"\u00020\u0005H\u0016\u00a2\u0006\u0002\u0010)J7\u0010*\u001a\u00020+2\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u00052\u0012\u0010\u001f\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00050\t\"\u00020\u0005H&\u00a2\u0006\u0002\u0010,J#\u0010-\u001a\u00020\u00052\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\t2\u0006\u0010\n\u001a\u00020\u0005H&\u00a2\u0006\u0002\u0010.J\u0010\u0010-\u001a\u00020\u00052\u0006\u0010\b\u001a\u00020\u0005H\u0016J\u0018\u0010-\u001a\u00020\u00052\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u0005H\u0016J\b\u0010/\u001a\u00020\u0005H&J\b\u00100\u001a\u00020\u0016H&\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u00061\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/skill/SkillConfig;", "", "entrySet", "", "", "", "getBoolean", "", "key", "", "def", "([Ljava/lang/String;Z)Z", "getColor", "Ljava/awt/Color;", "([Ljava/lang/String;Ljava/lang/String;)Ljava/awt/Color;", "getDouble", "", "([Ljava/lang/String;D)D", "getFloat", "", "([Ljava/lang/String;F)F", "getInt", "", "([Ljava/lang/String;I)I", "getKey", "s", "getLong", "", "([Ljava/lang/String;J)J", "getPlaceholderDouble", "Link/ptms/chemdah/um/skill/data/PlaceholderDouble;", "args", "([Ljava/lang/String;D[Ljava/lang/String;)Link/ptms/chemdah/um/skill/data/PlaceholderDouble;", "(Ljava/lang/String;D[Ljava/lang/String;)Link/ptms/chemdah/um/skill/data/PlaceholderDouble;", "getPlaceholderFloat", "Link/ptms/chemdah/um/skill/data/PlaceholderFloat;", "([Ljava/lang/String;F[Ljava/lang/String;)Link/ptms/chemdah/um/skill/data/PlaceholderFloat;", "(Ljava/lang/String;F[Ljava/lang/String;)Link/ptms/chemdah/um/skill/data/PlaceholderFloat;", "getPlaceholderInt", "Link/ptms/chemdah/um/skill/data/PlaceholderInt;", "([Ljava/lang/String;I[Ljava/lang/String;)Link/ptms/chemdah/um/skill/data/PlaceholderInt;", "(Ljava/lang/String;I[Ljava/lang/String;)Link/ptms/chemdah/um/skill/data/PlaceholderInt;", "getPlaceholderString", "Link/ptms/chemdah/um/skill/data/PlaceholderString;", "([Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)Link/ptms/chemdah/um/skill/data/PlaceholderString;", "getString", "([Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", "line", "size", "common"})
public interface SkillConfig {
    @NotNull
    public String line();

    public int size();

    @NotNull
    public Set<Map.Entry<String, Object>> entrySet();

    @NotNull
    public String getKey();

    @NotNull
    public String getKey(@NotNull String var1);

    default public boolean getBoolean(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return this.getBoolean(key, false);
    }

    default public boolean getBoolean(@NotNull String key, boolean def) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        String[] stringArray = new String[]{key};
        return this.getBoolean(stringArray, def);
    }

    public boolean getBoolean(@NotNull String[] var1, boolean var2);

    @NotNull
    default public String getString(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return this.getString(key, "");
    }

    @NotNull
    default public String getString(@NotNull String key, @NotNull String def) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)def, (String)"def");
        String[] stringArray = new String[]{key};
        return this.getString(stringArray, def);
    }

    @NotNull
    public String getString(@NotNull String[] var1, @NotNull String var2);

    @NotNull
    public PlaceholderString getPlaceholderString(@NotNull String[] var1, @NotNull String var2, String ... var3);

    default public int getInt(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return this.getInt(key, 0);
    }

    default public int getInt(@NotNull String key, int def) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        String[] stringArray = new String[]{key};
        return this.getInt(stringArray, def);
    }

    public int getInt(@NotNull String[] var1, int var2);

    @NotNull
    default public PlaceholderInt getPlaceholderInt(@NotNull String key, int def, String ... args) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)args, (String)"args");
        String[] stringArray = new String[]{key};
        return this.getPlaceholderInt(stringArray, def, Arrays.copyOf(args, args.length));
    }

    @NotNull
    public PlaceholderInt getPlaceholderInt(@NotNull String[] var1, int var2, String ... var3);

    default public double getDouble(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return this.getDouble(key, 0.0);
    }

    default public double getDouble(@NotNull String key, double def) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        String[] stringArray = new String[]{key};
        return this.getDouble(stringArray, def);
    }

    public double getDouble(@NotNull String[] var1, double var2);

    @NotNull
    default public PlaceholderDouble getPlaceholderDouble(@NotNull String key, double def, String ... args) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)args, (String)"args");
        String[] stringArray = new String[]{key};
        return this.getPlaceholderDouble(stringArray, def, Arrays.copyOf(args, args.length));
    }

    @NotNull
    public PlaceholderDouble getPlaceholderDouble(@NotNull String[] var1, double var2, String ... var4);

    default public float getFloat(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return this.getFloat(key, 0.0f);
    }

    default public float getFloat(@NotNull String key, float def) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        String[] stringArray = new String[]{key};
        return this.getFloat(stringArray, def);
    }

    public float getFloat(@NotNull String[] var1, float var2);

    @NotNull
    default public PlaceholderFloat getPlaceholderFloat(@NotNull String key, float def, String ... args) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)args, (String)"args");
        String[] stringArray = new String[]{key};
        return this.getPlaceholderFloat(stringArray, def, Arrays.copyOf(args, args.length));
    }

    @NotNull
    public PlaceholderFloat getPlaceholderFloat(@NotNull String[] var1, float var2, String ... var3);

    default public long getLong(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return this.getLong(key, 0L);
    }

    default public long getLong(@NotNull String key, long def) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        String[] stringArray = new String[]{key};
        return this.getLong(stringArray, def);
    }

    public long getLong(@NotNull String[] var1, long var2);

    @NotNull
    public Color getColor(@NotNull String var1, @NotNull String var2);

    @NotNull
    public Color getColor(@NotNull String[] var1, @NotNull String var2);
}

