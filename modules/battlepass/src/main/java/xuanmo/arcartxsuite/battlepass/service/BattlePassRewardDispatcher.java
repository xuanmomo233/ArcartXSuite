package xuanmo.arcartxsuite.battlepass.service;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository;

/** Shares the same durable reward request between player claims and season settlement. */
final class BattlePassRewardDispatcher {
    enum Result { ACCEPTED, ALREADY_ACCEPTED, RETRY }

    private static final String MODULE_ID = "battlepass";
    private static final Gson GSON = new Gson();
    private record StoredRewards(UUID receiptStoreId, List<PendingReward> rewards) {}
    private final BattlePassRepository repository;
    private final PendingRewardService pending;

    BattlePassRewardDispatcher(BattlePassRepository repository, PendingRewardService pending) {
        this.repository = repository;
        this.pending = pending;
    }

    Result accept(UUID player, String season, String rewardId, BattlePassReward reward) throws SQLException {
        if (pending == null) return Result.RETRY;
        boolean sharedProgress = BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress();
        UUID storeId = BuildEdition.CROSS_SERVER_INCLUDED && sharedProgress ? pending.rewardReceiptStoreId() : null;
        if (BuildEdition.CROSS_SERVER_INCLUDED && sharedProgress && storeId == null) return Result.RETRY;
        var batch = createBatch(player, rewardId, reward);
        String payload = batch.isEmpty() ? null
            : GSON.toJson(BuildEdition.CROSS_SERVER_INCLUDED && sharedProgress ? new StoredRewards(storeId, batch) : batch);
        var request = repository.stageReward(player, season, rewardId, payload);
        if (request == null) return Result.RETRY;
        // Retry with the original payload even if the reward configuration has changed.
        List<PendingReward> staged;
        try {
            var original = JsonParser.parseString(request.payload());
            if (original.isJsonObject()) {
                // A previously staged customer reward may already be accepted by its original receipt store.
                var stored = GSON.fromJson(original, StoredRewards.class);
                UUID currentStoreId = pending.rewardReceiptStoreId();
                if (currentStoreId == null || !currentStoreId.equals(stored.receiptStoreId()) || stored.rewards() == null) {
                    return Result.RETRY;
                }
                staged = stored.rewards();
            } else if (!sharedProgress && original.isJsonArray()) {
                staged = Arrays.asList(GSON.fromJson(original, PendingReward[].class));
            } else {
                return Result.RETRY;
            }
        } catch (JsonParseException malformed) {
            return Result.RETRY;
        }
        if (staged.isEmpty() || !pending.savePendingBatchConfirmed("battlepass:" + request.id(), staged)) {
            return Result.RETRY;
        }
        if (repository.completeReward(player, season, rewardId, request.id())) return Result.ACCEPTED;
        return repository.loadClaimedRewards(player, season).contains(rewardId) ? Result.ALREADY_ACCEPTED : Result.RETRY;
    }

    void flushOnline(org.bukkit.entity.Player player) {
        if (pending != null) pending.flushPlayer(player);
    }

    private List<PendingReward> createBatch(UUID player, String rewardId, BattlePassReward reward) {
        String description = "战令奖励 " + rewardId;
        return switch (reward.type().toLowerCase(Locale.ROOT)) {
            case "item" -> reward.item() == null ? List.of() : List.of(item(player, description, reward, false));
            case "currency" -> reward.currency() == null || reward.currency().isBlank()
                    || !Double.isFinite(reward.currencyAmount()) || reward.currencyAmount() <= 0
                ? List.of()
                : List.of(PendingReward.currency(player, MODULE_ID, reward.currency(), reward.currencyAmount(), description));
            case "command" -> commands(player, description, reward);
            case "title" -> targetReward(player, description, reward.targetId(), PendingReward.Type.TITLE);
            case "map-anchor" -> targetReward(player, description, reward.targetId(), PendingReward.Type.MAP_ANCHOR);
            case "mail" -> {
                if (reward.mailPresets() != null && !reward.mailPresets().isEmpty()) {
                    yield reward.mailPresets().stream().filter(preset -> !preset.isBlank())
                        .map(preset -> PendingReward.mailPreset(player, MODULE_ID, List.of(preset), description)).toList();
                }
                yield reward.item() == null ? commands(player, description, reward)
                    : List.of(item(player, description, reward, true));
            }
            default -> List.of();
        };
    }

    private static List<PendingReward> targetReward(UUID player, String description, String target, PendingReward.Type type) {
        if (target == null || target.isBlank()) return List.of();
        return List.of(new PendingReward(0, player, MODULE_ID, type,
            GSON.toJson(java.util.Map.of("targetId", target.trim())), description, System.currentTimeMillis(), 0));
    }

    private static List<PendingReward> commands(UUID player, String description, BattlePassReward reward) {
        if (reward.commands() == null) return List.of();
        var commands = reward.commands().stream().filter(command -> !command.isBlank()).toList();
        return commands.isEmpty() ? List.of() : List.of(PendingReward.command(player, MODULE_ID, commands, description));
    }

    private static PendingReward item(UUID player, String description, BattlePassReward reward, boolean mail) {
        String subject = mail ? (reward.mailSubject() == null ? "战令奖励" : reward.mailSubject()) : null;
        String body = mail ? (reward.mailBody() == null ? "您收到了战令奖励，请查收附件。" : reward.mailBody()) : null;
        var spec = new PendingReward.ItemSpec(reward.item(), subject, body);
        return new PendingReward(0, player, MODULE_ID, PendingReward.Type.ITEM_SPEC, GSON.toJson(spec),
            description, System.currentTimeMillis(), 0);
    }
}
