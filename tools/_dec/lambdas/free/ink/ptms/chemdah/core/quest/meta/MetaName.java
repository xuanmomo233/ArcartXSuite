/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.meta;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.meta.Meta;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Id(id="name")
@Option(type=Option.Type.TEXT)
@MetaInfo(name="\u663e\u793a\u540d\u79f0\u7ec4\u4ef6", description={"\u7528\u4e8e\u8bbe\u7f6e\u4efb\u52a1\u6216\u6761\u76ee\u7684\u663e\u793a\u540d\u79f0", "\u652f\u6301\u989c\u8272\u4ee3\u7801", "\u6761\u76ee\u4f1a\u7ee7\u627f\u4efb\u52a1\u7684\u540d\u79f0"}, alias={"\u540d\u79f0", "\u6807\u9898", "\u663e\u793a\u540d"}, params={@ParamInfo(name="name", type="string", required=false, description="\u663e\u793a\u540d\u79f0\u6587\u672c\uff0c\u652f\u6301\u989c\u8272\u4ee3\u7801\uff08& \u7b26\u53f7\uff09")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\b\b\u0007\u0018\u0000 \f2\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u0001:\u0001\fB\u0017\u0012\b\u0010\u0003\u001a\u0004\u0018\u00010\u0002\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u001c\u0010\u0007\u001a\u0004\u0018\u00010\u0002X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\b\u0010\t\"\u0004\b\n\u0010\u000b\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/core/quest/meta/MetaName;", "Link/ptms/chemdah/core/quest/meta/Meta;", "", "source", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Ljava/lang/String;Link/ptms/chemdah/core/quest/QuestContainer;)V", "displayName", "getDisplayName", "()Ljava/lang/String;", "setDisplayName", "(Ljava/lang/String;)V", "Companion", "Chemdah"})
public final class MetaName
extends Meta<String> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @Nullable
    private String displayName;

    public MetaName(@Nullable String source, @NotNull QuestContainer questContainer) {
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        super(source, questContainer);
        this.displayName = source;
    }

    @Nullable
    public final String getDisplayName() {
        return this.displayName;
    }

    public final void setDisplayName(@Nullable String string) {
        this.displayName = string;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0014\u0010\u0003\u001a\u00020\u0004*\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0007J \u0010\u0003\u001a\u00020\u0004*\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\tJ\u0014\u0010\n\u001a\u0004\u0018\u00010\u0004*\u00020\u00052\u0006\u0010\b\u001a\u00020\tJ\u001c\u0010\u000b\u001a\u00020\f*\u00020\u00052\u0006\u0010\b\u001a\u00020\t2\b\u0010\r\u001a\u0004\u0018\u00010\u0004\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/core/quest/meta/MetaName$Companion;", "", "()V", "displayName", "", "Link/ptms/chemdah/core/quest/QuestContainer;", "colored", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "displayNameOverride", "setDisplayNameOverride", "", "name", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nMetaName.kt\nKotlin\n*S Kotlin\n*F\n+ 1 MetaName.kt\nink/ptms/chemdah/core/quest/meta/MetaName$Companion\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,107:1\n1#2:108\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final String displayName(@NotNull QuestContainer $this$displayName, boolean colored) {
            Intrinsics.checkNotNullParameter((Object)$this$displayName, (String)"<this>");
            return this.displayName($this$displayName, colored, null);
        }

        public static /* synthetic */ String displayName$default(Companion companion, QuestContainer questContainer, boolean bl, int n, Object object) {
            if ((n & 1) != 0) {
                bl = true;
            }
            return companion.displayName(questContainer, bl);
        }

        @NotNull
        public final String displayName(@NotNull QuestContainer $this$displayName, boolean colored, @Nullable PlayerProfile profile) {
            String override;
            Intrinsics.checkNotNullParameter((Object)$this$displayName, (String)"<this>");
            if (profile != null && (override = this.displayNameOverride($this$displayName, profile)) != null) {
                return override;
            }
            Object object = (MetaName)$this$displayName.meta("name");
            if (object == null || (object = ((MetaName)object).getDisplayName()) == null) {
                object = $this$displayName instanceof Task ? this.displayName(((Task)$this$displayName).getTemplate(), colored, profile) : $this$displayName.getId();
            }
            Object displayName = object;
            return colored ? UtilKt.colored((String)displayName) : displayName;
        }

        public static /* synthetic */ String displayName$default(Companion companion, QuestContainer questContainer, boolean bl, PlayerProfile playerProfile2, int n, Object object) {
            if ((n & 1) != 0) {
                bl = true;
            }
            if ((n & 2) != 0) {
                playerProfile2 = null;
            }
            return companion.displayName(questContainer, bl, playerProfile2);
        }

        @Nullable
        public final String displayNameOverride(@NotNull QuestContainer $this$displayNameOverride, @NotNull PlayerProfile profile) {
            String string;
            Intrinsics.checkNotNullParameter((Object)$this$displayNameOverride, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            QuestContainer questContainer = $this$displayNameOverride;
            if (questContainer instanceof Task) {
                Data data2 = profile.dataOperator((Task)$this$displayNameOverride).get("display-name");
                string = data2 != null ? data2.toString() : null;
            } else if (questContainer instanceof Template) {
                Object v2;
                block6: {
                    Iterable iterable = PlayerProfile.getQuests$default(profile, false, 1, null);
                    for (Object t : iterable) {
                        Quest it = (Quest)t;
                        boolean bl = false;
                        if (!Intrinsics.areEqual((Object)it.getId(), (Object)$this$displayNameOverride.getId())) continue;
                        v2 = t;
                        break block6;
                    }
                    v2 = null;
                }
                Quest quest2 = v2;
                if (quest2 == null) {
                    return null;
                }
                Quest quest3 = quest2;
                Data data3 = quest3.getPersistentDataContainer().get("display-name");
                string = data3 != null ? data3.toString() : null;
            } else {
                string = null;
            }
            return string;
        }

        public final void setDisplayNameOverride(@NotNull QuestContainer $this$setDisplayNameOverride, @NotNull PlayerProfile profile, @Nullable String name) {
            Intrinsics.checkNotNullParameter((Object)$this$setDisplayNameOverride, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            QuestContainer questContainer = $this$setDisplayNameOverride;
            if (questContainer instanceof Task) {
                profile.dataOperator((Task)$this$setDisplayNameOverride).set("display-name", name);
            } else if (questContainer instanceof Template) {
                Object v0;
                block7: {
                    Iterable iterable = PlayerProfile.getQuests$default(profile, false, 1, null);
                    for (Object t : iterable) {
                        Quest it = (Quest)t;
                        boolean bl = false;
                        if (!Intrinsics.areEqual((Object)it.getId(), (Object)$this$setDisplayNameOverride.getId())) continue;
                        v0 = t;
                        break block7;
                    }
                    v0 = null;
                }
                Quest quest2 = v0;
                if (quest2 == null) {
                    return;
                }
                Quest quest3 = quest2;
                if (name != null) {
                    quest3.getPersistentDataContainer().set("display-name", name);
                } else {
                    quest3.getPersistentDataContainer().remove("display-name");
                }
            }
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

