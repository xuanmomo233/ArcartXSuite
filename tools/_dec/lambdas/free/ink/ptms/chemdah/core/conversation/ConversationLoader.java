/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.io.FileKt
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.util.LocaleKt
 *  ink.ptms.chemdah.taboolib.common5.FileWatcher
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.configuration.ConfigNode
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  ink.ptms.chemdah.taboolib.platform.bukkit.Parallel
 *  kotlin.Metadata
 *  kotlin1822.TuplesKt
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.io.FilesKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.sequences.Sequence
 *  kotlin1822.sequences.SequencesKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.api.event.collect.PluginReloadEvent;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.ConversationLoader;
import ink.ptms.chemdah.core.conversation.ConversationSwitch;
import ink.ptms.chemdah.core.conversation.Option;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.core.conversation.theme.ThemeChat;
import ink.ptms.chemdah.core.conversation.theme.ThemeChest;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.io.FileKt;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
import ink.ptms.chemdah.taboolib.common5.FileWatcher;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.ConfigNode;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.platform.bukkit.Parallel;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kotlin.Metadata;
import kotlin1822.TuplesKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.io.FilesKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.sequences.Sequence;
import kotlin1822.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00008\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0011\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\t\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0005\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0014\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u00170\u00162\u0006\u0010\u0018\u001a\u00020\u0019J\b\u0010\u001a\u001a\u00020\u001bH\u0007J\u001e\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u00170\u00162\u0006\u0010\u001d\u001a\u00020\u00192\u0006\u0010\u001e\u001a\u00020\u0019H\u0002J\b\u0010\u001f\u001a\u00020\u001bH\u0003R\"\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0086\u000e\u00a2\u0006\u0010\n\u0002\u0010\n\u001a\u0004\b\u0006\u0010\u0007\"\u0004\b\b\u0010\tR\u001e\u0010\u000b\u001a\u00020\f8\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\r\"\u0004\b\u000e\u0010\u000fR\u001a\u0010\u0010\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014\u00a8\u0006 "}, d2={"Link/ptms/chemdah/core/conversation/ConversationLoader;", "", "()V", "allowExtension", "", "", "getAllowExtension", "()[Ljava/lang/String;", "setAllowExtension", "([Ljava/lang/String;)V", "[Ljava/lang/String;", "isDisableFileWatcher", "", "()Z", "setDisableFileWatcher", "(Z)V", "optionKey", "getOptionKey", "()Ljava/lang/String;", "setOptionKey", "(Ljava/lang/String;)V", "load", "", "Link/ptms/chemdah/core/conversation/Conversation;", "file", "Ljava/io/File;", "loadAll", "", "loadFromFile", "rootFile", "configFile", "watch", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConversationLoader.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConversationLoader.kt\nink/ptms/chemdah/core/conversation/ConversationLoader\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 4 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 5 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,165:1\n1549#2:166\n1620#2,3:167\n1855#2,2:170\n1477#2:172\n1502#2,3:173\n1505#2,3:183\n766#2:188\n857#2,2:189\n1603#2,9:191\n1855#2:200\n1856#2:202\n1612#2:203\n361#3,7:176\n215#4,2:186\n1#5:201\n*S KotlinDebug\n*F\n+ 1 ConversationLoader.kt\nink/ptms/chemdah/core/conversation/ConversationLoader\n*L\n85#1:166\n85#1:167,3\n86#1:170,2\n94#1:172\n94#1:173,3\n94#1:183,3\n134#1:188\n134#1:189,2\n135#1:191,9\n135#1:200\n135#1:202\n135#1:203\n94#1:176,7\n94#1:186,2\n135#1:201\n*E\n"})
public final class ConversationLoader {
    @NotNull
    public static final ConversationLoader INSTANCE = new ConversationLoader();
    @NotNull
    private static String optionKey = "__option__";
    @NotNull
    private static String[] allowExtension;
    @ConfigNode(value="default-conversation.disable-file-watcher")
    private static boolean isDisableFileWatcher;

    private ConversationLoader() {
    }

    @NotNull
    public final String getOptionKey() {
        return optionKey;
    }

    public final void setOptionKey(@NotNull String string) {
        Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
        optionKey = string;
    }

    @NotNull
    public final String[] getAllowExtension() {
        return allowExtension;
    }

    public final void setAllowExtension(@NotNull String[] stringArray) {
        Intrinsics.checkNotNullParameter((Object)stringArray, (String)"<set-?>");
        allowExtension = stringArray;
    }

    public final boolean isDisableFileWatcher() {
        return isDisableFileWatcher;
    }

    public final void setDisableFileWatcher(boolean bl) {
        isDisableFileWatcher = bl;
    }

    @Parallel(id="chemdah_conversation_watch", runOn=LifeCycle.ACTIVE)
    private final void watch() {
        if (isDisableFileWatcher) {
            return;
        }
        FileWatcher.INSTANCE.addSimpleListener(new File(IOKt.getDataFolder(), "core/conversation"), ConversationLoader::watch$lambda$0);
    }

    /*
     * WARNING - void declaration
     */
    @Awake(value=LifeCycle.ACTIVE)
    public final void loadAll() {
        void $this$groupByTo$iv$iv;
        Conversation it;
        Object $this$mapTo$iv$iv;
        void $this$map$iv;
        File file = new File(IOKt.getDataFolder(), "core/conversation");
        if (FileKt.notfound((File)file)) {
            IOKt.releaseResourceFile$default((String)"core/conversation/example.yml", (boolean)false, null, (int)6, null);
        }
        ConversationSwitch.Companion.getSwitchMap().clear();
        long start = System.currentTimeMillis();
        List<Conversation> conversations = this.load(file);
        ChemdahAPI.INSTANCE.getConversation().clear();
        Iterable iterable = conversations;
        Map map = ChemdahAPI.INSTANCE.getConversation();
        boolean $i$f$map = false;
        Iterator iterator = $this$map$iv;
        Object destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        Iterator iterator2 = $this$mapTo$iv$iv.iterator();
        while (iterator2.hasNext()) {
            Object item$iv$iv = iterator2.next();
            Conversation conversation2 = (Conversation)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(TuplesKt.to((Object)it.getId(), (Object)it));
        }
        MapsKt.putAll((Map)map, (Iterable)((List)destination$iv$iv));
        Collection<Theme<?>> collection = ChemdahAPI.INSTANCE.getConversationTheme().values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"ChemdahAPI.conversationTheme.values");
        Object[] $this$forEach$iv = (Object[])collection;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Theme it2 = (Theme)element$iv;
            boolean bl = false;
            it2.reloadConfig();
        }
        $this$forEach$iv = new Object[]{LocaleKt.t((String)("\n                \u5df2\u52a0\u8f7d " + ChemdahAPI.INSTANCE.getConversation().size() + " \u9879\u5bf9\u8bdd, \u8017\u65f6: " + (System.currentTimeMillis() - start) + "ms\n                " + ChemdahAPI.INSTANCE.getConversation().size() + " conversations loaded, " + (System.currentTimeMillis() - start) + "ms\n            "))};
        IOKt.info((Object[])$this$forEach$iv);
        Object $this$groupBy$iv = conversations;
        boolean $i$f$groupBy = false;
        $this$mapTo$iv$iv = $this$groupBy$iv;
        destination$iv$iv = new LinkedHashMap();
        boolean $i$f$groupByTo = false;
        for (Object element$iv$iv : $this$groupByTo$iv$iv) {
            Object object;
            it = (Conversation)element$iv$iv;
            boolean bl = false;
            String key$iv$iv = it.getId();
            Object $this$getOrPut$iv$iv$iv = destination$iv$iv;
            boolean $i$f$getOrPut = false;
            Object value$iv$iv$iv = $this$getOrPut$iv$iv$iv.get(key$iv$iv);
            if (value$iv$iv$iv == null) {
                boolean bl2 = false;
                List answer$iv$iv$iv = new ArrayList();
                $this$getOrPut$iv$iv$iv.put(key$iv$iv, answer$iv$iv$iv);
                object = answer$iv$iv$iv;
            } else {
                object = value$iv$iv$iv;
            }
            List list$iv$iv = (List)object;
            list$iv$iv.add(element$iv$iv);
        }
        $this$groupBy$iv = destination$iv$iv;
        $i$f$forEach = false;
        iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Object element$iv;
            Object object = element$iv = iterator.next();
            boolean bl = false;
            String id2 = (String)object.getKey();
            List c = (List)object.getValue();
            if (c.size() <= 1) continue;
            Object[] objectArray = new Object[]{LocaleKt.t((String)("\n                        \u6709 " + c.size() + " \u4e2a\u5bf9\u8bdd\u4f7f\u7528\u4e86\u91cd\u590d\u7684 ID: " + id2 + "\n                        There are " + c.size() + " conversations using duplicate id: " + id2 + "\n                    "))};
            IOKt.warning((Object[])objectArray);
        }
        new PluginReloadEvent.Conversation().call();
    }

    @NotNull
    public final List<Conversation> load(@NotNull File file) {
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        List<Conversation> list2 = SequencesKt.toList((Sequence)SequencesKt.filter((Sequence)((Sequence)FilesKt.walk$default((File)file, null, (int)1, null)), (Function1)load.1.INSTANCE)).parallelStream().flatMap(arg_0 -> ConversationLoader.load$lambda$5((Function1)new Function1<File, Stream<? extends Conversation>>(file){
            final /* synthetic */ File $file;
            {
                this.$file = $file;
                super(1);
            }

            public final Stream<? extends Conversation> invoke(File it) {
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                return ConversationLoader.access$loadFromFile(ConversationLoader.INSTANCE, this.$file, it).stream();
            }
        }, arg_0)).collect(Collectors.toList());
        Intrinsics.checkNotNullExpressionValue(list2, (String)"file: File): List<Conver\u2026lect(Collectors.toList())");
        return list2;
    }

    /*
     * WARNING - void declaration
     */
    private final List<Conversation> loadFromFile(File rootFile, File configFile) {
        List list2;
        try {
            void $this$mapNotNullTo$iv$iv;
            void $this$mapNotNull$iv;
            void $this$filterTo$iv$iv;
            Iterable $this$filter$iv;
            Configuration conf = Configuration.Companion.loadFromFile$default((Configuration.Companion)Configuration.Companion, (File)configFile, null, (boolean)false, (int)2, null);
            Option option = ChemdahAPI.INSTANCE.getCoreConfigDeserializer().conversationOption(conf.getConfigurationSection(optionKey));
            Iterable iterable = conf.getKeys(false);
            boolean $i$f$filter = false;
            void var7_9 = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                String key = (String)element$iv$iv;
                boolean bl = false;
                if (!(!Intrinsics.areEqual((Object)key, (Object)optionKey) && conf.isConfigurationSection(key))) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            $this$filter$iv = (List)destination$iv$iv;
            boolean $i$f$mapNotNull = false;
            $this$filterTo$iv$iv = $this$mapNotNull$iv;
            destination$iv$iv = new ArrayList();
            boolean $i$f$mapNotNullTo = false;
            void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
            boolean $i$f$forEach = false;
            Iterator iterator = $this$forEach$iv$iv$iv.iterator();
            while (iterator.hasNext()) {
                Conversation conversation2;
                ConfigurationSection section;
                Object element$iv$iv$iv;
                Object element$iv$iv = element$iv$iv$iv = iterator.next();
                boolean bl = false;
                String key = (String)element$iv$iv;
                boolean bl2 = false;
                Intrinsics.checkNotNull((Object)conf.getConfigurationSection(key));
                if (new ConversationEvents.Load(rootFile, option, section).call()) {
                    Conversation conversation3;
                    try {
                        conversation3 = ChemdahAPI.INSTANCE.getCoreConfigDeserializer().conversation(configFile, key, section, option);
                    }
                    catch (Exception e) {
                        Object[] objectArray = new Object[]{LocaleKt.t((String)("\n                                    \u5bf9\u8bdd " + key + " \u52a0\u8f7d\u5931\u8d25\uff1a" + e.getMessage() + "\n                                    Conversation " + key + " load failed: " + e.getMessage() + "\n                                "))};
                        IOKt.warning((Object[])objectArray);
                        e.printStackTrace();
                        conversation3 = null;
                    }
                    conversation2 = conversation3;
                } else {
                    conversation2 = null;
                }
                if (conversation2 == null) continue;
                Conversation it$iv$iv = conversation2;
                boolean bl3 = false;
                destination$iv$iv.add(it$iv$iv);
            }
            list2 = (List)destination$iv$iv;
        }
        catch (Exception e) {
            Object[] objectArray = new Object[]{LocaleKt.t((String)("\n                    \u914d\u7f6e\u6587\u4ef6 " + configFile.getName() + " \u52a0\u8f7d\u5931\u8d25\uff1a" + e.getMessage() + "\n                    Config file " + configFile.getName() + " load failed: " + e.getMessage() + "\n                "))};
            IOKt.warning((Object[])objectArray);
            e.printStackTrace();
            list2 = CollectionsKt.emptyList();
        }
        return list2;
    }

    private static final void watch$lambda$0(File it) {
        Object[] objectArray = new Object[]{LocaleKt.t((String)"\n                    \u5bf9\u8bdd\u6587\u4ef6\u53d1\u751f\u53d8\u52a8\uff0c\u6b63\u5728\u91cd\u8f7d...\n                    Conversation file changed, reloading...\n                ")};
        IOKt.info((Object[])objectArray);
        INSTANCE.loadAll();
    }

    private static final Stream load$lambda$5(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (Stream)$tmp0.invoke(p0);
    }

    public static final /* synthetic */ List access$loadFromFile(ConversationLoader $this, File rootFile, File configFile) {
        return $this.loadFromFile(rootFile, configFile);
    }

    static {
        String[] stringArray = new String[]{"yaml", "yml", "json", "toml"};
        allowExtension = stringArray;
        isDisableFileWatcher = true;
        ChemdahAPI.INSTANCE.addConversationTheme("chat", ThemeChat.INSTANCE);
        ChemdahAPI.INSTANCE.addConversationTheme("chest", ThemeChest.INSTANCE);
    }
}

