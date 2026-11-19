package xuanmo.arcartxsuite.fishing.reward;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.RewardReservation;

/** Reserves each season reward step and confirms persistent ownership before advancing it. */
public final class RewardDispatcher {
    private static final String SOURCE = "fishing";
    private static final Gson JSON = new Gson();
    private final FishingRepository repository;
    private final PendingRewardService pendingRewardService;
    private final Logger logger;
    private final String seasonId;
    private DeliveryOwner deliveryOwner;

    public RewardDispatcher(FishingRepository repository, PendingRewardService pendingRewardService,
                            Logger logger, String seasonId) {
        this.repository = repository;
        this.pendingRewardService = pendingRewardService;
        this.logger = logger;
        this.seasonId = seasonId;
    }

    @FunctionalInterface
    public interface DeliveryOwner {
        String get() throws IOException;
    }

    public void configureDeliveryOwner(DeliveryOwner deliveryOwner) {
        this.deliveryOwner = deliveryOwner;
    }

    public static String rewardKey(String kind, String rewardId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((kind + ":" + rewardId).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean dispatch(UUID uuid, String key, SeasonRewardDefinition reward, String description) {
        if (pendingRewardService == null || deliveryOwner == null) return false;
        List<PendingReward> configured = switch (SeasonRewardDefinition.Type.fromKey(reward.type())) {
            case ITEM -> reward.item() == null || reward.item().amount() <= 0 ? List.of()
                : List.of(new PendingReward(0, uuid, SOURCE, PendingReward.Type.ITEM_SPEC,
                    JSON.toJson(new PendingReward.ItemSpec(reward.item(), null, null)), description,
                    System.currentTimeMillis(), 0));
            case CURRENCY -> reward.currencyId() == null || reward.currencyId().isBlank()
                    || !Double.isFinite(reward.currencyAmount()) || reward.currencyAmount() <= 0 ? List.of()
                : List.of(PendingReward.currency(uuid, SOURCE, reward.currencyId(), reward.currencyAmount(), description));
            case COMMAND -> steps(reward.commands(), command -> PendingReward.command(uuid, SOURCE, List.of(command), description));
            case MAIL -> steps(reward.mailPresets(), preset -> PendingReward.mailPreset(uuid, SOURCE, List.of(preset), description));
        };
        String proposed = configured.isEmpty() || configured.stream().anyMatch(java.util.Objects::isNull)
            ? null : JSON.toJson(configured);

        int step = 0;
        try {
            String frozen = repository.reserveRewardPlan(uuid, seasonId, key, deliveryOwner.get(), proposed);
            if (frozen == null) return false;
            List<PendingReward> steps = List.of(JSON.fromJson(frozen, PendingReward[].class));
            if (steps.isEmpty() || steps.stream().anyMatch(pending -> !uuid.equals(pending.playerUuid())
                    || !SOURCE.equals(pending.sourceModule()))) {
                throw new IllegalStateException("钓鱼奖励凭证内容不符");
            }
            for (; step < steps.size(); step++) {
                RewardReservation reservation = repository.reserveReward(uuid, seasonId, key, step);
                if (reservation == RewardReservation.DELIVERED) continue;
                var pending = steps.get(step);
                boolean accepted = pendingRewardService.savePendingBatchConfirmed(
                    "season:" + seasonId + ":" + uuid + ":" + key + ":" + step, List.of(pending));
                if (!accepted) return false;
                repository.finishReward(uuid, seasonId, key, step, true);
            }
            repository.markRewardComplete(uuid, seasonId, key);
            return true;
        } catch (IOException | SQLException | RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "钓鱼奖励发放未确认: player=" + uuid + " season=" + seasonId
                + " reward=" + reward.rewardId() + " key=" + key + " step=" + step, failure);
            return false;
        }
    }

    public void flushPlayer(Player player) {
        if (pendingRewardService != null) pendingRewardService.flushPlayer(player);
    }

    private static List<PendingReward> steps(@Nullable List<String> values, Function<String, PendingReward> factory) {
        if (values == null) return List.of();
        // Keep list positions so earlier receipts remain attached to their configured action.
        return values.stream().map(value -> value == null || value.isBlank() ? null : factory.apply(value)).toList();
    }
}
