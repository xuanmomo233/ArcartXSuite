package xuanmo.arcartxsuite.market.storage;

import java.util.UUID;

/** Durable business operation or delivery task; external effects are never blindly replayed. */
public record MarketOperation(String id, String tradeId, UUID player, String kind, String reference,
                              String state, String currency, String amount, String itemData,
                              String note, String owner, long createdAt, long updatedAt,String itemName,String material) {
    public MarketOperation(String id,String tradeId,UUID player,String kind,String reference,String state,String currency,String amount,
                           String itemData,String note,String owner,long createdAt,long updatedAt){
        this(id,tradeId,player,kind,reference,state,currency,amount,itemData,note,owner,createdAt,updatedAt,"","");
    }
    public MarketOperation withItem(String name,String type){return new MarketOperation(id,tradeId,player,kind,reference,state,currency,amount,itemData,note,owner,createdAt,updatedAt,name,type);}
    public MarketOperation withItem(String name,String type,String snapshot){return new MarketOperation(id,tradeId,player,kind,reference,state,currency,amount,itemData.isBlank()?snapshot:itemData,note,owner,createdAt,updatedAt,name,type);}
}
