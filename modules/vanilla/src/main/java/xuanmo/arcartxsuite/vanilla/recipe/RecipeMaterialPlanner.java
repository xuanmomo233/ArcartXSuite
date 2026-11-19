package xuanmo.arcartxsuite.vanilla.recipe;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.InventoryOperation;
import xuanmo.arcartxsuite.api.inventory.MaterialStock;

/** Builds a complete one-recipe placement before mutating either inventory. */
public final class RecipeMaterialPlanner {
    public static final int MAX_BATCH = 64;
    private record Pending(InventoryOperation reservation, Consumer<String> callback) { }
    private final Map<UUID, Pending> transfers = new HashMap<>();
    private boolean closed;
    private long generation;
    private WarehouseMaterialAccess transferProvider;
    public void cancelPending() {
        generation++;
        var cancelled = List.copyOf(transfers.values());
        transfers.clear();
        cancelled.forEach(pending -> pending.reservation().close());
        cancelled.forEach(pending -> pending.callback().accept("材料服务已变化，本次填料已停止"));
    }
    public void close(){closed=true;cancelPending();}
    private final List<xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter> adapters = new ArrayList<>(Arrays.stream(WorkshopType.values()).map(NativeMaterialContainerAdapter::new).toList());
    public boolean busy(Player player) {
        if(!transfers.isEmpty() && provider.get()!=transferProvider)cancelPending();
        return InventoryOperation.busy(player);
    }
    public void registerAdapter(xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter adapter) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("main thread required");
        if (adapters.stream().anyMatch(a -> a.id().equals(adapter.id()))) throw new IllegalArgumentException("duplicate adapter");
        adapters.add(adapter);
    }
    private Supplier<WarehouseMaterialAccess> provider = () -> null;
    public void setProvider(Supplier<WarehouseMaterialAccess> provider) { this.provider = provider; }
    public WarehouseMaterialAccess warehouseAccess(){return provider.get();}
    public MaterialStock stock(Player player) {
        return MaterialStock.read(player, warehouseAccess());
    }

    public void stockAsync(Player player, java.util.function.Consumer<MaterialStock> callback) {
        MaterialStock.readAsync(player, warehouseAccess(), callback);
    }

    private Pending begin(Player player, Consumer<String> callback) {
        InventoryOperation operation = InventoryOperation.tryAcquire(player);
        if (operation == null) return null;
        Pending pending = new Pending(operation, callback);
        transfers.put(player.getUniqueId(), pending);
        transferProvider = provider.get();
        return pending;
    }

    private void finish(Player player, Pending pending, String message) {
        if (!transfers.remove(player.getUniqueId(), pending)) return;
        pending.reservation().close();
        pending.callback().accept(message);
    }

    private static boolean empty(ItemStack s) { return s == null || s.getType().isAir() || s.getAmount() <= 0; }
    private static ItemStack copy(ItemStack s) { return empty(s) ? null : s.clone(); }
    private static ItemStack[] copy(ItemStack[] a) { return Arrays.stream(a).map(RecipeMaterialPlanner::copy).toArray(ItemStack[]::new); }
    private static RecipeChoice[] choices(Recipe recipe) {
        RecipeChoice[] result = new RecipeChoice[recipe instanceof SmithingRecipe ? 3 : (recipe instanceof CookingRecipe<?> || recipe instanceof StonecuttingRecipe ? 1 : 9)];
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape(); var map = shaped.getChoiceMap();
            for (int y=0;y<shape.length;y++) for(int x=0;x<shape[y].length();x++) result[y*3+x]=map.get(shape[y].charAt(x));
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            var list=shapeless.getChoiceList(); for(int i=0;i<list.size();i++) result[i]=list.get(i);
        } else if (recipe instanceof CookingRecipe<?> cooking) result[0]=cooking.getInputChoice();
        else if (recipe instanceof StonecuttingRecipe stone) result[0]=stone.getInputChoice();
        else if (recipe instanceof SmithingTransformRecipe smith) {result[0]=smith.getTemplate();result[1]=smith.getBase();result[2]=smith.getAddition();}
        else if (recipe instanceof SmithingTrimRecipe smith) {result[0]=smith.getTemplate();result[1]=smith.getBase();result[2]=smith.getAddition();}
        return result;
    }
    private record Source(ItemStack item, int kind, int slot, long count) { }
    private record Plan(ItemStack[] storage, ItemStack[] target, List<WarehouseMaterialAccess.Request> requests) { }

    public boolean canMake(Player player, Recipe recipe, MaterialStock stock) { return plan(player,recipe,stock) != null; }

    /** Read once for all display checks in the current recipe book. */
    Availability availability(Player player, WorkshopType type, MaterialStock stock) {
        ItemStack[] storage = copy(player.getInventory().getStorageContents());
        ItemStack[] station = readStation(player.getOpenInventory().getTopInventory(), type.inputCount());
        var materials = EnumSet.noneOf(Material.class);
        for (ItemStack item : storage) if (item != null) materials.add(item.getType());
        for (ItemStack item : station) if (item != null) materials.add(item.getType());
        List<Source> warehouse = new ArrayList<>();
        for (var entry : stock.entries()) {
            if (entry.amount() <= 0) continue;
            ItemStack item = entry.item();
            materials.add(item.getType());
            warehouse.add(new Source(item, 2, entry.slot(), entry.amount()));
        }
        return new Availability(storage, station, warehouse, materials);
    }

    record Availability(ItemStack[] storage, ItemStack[] station, List<Source> warehouse, Set<Material> materials) {
        boolean canMake(Recipe recipe) {
            RecipeChoice[] needed = choices(recipe);
            if (needed.length != station.length) return false;
            for (RecipeChoice choice : needed) {
                if (choice instanceof RecipeChoice.MaterialChoice types
                    && types.getClass() == RecipeChoice.MaterialChoice.class
                    && types.getChoices().stream().noneMatch(materials::contains)) return false;
                if (choice instanceof RecipeChoice.ExactChoice items
                    && items.getClass() == RecipeChoice.ExactChoice.class
                    && items.getChoices().stream().noneMatch(item -> materials.contains(item.getType()))) return false;
            }
            return plan(needed, storage, station, null, warehouse, 1, recipe instanceof SmithingRecipe) != null;
        }
    }

    private Plan plan(Player player, Recipe recipe, MaterialStock stock) {
        return plan(player, recipe, stock, 1);
    }

    private Plan plan(Player player, Recipe recipe, MaterialStock stock, int quantity) {
        RecipeChoice[] needed = choices(recipe);
        ItemStack[] storage = copy(player.getInventory().getStorageContents());
        ItemStack[] current = readStation(player.getOpenInventory().getTopInventory(), needed.length);
        return plan(needed, storage, current, stock, null, quantity, recipe instanceof SmithingRecipe);
    }

    private static ItemStack[] readStation(Inventory top, int slots) {
        ItemStack[] current = new ItemStack[slots];
        if (slots == 9 && top instanceof CraftingInventory crafting && top.getSize() == 10) return copy(crafting.getMatrix());
        if (slots == 1 && (top instanceof FurnaceInventory
            || top.getType() == org.bukkit.event.inventory.InventoryType.STONECUTTER)) current[0] = copy(top.getItem(0));
        if (slots == 3 && top instanceof SmithingInventory) {
            for (int i = 0; i < slots; i++) current[i] = copy(top.getItem(i));
        }
        return current;
    }

    private static Plan plan(RecipeChoice[] needed, ItemStack[] initialStorage, ItemStack[] current,
                             MaterialStock stock, List<Source> warehouse, int quantity, boolean smithing) {
        // Equipment is an explicit player choice: never pick an arbitrary valuable
        // piece from storage. Templates and additions can still come from warehouse.
        if(smithing && (empty(current[1]) || !needed[1].test(current[1])))return null;
        boolean ready=true;
        for(int i=0;i<needed.length;i++) {
            if(needed[i]==null ? !empty(current[i]) : empty(current[i]) || current[i].getAmount() < quantity
                || !needed[i].test(current[i])) ready=false;
        }
        if(ready) return new Plan(initialStorage,current,List.of());
        List<Source> sources=new ArrayList<>();
        for(int i=0;i<current.length;i++) if(!empty(current[i])) sources.add(new Source(current[i],0,i,current[i].getAmount()));
        for(int i=0;i<initialStorage.length;i++) if(!empty(initialStorage[i])) sources.add(new Source(initialStorage[i],1,i,initialStorage[i].getAmount()));
        if (warehouse == null) {
            for (var entry : stock.entries()) {
                ItemStack item = entry.item();
                if (matchesAny(needed, item)) sources.add(new Source(item, 2, entry.slot(), entry.amount()));
            }
        } else {
            for (Source source : warehouse) {
                if (matchesAny(needed, source.item())) sources.add(source);
            }
        }
        List<Integer> order=new ArrayList<>();
        for(int i=0;i<needed.length;i++) if(needed[i]!=null) order.add(i);
        if(order.isEmpty()) return null;
        order.sort(Comparator.comparingLong(i -> sources.stream().filter(s->needed[i].test(s.item())).count()));
        long[] remaining=sources.stream().mapToLong(Source::count).toArray();
        int[] selected=new int[needed.length]; Arrays.fill(selected,-1);
        if (quantity == 1) {
            if(!allocate(0,order,needed,sources,remaining,selected,new HashSet<>(),smithing)) return null;
        } else {
            Map<ItemStack, List<Integer>> groups = new LinkedHashMap<>();
            for (int i = 0; i < sources.size(); i++) {
                ItemStack unit = sources.get(i).item().clone();
                unit.setAmount(1);
                if (unit.getMaxStackSize() >= quantity) {
                    groups.computeIfAbsent(unit, ignored -> new ArrayList<>()).add(i);
                }
            }
            if (!allocateBatch(0, order, needed, groups, remaining, selected, quantity, new HashSet<>())) return null;
        }
        ItemStack[] target=new ItemStack[needed.length];
        // Every stack changed by the simulation belongs to this plan, not the shared input snapshot.
        ItemStack[] storage = new ItemStack[initialStorage.length];
        Map<Integer,Integer> warehouseUse=new LinkedHashMap<>();
        for(int i=0;i<selected.length;i++) if(selected[i]>=0) {
            Source s=sources.get(selected[i]); target[i]=s.item().clone(); target[i].setAmount(quantity);
        }
        // Preserve surplus stacks already in the correct target position.
        for(int n=0;n<sources.size();n++) {
            Source s=sources.get(n); int left=(int)Math.min(Integer.MAX_VALUE,remaining[n]);
            if (s.kind() == 2 && remaining[n] < s.count()) {
                warehouseUse.merge(s.slot(), (int) (s.count() - remaining[n]), Integer::sum);
            }
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
        if (warehouseUse.size() > 9) return null;
        return new Plan(storage,target,warehouseUse.entrySet().stream().map(e->new WarehouseMaterialAccess.Request(e.getKey(),e.getValue())).toList());
    }

    private static boolean matchesAny(RecipeChoice[] choices, ItemStack item) {
        for (RecipeChoice choice : choices) {
            if (choice != null && choice.test(item)) return true;
        }
        return false;
    }

    private static boolean allocateBatch(int depth, List<Integer> order, RecipeChoice[] needed,
                                         Map<ItemStack, List<Integer>> groups, long[] remaining,
                                         int[] selected, int quantity, Set<String> failed) {
        if (depth == order.size()) return true;
        String state = depth + ":" + Arrays.toString(remaining);
        if (failed.contains(state)) return false;
        int slot = order.get(depth);
        for (var group : groups.entrySet()) {
            if (!needed[slot].test(group.getKey())) continue;
            List<Integer> indices = group.getValue();
            int left = quantity;
            int[] taken = new int[indices.size()];
            for (int i = 0; i < indices.size() && left > 0; i++) {
                int index = indices.get(i);
                taken[i] = (int) Math.min(left, remaining[index]);
                remaining[index] -= taken[i];
                left -= taken[i];
            }
            if (left == 0) {
                selected[slot] = indices.get(0);
                if (allocateBatch(depth + 1, order, needed, groups, remaining, selected, quantity, failed)) return true;
                selected[slot] = -1;
            }
            for (int i = 0; i < indices.size(); i++) remaining[indices.get(i)] += taken[i];
        }
        failed.add(state);
        return false;
    }

    public record IngredientStock(ItemStack sample, boolean alternatives, int perCraft, long workbench, long backpack, long warehouse) {
        public long missing(int quantity) {
            long need = (long) perCraft * quantity;
            return Math.max(0, need - Math.min(need, workbench) - Math.min(need, backpack) - Math.min(need, warehouse));
        }
    }

    public List<IngredientStock> describe(Player player, Recipe recipe, MaterialStock stock) {
        if (!(player.getOpenInventory().getTopInventory() instanceof CraftingInventory crafting)) return List.of();
        var warehouse = new ArrayList<Source>();
        for (var entry : stock.entries()) warehouse.add(new Source(entry.item(), 2, entry.slot(), entry.amount()));
        return describe(choices(recipe), crafting.getMatrix(), player.getInventory().getStorageContents(), warehouse);
    }

    private List<IngredientStock> describe(RecipeChoice[] needed, ItemStack[] workbench, ItemStack[] backpack,
                                           List<Source> warehouse) {
        Map<RecipeChoice, Integer> groups = new LinkedHashMap<>();
        for (RecipeChoice choice : needed) if (choice != null) groups.merge(choice, 1, Integer::sum);
        return groups.entrySet().stream().map(group -> {
            RecipeChoice choice = group.getKey();
            long inWorkbench = Arrays.stream(workbench).filter(item -> !empty(item) && choice.test(item))
                .mapToLong(ItemStack::getAmount).sum();
            long inBackpack = Arrays.stream(backpack).filter(item -> !empty(item) && choice.test(item))
                .mapToLong(ItemStack::getAmount).sum();
            long inWarehouse = warehouse.stream().filter(entry -> choice.test(entry.item()))
                .mapToLong(Source::count)
                .reduce(0L, (left, right) -> left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right);
            return new IngredientStock(choice.getItemStack().clone(), hasAlternatives(choice), group.getValue(),
                inWorkbench, inBackpack, inWarehouse);
        }).toList();
    }

    public static List<RecipeChoice> ingredients(Recipe recipe) {
        return Arrays.stream(choices(recipe)).filter(Objects::nonNull).toList();
    }

    public static boolean hasAlternatives(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice material) return material.getChoices().size() > 1;
        return choice instanceof RecipeChoice.ExactChoice exact && exact.getChoices().size() > 1;
    }

    private int upperBatchLimit(List<IngredientStock> ingredients) {
        return ingredients.stream()
            .mapToInt(row -> (int) Math.min(MAX_BATCH,
                (row.workbench() + row.backpack() + Math.min(row.warehouse(), 9L * MAX_BATCH)) / row.perCraft()))
            .min().orElse(0);
    }

    public int maximumCraftingBatch(Player player, NamespacedKey key, Recipe recipe, MaterialStock stock) {
        return estimateCrafting(player, key, recipe, stock).batch().quantity();
    }

    public record CraftingPreview(List<IngredientStock> rows, int maximum) {}

    public CraftingPreview previewCrafting(Player player, NamespacedKey key, Recipe recipe, MaterialStock stock) {
        var estimate = estimateCrafting(player, key, recipe, stock);
        return new CraftingPreview(estimate.rows(), estimate.batch().quantity());
    }

    private record Batch(int quantity, Plan plan) { }
    private record CraftingEstimate(List<IngredientStock> rows, Batch batch) {}

    private CraftingEstimate estimateCrafting(Player player, NamespacedKey key, Recipe recipe, MaterialStock stock) {
        if (!(player.getOpenInventory().getTopInventory() instanceof CraftingInventory)) {
            return new CraftingEstimate(List.of(), new Batch(0, null));
        }
        var available = availability(player, WorkshopType.CRAFTING, stock);
        var needed = choices(recipe);
        var rows = describe(needed, available.station(), available.storage(), available.warehouse());
        int limit = upperBatchLimit(rows);
        for (int quantity = limit; quantity > 0; quantity--) {
            Plan plan = plan(needed, available.storage(), available.station(), null, available.warehouse(), quantity, false);
            if (plan != null && matchesCrafting(player, key, plan.target())) {
                return new CraftingEstimate(rows, new Batch(quantity, plan));
            }
        }
        return new CraftingEstimate(rows, new Batch(0, null));
    }
    private static boolean allocate(int depth,List<Integer> order,RecipeChoice[] needed,List<Source> sources,long[] remaining,int[] selected,Set<String> failed,boolean smithing) {
        if(depth==order.size()) return true;
        String state=depth+":"+Arrays.toString(remaining);
        if(failed.contains(state)) return false;
        int slot=order.get(depth);
        for(int i=0;i<sources.size();i++) if(remaining[i]>0 && needed[slot].test(sources.get(i).item())
            && (!smithing || slot!=1 || (sources.get(i).kind()==0 && sources.get(i).slot()==1))) {
            remaining[i]--;selected[slot]=i;
            if(allocate(depth+1,order,needed,sources,remaining,selected,failed,smithing)) return true;
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
    public void fill(Player player,String key,WorkshopType type,java.util.function.Consumer<String> callback) {
        fill(player, key, type, () -> true, callback);
    }

    public void fill(Player player, String key, WorkshopType type, BooleanSupplier current, Consumer<String> callback) {
        fill(player, key, type, 1, current, callback);
    }

    /** Quantity zero selects the current maximum for a crafting grid. */
    public void fill(Player player, String key, WorkshopType type, int quantity, BooleanSupplier current, Consumer<String> callback) {
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("main thread required");
        if(closed || busy(player)){callback.accept("材料服务暂停或上一笔填料处理中");return;}
        var view=player.getOpenInventory();long epoch=generation;
        Pending pending = begin(player, callback);
        if (pending == null) { callback.accept("上一笔材料或兑换仍在处理"); return; }
        try { stockAsync(player,stock->{
            if(epoch!=generation)return;
            Consumer<String> done = message -> finish(player, pending, message);
            if(closed || !player.isOnline() || player.getOpenInventory()!=view){done.accept("界面已变化，未扣取材料");return;}
            try{fillWithStock(player,key,type,stock,quantity,current,done);}catch(RuntimeException e){done.accept("材料计划异常，未继续交付");}
        }); } catch (RuntimeException exception) {
            finish(player, pending, "无法读取材料库存");
        }
    }
    private void fillWithStock(Player player,String key,WorkshopType type,MaterialStock stock,
                               int quantity, BooleanSupplier current, Consumer<String> callback) {
        long epoch=generation;
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("main thread required");
        if(closed){callback.accept("材料服务暂停");return;}
        if(!empty(player.getItemOnCursor())){callback.accept("请先放下光标上的物品");return;}
        NamespacedKey id=NamespacedKey.fromString(key);
        Recipe recipe=id==null?null:Bukkit.getRecipe(id);
        InventoryView view=player.getOpenInventory();
        if(id==null || !type.matches(view) || !type.visibleTo(player,recipe) || !current.getAsBoolean()) {
            callback.accept("配方或容器已变化，请刷新");return;
        }
        if(type==WorkshopType.SMITHING && empty(view.getTopInventory().getItem(1))){callback.accept("请先放入要锻造的装备，再补齐模板与材料");return;}
        var adapter=adapters.stream().filter(a->a.supports(view)).findFirst().orElse(null);
        if(adapter==null){callback.accept("此容器尚无材料适配器");return;}
        if (quantity < 0 || quantity > MAX_BATCH || (type != WorkshopType.CRAFTING && quantity != 1)) {
            callback.accept("当前配方的填料份数无效");
            return;
        }
        Plan plan = quantity == 0 ? estimateCrafting(player, id, recipe, stock).batch().plan() : plan(player,recipe,stock,quantity);
        if(plan==null){callback.accept("材料不足，或背包没有空间退回原材料");return;}
        BooleanSupplier valid = () -> current.getAsBoolean() && type.visibleTo(player, Bukkit.getRecipe(id))
            && (type != WorkshopType.CRAFTING || matchesCrafting(player, id, plan.target()));
        if (!valid.getAsBoolean()) {
            callback.accept("填入的材料会命中其他配方，未扣取材料");
            return;
        }
        Map<Integer,ItemStack> storageChanges=new LinkedHashMap<>(),targetChanges=new LinkedHashMap<>();
        ItemStack[] currentStorage=player.getInventory().getStorageContents();
        for(int i=0;i<plan.storage().length;i++) if(!Objects.equals(copy(currentStorage[i]),copy(plan.storage()[i])))storageChanges.put(i,plan.storage()[i]);
        for(int i=0;i<plan.target().length;i++){
            int slot=type.inputSlot(i);
            if(!Objects.equals(copy(view.getTopInventory().getItem(slot)),copy(plan.target()[i])))targetChanges.put(slot,plan.target()[i]);
        }
        final xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter.Delivery delivery;
        try{delivery=adapter.capture(player,view,storageChanges,targetChanges);}
        catch(RuntimeException e){callback.accept("无法保存本次材料计划，未扣取材料");return;}
        deliver(player, stock, guarded(delivery, valid, id.toString()), plan.requests(), epoch, callback);
    }

    private static boolean matchesCrafting(Player player, NamespacedKey key, ItemStack[] matrix) {
        return Bukkit.getCraftingRecipe(matrix, player.getWorld()) instanceof Keyed matched
            && key.equals(matched.getKey());
    }

    private static xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter.Delivery guarded(
        xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter.Delivery base, BooleanSupplier valid, String evidence
    ) {
        return new xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter.Delivery() {
            public WarehouseMaterialAccess.Target target() {
                var target = base.target();
                return new WarehouseMaterialAccess.Target(target.adapter(), target.identity(), target.persistence(),
                    target.evidence() + "\nRECIPE " + evidence);
            }
            public boolean unchanged() {
                try { return base.unchanged() && valid.getAsBoolean(); }
                catch (RuntimeException exception) { return false; }
            }
            public void apply() {
                if (!unchanged()) throw new IllegalStateException("配方状态已变化");
                base.apply();
            }
        };
    }

    public record CustomDemand(List<ItemStack> inputs, java.util.function.BooleanSupplier valid, String evidence) { }

    public void fillCustom(Player player, WorkshopType type, java.util.function.Function<MaterialStock,CustomDemand> resolve, java.util.function.Consumer<String> callback) {
        fillCustom(player, type, resolve, () -> true, callback);
    }

    public void fillCustom(Player player, WorkshopType type, java.util.function.Function<MaterialStock,CustomDemand> resolve,
                           BooleanSupplier currentView, Consumer<String> callback) {
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("main thread required");
        if(closed || busy(player)){callback.accept("上一笔材料处理中，请稍候");return;}
        if(!InventoryItems.empty(player.getItemOnCursor())){callback.accept("请先放下光标物品");return;}
        var view=player.getOpenInventory();long epoch=generation;
        Pending pending = begin(player, callback);
        if (pending == null) { callback.accept("上一笔材料或兑换仍在处理"); return; }
        try { stockAsync(player,stock->{
            if(epoch!=generation)return;
            Consumer<String> done = message -> finish(player, pending, message);
            if(closed || !player.isOnline() || player.getOpenInventory()!=view || !type.matches(view)
                || !currentView.getAsBoolean()){done.accept("容器已变化，未取材料");return;}
            try {
                CustomDemand demand=resolve.apply(stock);
                if(demand==null || !demand.valid().getAsBoolean()){done.accept("配方、价格或库存已变化，请刷新");return;}
                ExactMaterialPlan plan=ExactMaterialPlan.build(player,type,demand.inputs(),stock);
                if(plan==null){done.accept("材料不足，或背包没有空间退回原材料");return;}
                Map<Integer,ItemStack> storageChanges=new LinkedHashMap<>(),inputChanges=new LinkedHashMap<>();
                ItemStack[] current=player.getInventory().getStorageContents();
                for(int i=0;i<plan.storage().length;i++)if(!Objects.equals(copy(current[i]),copy(plan.storage()[i])))storageChanges.put(i,plan.storage()[i]);
                for(int i=0;i<plan.inputs().length;i++){int raw=type.inputSlot(i);if(!Objects.equals(copy(view.getTopInventory().getItem(raw)),copy(plan.inputs()[i])))inputChanges.put(raw,plan.inputs()[i]);}
                var base=new NativeMaterialContainerAdapter(type).capture(player,view,storageChanges,inputChanges);
                BooleanSupplier valid = () -> currentView.getAsBoolean() && demand.valid().getAsBoolean();
                deliver(player,stock,guarded(base,valid,demand.evidence()),plan.requests(),epoch,done);
            }catch(RuntimeException exception){done.accept("材料计划未能完成，未继续交付");}
        }); } catch (RuntimeException exception) {
            finish(player, pending, "无法读取材料库存");
        }
    }

    private void deliver(Player player,MaterialStock stock,xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter.Delivery delivery,List<WarehouseMaterialAccess.Request> requests,long epoch,java.util.function.Consumer<String> callback){
        if(requests.isEmpty()){
            try{delivery.apply();callback.accept("");}catch(RuntimeException e){callback.accept("库存发生变化，请重新填入");}
            return;
        }
        java.util.function.Consumer<String> done=message->{if(epoch==generation)callback.accept(message);};
        stock.access().prepareMaterials(player,stock.warehouse(),requests,delivery.target(),prepared->{
            if(!prepared.success()){done.accept(prepared.message());return;}
            if(closed || epoch!=generation || !delivery.unchanged()){
                stock.access().cancelPrepared(player,prepared.operationId());
                done.accept("库存或界面已变化，未交付材料正在回仓");return;
            }
            stock.access().authorizeDelivery(player,prepared.operationId(),authorized->{
                if(!authorized){done.accept("未能授权交付，请稍后刷新；凭证保留");return;}
                if(closed || epoch!=generation || !delivery.unchanged()){
                    stock.access().cancelUndelivered(player,prepared.operationId(),restored->done.accept(restored
                        ?"库存或界面已变化，未交付材料已回仓"
                        :"库存或界面已变化，未确认回仓，凭证 "+prepared.operationId()));
                    return;
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
