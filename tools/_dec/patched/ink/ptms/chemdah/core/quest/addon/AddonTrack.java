/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.common.util.LazyMakerKt
 *  ink.ptms.chemdah.taboolib.common.util.ResettableLazy
 *  ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.kether.KetherFunction
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell$VariableMap
 *  ink.ptms.chemdah.taboolib.module.lang.inline.InlineLanguageKt
 *  ink.ptms.chemdah.taboolib.module.lang.inline.TranslatedString
 *  ink.ptms.chemdah.taboolib.module.lang.inline.TranslatedStringList
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.IntIterator
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.IntRange
 *  kotlin1822.text.StringsKt
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.AdyeshachChecker;
import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.TrackCenterEvent;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.addon.AddonUI;
import ink.ptms.chemdah.core.quest.addon.data.NullTrackCenter;
import ink.ptms.chemdah.core.quest.addon.data.TrackBeacon;
import ink.ptms.chemdah.core.quest.addon.data.TrackCenter;
import ink.ptms.chemdah.core.quest.addon.data.TrackLandmark;
import ink.ptms.chemdah.core.quest.addon.data.TrackNavigation;
import ink.ptms.chemdah.core.quest.addon.data.TrackScoreboard;
import ink.ptms.chemdah.core.quest.addon.data.TrackToAdyeshach;
import ink.ptms.chemdah.core.quest.addon.data.TrackToLocation;
import ink.ptms.chemdah.core.quest.meta.MetaName;
import ink.ptms.chemdah.module.party.PartySystem;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.common.util.ResettableLazy;
import ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.lang.inline.InlineLanguageKt;
import ink.ptms.chemdah.taboolib.module.lang.inline.TranslatedString;
import ink.ptms.chemdah.taboolib.module.lang.inline.TranslatedStringList;
import ink.ptms.chemdah.util.StringKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.IntIterator;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.IntRange;
import kotlin1822.text.StringsKt;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Id(id="track")
@Option(type=Option.Type.SECTION)
@MetaInfo(name="\u4efb\u52a1\u8ffd\u8e2a\u7ec4\u4ef6", description={"\u7528\u4e8e\u8ffd\u8e2a\u4efb\u52a1\u76ee\u6807\u4f4d\u7f6e\u5e76\u63d0\u4f9b\u8def\u5f84\u6307\u5f15", "\u652f\u6301\u4fe1\u6807\u3001\u5730\u6807\u3001\u5bfc\u822a\u548c\u8bb0\u5206\u677f\u663e\u793a", "\u73a9\u5bb6\u540c\u65f6\u53ea\u80fd\u8ffd\u8e2a\u4e00\u4e2a\u4efb\u52a1"}, alias={"\u8ffd\u8e2a", "\u5bfc\u822a", "\u8def\u5f84\u6307\u5f15"}, params={@ParamInfo(name="center", type="string", required=false, description="\u8ffd\u8e2a\u76ee\u6807\u4f4d\u7f6e\uff1aworld x y z\uff08\u5750\u6807\uff09 \u6216 adyeshach <\u5b9e\u4f53ID>\uff08NPC\uff09"), @ParamInfo(name="name", type="string", required=false, description="\u8ffd\u8e2a\u663e\u793a\u540d\u79f0\uff08\u652f\u6301\u8bed\u8a00\u6587\u4ef6\uff09"), @ParamInfo(name="description", type="list", required=false, description="\u8ffd\u8e2a\u63cf\u8ff0\u6587\u672c\u5217\u8868\uff08\u652f\u6301\u8bed\u8a00\u6587\u4ef6\uff09"), @ParamInfo(name="message", type="list", required=false, description="\u5f00\u542f\u8ffd\u8e2a\u65f6\u7684\u63d0\u793a\u4fe1\u606f\uff08\u652f\u6301\u8bed\u8a00\u6587\u4ef6\uff09"), @ParamInfo(name="beacon", type="boolean", required=false, description="\u662f\u5426\u542f\u7528\u4fe1\u6807\u8ffd\u8e2a\uff08\u7c92\u5b50\u67f1\u6548\u679c\uff09"), @ParamInfo(name="beacon-option", type="section", required=false, description="\u4fe1\u6807\u8ffd\u8e2a\u914d\u7f6e\u8282"), @ParamInfo(name="beacon-option.type", type="string", required=false, description="\u7c92\u5b50\u7c7b\u578b\uff0c\u5982 FLAME\u3001CRIT \u7b49"), @ParamInfo(name="beacon-option.size", type="number", required=false, description="\u7c92\u5b50\u5bbd\u5ea6\u5927\u5c0f"), @ParamInfo(name="beacon-option.count", type="number", required=false, description="\u7c92\u5b50\u6570\u91cf"), @ParamInfo(name="beacon-option.distance", type="number", required=false, description="\u4fe1\u6807\u663e\u793a\u8ddd\u79bb\uff08\u65b9\u5757\uff09"), @ParamInfo(name="beacon-option.fixed", type="boolean", required=false, description="\u662f\u5426\u56fa\u5b9a\u4f4d\u7f6e\uff08false \u5219\u8ddf\u968f\u73a9\u5bb6\u89c6\u89d2\u52a8\u6001\u8c03\u6574\uff09"), @ParamInfo(name="beacon-option.period", type="number", required=false, description="\u7c92\u5b50\u5237\u65b0\u5468\u671f\uff08tick\uff09"), @ParamInfo(name="landmark", type="boolean", required=false, description="\u662f\u5426\u542f\u7528\u5730\u6807\u8ffd\u8e2a\uff08\u76ee\u6807\u70b9\u6807\u8bb0\uff09"), @ParamInfo(name="landmark-option", type="section", required=false, description="\u5730\u6807\u8ffd\u8e2a\u914d\u7f6e\u8282"), @ParamInfo(name="landmark-option.hide-near", type="boolean", required=false, description="\u9760\u8fd1\u76ee\u6807\u65f6\u662f\u5426\u9690\u85cf\u5730\u6807"), @ParamInfo(name="landmark-option.distance", type="number", required=false, description="\u5730\u6807\u663e\u793a\u8ddd\u79bb\uff08\u65b9\u5757\uff09"), @ParamInfo(name="landmark-option.content", type="list", required=false, description="\u5730\u6807\u663e\u793a\u5185\u5bb9\uff0c\u652f\u6301\u53d8\u91cf\uff1a{distance} {name} {description}"), @ParamInfo(name="navigation", type="boolean", required=false, description="\u662f\u5426\u542f\u7528\u5bfc\u822a\u8ffd\u8e2a\uff08\u8def\u5f84\u6307\u5f15\uff09"), @ParamInfo(name="navigation-option", type="section", required=false, description="\u5bfc\u822a\u8ffd\u8e2a\u914d\u7f6e\u8282"), @ParamInfo(name="navigation-option.sync", type="boolean", required=false, description="\u662f\u5426\u5728\u4e3b\u7ebf\u7a0b\u5bfb\u8def\uff08\u5efa\u8bae false \u4f7f\u7528\u5f02\u6b65\uff09"), @ParamInfo(name="navigation-option.distance", type="number", required=false, description="\u6700\u5927\u5bfb\u8def\u8ddd\u79bb\uff08\u65b9\u5757\uff09"), @ParamInfo(name="navigation-option.type", type="string", required=false, options={"POINT", "ARROW"}, description="\u5bfc\u822a\u663e\u793a\u7c7b\u578b\uff1aPOINT\uff08\u70b9\u72b6\uff09\u6216 ARROW\uff08\u7bad\u5934\uff09"), @ParamInfo(name="navigation-option.point", type="section", required=false, description="\u70b9\u72b6\u5bfc\u822a\u914d\u7f6e\u8282\uff08type=POINT \u65f6\u6709\u6548\uff09"), @ParamInfo(name="navigation-option.point.period", type="number", required=false, description="\u7c92\u5b50\u53d1\u9001\u95f4\u9694\uff08tick\uff09"), @ParamInfo(name="navigation-option.point.type", type="string", required=false, description="\u7c92\u5b50\u7c7b\u578b"), @ParamInfo(name="navigation-option.point.y", type="number", required=false, description="Y\u8f74\u504f\u79fb\u91cf"), @ParamInfo(name="navigation-option.point.size", type="section", required=false, description="\u7c92\u5b50\u5927\u5c0f\u914d\u7f6e"), @ParamInfo(name="navigation-option.point.size.x", type="number", required=false, description="X\u8f74\u5927\u5c0f"), @ParamInfo(name="navigation-option.point.size.y", type="number", required=false, description="Y\u8f74\u5927\u5c0f"), @ParamInfo(name="navigation-option.point.count", type="number", required=false, description="\u7c92\u5b50\u6570\u91cf"), @ParamInfo(name="navigation-option.point.speed", type="number", required=false, description="\u7c92\u5b50\u901f\u5ea6"), @ParamInfo(name="navigation-option.arrow", type="section", required=false, description="\u7bad\u5934\u5bfc\u822a\u914d\u7f6e\u8282\uff08type=ARROW \u65f6\u6709\u6548\uff09"), @ParamInfo(name="navigation-option.arrow.period", type="number", required=false, description="\u7c92\u5b50\u53d1\u9001\u95f4\u9694\uff08tick\uff09"), @ParamInfo(name="navigation-option.arrow.type", type="string", required=false, description="\u7c92\u5b50\u7c7b\u578b"), @ParamInfo(name="navigation-option.arrow.y", type="number", required=false, description="Y\u8f74\u504f\u79fb\u91cf"), @ParamInfo(name="navigation-option.arrow.density", type="number", required=false, description="\u7bad\u5934\u5bc6\u5ea6"), @ParamInfo(name="navigation-option.arrow.length", type="number", required=false, description="\u7bad\u5934\u957f\u5ea6"), @ParamInfo(name="navigation-option.arrow.angle", type="number", required=false, description="\u7bad\u5934\u89d2\u5ea6"), @ParamInfo(name="navigation-option.arrow.speed", type="number", required=false, description="\u7bad\u5934\u901f\u5ea6"), @ParamInfo(name="scoreboard", type="boolean", required=false, description="\u662f\u5426\u542f\u7528\u8bb0\u5206\u677f\u8ffd\u8e2a"), @ParamInfo(name="scoreboard-option", type="section", required=false, description="\u8bb0\u5206\u677f\u8ffd\u8e2a\u914d\u7f6e\u8282"), @ParamInfo(name="scoreboard-option.length", type="number", required=false, description="\u8bb0\u5206\u677f\u5355\u884c\u957f\u5ea6\uff08\u5b57\u7b26\u6570\uff09"), @ParamInfo(name="scoreboard-option.content", type="list", required=false, description="\u8bb0\u5206\u677f\u663e\u793a\u5185\u5bb9\uff0c\u652f\u6301\u53d8\u91cf\uff1a{name} {description}")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000j\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0007\u0018\u0000 >2\u00020\u0001:\u0001>B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J$\u00104\u001a\b\u0012\u0004\u0012\u000206052\u0006\u00107\u001a\u0002082\u0006\u00109\u001a\u00020\u00052\u0006\u0010:\u001a\u00020;J&\u00104\u001a\b\u0012\u0004\u0012\u000206052\u0006\u00107\u001a\u0002082\u0006\u0010<\u001a\u0002062\b\u0010=\u001a\u0004\u0018\u00010\u0014R\u001a\u0010\u0007\u001a\u00020\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u001b\u0010\r\u001a\u00020\u000e8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0011\u0010\u0012\u001a\u0004\b\u000f\u0010\u0010R\u001a\u0010\u0013\u001a\u00020\u0014X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018R\u001a\u0010\u0019\u001a\u00020\u001aX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001b\u0010\u001c\"\u0004\b\u001d\u0010\u001eR\u001a\u0010\u001f\u001a\u00020\u0014X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b \u0010\u0016\"\u0004\b!\u0010\u0018R\u001c\u0010\"\u001a\u0004\u0018\u00010#X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b$\u0010%\"\u0004\b&\u0010'R\u001a\u0010(\u001a\u00020)X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b*\u0010+\"\u0004\b,\u0010-R\u001a\u0010.\u001a\u00020/X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b0\u00101\"\u0004\b2\u00103\u00a8\u0006?"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonTrack;", "Link/ptms/chemdah/core/quest/addon/Addon;", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Link/ptms/chemdah/core/quest/QuestContainer;)V", "beacon", "Link/ptms/chemdah/core/quest/addon/data/TrackBeacon;", "getBeacon", "()Link/ptms/chemdah/core/quest/addon/data/TrackBeacon;", "setBeacon", "(Link/ptms/chemdah/core/quest/addon/data/TrackBeacon;)V", "center", "Link/ptms/chemdah/core/quest/addon/data/TrackCenter;", "getCenter", "()Link/ptms/chemdah/core/quest/addon/data/TrackCenter;", "center$delegate", "Lkotlin1822/Lazy;", "description", "Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedStringList;", "getDescription", "()Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedStringList;", "setDescription", "(Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedStringList;)V", "landmark", "Link/ptms/chemdah/core/quest/addon/data/TrackLandmark;", "getLandmark", "()Link/ptms/chemdah/core/quest/addon/data/TrackLandmark;", "setLandmark", "(Link/ptms/chemdah/core/quest/addon/data/TrackLandmark;)V", "message", "getMessage", "setMessage", "name", "Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedString;", "getName", "()Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedString;", "setName", "(Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedString;)V", "navigation", "Link/ptms/chemdah/core/quest/addon/data/TrackNavigation;", "getNavigation", "()Link/ptms/chemdah/core/quest/addon/data/TrackNavigation;", "setNavigation", "(Link/ptms/chemdah/core/quest/addon/data/TrackNavigation;)V", "scoreboard", "Link/ptms/chemdah/core/quest/addon/data/TrackScoreboard;", "getScoreboard", "()Link/ptms/chemdah/core/quest/addon/data/TrackScoreboard;", "setScoreboard", "(Link/ptms/chemdah/core/quest/addon/data/TrackScoreboard;)V", "formatDescription", "", "", "player", "Lorg/bukkit/entity/Player;", "quest", "line", "Link/ptms/chemdah/core/quest/addon/data/TrackScoreboard$Line;", "questNode", "def", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAddonTrack.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonTrack.kt\nink/ptms/chemdah/core/quest/addon/AddonTrack\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,336:1\n1360#2:337\n1446#2,2:338\n1549#2:340\n1620#2,3:341\n1448#2,3:344\n1549#2:348\n1620#2,3:349\n1#3:347\n*S KotlinDebug\n*F\n+ 1 AddonTrack.kt\nink/ptms/chemdah/core/quest/addon/AddonTrack\n*L\n209#1:337\n209#1:338,2\n217#1:340\n217#1:341,3\n209#1:344,3\n244#1:348\n244#1:349,3\n*E\n"})
public final class AddonTrack
extends Addon {
    @NotNull
    public static final Companion Companion;
    @NotNull
    private final Lazy center$delegate;
    @NotNull
    private TranslatedStringList message;
    @Nullable
    private TranslatedString name;
    @NotNull
    private TranslatedStringList description;
    @NotNull
    private TrackBeacon beacon;
    @NotNull
    private TrackLandmark landmark;
    @NotNull
    private TrackNavigation navigation;
    @NotNull
    private TrackScoreboard scoreboard;
    @NotNull
    private static final List<Character> uniqueChars;
    @NotNull
    private static final ResettableLazy<List<TrackScoreboard.Line>> defaultContent$delegate;
    @NotNull
    private static final ResettableLazy<TranslatedStringList> defaultMessage$delegate;
    @NotNull
    private static final ResettableLazy<Integer> defaultLength$delegate;

    public AddonTrack(@NotNull ConfigurationSection config, @NotNull QuestContainer questContainer) {
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        super(config, questContainer);
        this.center$delegate = LazyMakerKt.unsafeLazy((Function0)((Function0)new Function0<TrackCenter>(config){
            final /* synthetic */ ConfigurationSection $config;
            {
                this.$config = $config;
                super(0);
            }

            @NotNull
            public final TrackCenter invoke() {
                TrackCenter trackCenter;
                String center2 = this.$config.getString("center");
                CharSequence charSequence = center2;
                if (charSequence == null || charSequence.length() == 0) {
                    trackCenter = NullTrackCenter.INSTANCE;
                } else if (StringsKt.startsWith$default((String)center2, (String)"adyeshach", (boolean)false, (int)2, null)) {
                    if (!AdyeshachChecker.INSTANCE.isLoaded()) {
                        throw new IllegalStateException("Failed to track to Adyeshach Entity, please install Adyeshach plugin.".toString());
                    }
                    trackCenter = new TrackToAdyeshach(((Object)StringsKt.trim((CharSequence)StringsKt.substringAfter$default((String)center2, (String)"adyeshach", null, (int)2, null))).toString());
                } else {
                    trackCenter = new TrackCenterEvent(center2).fire().getTrackCenter();
                    if (trackCenter == null) {
                        trackCenter = new TrackToLocation(center2);
                    }
                }
                return trackCenter;
            }
        }));
        this.message = config.contains("message") ? InlineLanguageKt.getTranslatedStringList((ConfigurationSection)config, (String)"message") : InlineLanguageKt.getTranslatedStringList((ConfigurationSection)config, (String)"default-track.message");
        this.name = InlineLanguageKt.getTranslatedString((ConfigurationSection)config, (String)"name");
        this.description = InlineLanguageKt.getTranslatedStringList((ConfigurationSection)config, (String)"description");
        ConfigurationSection configurationSection = Chemdah.INSTANCE.getConf().getConfigurationSection("default-track.beacon");
        if (configurationSection == null) {
            throw new IllegalStateException("default-track.beacon not found".toString());
        }
        this.beacon = new TrackBeacon(config, configurationSection);
        ConfigurationSection configurationSection2 = Chemdah.INSTANCE.getConf().getConfigurationSection("default-track.landmark");
        if (configurationSection2 == null) {
            throw new IllegalStateException("default-track.landmark not found".toString());
        }
        this.landmark = new TrackLandmark(config, configurationSection2);
        ConfigurationSection configurationSection3 = Chemdah.INSTANCE.getConf().getConfigurationSection("default-track.navigation");
        if (configurationSection3 == null) {
            throw new IllegalStateException("default-track.navigation not found".toString());
        }
        this.navigation = new TrackNavigation(config, configurationSection3);
        ConfigurationSection configurationSection4 = Chemdah.INSTANCE.getConf().getConfigurationSection("default-track.scoreboard");
        if (configurationSection4 == null) {
            throw new IllegalStateException("default-track.scoreboard not found".toString());
        }
        this.scoreboard = new TrackScoreboard(config, configurationSection4);
    }

    @NotNull
    public final TrackCenter getCenter() {
        Lazy lazy = this.center$delegate;
        return (TrackCenter)lazy.getValue();
    }

    @NotNull
    public final TranslatedStringList getMessage() {
        return this.message;
    }

    public final void setMessage(@NotNull TranslatedStringList translatedStringList) {
        Intrinsics.checkNotNullParameter((Object)translatedStringList, (String)"<set-?>");
        this.message = translatedStringList;
    }

    @Nullable
    public final TranslatedString getName() {
        return this.name;
    }

    public final void setName(@Nullable TranslatedString translatedString) {
        this.name = translatedString;
    }

    @NotNull
    public final TranslatedStringList getDescription() {
        return this.description;
    }

    public final void setDescription(@NotNull TranslatedStringList translatedStringList) {
        Intrinsics.checkNotNullParameter((Object)translatedStringList, (String)"<set-?>");
        this.description = translatedStringList;
    }

    @NotNull
    public final TrackBeacon getBeacon() {
        return this.beacon;
    }

    public final void setBeacon(@NotNull TrackBeacon trackBeacon) {
        Intrinsics.checkNotNullParameter((Object)trackBeacon, (String)"<set-?>");
        this.beacon = trackBeacon;
    }

    @NotNull
    public final TrackLandmark getLandmark() {
        return this.landmark;
    }

    public final void setLandmark(@NotNull TrackLandmark trackLandmark) {
        Intrinsics.checkNotNullParameter((Object)trackLandmark, (String)"<set-?>");
        this.landmark = trackLandmark;
    }

    @NotNull
    public final TrackNavigation getNavigation() {
        return this.navigation;
    }

    public final void setNavigation(@NotNull TrackNavigation trackNavigation) {
        Intrinsics.checkNotNullParameter((Object)trackNavigation, (String)"<set-?>");
        this.navigation = trackNavigation;
    }

    @NotNull
    public final TrackScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public final void setScoreboard(@NotNull TrackScoreboard trackScoreboard) {
        Intrinsics.checkNotNullParameter((Object)trackScoreboard, (String)"<set-?>");
        this.scoreboard = trackScoreboard;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final List<String> formatDescription(@NotNull Player player2, @NotNull QuestContainer quest2, @NotNull TrackScoreboard.Line line) {
        void $this$flatMapTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Intrinsics.checkNotNullParameter((Object)line, (String)"line");
        PlayerProfile profile = ChemdahAPI.INSTANCE.getChemdahProfile(player2);
        AddonUI addonUI = AddonUI.Companion.ui(Template.Companion.toTemplate(quest2));
        TranslatedStringList uiDesc = addonUI != null ? addonUI.getDescription() : null;
        Iterable $this$flatMap$iv = line.getContent();
        boolean $i$f$flatMap = false;
        Iterable iterable = $this$flatMap$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$flatMapTo = false;
        for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
            List list2;
            block5: {
                Object object;
                Pair[] pairArray;
                String body;
                block8: {
                    block7: {
                        block6: {
                            void $this$mapTo$iv$iv;
                            block4: {
                                body = (String)element$iv$iv;
                                boolean bl = false;
                                if (!CoerceExtensionsKt.eqic((String)body, (String)"null") && !(((CharSequence)body).length() == 0)) break block4;
                                list2 = CollectionsKt.emptyList();
                                break block5;
                            }
                            if (!StringsKt.contains$default((CharSequence)body, (CharSequence)"description", (boolean)false, (int)2, null)) break block6;
                            Iterable $this$map$iv = this.formatDescription(player2, Template.Companion.toTemplate(quest2).getNode(), uiDesc);
                            boolean $i$f$map = false;
                            Iterable iterable2 = $this$map$iv;
                            Collection destination$iv$iv2 = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                            boolean $i$f$mapTo = false;
                            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                                void it;
                                String string = (String)item$iv$iv;
                                Collection collection = destination$iv$iv2;
                                boolean bl = false;
                                Pair[] pairArray2 = new Pair[]{TuplesKt.to((Object)"description", (Object)it)};
                                collection.add(StringKt.replace(body, pairArray2));
                            }
                            list2 = (List)destination$iv$iv2;
                            break block5;
                        }
                        pairArray = new Pair[1];
                        object = this.name;
                        if (object == null) break block7;
                        String string = player2.getName();
                        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"player.name");
                        if ((object = object.get(string)) != null) break block8;
                    }
                    object = MetaName.Companion.displayName$default(MetaName.Companion, quest2, false, profile, 1, null);
                }
                pairArray[0] = TuplesKt.to((Object)"name", (Object)object);
                list2 = CollectionKt.asList((Object)StringKt.replace(body, pairArray));
            }
            Iterable list$iv$iv = list2;
            CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
        }
        return (List)destination$iv$iv;
    }

    @NotNull
    public final List<String> formatDescription(@NotNull Player player2, @NotNull String questNode, @Nullable TranslatedStringList def) {
        Collection collection;
        block5: {
            Collection collection2;
            block3: {
                block4: {
                    Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                    Intrinsics.checkNotNullParameter((Object)questNode, (String)"questNode");
                    String string = player2.getLocale();
                    Intrinsics.checkNotNullExpressionValue((Object)string, (String)"player.locale");
                    collection2 = this.description.get(string);
                    if (!collection2.isEmpty()) break block3;
                    boolean bl = false;
                    collection = def;
                    if (collection == null) break block4;
                    String string2 = player2.getLocale();
                    Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"player.locale");
                    if ((collection = collection.get(string2)) != null) break block5;
                }
                collection = CollectionsKt.emptyList();
                break block5;
            }
            collection = collection2;
        }
        List lines = (List)collection;
        if (lines.isEmpty()) {
            return lines;
        }
        Object object = KetherHelperKt.runKether$default(null, (boolean)false, (Function0)((Function0)new Function0<List<? extends String>>((List<String>)lines, player2, questNode, this){
            final /* synthetic */ List<String> $lines;
            final /* synthetic */ Player $player;
            final /* synthetic */ String $questNode;
            final /* synthetic */ AddonTrack this$0;
            {
                this.$lines = $lines;
                this.$player = $player;
                this.$questNode = $questNode;
                this.this$0 = $receiver;
                super(0);
            }

            @NotNull
            public final List<String> invoke() {
                Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"@QuestSelected", (Object)this.$questNode)};
                return StringKt.splitBy(KetherFunction.parse$default((KetherFunction)KetherFunction.INSTANCE, this.$lines, (boolean)false, UtilsForKetherKt.getNamespaceQuestUI(), null, (ProxyCommandSender)((ProxyCommandSender)AdapterKt.adaptPlayer((Object)this.$player)), (KetherShell.VariableMap)new KetherShell.VariableMap(pairArray), null, (int)74, null), this.this$0.getScoreboard().getLength());
            }
        }), (int)3, null);
        Intrinsics.checkNotNull((Object)object);
        return (List)object;
    }

    /*
     * WARNING - void declaration
     */
    static {
        void var3_3;
        void $this$mapTo$iv$iv;
        Companion = new Companion(null);
        Iterable $this$map$iv = (Iterable)new IntRange(1, 50);
        boolean $i$f$map = false;
        Iterable iterable = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        Iterator iterator = $this$mapTo$iv$iv.iterator();
        while (iterator.hasNext()) {
            void it;
            int item$iv$iv;
            int n = item$iv$iv = ((IntIterator)iterator).nextInt();
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(Character.valueOf((char)(40657 + it)));
        }
        uniqueChars = (List)var3_3;
        defaultContent$delegate = LazyMakerKt.resettableLazy$default((String[])new String[0], (boolean)false, (Function0)Companion.defaultContent.2.INSTANCE, (int)2, null);
        defaultMessage$delegate = LazyMakerKt.resettableLazy$default((String[])new String[0], (boolean)false, (Function0)Companion.defaultMessage.2.INSTANCE, (int)2, null);
        defaultLength$delegate = LazyMakerKt.resettableLazy$default((String[])new String[0], (boolean)false, (Function0)Companion.defaultLength.2.INSTANCE, (int)2, null);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000h\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\f\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020&H\u0003J\u0010\u0010'\u001a\u00020$2\u0006\u0010%\u001a\u00020(H\u0003J\n\u0010)\u001a\u00020**\u00020+J\f\u0010,\u001a\u0004\u0018\u00010-*\u00020+R!\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u00048FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\b\u0010\t\u001a\u0004\b\u0006\u0010\u0007R\u001b\u0010\n\u001a\u00020\u000b8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u000e\u0010\t\u001a\u0004\b\f\u0010\rR\u001b\u0010\u000f\u001a\u00020\u00108FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0013\u0010\t\u001a\u0004\b\u0011\u0010\u0012R\u001a\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00150\u0004X\u0080\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0007R,\u0010\u0019\u001a\u0004\u0018\u00010\u0018*\u00020\u001a2\b\u0010\u0017\u001a\u0004\u0018\u00010\u00188F@FX\u0086\u000e\u00a2\u0006\f\u001a\u0004\b\u001b\u0010\u001c\"\u0004\b\u001d\u0010\u001eR\u0017\u0010\u001f\u001a\u0004\u0018\u00010 *\u00020\u001a8F\u00a2\u0006\u0006\u001a\u0004\b!\u0010\"\u00a8\u0006."}, d2={"Link/ptms/chemdah/core/quest/addon/AddonTrack$Companion;", "", "()V", "defaultContent", "", "Link/ptms/chemdah/core/quest/addon/data/TrackScoreboard$Line;", "getDefaultContent", "()Ljava/util/List;", "defaultContent$delegate", "Link/ptms/chemdah/taboolib/common/util/ResettableLazy;", "defaultLength", "", "getDefaultLength", "()I", "defaultLength$delegate", "defaultMessage", "Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedStringList;", "getDefaultMessage", "()Link/ptms/chemdah/taboolib/module/lang/inline/TranslatedStringList;", "defaultMessage$delegate", "uniqueChars", "", "getUniqueChars$Chemdah", "value", "Link/ptms/chemdah/core/quest/Template;", "trackQuest", "Link/ptms/chemdah/core/PlayerProfile;", "getTrackQuest", "(Link/ptms/chemdah/core/PlayerProfile;)Link/ptms/chemdah/core/quest/Template;", "setTrackQuest", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Template;)V", "trackQuestId", "", "getTrackQuestId", "(Link/ptms/chemdah/core/PlayerProfile;)Ljava/lang/String;", "onCommand", "", "e", "Lorg/bukkit/event/player/PlayerCommandPreprocessEvent;", "onUnregistered", "Link/ptms/chemdah/api/event/collect/QuestEvents$Unregistered;", "allowTracked", "", "Link/ptms/chemdah/core/quest/QuestContainer;", "track", "Link/ptms/chemdah/core/quest/addon/AddonTrack;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nAddonTrack.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonTrack.kt\nink/ptms/chemdah/core/quest/addon/AddonTrack$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,336:1\n1747#2,3:337\n1855#2,2:341\n1#3:340\n*S KotlinDebug\n*F\n+ 1 AddonTrack.kt\nink/ptms/chemdah/core/quest/addon/AddonTrack$Companion\n*L\n263#1:337,3\n328#1:341,2\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final List<Character> getUniqueChars$Chemdah() {
            return uniqueChars;
        }

        @NotNull
        public final List<TrackScoreboard.Line> getDefaultContent() {
            Lazy lazy = (Lazy)defaultContent$delegate;
            return (List)lazy.getValue();
        }

        @NotNull
        public final TranslatedStringList getDefaultMessage() {
            Lazy lazy = (Lazy)defaultMessage$delegate;
            return (TranslatedStringList)lazy.getValue();
        }

        public final int getDefaultLength() {
            Lazy lazy = (Lazy)defaultLength$delegate;
            return ((Number)lazy.getValue()).intValue();
        }

        /*
         * Enabled force condition propagation
         * Lifted jumps to return sites
         */
        public final boolean allowTracked(@NotNull QuestContainer $this$allowTracked) {
            Intrinsics.checkNotNullParameter((Object)$this$allowTracked, (String)"<this>");
            QuestContainer questContainer = $this$allowTracked;
            if (questContainer instanceof Template) {
                boolean bl;
                if (this.track($this$allowTracked) != null) return true;
                Collection<Task> collection = ((Template)$this$allowTracked).getTaskMap().values();
                Intrinsics.checkNotNullExpressionValue(collection, (String)"taskMap.values");
                Iterable $this$any$iv = collection;
                boolean $i$f$any = false;
                if (((Collection)$this$any$iv).isEmpty()) {
                    return false;
                }
                Iterator iterator = $this$any$iv.iterator();
                do {
                    if (!iterator.hasNext()) return false;
                    Object element$iv = iterator.next();
                    Task it = (Task)element$iv;
                    boolean bl2 = false;
                    Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                    if (Companion.track(it) != null) {
                        return true;
                    }
                    bl = false;
                } while (!bl);
                return true;
            }
            if (!(questContainer instanceof Task)) throw new IllegalStateException("out of case".toString());
            if (this.track($this$allowTracked) == null) return false;
            return true;
        }

        @Nullable
        public final AddonTrack track(@NotNull QuestContainer $this$track) {
            AddonTrack addonTrack;
            Intrinsics.checkNotNullParameter((Object)$this$track, (String)"<this>");
            QuestContainer questContainer = $this$track;
            if (questContainer instanceof Template) {
                addonTrack = (AddonTrack)$this$track.addon("track");
            } else if (questContainer instanceof Task) {
                addonTrack = (AddonTrack)$this$track.addon("track");
                if (addonTrack == null) {
                    addonTrack = this.track(((Task)$this$track).getTemplate());
                }
            } else {
                throw new IllegalStateException("out of case".toString());
            }
            return addonTrack;
        }

        @Nullable
        public final String getTrackQuestId(@NotNull PlayerProfile $this$trackQuestId) {
            Intrinsics.checkNotNullParameter((Object)$this$trackQuestId, (String)"<this>");
            Template template = this.getTrackQuest($this$trackQuestId);
            return template != null ? template.getId() : null;
        }

        @Nullable
        public final Template getTrackQuest(@NotNull PlayerProfile $this$trackQuest) {
            Template template;
            Intrinsics.checkNotNullParameter((Object)$this$trackQuest, (String)"<this>");
            Data data2 = $this$trackQuest.getPersistentDataContainer().get("quest.track");
            if (data2 != null) {
                Data it = data2;
                boolean bl = false;
                template = ChemdahAPI.INSTANCE.getQuestTemplate(it.toString());
            } else {
                template = null;
            }
            return template;
        }

        public final void setTrackQuest(@NotNull PlayerProfile $this$trackQuest, @Nullable Template value2) {
            Intrinsics.checkNotNullParameter((Object)$this$trackQuest, (String)"<this>");
            if (value2 != null && !this.allowTracked(value2)) {
                Object[] objectArray = new Object[]{"Quest(" + value2.getPath() + ") not allowed to tracked."};
                IOKt.warning((Object[])objectArray);
                return;
            }
            Player player2 = $this$trackQuest.getPlayer();
            Template template = value2;
            if (template == null) {
                template = this.getTrackQuest($this$trackQuest);
            }
            if (new PlayerEvents.Track(player2, $this$trackQuest, template, value2 == null).call()) {
                if (value2 != null) {
                    $this$trackQuest.getPersistentDataContainer().set("quest.track", value2.getId());
                } else {
                    $this$trackQuest.getPersistentDataContainer().remove("quest.track");
                }
            }
        }

        @SubscribeEvent
        private final void onCommand(PlayerCommandPreprocessEvent e) {
            if (StringsKt.equals((String)e.getMessage(), (String)"/chemdahtrackcancel", (boolean)true)) {
                e.setCancelled(true);
                Player player2 = e.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
                this.setTrackQuest(ChemdahAPI.INSTANCE.getChemdahProfile(player2), null);
            }
        }

        @SubscribeEvent
        private final void onUnregistered(QuestEvents.Unregistered e) {
            if (Intrinsics.areEqual((Object)this.getTrackQuest(e.getPlayerProfile()), (Object)e.getQuest().getTemplate())) {
                Iterable $this$forEach$iv = PartySystem.INSTANCE.getMembers(e.getQuest(), true);
                boolean $i$f$forEach = false;
                for (Object element$iv : $this$forEach$iv) {
                    Player it = (Player)element$iv;
                    boolean bl = false;
                    if (!Intrinsics.areEqual((Object)Companion.getTrackQuest(ChemdahAPI.INSTANCE.getChemdahProfile(it)), (Object)e.getQuest().getTemplate()) || PlayerProfile.getQuestById$default(ChemdahAPI.INSTANCE.getChemdahProfile(it), e.getQuest().getId(), false, 2, null) != null) continue;
                    Companion.setTrackQuest(ChemdahAPI.INSTANCE.getChemdahProfile(it), null);
                }
            }
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

