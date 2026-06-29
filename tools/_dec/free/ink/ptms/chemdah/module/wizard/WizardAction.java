/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.entity.EntityInstance
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common5.Baffle
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.wizard;

import ink.ptms.adyeshach.core.entity.EntityInstance;
import ink.ptms.chemdah.module.wizard.WizardInfo;
import ink.ptms.chemdah.module.wizard.WizardSystem;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common5.Baffle;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.util.LocationKt;
import ink.ptms.chemdah.util.UtilsForAdyKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0006\b\u0016\u0018\u00002\u00020\u0001:\u0001'B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0012\u0010!\u001a\u00020\"2\b\b\u0002\u0010#\u001a\u00020\u0016H\u0016J\b\u0010$\u001a\u00020\u0000H\u0016J\u0006\u0010%\u001a\u00020\u0016J\b\u0010&\u001a\u00020\"H\u0016R\u0016\u0010\t\u001a\u00070\n\u00a2\u0006\u0002\b\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0016\u0010\u000e\u001a\u00070\n\u00a2\u0006\u0002\b\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\rR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0017\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00160\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u001a\u0010\u001b\u001a\u00020\u001cX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001d\u0010\u001e\"\u0004\b\u001f\u0010 \u00a8\u0006("}, d2={"Link/ptms/chemdah/module/wizard/WizardAction;", "", "player", "Lorg/bukkit/entity/Player;", "entityInstance", "Link/ptms/adyeshach/core/entity/EntityInstance;", "info", "Link/ptms/chemdah/module/wizard/WizardInfo;", "(Lorg/bukkit/entity/Player;Link/ptms/adyeshach/core/entity/EntityInstance;Link/ptms/chemdah/module/wizard/WizardInfo;)V", "cooldownMove", "Link/ptms/chemdah/taboolib/common5/Baffle;", "Lorg/jetbrains/annotations/NotNull;", "getCooldownMove", "()Link/ptms/chemdah/taboolib/common5/Baffle;", "cooldownWait", "getCooldownWait", "getEntityInstance", "()Link/ptms/adyeshach/core/entity/EntityInstance;", "getInfo", "()Link/ptms/chemdah/module/wizard/WizardInfo;", "onFinish", "Ljava/util/concurrent/CompletableFuture;", "", "getOnFinish", "()Ljava/util/concurrent/CompletableFuture;", "getPlayer", "()Lorg/bukkit/entity/Player;", "state", "Link/ptms/chemdah/module/wizard/WizardAction$State;", "getState", "()Link/ptms/chemdah/module/wizard/WizardAction$State;", "setState", "(Link/ptms/chemdah/module/wizard/WizardAction$State;)V", "cancel", "", "success", "check", "shouldMoving", "start", "State", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nWizardAction.kt\nKotlin\n*S Kotlin\n*F\n+ 1 WizardAction.kt\nink/ptms/chemdah/module/wizard/WizardAction\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,107:1\n1#2:108\n*E\n"})
public class WizardAction {
    @NotNull
    private final Player player;
    @NotNull
    private final EntityInstance entityInstance;
    @NotNull
    private final WizardInfo info;
    @NotNull
    private final CompletableFuture<Boolean> onFinish;
    @NotNull
    private State state;
    @NotNull
    private final Baffle cooldownWait;
    @NotNull
    private final Baffle cooldownMove;

    public WizardAction(@NotNull Player player2, @NotNull EntityInstance entityInstance, @NotNull WizardInfo info2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)entityInstance, (String)"entityInstance");
        Intrinsics.checkNotNullParameter((Object)info2, (String)"info");
        this.player = player2;
        this.entityInstance = entityInstance;
        this.info = info2;
        this.onFinish = new CompletableFuture();
        this.state = State.MOVING;
        Baffle baffle = Baffle.of((long)this.info.getEventCooldown(), (TimeUnit)TimeUnit.MILLISECONDS);
        Intrinsics.checkNotNullExpressionValue((Object)baffle, (String)"of(info.eventCooldown, TimeUnit.MILLISECONDS)");
        this.cooldownWait = baffle;
        Baffle baffle2 = Baffle.of((long)this.info.getEventCooldown(), (TimeUnit)TimeUnit.MILLISECONDS);
        Intrinsics.checkNotNullExpressionValue((Object)baffle2, (String)"of(info.eventCooldown, TimeUnit.MILLISECONDS)");
        this.cooldownMove = baffle2;
    }

    @NotNull
    public final Player getPlayer() {
        return this.player;
    }

    @NotNull
    public final EntityInstance getEntityInstance() {
        return this.entityInstance;
    }

    @NotNull
    public final WizardInfo getInfo() {
        return this.info;
    }

    @NotNull
    public final CompletableFuture<Boolean> getOnFinish() {
        return this.onFinish;
    }

    @NotNull
    public final State getState() {
        return this.state;
    }

    public final void setState(@NotNull State state) {
        Intrinsics.checkNotNullParameter((Object)((Object)state), (String)"<set-?>");
        this.state = state;
    }

    @NotNull
    public final Baffle getCooldownWait() {
        return this.cooldownWait;
    }

    @NotNull
    public final Baffle getCooldownMove() {
        return this.cooldownMove;
    }

    public void start() {
        UtilsForAdyKt.controllerMoveWithPathList(this.entityInstance, (List<? extends Location>)this.info.getPathList());
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public final boolean shouldMoving() {
        if (!Intrinsics.areEqual((Object)this.player.getWorld(), (Object)this.entityInstance.getWorld())) return false;
        Location location = this.player.getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"player.location");
        if (!(LocationKt.safeDistance(location, this.entityInstance.getLocation()) < this.info.getWaitingDistance())) return false;
        return true;
    }

    public void cancel(boolean success) {
        this.entityInstance.setFreeze(false);
        this.entityInstance.controllerStopMove();
        WizardSystem.INSTANCE.getActionMap().remove(this.entityInstance.getUniqueId());
        if (success) {
            String string = this.info.getEventOnFinish();
            if (string != null) {
                String it = string;
                boolean bl = false;
                CompletableFuture cfr_ignored_0 = (CompletableFuture)KetherHelperKt.runKether$default(null, (boolean)false, (Function0)((Function0)new Function0<CompletableFuture<Object>>(this, it){
                    final /* synthetic */ WizardAction this$0;
                    final /* synthetic */ String $it;
                    {
                        this.this$0 = $receiver;
                        this.$it = $it;
                        super(0);
                    }

                    @NotNull
                    public final CompletableFuture<Object> invoke() {
                        KetherShell ketherShell = KetherShell.INSTANCE;
                        ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)this.this$0.getPlayer());
                        List<String> list2 = UtilsForKetherKt.getNamespace();
                        return KetherShell.eval$default((KetherShell)ketherShell, (String)this.$it, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, null, (int)106, null);
                    }
                }), (int)3, null);
            }
        }
        this.onFinish.complete(success);
    }

    public static /* synthetic */ void cancel$default(WizardAction wizardAction, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: cancel");
        }
        if ((n & 1) != 0) {
            bl = false;
        }
        wizardAction.cancel(bl);
    }

    @NotNull
    public WizardAction check() {
        block7: {
            block6: {
                if (LocationKt.safeDistance(this.entityInstance.getLocation(), (Location)CollectionsKt.last(this.info.getNodes())) < this.info.getFinishDistance()) {
                    if (!this.entityInstance.hasTag("IS_MOVING")) {
                        this.cancel(true);
                    }
                    return this;
                }
                if (!this.shouldMoving()) break block6;
                this.entityInstance.setFreeze(false);
                if (this.state != State.WAITING) break block7;
                this.state = State.MOVING;
                if (this.cooldownMove.hasNext()) {
                    String string = this.info.getEventOnContinue();
                    if (string != null) {
                        String it = string;
                        boolean bl = false;
                        CompletableFuture cfr_ignored_0 = (CompletableFuture)KetherHelperKt.runKether$default(null, (boolean)false, (Function0)((Function0)new Function0<CompletableFuture<Object>>(this, it){
                            final /* synthetic */ WizardAction this$0;
                            final /* synthetic */ String $it;
                            {
                                this.this$0 = $receiver;
                                this.$it = $it;
                                super(0);
                            }

                            @NotNull
                            public final CompletableFuture<Object> invoke() {
                                KetherShell ketherShell = KetherShell.INSTANCE;
                                ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)this.this$0.getPlayer());
                                List<String> list2 = UtilsForKetherKt.getNamespace();
                                return KetherShell.eval$default((KetherShell)ketherShell, (String)this.$it, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, null, (int)106, null);
                            }
                        }), (int)3, null);
                    }
                }
                break block7;
            }
            this.entityInstance.setFreeze(true);
            if (this.state != State.MOVING) break block7;
            this.state = State.WAITING;
            if (this.cooldownWait.hasNext()) {
                String string = this.info.getEventOnWaiting();
                if (string != null) {
                    String it = string;
                    boolean bl = false;
                    CompletableFuture cfr_ignored_1 = (CompletableFuture)KetherHelperKt.runKether$default(null, (boolean)false, (Function0)((Function0)new Function0<CompletableFuture<Object>>(this, it){
                        final /* synthetic */ WizardAction this$0;
                        final /* synthetic */ String $it;
                        {
                            this.this$0 = $receiver;
                            this.$it = $it;
                            super(0);
                        }

                        @NotNull
                        public final CompletableFuture<Object> invoke() {
                            KetherShell ketherShell = KetherShell.INSTANCE;
                            ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)this.this$0.getPlayer());
                            List<String> list2 = UtilsForKetherKt.getNamespace();
                            return KetherShell.eval$default((KetherShell)ketherShell, (String)this.$it, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, null, (int)106, null);
                        }
                    }), (int)3, null);
                }
            }
        }
        return this;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0005\b\u0086\u0001\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/wizard/WizardAction$State;", "", "(Ljava/lang/String;I)V", "MOVING", "WAITING", "FINISHED", "Chemdah"})
    public static final class State
    extends Enum<State> {
        public static final /* enum */ State MOVING = new State();
        public static final /* enum */ State WAITING = new State();
        public static final /* enum */ State FINISHED = new State();
        private static final /* synthetic */ State[] $VALUES;

        public static State[] values() {
            return (State[])$VALUES.clone();
        }

        public static State valueOf(String value2) {
            return Enum.valueOf(State.class, value2);
        }

        static {
            $VALUES = stateArray = new State[]{State.MOVING, State.WAITING, State.FINISHED};
        }
    }
}

