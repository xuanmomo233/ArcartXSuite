/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.util.LocaleKt
 *  ink.ptms.chemdah.taboolib.expansion.AlkaidRedis
 *  ink.ptms.chemdah.taboolib.expansion.IRedisConnection
 *  ink.ptms.chemdah.taboolib.expansion.RedisConfigurationKt
 *  ink.ptms.chemdah.taboolib.expansion.RedisMessage
 *  ink.ptms.chemdah.taboolib.expansion.SingleRedisConnection
 *  ink.ptms.chemdah.taboolib.expansion.SingleRedisConnector
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.messaging;

import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.messaging.Messaging;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
import ink.ptms.chemdah.taboolib.expansion.AlkaidRedis;
import ink.ptms.chemdah.taboolib.expansion.IRedisConnection;
import ink.ptms.chemdah.taboolib.expansion.RedisConfigurationKt;
import ink.ptms.chemdah.taboolib.expansion.RedisMessage;
import ink.ptms.chemdah.taboolib.expansion.SingleRedisConnection;
import ink.ptms.chemdah.taboolib.expansion.SingleRedisConnector;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000>\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\rH\u0016J\u0010\u0010\u0012\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\rH\u0016J\u0010\u0010\u0013\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\rH\u0016J\u0010\u0010\u0014\u001a\u00020\u00102\u0006\u0010\u0015\u001a\u00020\u0016H\u0002J\u0010\u0010\u0017\u001a\u00020\u00102\u0006\u0010\u0015\u001a\u00020\u0016H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082D\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0082D\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u000b\u001a\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u000e0\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0018"}, d2={"Link/ptms/chemdah/core/messaging/MessagingRedis;", "Link/ptms/chemdah/core/messaging/Messaging;", "()V", "announceId", "", "connection", "Link/ptms/chemdah/taboolib/expansion/SingleRedisConnection;", "connector", "Link/ptms/chemdah/taboolib/expansion/SingleRedisConnector;", "serverId", "variableId", "waitGroups", "Ljava/util/concurrent/ConcurrentHashMap;", "Ljava/util/UUID;", "Ljava/util/concurrent/CountDownLatch;", "awaitProfileReleased", "", "player", "markProfileReleased", "markProfileSelected", "onProfileReleaseRequest", "message", "Link/ptms/chemdah/taboolib/expansion/RedisMessage;", "onProfileReleaseResponse", "Chemdah"})
public final class MessagingRedis
implements Messaging {
    @NotNull
    private final SingleRedisConnector connector;
    @NotNull
    private final SingleRedisConnection connection;
    @NotNull
    private final String announceId;
    @NotNull
    private final String variableId;
    @NotNull
    private final String serverId;
    @NotNull
    private final ConcurrentHashMap<UUID, CountDownLatch> waitGroups;

    public MessagingRedis() {
        this.announceId = "chemddah";
        this.variableId = "chemddah";
        String string = UUID.randomUUID().toString();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"randomUUID().toString()");
        this.serverId = string;
        this.waitGroups = new ConcurrentHashMap();
        SingleRedisConnector singleRedisConnector = AlkaidRedis.INSTANCE.create();
        ConfigurationSection configurationSection = Chemdah.INSTANCE.getConf().getConfigurationSection("messaging.source.redis");
        if (configurationSection == null) {
            configurationSection = (ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null);
        }
        this.connector = RedisConfigurationKt.fromConfig((SingleRedisConnector)singleRedisConnector, (ConfigurationSection)configurationSection).connect();
        this.connection = this.connector.connection();
        Object[] objectArray = new Object[]{LocaleKt.t((String)"\n                \u5df2\u8fde\u63a5\u5230 Redis \u670d\u52a1\u5668\u3002\n                Redis server connected.\n            ")};
        IOKt.info((Object[])objectArray);
        objectArray = new String[]{this.announceId + ":profile_release_request:" + this.serverId};
        IRedisConnection.subscribe$default((IRedisConnection)((IRedisConnection)this.connection), (String[])objectArray, (boolean)false, (Function1)((Function1)new Function1<RedisMessage, Unit>(){

            public final void invoke(@NotNull RedisMessage $this$subscribe) {
                Intrinsics.checkNotNullParameter((Object)$this$subscribe, (String)"$this$subscribe");
                this.onProfileReleaseRequest($this$subscribe);
            }
        }), (int)2, null);
        objectArray = new String[]{this.announceId + ":profile_release_response"};
        IRedisConnection.subscribe$default((IRedisConnection)((IRedisConnection)this.connection), (String[])objectArray, (boolean)false, (Function1)((Function1)new Function1<RedisMessage, Unit>(){

            public final void invoke(@NotNull RedisMessage $this$subscribe) {
                Intrinsics.checkNotNullParameter((Object)$this$subscribe, (String)"$this$subscribe");
                this.onProfileReleaseResponse($this$subscribe);
            }
        }), (int)2, null);
    }

    private final void onProfileReleaseRequest(RedisMessage message2) {
        Player player2;
        Player player3 = Bukkit.getPlayer((UUID)UUID.fromString(message2.getMessage()));
        if (player3 == null) {
            return;
        }
        Player player4 = player2 = player3;
        try {
            ChemdahAPI.INSTANCE.getChemdahProfile(player4).push();
        }
        catch (Exception exception) {
            // empty catch block
        }
        String string = this.announceId + ":profile_release_response";
        String string2 = player4.getUniqueId().toString();
        Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"player.uniqueId.toString()");
        this.connection.publish(string, (Object)string2);
    }

    private final void onProfileReleaseResponse(RedisMessage message2) {
        UUID player2 = UUID.fromString(message2.getMessage());
        CountDownLatch countDownLatch = this.waitGroups.get(player2);
        if (countDownLatch == null) {
            return;
        }
        CountDownLatch waitGroup = countDownLatch;
        waitGroup.countDown();
    }

    @Override
    public void markProfileReleased(@NotNull UUID player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        this.connection.eval("if redis.call('get', KEYS[1]) == ARGV[1] then\n    return redis.call('del', KEYS[1])\nelse\n   return 0\nend", CollectionsKt.listOf((Object)(this.variableId + ':' + player2)), CollectionsKt.listOf((Object)this.serverId));
    }

    @Override
    public void markProfileSelected(@NotNull UUID player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        this.connection.set(this.variableId + ':' + player2, this.serverId);
    }

    @Override
    public void awaitProfileReleased(@NotNull UUID player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        String string = this.connection.get(this.variableId + ':' + player2);
        if (string == null) {
            return;
        }
        String server = string;
        CountDownLatch waitGroup = new CountDownLatch(1);
        ((Map)this.waitGroups).put(player2, waitGroup);
        String string2 = this.announceId + ":profile_release_request:" + server;
        String string3 = player2.toString();
        Intrinsics.checkNotNullExpressionValue((Object)string3, (String)"player.toString()");
        this.connection.publish(string2, (Object)string3);
        waitGroup.await(10L, TimeUnit.SECONDS);
        this.waitGroups.remove(player2);
    }
}

