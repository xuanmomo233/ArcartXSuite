/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.PlatformFactory
 *  ink.ptms.chemdah.taboolib.common.util.LocaleKt
 *  ink.ptms.chemdah.taboolib.module.configuration.ConfigNode
 *  ink.ptms.chemdah.taboolib.platform.bukkit.Parallel
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.messaging;

import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.core.messaging.MessagingError;
import ink.ptms.chemdah.core.messaging.MessagingRedis;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.PlatformFactory;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
import ink.ptms.chemdah.taboolib.module.configuration.ConfigNode;
import ink.ptms.chemdah.taboolib.platform.bukkit.Parallel;
import java.util.Locale;
import java.util.UUID;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\bf\u0018\u0000 \b2\u00020\u0001:\u0001\bJ\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&J\u0010\u0010\u0006\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&J\u0010\u0010\u0007\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\t\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/core/messaging/Messaging;", "", "awaitProfileReleased", "", "player", "Ljava/util/UUID;", "markProfileReleased", "markProfileSelected", "Companion", "Chemdah"})
public interface Messaging {
    @NotNull
    public static final Companion Companion = ink.ptms.chemdah.core.messaging.Messaging$Companion.$$INSTANCE;

    public void markProfileReleased(@NotNull UUID var1);

    public void markProfileSelected(@NotNull UUID var1);

    public void awaitProfileReleased(@NotNull UUID var1);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u000e\u001a\u00020\u000fH\u0007R\u001a\u0010\u0003\u001a\u00020\u0004X\u0086.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u001e\u0010\t\u001a\u00020\n8\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\u000b\"\u0004\b\f\u0010\r\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/messaging/Messaging$Companion;", "", "()V", "INSTANCE", "Link/ptms/chemdah/core/messaging/Messaging;", "getINSTANCE", "()Link/ptms/chemdah/core/messaging/Messaging;", "setINSTANCE", "(Link/ptms/chemdah/core/messaging/Messaging;)V", "isEnabled", "", "()Z", "setEnabled", "(Z)V", "setup", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nMessaging.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Messaging.kt\nink/ptms/chemdah/core/messaging/Messaging$Companion\n+ 2 PlatformFactory.kt\ntaboolib/common/platform/PlatformFactory\n*L\n1#1,76:1\n87#2:77\n*S KotlinDebug\n*F\n+ 1 Messaging.kt\nink/ptms/chemdah/core/messaging/Messaging$Companion\n*L\n50#1:77\n*E\n"})
    public static final class Companion {
        static final /* synthetic */ Companion $$INSTANCE;
        public static Messaging INSTANCE;
        @ConfigNode(value="messaging.enabled")
        private static boolean isEnabled;

        private Companion() {
        }

        @NotNull
        public final Messaging getINSTANCE() {
            Messaging messaging = INSTANCE;
            if (messaging != null) {
                return messaging;
            }
            Intrinsics.throwUninitializedPropertyAccessException((String)"INSTANCE");
            return null;
        }

        public final void setINSTANCE(@NotNull Messaging messaging) {
            Intrinsics.checkNotNullParameter((Object)messaging, (String)"<set-?>");
            INSTANCE = messaging;
        }

        public final boolean isEnabled() {
            return isEnabled;
        }

        public final void setEnabled(boolean bl) {
            isEnabled = bl;
        }

        /*
         * WARNING - void declaration
         */
        @Parallel(id="chemdah_messaging_setup", runOn=LifeCycle.ACTIVE)
        public final void setup() {
            Messaging messaging;
            Companion companion;
            if (INSTANCE != null) {
                return;
            }
            if (!isEnabled) {
                this.setINSTANCE(new MessagingError(LocaleKt.t((String)"\n                        \u6d88\u606f\u672a\u542f\u7528\u3002\n                        Messaging not enabled.\n                    ")));
                return;
            }
            Companion companion2 = this;
            try {
                Messaging messaging2;
                Messaging impl;
                companion = companion2;
                PlatformFactory this_$iv = PlatformFactory.INSTANCE;
                boolean $i$f$getAPIOrNull = false;
                Object v = this_$iv.getAwokenMap().get(Messaging.class.getName());
                if (!(v instanceof Messaging)) {
                    v = null;
                }
                if ((impl = (Messaging)v) == null) {
                    String string = Chemdah.INSTANCE.getConf().getString("messaging.use", "");
                    Intrinsics.checkNotNull((Object)string);
                    String string2 = string.toLowerCase(Locale.ROOT);
                    Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
                    String use = string2;
                    if (!Intrinsics.areEqual((Object)use, (Object)"redis")) {
                        throw new IllegalStateException(LocaleKt.t((String)"\n                                    \u6ca1\u6709\u627e\u5230\u81ea\u5b9a\u4e49\u7684\u6d88\u606f\u5b9e\u73b0\u3002\n                                    No custom messaging implementation found.\n                                ").toString());
                    }
                    messaging2 = new MessagingRedis();
                } else {
                    messaging2 = messaging;
                }
                messaging = messaging2;
            }
            catch (Throwable use) {
                void e;
                companion = companion2;
                e.printStackTrace();
                String string = e.getLocalizedMessage();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.localizedMessage");
                messaging = new MessagingError(string);
            }
            companion.setINSTANCE(messaging);
        }

        static {
            $$INSTANCE = new Companion();
        }
    }
}

