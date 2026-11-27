package xuanmo.arcartxsuite.entitytracker.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.config.CrossServerRankingSettings;

public final class CrossServerRankingCacheService {
    public CrossServerRankingCacheService(JavaPlugin plugin, Logger logger, CrossServerRankingSettings settings,
                                          DataSource dataSource, Supplier<PluginConfiguration> configuration,
                                          Supplier<String> node, BooleanSupplier allowed) {
        throw new UnsupportedOperationException("Cross-server boss ranking cache is not included");
    }

    public void start() {}
    public void shutdown() {}
    public void requestKillRefresh() {}
    public CompletionStage<Void> databaseStopped() { return CompletableFuture.completedFuture(null); }
}
