/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.level;

import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B!\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u0012\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00060\u0003\u00a2\u0006\u0002\u0010\u0007J\u0016\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0002\u001a\u00020\u0004R\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0017\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00060\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\t\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/module/level/LevelReward;", "", "level", "", "", "script", "", "(Ljava/util/List;Ljava/util/List;)V", "getLevel", "()Ljava/util/List;", "getScript", "eval", "", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
public final class LevelReward {
    @NotNull
    private final List<Integer> level;
    @NotNull
    private final List<String> script;

    public LevelReward(@NotNull List<Integer> level, @NotNull List<String> script) {
        Intrinsics.checkNotNullParameter(level, (String)"level");
        Intrinsics.checkNotNullParameter(script, (String)"script");
        this.level = level;
        this.script = script;
    }

    @NotNull
    public final List<Integer> getLevel() {
        return this.level;
    }

    @NotNull
    public final List<String> getScript() {
        return this.script;
    }

    public final void eval(@NotNull Player player, int level) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        try {
            KetherShell.eval$default((KetherShell)KetherShell.INSTANCE, this.script, (boolean)false, null, null, (ProxyCommandSender)((ProxyCommandSender)AdapterKt.adaptPlayer((Object)player)), null, (Function1)((Function1)new Function1<ScriptContext, Unit>(level){
                final /* synthetic */ int $level;
                {
                    this.$level = $level;
                    super(1);
                }

                public final void invoke(@NotNull ScriptContext $this$eval) {
                    Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                    $this$eval.set("level", (Object)this.$level);
                }
            }), (int)46, null);
        }
        catch (Exception ex) {
            KetherHelperKt.printKetherErrorMessage((Throwable)ex, (boolean)true);
        }
    }
}

