package xuanmo.arcartxsuite.onlinerewards.model;

import java.util.UUID;

public record OnlineRewardsLeaderboardEntry(
    UUID playerUuid,
    String playerName,
    int minutes
) {
}
