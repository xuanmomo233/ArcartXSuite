package xuanmo.arcartxsuite.entitytracker.boss.tracker;

public record BossDamagePlayerSettlementView(
    BossDamageSettlementRecord settlement,
    BossDamageSettlementEntry entry
) {

    private static final BossDamagePlayerSettlementView EMPTY = new BossDamagePlayerSettlementView(
        BossDamageSettlementRecord.empty(),
        BossDamageSettlementEntry.empty()
    );

    public static BossDamagePlayerSettlementView empty() {
        return EMPTY;
    }
}

