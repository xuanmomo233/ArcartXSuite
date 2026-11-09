package xuanmo.arcartxsuite.vanilla.recipe;

import xuanmo.arcartxsuite.api.inventory.ItemPresentation;
import xuanmo.arcartxsuite.api.inventory.MaterialStock;
import xuanmo.arcartxsuite.api.inventory.ItemSnapshots;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;

/** Offers are bound to the actual open view and their full costs/result identity. */
public final class NativeOfferBook {
    private record Offer(WorkshopType type,InventoryView view,int index,String digest,List<ItemStack> costs,ItemStack output,BrewingRecipes.Mix mix) { }
    private static final class Session {
        final InventoryView view;final String token=UUID.randomUUID().toString();
        final Map<String,Offer> offers=new LinkedHashMap<>();
        Session(InventoryView view){this.view=view;}
    }
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final BrewingRecipes brewing=new BrewingRecipes();
    private final ItemPresentation presentation;
    public NativeOfferBook(ItemBridgeAPI items,VanillaItemNameBridge names){this.presentation=new ItemPresentation(items,names);}
    public void close(Player player){sessions.remove(player.getUniqueId());}
    public void clear(){sessions.clear();}
    public String icon(ItemStack item) { return presentation.icon(item); }
    public String name(ItemStack item) { return presentation.name(item); }
    private static List<ItemStack> costs(MerchantRecipe recipe){
        List<ItemStack> original=recipe.getIngredients();
        return Arrays.asList(original.isEmpty()?null:recipe.getAdjustedIngredient1(),original.size()>1?original.get(1).clone():null);
    }
    private static String merchantDigest(MerchantRecipe recipe){
        var costs=costs(recipe);
        return ItemSnapshots.hash(ItemSnapshots.encode(costs.get(0))+":"+ItemSnapshots.encode(costs.get(1))+":"+ItemSnapshots.encode(recipe.getResult()));
    }
    private boolean valid(Offer offer,boolean selected){
        if(!offer.type().matches(offer.view()) || offer.view().getPlayer().getOpenInventory()!=offer.view())return false;
        if(offer.type()==WorkshopType.MERCHANT){
            MerchantInventory inv=(MerchantInventory)offer.view().getTopInventory();
            if(offer.index()>=inv.getMerchant().getRecipeCount() || selected&&inv.getSelectedRecipeIndex()!=offer.index())return false;
            MerchantRecipe recipe=inv.getMerchant().getRecipe(offer.index());
            return recipe.getUses()<recipe.getMaxUses() && merchantDigest(recipe).equals(offer.digest());
        }
        BrewerInventory inv=(BrewerInventory)offer.view().getTopInventory();
        if(inv.getHolder()==null || inv.getHolder().getBrewingTime()>0)return false;
        ItemStack result=brewing.result(offer.mix().input(),offer.mix().ingredient());
        return result!=null && result.isSimilar(offer.output());
    }
    private static List<ItemStack> actual(Player player,MaterialStock stock){
        List<ItemStack> result=new ArrayList<>();
        for(ItemStack item:player.getInventory().getStorageContents())if(!InventoryItems.empty(item))result.add(item);
        for(ItemStack item:player.getOpenInventory().getTopInventory().getContents())if(!InventoryItems.empty(item))result.add(item);
        if(stock.warehouse()!=null&&stock.warehouse().available())for(var row:stock.warehouse().entries())result.add(row.item());
        return result;
    }
    private List<ItemStack> wanted(Offer offer){
        if(offer.type()==WorkshopType.MERCHANT)return offer.costs();
        List<ItemStack> result=new ArrayList<>(Arrays.asList(null,null,null,offer.mix().ingredient().clone()));
        Inventory top=offer.view().getTopInventory();boolean any=false;
        for(int i=0;i<3;i++)if(!InventoryItems.empty(top.getItem(i))&&top.getItem(i).isSimilar(offer.mix().input())){result.set(i,offer.mix().input().clone());any=true;}
        if(!any)result.set(0,offer.mix().input().clone());
        return result;
    }
    private long availableTrades(Player player,MaterialStock stock,List<ItemStack> costs,int limit){
        Map<ItemStack,Long> required=new LinkedHashMap<>();
        for(ItemStack item:costs)if(!InventoryItems.empty(item)){ItemStack key=item.clone();key.setAmount(1);required.merge(key,(long)item.getAmount(),Long::sum);}
        if(required.isEmpty())return 0;
        long max=limit;
        for(var need:required.entrySet()){
            long count=0;
            for(ItemStack item:player.getInventory().getStorageContents())if(!InventoryItems.empty(item)&&item.isSimilar(need.getKey()))count+=item.getAmount();
            Inventory top=player.getOpenInventory().getTopInventory();
            for(int i=0;i<2;i++)if(!InventoryItems.empty(top.getItem(i))&&top.getItem(i).isSimilar(need.getKey()))count+=top.getItem(i).getAmount();
            if(stock.warehouse()!=null&&stock.warehouse().available())for(var row:stock.warehouse().entries())if(row.item().isSimilar(need.getKey()))count+=Math.min(row.amount(),Long.MAX_VALUE-count);
            max=Math.min(max,count/need.getValue());
        }
        return max;
    }
    public Map<String,Object> collect(Player player,WorkshopType type,MaterialStock stock){
        InventoryView view=player.getOpenInventory();Session session=sessions.get(player.getUniqueId());
        if(session==null||session.view!=view){session=new Session(view);sessions.put(player.getUniqueId(),session);}
        session.offers.clear();Map<String,Object> result=new LinkedHashMap<>();
        if(type==WorkshopType.MERCHANT){
            Merchant merchant=((MerchantInventory)view.getTopInventory()).getMerchant();
            for(int i=0;i<merchant.getRecipeCount();i++){
                MerchantRecipe recipe=merchant.getRecipe(i);String digest=merchantDigest(recipe),key=session.token+":"+i+":"+digest;
                Offer offer=new Offer(type,view,i,digest,costs(recipe),recipe.getResult(),null);session.offers.put(key,offer);
                Map<String,Object> entry=entry(player,offer,stock);
                int remaining=Math.max(0,recipe.getMaxUses()-recipe.getUses());
                entry.put("stock",remaining);entry.put("availability","可交易 "+availableTrades(player,stock,offer.costs(),remaining)+" 次");
                entry.put("cost1",name(offer.costs().get(0))+" ×"+(InventoryItems.empty(offer.costs().get(0))?0:offer.costs().get(0).getAmount()));
                entry.put("cost2",InventoryItems.empty(offer.costs().get(1))?"":name(offer.costs().get(1))+" ×"+offer.costs().get(1).getAmount());
                result.put(key,entry);
            }
        }else for(var mix:brewing.collect(actual(player,stock))){
            String key=session.token+":brew:"+mix.key();Offer offer=new Offer(type,view,-1,mix.key(),List.of(mix.input(),mix.ingredient()),mix.result(),mix);
            session.offers.put(key,offer);result.put(key,entry(player,offer,stock));
        }
        return result;
    }
    private Map<String,Object> entry(Player player,Offer offer,MaterialStock stock){
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("resultJson",icon(offer.output()));row.put("resultAmount",offer.output().getAmount());
        row.put("resultName",offer.output().getType().name().toLowerCase().replace('_',' '));row.put("resultZhName",name(offer.output()));
        row.put("previewGrid",offer.costs().stream().map(this::icon).toList());
        row.put("previewAmounts",offer.costs().stream().map(item->InventoryItems.empty(item)?0:item.getAmount()).toList());
        row.put("canCraft",valid(offer,false)&&ExactMaterialPlan.build(player,offer.type(),wanted(offer),stock)!=null?1:0);
        return row;
    }
    private Offer get(Player player,String key){Session session=sessions.get(player.getUniqueId());return session==null||session.view!=player.getOpenInventory()?null:session.offers.get(key);}
    public boolean selectMerchant(Player player,String key){Offer offer=get(player,key);return offer!=null&&offer.type()==WorkshopType.MERCHANT&&valid(offer,false)&&MerchantSelection.select(player,offer.index())&&valid(offer,true);}
    public RecipeMaterialPlanner.CustomDemand demand(Player player,String key){
        Offer offer=get(player,key);
        if(offer==null||!valid(offer,offer.type()==WorkshopType.MERCHANT))return null;
        return new RecipeMaterialPlanner.CustomDemand(wanted(offer),()->valid(offer,offer.type()==WorkshopType.MERCHANT),key);
    }
}
