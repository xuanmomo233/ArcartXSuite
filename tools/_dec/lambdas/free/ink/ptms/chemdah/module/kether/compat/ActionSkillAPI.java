/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sucy.skill.SkillAPI
 *  com.sucy.skill.api.player.PlayerData
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.OfflinePlayer
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether.compat;

import com.sucy.skill.SkillAPI;
import com.sucy.skill.api.player.PlayerData;
import ink.ptms.chemdah.module.kether.compat.ActionSkillAPI;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u0000 \u00042\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/kether/compat/ActionSkillAPI;", "", "()V", "Base", "Companion", "Chemdah"})
public final class ActionSkillAPI {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0019\u0012\u0012\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00020\u0004\u00a2\u0006\u0002\u0010\u0006J\u001a\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00020\n2\n\u0010\u000b\u001a\u00060\fj\u0002`\rH\u0016R\u001d\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/module/kether/compat/ActionSkillAPI$Base;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "action", "Ljava/util/function/Function;", "Lcom/sucy/skill/api/player/PlayerData;", "(Ljava/util/function/Function;)V", "getAction", "()Ljava/util/function/Function;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class Base
    extends ScriptAction<Object> {
        @NotNull
        private final Function<PlayerData, Object> action;

        public Base(@NotNull Function<PlayerData, Object> action) {
            Intrinsics.checkNotNullParameter(action, (String)"action");
            this.action = action;
        }

        @NotNull
        public final Function<PlayerData, Object> getAction() {
            return this.action;
        }

        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(this.action.apply(SkillAPI.getPlayerData((OfflinePlayer)((OfflinePlayer)UtilsForKetherKt.getBukkitPlayer(frame)))));
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(action.a\u2026rame.getBukkitPlayer())))");
            return completableFuture;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00010\u0004H\u0007\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/kether/compat/ActionSkillAPI$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"skillapi"}, shared=true)
        @NotNull
        public final ScriptActionParser<Object> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

