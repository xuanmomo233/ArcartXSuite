package xuanmo.arcartxsuite.fishing.storage;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.MigrationResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
import xuanmo.arcartxsuite.fishing.model.FishingPlayerData;

/**
 * 钓鱼模块的数据仓储接口，定义玩家数据与图鉴条目的持久化操作。
 * <p>
 * 关联模块：{@code fishing}。默认实现为 {@link JdbcFishingRepository}。
 */
public interface FishingRepository {

    void initialize() throws Exception;

    void shutdown();

    @NotNull FishingPlayerData loadPlayerData(@NotNull UUID uuid);

    void savePlayerData(@NotNull FishingPlayerData data);

    @Nullable FishCollectionEntry loadCollectionEntry(@NotNull UUID playerUuid, @NotNull String fishId);

    void saveCollectionEntry(@NotNull FishCollectionEntry entry);

    @NotNull List<FishCollectionEntry> loadCollection(@NotNull UUID playerUuid);
}
