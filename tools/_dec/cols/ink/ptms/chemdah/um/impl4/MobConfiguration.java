/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um.impl4;

import com.electronwill.nightconfig.core.CommentedConfig;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.module.configuration.Type;
import io.lumine.xikage.mythicmobs.io.MythicConfig;
import io.lumine.xikage.mythicmobs.utils.config.file.FileConfiguration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.Result;
import kotlin1822.ResultKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.collections.SetsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0098\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0010\u0005\n\u0000\n\u0002\u0010\f\n\u0002\b\u0004\n\u0002\u0010\u0006\n\u0002\b\u0003\n\u0002\u0010\u0010\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\"\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010$\n\u0000\n\u0002\u0010\n\n\u0002\b\u0011\b\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\b2\f\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\b0 H\u0016J\b\u0010!\u001a\u00020\u001dH\u0016J\u0011\u0010\"\u001a\u00020#2\u0006\u0010\u001e\u001a\u00020\bH\u0096\u0002J\u0010\u0010$\u001a\u00020\u00012\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0013\u0010%\u001a\u0004\u0018\u00010\u000f2\u0006\u0010\u001e\u001a\u00020\bH\u0096\u0002J\u001d\u0010%\u001a\u0004\u0018\u00010\u000f2\u0006\u0010\u001e\u001a\u00020\b2\b\u0010&\u001a\u0004\u0018\u00010\u000fH\u0096\u0002J\u0010\u0010'\u001a\u00020#2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0018\u0010'\u001a\u00020#2\u0006\u0010\u001e\u001a\u00020\b2\u0006\u0010&\u001a\u00020#H\u0016J\u0016\u0010(\u001a\b\u0012\u0004\u0012\u00020#0 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0016\u0010)\u001a\b\u0012\u0004\u0012\u00020*0 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0016\u0010+\u001a\b\u0012\u0004\u0012\u00020,0 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0012\u0010-\u001a\u0004\u0018\u00010\b2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0016\u0010.\u001a\b\u0012\u0004\u0012\u00020\b0 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0012\u0010/\u001a\u0004\u0018\u00010\u00012\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0010\u00100\u001a\u0002012\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0018\u00100\u001a\u0002012\u0006\u0010\u001e\u001a\u00020\b2\u0006\u0010&\u001a\u000201H\u0016J\u0016\u00102\u001a\b\u0012\u0004\u0012\u0002010 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J5\u00103\u001a\u0004\u0018\u0001H4\"\u000e\b\u0000\u00104*\b\u0012\u0004\u0012\u0002H4052\u0006\u0010\u001e\u001a\u00020\b2\f\u0010\u0018\u001a\b\u0012\u0004\u0012\u0002H406H\u0016\u00a2\u0006\u0002\u00107J4\u00108\u001a\b\u0012\u0004\u0012\u0002H40 \"\u000e\b\u0000\u00104*\b\u0012\u0004\u0012\u0002H4052\u0006\u0010\u001e\u001a\u00020\b2\f\u0010\u0018\u001a\b\u0012\u0004\u0012\u0002H406H\u0016J\u0016\u00109\u001a\b\u0012\u0004\u0012\u00020:0 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0010\u0010;\u001a\u00020<2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0018\u0010;\u001a\u00020<2\u0006\u0010\u001e\u001a\u00020\b2\u0006\u0010&\u001a\u00020<H\u0016J\u0016\u0010=\u001a\b\u0012\u0004\u0012\u00020<0 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0016\u0010>\u001a\b\u0012\u0004\u0012\u00020\b0?2\u0006\u0010@\u001a\u00020#H\u0016J\u0016\u0010A\u001a\b\u0012\u0002\b\u0003\u0018\u00010 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J$\u0010A\u001a\b\u0012\u0002\b\u0003\u0018\u00010 2\u0006\u0010\u001e\u001a\u00020\b2\f\u0010&\u001a\b\u0012\u0002\b\u0003\u0018\u00010 H\u0016J\u0010\u0010B\u001a\u00020C2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0018\u0010B\u001a\u00020C2\u0006\u0010\u001e\u001a\u00020\b2\u0006\u0010&\u001a\u00020CH\u0016J\u0016\u0010D\u001a\b\u0012\u0004\u0012\u00020C0 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u001e\u0010E\u001a\u0010\u0012\f\u0012\n\u0012\u0002\b\u0003\u0012\u0002\b\u00030F0 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0016\u0010G\u001a\b\u0012\u0004\u0012\u00020H0 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0012\u0010I\u001a\u0004\u0018\u00010\b2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u001c\u0010I\u001a\u0004\u0018\u00010\b2\u0006\u0010\u001e\u001a\u00020\b2\b\u0010&\u001a\u0004\u0018\u00010\bH\u0016J\u0016\u0010J\u001a\b\u0012\u0004\u0012\u00020\b0 2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u001e\u0010K\u001a\u0010\u0012\u0004\u0012\u00020\b\u0012\u0006\u0012\u0004\u0018\u00010\u000f0F2\u0006\u0010@\u001a\u00020#H\u0016J\u0010\u0010L\u001a\u00020#2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0010\u0010M\u001a\u00020#2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0010\u0010N\u001a\u00020#2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0010\u0010O\u001a\u00020#2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0010\u0010P\u001a\u00020#2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0010\u0010Q\u001a\u00020#2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u0010\u0010R\u001a\u00020#2\u0006\u0010\u001e\u001a\u00020\bH\u0016J\u001b\u0010S\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\b2\b\u0010T\u001a\u0004\u0018\u00010\u000fH\u0096\u0002J\u001a\u0010U\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\b2\b\u0010V\u001a\u0004\u0018\u00010\bH\u0016J\u001e\u0010W\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\b2\f\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\b0 H\u0016J\u0016\u0010X\u001a\u0010\u0012\u0004\u0012\u00020\b\u0012\u0006\u0012\u0004\u0018\u00010\u000f0FH\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\t\u0010\nR\u0016\u0010\u000b\u001a\u0004\u0018\u00010\u00018VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\f\u0010\rR\u0014\u0010\u000e\u001a\u00020\u000f8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0010\u0010\u0011R\u001b\u0010\u0012\u001a\u00020\u00138FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0016\u0010\u0017\u001a\u0004\b\u0014\u0010\u0015R\u0014\u0010\u0018\u001a\u00020\u00198VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u001a\u0010\u001b\u00a8\u0006Y"}, d2={"Link/ptms/chemdah/um/impl4/MobConfiguration;", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "config", "Lio/lumine/xikage/mythicmobs/io/MythicConfig;", "(Lio/lumine/xikage/mythicmobs/io/MythicConfig;)V", "getConfig", "()Lio/lumine/xikage/mythicmobs/io/MythicConfig;", "name", "", "getName", "()Ljava/lang/String;", "parent", "getParent", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "primitiveConfig", "", "getPrimitiveConfig", "()Ljava/lang/Object;", "root", "Lio/lumine/xikage/mythicmobs/utils/config/file/FileConfiguration;", "getRoot", "()Lio/lumine/xikage/mythicmobs/utils/config/file/FileConfiguration;", "root$delegate", "Lkotlin1822/Lazy;", "type", "Link/ptms/chemdah/taboolib/module/configuration/Type;", "getType", "()Link/ptms/chemdah/taboolib/module/configuration/Type;", "addComments", "", "path", "comments", "", "clear", "contains", "", "createSection", "get", "def", "getBoolean", "getBooleanList", "getByteList", "", "getCharacterList", "", "getComment", "getComments", "getConfigurationSection", "getDouble", "", "getDoubleList", "getEnum", "T", "", "Ljava/lang/Class;", "(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Enum;", "getEnumList", "getFloatList", "", "getInt", "", "getIntegerList", "getKeys", "", "deep", "getList", "getLong", "", "getLongList", "getMapList", "", "getShortList", "", "getString", "getStringList", "getValues", "isBoolean", "isConfigurationSection", "isDouble", "isInt", "isList", "isLong", "isString", "set", "value", "setComment", "comment", "setComments", "toMap", "implementation-v4"})
public final class MobConfiguration
implements ConfigurationSection {
    @NotNull
    private final MythicConfig config;
    @NotNull
    private final Lazy root$delegate;

    public MobConfiguration(@NotNull MythicConfig config) {
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        this.config = config;
        this.root$delegate = LazyMakerKt.unsafeLazy((Function0)((Function0)new Function0<FileConfiguration>(this){
            final /* synthetic */ MobConfiguration this$0;
            {
                this.this$0 = $receiver;
                super(0);
            }

            public final FileConfiguration invoke() {
                Object object;
                Object object2;
                Object object3 = this.this$0;
                try {
                    MobConfiguration $this$invoke_u24lambda_u240 = object3;
                    boolean bl = false;
                    object2 = Result.constructor-impl((Object)$this$invoke_u24lambda_u240.getConfig().getFileConfiguration());
                }
                catch (Throwable bl) {
                    object2 = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)bl));
                }
                object3 = object2;
                object2 = this.this$0;
                Throwable throwable = Result.exceptionOrNull-impl((Object)object3);
                if (throwable == null) {
                    object = object3;
                } else {
                    Throwable it = throwable;
                    boolean bl = false;
                    Object object4 = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)((MobConfiguration)object2).getConfig(), (String)"fc", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                    Intrinsics.checkNotNull((Object)object4);
                    object = (FileConfiguration)object4;
                }
                return (FileConfiguration)object;
            }
        }));
    }

    @NotNull
    public final MythicConfig getConfig() {
        return this.config;
    }

    @NotNull
    public Object getPrimitiveConfig() {
        return this.config;
    }

    @NotNull
    public String getName() {
        String string = this.config.getKey();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getKey(...)");
        return string;
    }

    @Nullable
    public ConfigurationSection getParent() {
        return null;
    }

    @NotNull
    public Type getType() {
        return Type.YAML;
    }

    public void addComments(@NotNull String path, @NotNull List<String> comments) {
        List list2;
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        Intrinsics.checkNotNullParameter(comments, (String)"comments");
        List $this$addComments_u24lambda_u240 = list2 = CollectionsKt.toMutableList((Collection)this.getComments(path));
        boolean bl = false;
        $this$addComments_u24lambda_u240.addAll((Collection)comments);
        this.setComments(path, $this$addComments_u24lambda_u240);
    }

    @NotNull
    public final FileConfiguration getRoot() {
        Lazy lazy = this.root$delegate;
        Object object = lazy.getValue();
        Intrinsics.checkNotNullExpressionValue((Object)object, (String)"getValue(...)");
        return (FileConfiguration)object;
    }

    public void clear() {
        throw new IllegalStateException("Unmodifiable".toString());
    }

    public boolean contains(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.config.isSet(path);
    }

    @NotNull
    public ConfigurationSection createSection(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        throw new IllegalStateException("Unmodifiable".toString());
    }

    @Nullable
    public Object get(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().get(this.getName() + '.' + path);
    }

    @Nullable
    public Object get(@NotNull String path, @Nullable Object def) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().get(this.getName() + '.' + path, def);
    }

    public boolean getBoolean(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getBoolean(this.getName() + '.' + path);
    }

    public boolean getBoolean(@NotNull String path, boolean def) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getBoolean(this.getName() + '.' + path, def);
    }

    @NotNull
    public List<Boolean> getBooleanList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        List list2 = this.getRoot().getBooleanList(this.getName() + '.' + path);
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getBooleanList(...)");
        return list2;
    }

    @NotNull
    public List<Byte> getByteList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        List list2 = this.getRoot().getByteList(this.getName() + '.' + path);
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getByteList(...)");
        return list2;
    }

    @NotNull
    public List<Character> getCharacterList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        List list2 = this.getRoot().getCharacterList(this.getName() + '.' + path);
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getCharacterList(...)");
        return list2;
    }

    @Nullable
    public String getComment(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        FileConfiguration fileConfiguration = this.getRoot();
        CommentedConfig commentedConfig = fileConfiguration instanceof CommentedConfig ? (CommentedConfig)fileConfiguration : null;
        return commentedConfig != null ? commentedConfig.getComment(path) : null;
    }

    @NotNull
    public List<String> getComments(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        Object object = this.getComment(path);
        if (object == null || (object = StringsKt.lines((CharSequence)((CharSequence)object))) == null) {
            object = CollectionsKt.emptyList();
        }
        return object;
    }

    @Nullable
    public ConfigurationSection getConfigurationSection(@NotNull String path) {
        MobConfiguration mobConfiguration;
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        io.lumine.xikage.mythicmobs.utils.config.ConfigurationSection configurationSection = this.getRoot().getConfigurationSection(this.getName() + '.' + path);
        if (configurationSection != null) {
            io.lumine.xikage.mythicmobs.utils.config.ConfigurationSection it = configurationSection;
            boolean bl = false;
            mobConfiguration = new MobConfiguration(new MythicConfig(this.getName() + '.' + path, this.getRoot()));
        } else {
            mobConfiguration = null;
        }
        return mobConfiguration;
    }

    public double getDouble(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getDouble(this.getName() + '.' + path);
    }

    public double getDouble(@NotNull String path, double def) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getDouble(this.getName() + '.' + path, def);
    }

    @NotNull
    public List<Double> getDoubleList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        List list2 = this.getRoot().getDoubleList(this.getName() + '.' + path);
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getDoubleList(...)");
        return list2;
    }

    @Nullable
    public <T extends Enum<T>> T getEnum(@NotNull String path, @NotNull Class<T> type) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        Intrinsics.checkNotNullParameter(type, (String)"type");
        throw new IllegalStateException("Unsupported".toString());
    }

    @NotNull
    public <T extends Enum<T>> List<T> getEnumList(@NotNull String path, @NotNull Class<T> type) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        Intrinsics.checkNotNullParameter(type, (String)"type");
        throw new IllegalStateException("Unsupported".toString());
    }

    @NotNull
    public List<Float> getFloatList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        List list2 = this.getRoot().getFloatList(this.getName() + '.' + path);
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getFloatList(...)");
        return list2;
    }

    public int getInt(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getInt(this.getName() + '.' + path);
    }

    public int getInt(@NotNull String path, int def) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getInt(this.getName() + '.' + path, def);
    }

    @NotNull
    public List<Integer> getIntegerList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        List list2 = this.getRoot().getIntegerList(this.getName() + '.' + path);
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getIntegerList(...)");
        return list2;
    }

    @NotNull
    public Set<String> getKeys(boolean deep) {
        io.lumine.xikage.mythicmobs.utils.config.ConfigurationSection configurationSection = this.getRoot().getConfigurationSection(this.getName());
        Set set2 = configurationSection != null ? configurationSection.getKeys(deep) : null;
        if (set2 == null) {
            set2 = SetsKt.emptySet();
        }
        return set2;
    }

    @Nullable
    public List<?> getList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getList(this.getName() + '.' + path);
    }

    @Nullable
    public List<?> getList(@NotNull String path, @Nullable List<?> def) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getList(this.getName() + '.' + path, def);
    }

    public long getLong(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getLong(this.getName() + '.' + path);
    }

    public long getLong(@NotNull String path, long def) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getLong(this.getName() + '.' + path, def);
    }

    @NotNull
    public List<Long> getLongList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        List list2 = this.getRoot().getLongList(this.getName() + '.' + path);
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getLongList(...)");
        return list2;
    }

    @NotNull
    public List<Map<?, ?>> getMapList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        List list2 = this.getRoot().getMapList(this.getName() + '.' + path);
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getMapList(...)");
        return list2;
    }

    @NotNull
    public List<Short> getShortList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        List list2 = this.getRoot().getShortList(this.getName() + '.' + path);
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getShortList(...)");
        return list2;
    }

    @Nullable
    public String getString(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getString(this.getName() + '.' + path);
    }

    @Nullable
    public String getString(@NotNull String path, @Nullable String def) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().getString(this.getName() + '.' + path, def);
    }

    @NotNull
    public List<String> getStringList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        List list2 = this.getRoot().getStringList(this.getName() + '.' + path);
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getStringList(...)");
        return list2;
    }

    @NotNull
    public Map<String, Object> getValues(boolean deep) {
        io.lumine.xikage.mythicmobs.utils.config.ConfigurationSection configurationSection = this.getRoot().getConfigurationSection(this.getName());
        Map map = configurationSection != null ? configurationSection.getValues(deep) : null;
        if (map == null) {
            map = MapsKt.emptyMap();
        }
        return map;
    }

    public boolean isBoolean(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().isBoolean(this.getName() + '.' + path);
    }

    public boolean isConfigurationSection(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().isConfigurationSection(this.getName() + '.' + path);
    }

    public boolean isDouble(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().isDouble(this.getName() + '.' + path);
    }

    public boolean isInt(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().isInt(this.getName() + '.' + path);
    }

    public boolean isList(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().isList(this.getName() + '.' + path);
    }

    public boolean isLong(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().isLong(this.getName() + '.' + path);
    }

    public boolean isString(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getRoot().isString(this.getName() + '.' + path);
    }

    public void set(@NotNull String path, @Nullable Object value2) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        this.getRoot().set(path, value2);
    }

    public void setComment(@NotNull String path, @Nullable String comment) {
        block0: {
            Intrinsics.checkNotNullParameter((Object)path, (String)"path");
            FileConfiguration fileConfiguration = this.getRoot();
            CommentedConfig commentedConfig = fileConfiguration instanceof CommentedConfig ? (CommentedConfig)fileConfiguration : null;
            if (commentedConfig == null) break block0;
            String string = comment;
            commentedConfig.setComment(path, (string != null ? StringsKt.isBlank((CharSequence)string) : false) ? null : comment);
        }
    }

    public void setComments(@NotNull String path, @NotNull List<String> comments) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        Intrinsics.checkNotNullParameter(comments, (String)"comments");
        this.setComment(path, CollectionsKt.joinToString$default((Iterable)comments, (CharSequence)"\n", null, null, (int)0, null, null, (int)62, null));
    }

    @NotNull
    public Map<String, Object> toMap() {
        throw new IllegalStateException("Unsupported".toString());
    }
}

