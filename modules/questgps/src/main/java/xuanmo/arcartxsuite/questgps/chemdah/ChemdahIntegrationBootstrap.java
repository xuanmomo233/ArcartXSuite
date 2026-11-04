package xuanmo.arcartxsuite.questgps.chemdah;

public record ChemdahIntegrationBootstrap(ChemdahMetadataReader metadataReader,
                                        ChemdahRewardReader rewardReader,
                                        ChemdahTrackerBridge trackerBridge) {}
