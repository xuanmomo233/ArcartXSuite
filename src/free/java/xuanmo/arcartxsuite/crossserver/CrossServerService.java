package xuanmo.arcartxsuite.crossserver;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerDelivery;

/** Compatibility API for modules on the single-server edition. */
public final class CrossServerService implements CrossServerAPI {
    private final JavaPlugin plugin;
    private volatile String nodeId = "default";

    public CrossServerService(JavaPlugin plugin, BooleanSupplier transportAllowed) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public void start(CompletionStage<Void> modulesReady) {
        Objects.requireNonNull(modulesReady);
        String configured = plugin.getConfig().getString("cross-server.node-id", "default");
        nodeId = configured == null || configured.isBlank() ? "default" : configured.trim();
    }

    public CompletionStage<Void> shutdown() { return CompletableFuture.completedFuture(null); }
    @Override public String nodeId() { return nodeId; }
    public boolean isRedisAvailable() { return false; }

    @Override public CrossServerChannel openChannel(String moduleId, CrossServerChannelConfig config,
                                                     Consumer<CrossServerDelivery> consumer) {
        Objects.requireNonNull(consumer);
        return new DisabledChannel(moduleId);
    }

    @Override public CrossServerChannel openChannelAdmitting(String moduleId, CrossServerChannelConfig config,
                                                              Predicate<CrossServerDelivery> admit) {
        Objects.requireNonNull(admit);
        return new DisabledChannel(moduleId);
    }

    @Override public CrossServerChannel openChannelCompleting(String moduleId, CrossServerChannelConfig config,
                                      Function<CrossServerDelivery, CompletionStage<Boolean>> admit) {
        Objects.requireNonNull(admit);
        return new DisabledChannel(moduleId);
    }

    private record DisabledChannel(String moduleId) implements CrossServerChannel {
        private DisabledChannel {
            if (moduleId == null || moduleId.isBlank() || moduleId.length() > 128
                    || moduleId.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid cross-server module id");
            }
        }

        @Override public boolean isActive() { return false; }
        @Override public void publish(String payload) {}
        @Override public CompletionStage<Boolean> publishDurably(String payload) {
            return CompletableFuture.completedFuture(false);
        }
        @Override public CompletionStage<Boolean> publishWithReceipt(String payload) {
            return CompletableFuture.completedFuture(false);
        }
        @Override public boolean requiresSharedStateCatchUp() { return false; }
        @Override public void close() {}
    }
}
