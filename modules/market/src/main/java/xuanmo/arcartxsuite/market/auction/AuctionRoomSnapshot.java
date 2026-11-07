package xuanmo.arcartxsuite.market.auction;

import java.util.List;

/** One database read transaction: public lot data plus the viewer's own delivery progress. */
public record AuctionRoomSnapshot(AuctionListing listing,List<AuctionBid> recentBids,
                                  int bidCount,int bidderCount,int watchCount,boolean followed,
                                  double myHighestBid,long endedAt,String endType,
                                  int myPendingDeliveries,int myReviewDeliveries,int myDoneDeliveries) {}
