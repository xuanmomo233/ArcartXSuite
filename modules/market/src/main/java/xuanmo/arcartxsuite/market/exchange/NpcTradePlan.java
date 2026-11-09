package xuanmo.arcartxsuite.market.exchange;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.inventory.*;

/** Plans all inventory costs and frozen item rewards before any external debit. */
public record NpcTradePlan(ItemStack[] before,ItemStack[] after,List<WarehouseMaterialAccess.Request> requests,
                           Map<String,BigDecimal> debits,Map<String,BigDecimal> credits,List<String> rewardSnapshots) {
    private record Source(ItemStack item,int kind,int slot,long amount) { }
    private record Need(TradeCatalog.Asset asset,Predicate<ItemStack> matches,int candidates) { }
    public static NpcTradePlan create(Player player,TradeCatalog.Offer offer,MaterialStock stock,ItemSourceRegistry registry){
        ItemStack[] before=InventoryItems.copy(player.getInventory().getStorageContents()),after=InventoryItems.copy(before);
        List<Source> sources=new ArrayList<>();
        for(int i=0;i<before.length;i++)if(!InventoryItems.empty(before[i]))sources.add(new Source(before[i],0,i,before[i].getAmount()));
        if(stock.warehouse()!=null&&stock.warehouse().available())for(var entry:stock.warehouse().entries())sources.add(new Source(entry.item(),1,entry.slot(),entry.amount()));
        List<Need> needs=new ArrayList<>();Map<String,BigDecimal> debits=new LinkedHashMap<>(),credits=new LinkedHashMap<>();
        for(var asset:offer.costs()){
            if(asset.currency()){debits.merge(asset.id(),asset.quantity(),BigDecimal::add);continue;}
            Predicate<ItemStack> matcher=asset.matcher(registry,player);
            int candidates=(int)sources.stream().filter(s->matcher.test(s.item())).count();
            needs.add(new Need(asset,matcher,candidates));
        }
        needs.sort(Comparator.comparingInt(Need::candidates));
        long[] left=sources.stream().mapToLong(Source::amount).toArray();Map<Integer,Integer> warehouse=new LinkedHashMap<>();
        for(Need need:needs){
            int remaining=need.asset().itemAmount();
            for(int n=0;n<sources.size()&&remaining>0;n++)if(need.matches().test(sources.get(n).item())){
                int take=(int)Math.min(remaining,left[n]);left[n]-=take;remaining-=take;
                if(take>0&&sources.get(n).kind()==1)warehouse.merge(sources.get(n).slot(),take,Integer::sum);
            }
            if(remaining!=0)throw new IllegalArgumentException("兑换所需物品不足");
        }
        for(int n=0;n<sources.size();n++)if(sources.get(n).kind()==0){Source source=sources.get(n);after[source.slot()]=left[n]==0?null:source.item().clone();if(after[source.slot()]!=null)after[source.slot()].setAmount((int)left[n]);}
        List<String> outputs=new ArrayList<>();
        for(var asset:offer.rewards()){
            if(asset.currency()){credits.merge(asset.id(),asset.quantity(),BigDecimal::add);continue;}
            ItemStack item=asset.create(registry,player);outputs.add(ItemSnapshots.encode(item));
            if(!InventoryItems.put(after,item))throw new IllegalArgumentException("背包没有空间接收全部兑换物品，请先整理");
        }
        if(warehouse.size()>9)throw new IllegalArgumentException("本次仓库取料条目过多，请先取部分到背包");
        return new NpcTradePlan(before,after,warehouse.entrySet().stream().map(e->new WarehouseMaterialAccess.Request(e.getKey(),e.getValue())).toList(),Map.copyOf(debits),Map.copyOf(credits),List.copyOf(outputs));
    }
    public boolean unchanged(Player player){return Arrays.equals(before,player.getInventory().getStorageContents());}
    public void apply(Player player){
        if(!unchanged(player))throw new IllegalStateException("背包已变化");
        List<Integer> changed=new ArrayList<>();for(int i=0;i<before.length;i++)if(!Objects.equals(before[i],after[i]))changed.add(i);
        for(int i:changed)player.getInventory().setItem(i,null);
        for(int i:changed)player.getInventory().setItem(i,InventoryItems.copy(after[i]));
        player.updateInventory();
    }
    public Map<String,Object> evidence(){
        Map<String,Object> value=new LinkedHashMap<>();Map<String,Object> changed=new LinkedHashMap<>();
        for(int i=0;i<before.length;i++)if(!Objects.equals(before[i],after[i]))changed.put(Integer.toString(i),Map.of("before",ItemSnapshots.encode(before[i]),"after",ItemSnapshots.encode(after[i])));
        value.put("storageChanges",changed);
        value.put("warehouse",requests);value.put("debits",debits);value.put("credits",credits);value.put("rewardSnapshots",rewardSnapshots);return value;
    }
}
