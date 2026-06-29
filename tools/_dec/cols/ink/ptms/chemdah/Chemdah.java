/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah;

import ink.ptms.chemdah.AdyeshachChecker;
import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.database.Database;
import ink.ptms.chemdah.taboolib.common.io.ProjectScannerKt;
import ink.ptms.chemdah.taboolib.common.platform.PlatformFactory;
import ink.ptms.chemdah.taboolib.common.platform.Plugin;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.CommonKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformIO;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.common.util.StringKt;
import ink.ptms.chemdah.taboolib.module.configuration.Config;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.lang.LangKt;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import ink.ptms.chemdah.taboolib.platform.BukkitPlugin;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000N\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J$\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00142\u0012\u0010\u0016\u001a\u000e\u0012\u0004\u0012\u00020\u0018\u0012\u0004\u0012\u00020\u00190\u0017H\u0002J\b\u0010\u001a\u001a\u00020\u001bH\u0016J\b\u0010\u001c\u001a\u00020\u001bH\u0016J\u001d\u0010\u001d\u001a\u00020\u001b\"\b\b\u0000\u0010\u001e*\u00020\u001f2\u0006\u0010 \u001a\u0002H\u001e\u00a2\u0006\u0002\u0010!R\u0011\u0010\u0003\u001a\u00020\u00048F\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006R \u0010\t\u001a\u00020\b2\u0006\u0010\u0007\u001a\u00020\b8\u0006@BX\u0087.\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR \u0010\f\u001a\u00070\r\u00a2\u0006\u0002\b\u000e8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0011\u0010\u0012\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\""}, d2={"Link/ptms/chemdah/Chemdah;", "Link/ptms/chemdah/taboolib/common/platform/Plugin;", "()V", "api", "Link/ptms/chemdah/api/ChemdahAPI;", "getApi", "()Link/ptms/chemdah/api/ChemdahAPI;", "<set-?>", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "conf", "getConf", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "plugin", "Link/ptms/chemdah/taboolib/platform/BukkitPlugin;", "Lorg/jetbrains/annotations/NotNull;", "getPlugin", "()Link/ptms/chemdah/taboolib/platform/BukkitPlugin;", "plugin$delegate", "Lkotlin1822/Lazy;", "newPrintStream", "Ljava/io/PrintStream;", "stream", "predicate", "Lkotlin1822/Function1;", "", "", "onActive", "", "onLoad", "registerDatabaseImpl", "T", "Link/ptms/chemdah/core/database/Database;", "database", "(Link/ptms/chemdah/core/database/Database;)V", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nChemdah.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Chemdah.kt\nink/ptms/chemdah/Chemdah\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 Plugin.kt\ntaboolib/common/platform/function/PluginKt\n+ 4 PlatformFactory.kt\ntaboolib/common/platform/PlatformFactory\n*L\n1#1,69:1\n1855#2:70\n1856#2:73\n16#3:71\n137#4:72\n148#4,2:74\n*S KotlinDebug\n*F\n+ 1 Chemdah.kt\nink/ptms/chemdah/Chemdah\n*L\n48#1:70\n48#1:73\n49#1:71\n49#1:72\n57#1:74,2\n*E\n"})
public final class Chemdah
extends Plugin {
    @NotNull
    public static final Chemdah INSTANCE = new Chemdah();
    @Config(autoReload=true)
    private static Configuration conf;
    @NotNull
    private static final Lazy plugin$delegate;

    private Chemdah() {
    }

    @NotNull
    public final Configuration getConf() {
        Configuration configuration = conf;
        if (configuration != null) {
            return configuration;
        }
        Intrinsics.throwUninitializedPropertyAccessException((String)"conf");
        return null;
    }

    @NotNull
    public final ChemdahAPI getApi() {
        return ChemdahAPI.INSTANCE;
    }

    @NotNull
    public final BukkitPlugin getPlugin() {
        Lazy lazy = plugin$delegate;
        return (BukkitPlugin)((Object)lazy.getValue());
    }

    @Override
    public void onLoad() {
        if (MinecraftVersion.INSTANCE.getMajorLegacy() < 10900 || !MinecraftVersion.INSTANCE.isSupported()) {
            LangKt.sendLang((ProxyCommandSender)AdapterKt.console(), (String)"not-support", (Object[])new Object[0]);
            CommonKt.disablePlugin();
        }
        if (AdyeshachChecker.INSTANCE.isLegacyVersion()) {
            LangKt.sendLang((ProxyCommandSender)AdapterKt.console(), (String)"not-support-ady", (Object[])new Object[0]);
            CommonKt.disablePlugin();
        }
        PrintStream printStream = System.err;
        Intrinsics.checkNotNullExpressionValue((Object)printStream, (String)"err");
        System.setErr(this.newPrintStream(printStream, (Function1<? super String, Boolean>)((Function1)onLoad.1.INSTANCE)));
        System.setProperty("nightconfig.preserveInsertionOrder", "true");
    }

    @Override
    public void onActive() {
        block3: {
            Object object = (byte[])ProjectScannerKt.getRunningResourcesInJar().get("FREE.txt");
            if (object == null) break block3;
            String string = StringsKt.decodeToString((byte[])object);
            object = string;
            if (string != null) {
                List list2 = StringsKt.lines((CharSequence)((CharSequence)object));
                object = list2;
                if (list2 != null) {
                    Iterable $this$forEach$iv = (Iterable)object;
                    boolean $i$f$forEach = false;
                    for (Object element$iv : $this$forEach$iv) {
                        String line = (String)element$iv;
                        boolean bl = false;
                        ProxyCommandSender proxyCommandSender = AdapterKt.console();
                        StringBuilder stringBuilder = new StringBuilder().append("[Chemdah] \u00a7b");
                        Object[] objectArray = new Object[1];
                        boolean $i$f$getPluginVersion = false;
                        PlatformFactory this_$iv$iv = PlatformFactory.INSTANCE;
                        boolean $i$f$getService = false;
                        String string2 = PlatformIO.class.getName();
                        Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"T::class.java.name");
                        objectArray[0] = ((PlatformIO)this_$iv$iv.getService(string2)).getPluginVersion();
                        proxyCommandSender.sendMessage(stringBuilder.append(StringKt.replaceWithOrder((String)line, (Object[])objectArray)).toString());
                    }
                }
            }
        }
    }

    public final <T extends Database> void registerDatabaseImpl(@NotNull T database) {
        Intrinsics.checkNotNullParameter(database, (String)"database");
        PlatformFactory this_$iv = PlatformFactory.INSTANCE;
        boolean $i$f$registerAPI = false;
        Map map = this_$iv.getAwokenMap();
        String string = Database.class.getName();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"T::class.java.name");
        map.put(string, database);
    }

    private final PrintStream newPrintStream(PrintStream stream, Function1<? super String, Boolean> predicate) {
        return new PrintStream(stream, predicate){
            final /* synthetic */ Function1<String, Boolean> $predicate;
            {
                this.$predicate = $predicate;
                super($stream);
            }

            public void println(@Nullable String x) {
                String string = x;
                if (string == null) {
                    return;
                }
                if (((Boolean)this.$predicate.invoke((Object)string)).booleanValue()) {
                    super.println(x);
                }
            }
        };
    }

    static {
        plugin$delegate = LazyMakerKt.unsafeLazy((Function0)plugin.2.INSTANCE);
    }
}

