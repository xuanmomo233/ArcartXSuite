package xuanmo.arcartxsuite.vanilla.recipe;

import java.util.*;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;

/** Builds a complete one-recipe placement before mutating either inventory. */
public final class RecipeMaterialPlanner {
    private final Set<UUID> transfers = new HashSet<>();
    private boolean closed;
    private long generation;
    private WarehouseMaterialAccess transferProvider;
    public void cancelPending(){generation++;transfers.clear();}
    public void close(){closed=true;transfers.clear();}
    private final List<xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter> adapters = new ArrayList<>(List.of(new NativeMaterialContainerAdapter(false), new NativeMaterialContainerAdapter(true)));
    public boolean busy(Player player) {
        if(!transfers.isEmpty() && provider.get()!=transferProvider)cancelPending();
        return transfers.contains(player.getUniqueId());
    }
    public void registerAdapter(xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter adapter) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("main thread required");
        if (adapters.stream().anyMatch(a -> a.id().equals(adapter.id()))) throw new IllegalArgumentException("duplicate adapter");
        adapters.add(adapter);
    }
    private Supplier<WarehouseMaterialAccess> provider = () -> null;
    public void setProvider(Supplier<WarehouseMaterialAccess> provider) { this.provider = provider; }
    public record Stock(WarehouseMaterialAccess access, WarehouseMaterialAccess.Snapshot warehouse, List<Source> warehouseSources) { }
    public Stock stock(Player player) {
        WarehouseMaterialAccess access = provider.get();
        var snapshot = access == null ? null : access.materials(player);
        List<Source> sources = new ArrayList<>();
        if (snapshot != null && snapshot.available()) for (var e : snapshot.entries()) sources.add(new Source(e.item(),2,e.slot(),e.amount()));
        return new Stock(access, snapshot, List.copyOf(sources));
    }
    public void stockAsync(Player player,java.util.function.Consumer<Stock> callback){
        WarehouseMaterialAccess access=provider.get();
        if(access==null){callback.accept(new Stock(null,null,List.of()));return;}
        access.materialsAsync(player,snapshot->{
            List<Source> sources=new ArrayList<>();
            if(snapshot.available())for(var e:snapshot.entries())sources.add(new Source(e.item(),2,e.slot(),e.amount()));
            callback.accept(new Stock(access,snapshot,List.copyOf(sources)));
        });
    }
    private static boolean empty(ItemStack s) { return s == null || s.getType().isAir() || s.getAmount() <= 0; }
    private static ItemStack copy(ItemStack s) { return empty(s) ? null : s.clone(); }
    private static ItemStack[] copy(ItemStack[] a) { return Arrays.stream(a).map(RecipeMaterialPlanner::copy).toArray(ItemStack[]::new); }
    private static RecipeChoice[] choices(Recipe recipe) {
        RecipeChoice[] result = new RecipeChoice[recipe instanceof FurnaceRecipe ? 1 : 9];
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape(); var map = shaped.getChoiceMap();
            for (int y=0;y<shape.length;y++) for(int x=0;x<shape[y].length();x++) result[y*3+x]=map.get(shape[y].charAt(x));
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            var list=shapeless.getChoiceList(); for(int i=0;i<list.size();i++) result[i]=list.get(i);
        } else if (recipe instanceof FurnaceRecipe furnace) result[0]=furnace.getInputChoice();
        return result;
    }
    private record Source(ItemStack item, int kind, int slot, long count) { }
    private record Plan(ItemStack[] storage, ItemStack[] target, List<WarehouseMaterialAccess.Request> requests) { }

    public boolean canMake(Player player, Recipe recipe, Stock stock) { return plan(player,recipe,stock) != null; }

    private Plan plan(Player player, Recipe recipe, Stock stock) {
        RecipeChoice[] needed=choices(recipe);
        ItemStack[] storage=copy(player.getInventory().getStorageContents());
        Inventory top=player.getOpenInventory().getTopInventory();
        ItemStack[] current=new ItemStack[needed.length];
        if (needed.length==9 && top instanceof CraftingInventory crafting && top.getSize()==10) current=copy(crafting.getMatrix());
        if (needed.length==1 && top instanceof FurnaceInventory) current[0]=copy(top.getItem(0));
        boolean ready=true;
        for(int i=0;i<needed.length;i++) {
            if(needed[i]==null ? !empty(current[i]) : empty(current[i]) || !needed[i].test(current[i])) ready=false;
        }
        if(ready) return new Plan(storage,current,List.of());
        List<Source> sources=new ArrayList<>();
        for(int i=0;i<current.length;i++) if(!empty(current[i])) sources.add(new Source(current[i],0,i,current[i].getAmount()));
        for(int i=0;i<storage.length;i++) if(!empty(storage[i])) sources.add(new Source(storage[i],1,i,storage[i].getAmount()));
        for (Source source : stock.warehouseSources()) {
            if (Arrays.stream(needed).anyMatch(choice -> choice != null && choice.test(source.item()))) sources.add(source);
        }
        List<Integer> order=new ArrayList<>();
        for(int i=0;i<needed.length;i++) if(needed[i]!=null) order.add(i);
        if(order.isEmpty()) return null;
        order.sort(Comparator.comparingLong(i -> sources.stream().filter(s->needed[i].test(s.item())).count()));
        long[] remaining=sources.stream().mapToLong(Source::count).toArray();
        int[] selected=new int[needed.length]; Arrays.fill(selected,-1);
        if(!allocate(0,order,needed,sources,remaining,selected,new HashSet<>())) return null;
        ItemStack[] target=new ItemStack[needed.length];
        Map<Integer,Integer> warehouseUse=new LinkedHashMap<>();
        for(int i=0;i<selected.length;i++) if(selected[i]>=0) {
            Source s=sources.get(selected[i]); target[i]=s.item().clone(); target[i].setAmount(1);
            if(s.kind()==2) warehouseUse.merge(s.slot(),1,Integer::sum);
        }
        // Preserve surplus stacks already in the correct target position.
        for(int n=0;n<sources.size();n++) {
            Source s=sources.get(n); int left=(int)Math.min(Integer.MAX_VALUE,remaining[n]);
            if(s.kind()==1) { storage[s.slot()]=left==0?null:s.item().clone(); if(left>0) storage[s.slot()].setAmount(left); }
        }
        for(int n=0;n<sources.size();n++) {
            Source s=sources.get(n); if(s.kind()!=0 || remaining[n]==0) continue;
            int left=(int)remaining[n];
            if(target[s.slot()]!=null && target[s.slot()].isSimilar(s.item())) {
                int add=Math.min(left,target[s.slot()].getMaxStackSize()-target[s.slot()].getAmount());
                target[s.slot()].setAmount(target[s.slot()].getAmount()+add); left-=add;
            }
            if(left>0) { ItemStack returned=s.item().clone();returned.setAmount(left); if(!put(storage,returned)) return null; }
        }
        return new Plan(storage,target,warehouseUse.entrySet().stream().map(e->new WarehouseMaterialAccess.Request(e.getKey(),e.getValue())).toList());
    }
    private static boolean allocate(int depth,List<Integer> order,RecipeChoice[] needed,List<Source> sources,long[] remaining,int[] selected,Set<String> failed) {
        if(depth==order.size()) return true;
        String state=depth+":"+Arrays.toString(remaining);
        if(failed.contains(state)) return false;
        int slot=order.get(depth);
        for(int i=0;i<sources.size();i++) if(remaining[i]>0 && needed[slot].test(sources.get(i).item())) {
            remaining[i]--;selected[slot]=i;
            if(allocate(depth+1,order,needed,sources,remaining,selected,failed)) return true;
            remaining[i]++;selected[slot]=-1;
        }
        failed.add(state);
        return false;
    }
    private static boolean put(ItemStack[] inventory,ItemStack item) {
        int left=item.getAmount();
        for(ItemStack s:inventory) if(!empty(s)&&s.isSimilar(item)) { int add=Math.min(left,s.getMaxStackSize()-s.getAmount());s.setAmount(s.getAmount()+add);left-=add;if(left==0)return true; }
        for(int i=0;i<inventory.length;i++) if(empty(inventory[i])) {int add=Math.min(left,item.getMaxStackSize());inventory[i]=item.clone();inventory[i].setAmount(add);left-=add;if(left==0)return true;}
        return left==0;
    }
    public void fill(Player player,String key,boolean furnace,java.util.function.Consumer<String> callback) {
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("main thread required");
        if(closed || busy(player)){callback.accept("材料服务暂停或上一笔填料处理中");return;}
        var view=player.getOpenInventory();long epoch=generation;
        transferProvider=provider.get();
        transfers.add(player.getUniqueId());
        stockAsync(player,stock->{
            if(epoch!=generation)return;
            java.util.function.Consumer<String> done=message->{if(epoch==generation){transfers.remove(player.getUniqueId());callback.accept(message);}};
            if(closed || !player.isOnline() || player.getOpenInventory()!=view){done.accept("界面已变化，未扣取材料");return;}
            try{fillWithStock(player,key,furnace,stock,done);}catch(RuntimeException e){done.accept("材料计划异常，未继续交付");}
        });
    }
    private void fillWithStock(Player player,String key,boolean furnace,Stock stock,java.util.function.Consumer<String> callback) {
        long epoch=generation;
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("main thread required");
        if(closed){callback.accept("材料服务暂停");return;}
        if(!empty(player.getItemOnCursor())){callback.accept("请先放下光标上的物品");return;}
        NamespacedKey id=NamespacedKey.fromString(key);
        Recipe recipe=id==null?null:Bukkit.getRecipe(id);
        InventoryView view=player.getOpenInventory();
        if(id==null || !player.getDiscoveredRecipes().contains(id) || (furnace?!(recipe instanceof FurnaceRecipe):!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe))) {callback.accept("配方已失效，请刷新");return;}
        var adapter=adapters.stream().filter(a->a.supports(view)).findFirst().orElse(null);
        if(adapter==null){callback.accept("此容器尚无材料适配器");return;}
        Plan plan=plan(player,recipe,stock);
        if(plan==null){callback.accept("材料不足，或背包没有空间退回原材料");return;}
        Map<Integer,ItemStack> storageChanges=new LinkedHashMap<>(),targetChanges=new LinkedHashMap<>();
        ItemStack[] currentStorage=player.getInventory().getStorageContents();
        for(int i=0;i<plan.storage().length;i++) if(!Objects.equals(copy(currentStorage[i]),copy(plan.storage()[i])))storageChanges.put(i,plan.storage()[i]);
        for(int i=0;i<plan.target().length;i++){
            int slot=furnace?0:i+1;
            if(!Objects.equals(copy(view.getTopInventory().getItem(slot)),copy(plan.target()[i])))targetChanges.put(slot,plan.target()[i]);
        }
        final xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter.Delivery delivery;
        try{delivery=adapter.capture(player,view,storageChanges,targetChanges);}
        catch(RuntimeException e){callback.accept("无法保存本次材料计划，未扣取材料");return;}
        if(plan.requests().isEmpty()){
            try{delivery.apply();callback.accept("");}catch(RuntimeException e){callback.accept("库存发生变化，请重新填入");}
            return;
        }
        transfers.add(player.getUniqueId());
        java.util.function.Consumer<String> done=message->{if(epoch==generation){transfers.remove(player.getUniqueId());callback.accept(message);}};
        stock.access().prepareMaterials(player,stock.warehouse(),plan.requests(),delivery.target(),prepared->{
            if(!prepared.success()){done.accept(prepared.message());return;}
            if(closed || epoch!=generation || !delivery.unchanged()){
                stock.access().cancelPrepared(player,prepared.operationId());
                done.accept("库存或界面已变化，未交付材料正在回仓");return;
            }
            stock.access().authorizeDelivery(player,prepared.operationId(),authorized->{
                if(!authorized){done.accept("未能授权交付，请稍后刷新；凭证保留");return;}
                if(closed || epoch!=generation || !delivery.unchanged()){
                    stock.access().recordUncertain(player,prepared.operationId(),"授权后库存或容器已变化，未自动补发");
                    done.accept("库存变化，本次转移保留待核对，凭证 "+prepared.operationId());return;
                }
                try{delivery.apply();}
                catch(RuntimeException e){
                    stock.access().recordUncertain(player,prepared.operationId(),"应用目标库存异常，可能部分落槽，禁止自动补发");
                    done.accept("材料转移需核对，凭证 "+prepared.operationId());return;
                }
                stock.access().completeDelivery(player,prepared.operationId(),ack->done.accept(ack?"":"材料已放置，凭证确认失败，禁止重放；凭证 "+prepared.operationId()));
            });
        });
    }
}
