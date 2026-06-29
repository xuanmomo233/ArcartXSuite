/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.Option;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.TemplateGroup;
import ink.ptms.chemdah.core.quest.meta.MetaType;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J.\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\n\u0010\u000b\u001a\u00060\fj\u0002`\rH\u0016J\u0016\u0010\u000e\u001a\u00060\fj\u0002`\r2\b\u0010\t\u001a\u0004\u0018\u00010\nH\u0016J,\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\b\u0010\u0011\u001a\u0004\u0018\u00010\nH\u0016J\u001e\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\b2\f\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\b0\u0016H\u0016\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/core/quest/CoreConfigDeserializer;", "", "()V", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "file", "Ljava/io/File;", "key", "", "section", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "option", "Link/ptms/chemdah/core/conversation/Option;", "Link/ptms/chemdah/core/conversation/ConversationOption;", "conversationOption", "template", "Link/ptms/chemdah/core/quest/Template;", "fileOption", "templateGroup", "Link/ptms/chemdah/core/quest/TemplateGroup;", "id", "list", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nCoreConfigDeserializer.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CoreConfigDeserializer.kt\nink/ptms/chemdah/core/quest/CoreConfigDeserializer\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,82:1\n1855#2:83\n766#2:84\n857#2,2:85\n1856#2:87\n1#3:88\n*S KotlinDebug\n*F\n+ 1 CoreConfigDeserializer.kt\nink/ptms/chemdah/core/quest/CoreConfigDeserializer\n*L\n42#1:83\n46#1:84\n46#1:85,2\n42#1:87\n*E\n"})
public class CoreConfigDeserializer {
    @Nullable
    public Template template(@NotNull File file, @NotNull String key, @NotNull ConfigurationSection section, @Nullable ConfigurationSection fileOption) {
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)section, (String)"section");
        return new Template(key, section, fileOption);
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public TemplateGroup templateGroup(@NotNull String id2, @NotNull List<String> list2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter(list2, (String)"list");
        HashSet groupList = new HashSet();
        Iterable $this$forEach$iv = list2;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            String it = (String)element$iv;
            boolean bl = false;
            if (StringsKt.startsWith$default((String)it, (String)"type:", (boolean)false, (int)2, null)) {
                void $this$filterTo$iv$iv;
                Collection collection = groupList;
                Collection<Template> collection2 = ChemdahAPI.INSTANCE.getQuestTemplate().values();
                Intrinsics.checkNotNullExpressionValue(collection2, (String)"ChemdahAPI.questTemplate.values");
                Iterable $this$filter$iv = collection2;
                boolean $i$f$filter = false;
                Iterable iterable = $this$filter$iv;
                Collection destination$iv$iv = new ArrayList();
                boolean $i$f$filterTo = false;
                for (Object element$iv$iv : $this$filterTo$iv$iv) {
                    Template t = (Template)element$iv$iv;
                    boolean bl2 = false;
                    Intrinsics.checkNotNullExpressionValue((Object)t, (String)"t");
                    if (!MetaType.Companion.type(t).contains(StringsKt.substringAfter$default((String)it, (String)"type:", null, (int)2, null))) continue;
                    destination$iv$iv.add(element$iv$iv);
                }
                Iterable iterable2 = (List)destination$iv$iv;
                CollectionsKt.addAll((Collection)collection, (Iterable)iterable2);
                continue;
            }
            Template template = ChemdahAPI.INSTANCE.getQuestTemplate(it);
            if (template == null) continue;
            ((Collection)groupList).add(template);
        }
        return new TemplateGroup(id2, groupList);
    }

    @Nullable
    public Conversation conversation(@NotNull File file, @NotNull String key, @NotNull ConfigurationSection section, @NotNull Option option) {
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        Intrinsics.checkNotNullParameter((Object)section, (String)"section");
        Intrinsics.checkNotNullParameter((Object)option, (String)"option");
        return new Conversation(section.getName(), file, section, option);
    }

    @NotNull
    public Option conversationOption(@Nullable ConfigurationSection section) {
        Option option;
        ConfigurationSection configurationSection = section;
        if (configurationSection != null) {
            ConfigurationSection it = configurationSection;
            boolean bl = false;
            option = new Option(it, null, null, 6, null);
        } else {
            option = Option.Companion.getDefault();
        }
        return option;
    }
}

