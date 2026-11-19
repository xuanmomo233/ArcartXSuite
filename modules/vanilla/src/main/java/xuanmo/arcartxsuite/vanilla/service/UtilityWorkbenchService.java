package xuanmo.arcartxsuite.vanilla.service;

import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.InventoryOperation;
import java.util.*;
import java.util.function.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.*;

/** Native storage stays in its block; beacon payment uses the common escrow. */
public final class UtilityWorkbenchService implements Listener,AutoCloseable {
    private record Power(String key,String name,PotionEffectType type,int tier) { }
    private static final List<Power> POWERS=List.of(new Power("speed","迅捷",PotionEffectType.SPEED,1),new Power("haste","急迫",PotionEffectType.FAST_DIGGING,1),new Power("resistance","抗性提升",PotionEffectType.DAMAGE_RESISTANCE,2),new Power("jump","跳跃提升",PotionEffectType.JUMP,2),new Power("strength","力量",PotionEffectType.INCREASE_DAMAGE,3));
    private static final Set<Material> PAYMENTS=EnumSet.of(Material.IRON_INGOT,Material.GOLD_INGOT,Material.EMERALD,Material.DIAMOND,Material.NETHERITE_INGOT);
    private record StorageDetails(String icon,String title,String description) { }
    private record ContainerDisplay(int occupied, long amount, int slots, String status, String sourceTitle, boolean canSort) { }
    private static final class State {
        final InventoryView view;final WorkshopType type;final boolean dropper;final StorageDetails storage;
        final String sortToken;
        BeaconNativeAccess.State beacon;ItemStack payment;
        String token="", sentBeaconToken;
        ContainerDisplay sentContainer;
        long nextPush, lastSubmit=-100;
        State(InventoryView view,WorkshopType type){
            this.view=view;this.type=type;this.dropper=type==WorkshopType.DISPENSER&&(view.getType()==InventoryType.DROPPER||view.getTopInventory().getHolder() instanceof org.bukkit.block.Dropper);this.storage=details(view,type);
            this.sortToken=storage!=null&&view.getPlayer() instanceof Player player&&isSortableStorage(player,view.getTopInventory())
                ?UUID.randomUUID().toString():"";
        }

        void resetDisplay() {
            sentBeaconToken = null;
            sentContainer = null;
        }
    }
    static boolean isSortableStorage(Player player, Inventory inventory) {
        boolean supported = switch (inventory.getType()) {
            case ENDER_CHEST -> true;
            case CHEST -> inventory instanceof DoubleChestInventory chest
                ? chest.getLeftSide().getHolder() instanceof Chest && chest.getRightSide().getHolder() instanceof Chest
                : inventory.getHolder() instanceof Chest;
            case BARREL -> inventory.getHolder() instanceof Barrel;
            case SHULKER_BOX -> inventory.getHolder() instanceof ShulkerBox;
            default -> false;
        };
        return supported && NativeMaterialContainerAdapter.persistentInventoryUnchanged(player, inventory);
    }
    private static StorageDetails details(InventoryView view,WorkshopType type){
        if(type!=WorkshopType.CONTAINER&&type!=WorkshopType.SHULKER_BOX)return null;
        Inventory inv=view.getTopInventory();
        if(type==WorkshopType.SHULKER_BOX){
            String icon="shulker_box";
            if(inv.getHolder() instanceof org.bukkit.block.ShulkerBox box&&box.isPlaced())icon=box.getBlock().getType().getKey().getKey();
            return new StorageDetails(icon,"潜影盒","物品保留在潜影盒内，嵌套限制遵循原版");
        }
        if(view.getType()==InventoryType.ENDER_CHEST)return new StorageDetails("ender_chest","末影箱","当前末影箱库存，存取规则由服务端处理");
        if(view.getType()==InventoryType.BARREL)return new StorageDetails("barrel","木桶","物品保存在原木桶内，关闭后保留");
        InventoryHolder holder=inv.getHolder();
        if(holder instanceof org.bukkit.entity.minecart.StorageMinecart)return new StorageDetails("chest_minecart","运输矿车","物品保存在原运输矿车内");
        if(holder instanceof org.bukkit.block.DoubleChest chest)holder=chest.getLeftSide();
        boolean trapped=holder instanceof org.bukkit.block.Chest chest&&chest.getType()==Material.TRAPPED_CHEST;
        String title=inv.getSize()>27?(trapped?"大型陷阱箱":"大型箱子"):(trapped?"陷阱箱":"箱子");
        if(holder==null&&!view.getTitle().isBlank())title=ChatColor.stripColor(view.getTitle());
        return new StorageDetails(trapped?"trapped_chest":"chest",title,"物品保存在当前容器中，关闭后原位保留");
    }
    private final PacketBridgeAPI bridge;private final VanillaConfiguration config;private final RecipeMaterialPlanner planner;
    private final Function<WorkshopType,String> ui;private final Predicate<Player> busy;
    private final Logger logger;
    private BiConsumer<Player,String> warehouseRefresh=(p,id)->{ };
    private final BeaconNativeAccess beacons=new BeaconNativeAccess();
    private final EquipmentNativeAccess nativeAccess=new EquipmentNativeAccess();
    private final Map<UUID,State> states=new HashMap<>();
    private BukkitTask task;private long ticks;
    public UtilityWorkbenchService(Plugin plugin,PacketBridgeAPI bridge,VanillaConfiguration config,RecipeMaterialPlanner planner,Function<WorkshopType,String> ui,Predicate<Player> busy){
        this.bridge=bridge;this.config=config;this.planner=planner;this.ui=ui;this.busy=busy;
        this.logger=plugin.getLogger();
        var types=Arrays.stream(WorkshopType.values()).filter(t->t.utility()&&config.enabled(t)).toList();
        if(!types.isEmpty()){
            int interval=types.stream().mapToInt(config::interval).min().orElse(2);
            task=Bukkit.getScheduler().runTaskTimer(plugin,()->{ticks+=interval;poll();},interval,interval);
        }
    }
    public void setWarehouseRefresh(BiConsumer<Player,String> refresh){warehouseRefresh=refresh;}
    private void poll(){
        for(UUID id:List.copyOf(states.keySet())){
            Player p=Bukkit.getPlayer(id);State s=states.get(id);
            if(s==null||p==null||!p.isOnline()||p.getOpenInventory()!=s.view){states.remove(id);continue;}
            if (ticks >= s.nextPush) push(p, s);
        }
    }
    private State attach(Player p,WorkshopType type){State s=states.get(p.getUniqueId());if(s==null||s.view!=p.getOpenInventory()||s.type!=type){s=new State(p.getOpenInventory(),type);states.put(p.getUniqueId(),s);}return s;}
    private void refresh(Player p,State s){
        if(s.type!=WorkshopType.BEACON)return;
        var state=beacons.state(p);
        ItemStack payment = s.view.getTopInventory().getItem(0);
        if (InventoryItems.empty(payment)) payment = null;
        if (s.token.isEmpty() || !Objects.equals(state, s.beacon) || !Objects.equals(payment, s.payment)) {
            s.token = UUID.randomUUID().toString();
            s.beacon = state;
            s.payment = InventoryItems.copy(payment);
        }
    }
    private static String key(int id){return POWERS.stream().filter(e->e.type.getId()==id).map(Power::key).findFirst().orElse("");}
    private void push(Player p,State s){
        s.nextPush = ticks + config.interval(s.type);
        String uiId = ui.apply(s.type);
        if (!bridge.isUiOpen(p, uiId)) {
            s.resetDisplay();
            return;
        }
        if(s.type==WorkshopType.BEACON){
            refresh(p,s);
            if (s.token.equals(s.sentBeaconToken)) return;
            Map<String,Object> packet = new LinkedHashMap<>();
            var b=s.beacon;int tier=b==null?0:Math.max(0,Math.min(4,b.tier()));String primary=b==null?"":key(b.primary());
            String secondary=b==null?"none":b.secondary()==PotionEffectType.REGENERATION.getId()?"regen":!primary.isEmpty()&&b.secondary()==b.primary()?"double":"none";
            packet.put("supported",b!=null);packet.put("tier",tier);packet.put("currentPrimary",primary);packet.put("currentSecondary",secondary);
            String label=POWERS.stream().filter(e->e.key.equals(primary)).map(Power::name).findFirst().orElse("未设置");
            packet.put("currentLabel",label+(secondary.equals("double")?" II":secondary.equals("regen")?" · 生命恢复":""));
            packet.put("settings",b==null?"unsupported":tier+":"+b.primary()+":"+b.secondary());packet.put("token",s.token);packet.put("hasPayment",b!=null&&b.payment());
            Map<String,Object> powers=new LinkedHashMap<>();for(Power power:POWERS)powers.put(power.key,Map.of("name",power.name,"tier",power.tier,"enabled",tier>=power.tier));packet.put("powers",powers);
            packet.put("status",b==null?"当前服务端不支持信标效果设置":tier==0?"信标尚未激活，请检查基座和光束":b.payment()?"选择效果后点击确认，消耗一份支付物品":"放入支付物品，或点击材料按钮补足一份");
            if (bridge.sendPacket(p, uiId, "utility_state", packet)) s.sentBeaconToken = s.token;
        }else{
            pushContainer(p, s, uiId);
        }
    }

    private void pushContainer(Player player, State state, String uiId) {
        Inventory inventory = state.view.getTopInventory();
        int used = 0;
        long amount = 0;
        for (ItemStack item : inventory.getContents()) {
            if (!InventoryItems.empty(item)) {
                used++;
                amount += item.getAmount();
            }
        }
        String sourceTitle = state.storage != null ? state.view.getTitle() : "";
        boolean canSort = !state.sortToken.isEmpty() && player.hasPermission(InventorySorter.PERMISSION);
        var display = new ContainerDisplay(used, amount, inventory.getSize(), containerStatus(inventory, state.type), sourceTitle, canSort);
        if (display.equals(state.sentContainer)) return;

        boolean hopper = state.type == WorkshopType.HOPPER;
        var packet = new LinkedHashMap<String, Object>();
        packet.put("kind", state.storage != null ? state.storage.icon() : hopper ? "hopper" : state.dropper ? "dropper" : "dispenser");
        packet.put("title", state.storage != null ? state.storage.title() : hopper ? "漏斗" : state.dropper ? "投掷器" : "发射器");
        packet.put("occupancy", display.occupied() + " / " + display.slots() + " 格");
        packet.put("amount", display.amount() + " 个物品");
        packet.put("used", display.occupied());
        packet.put("slots", display.slots());
        packet.put("mechanism", state.storage != null ? state.storage.description() : hopper ? "按原版规则吸取与输出物品"
            : state.dropper ? "收到红石脉冲时投出物品，或传入前方容器" : "收到红石脉冲时按原版规则使用或发射物品");
        packet.put("status", display.status());
        if (state.storage != null) {
            packet.put("sourceTitle", ChatColor.stripColor(display.sourceTitle()));
            packet.put("canSortContainer", display.canSort());
            packet.put("sortToken", display.canSort() ? state.sortToken : "");
        }
        if (bridge.sendPacket(player, uiId, "utility_state", packet)) state.sentContainer = display;
    }

    private static String containerStatus(Inventory inventory, WorkshopType type) {
        if (type == WorkshopType.HOPPER || type == WorkshopType.DISPENSER) {
            Location location = inventory.getLocation();
            if (location != null && location.isWorldLoaded()
                    && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                var blockData = location.getBlock().getBlockData();
                if (blockData instanceof org.bukkit.block.data.type.Hopper hopper) {
                    return hopper.isEnabled() ? "自动传输已启用" : "红石锁定中，自动传输已暂停";
                }
                if (blockData instanceof org.bukkit.block.data.type.Dispenser dispenser) {
                    return dispenser.isTriggered() ? "已接收红石信号" : "等待红石脉冲";
                }
            }
        }
        return "物品保存在真实容器中，关闭界面后仍然保留";
    }
    private void notice(Player p,State s,String message){bridge.sendPacket(p,ui.apply(s.type),"ui_notice",Map.of("message",message));}
    private boolean writable(Player p,State s){
        if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR){notice(p,s,"当前状态不能操作信标");return false;}
        if(busy.test(p)){notice(p,s,"上一笔材料或兑换仍在处理，请稍候");return false;}
        if(!nativeAccess.canUse(p)){notice(p,s,"工作站已不可用，请重新打开");return false;}
        return true;
    }
    private void sortContainer(Player player, State state, List<String> data) {
        if (state.storage == null || data.size() < 2 || state.sortToken.isEmpty()
            || !state.sortToken.equals(data.get(1)) || player.getOpenInventory() != state.view
            || !bridge.isUiOpen(player, ui.apply(state.type)) || !player.isOnline() || player.isDead()
            || player.getGameMode() == GameMode.SPECTATOR || !player.hasPermission(InventorySorter.PERMISSION)) return;
        if (!InventoryItems.empty(player.getItemOnCursor())) {
            notice(player, state, "请先放下光标上的物品，再整理容器");
            return;
        }
        if (busy.test(player)) {
            notice(player, state, "上一笔材料或兑换仍在处理，请稍候");
            return;
        }
        try (var operation = InventoryOperation.tryAcquire(player)) {
            if (operation == null) {
                notice(player, state, "上一笔材料或兑换仍在处理，请稍候");
                return;
            }
            Inventory inventory = state.view.getTopInventory();
            if (player.getOpenInventory() != state.view || !isSortableStorage(player, inventory)) {
                notice(player, state, "容器已变化，请重新打开");
                return;
            }
            InventorySorter.sort(inventory, 0, config.sortMode());
            player.updateInventory();
            push(player, state);
            notice(player, state, "容器已整理");
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "整理容器失败(" + player.getName() + ")", failure);
            notice(player, state, "未能完成整理，请重新打开容器");
        }
    }
    public boolean handle(Player p,WorkshopType type,String action,List<String> data){
        if(!type.utility()||!config.enabled(type)||!type.matches(p.getOpenInventory()))return false;
        State s=attach(p,type);
        switch(action){
            case "utility_open","refresh_utility" -> {s.resetDisplay();push(p,s);}
            case "sort_container" -> sortContainer(p,s,data);
            case "beacon_apply" -> {
                if(type!=WorkshopType.BEACON||data.size()<4||!writable(p,s))return true;
                refresh(p,s);
                if(s.beacon==null||!s.token.equals(data.get(3))){notice(p,s,"信标状态已变化，请重新确认");push(p,s);return true;}
                if(ticks-s.lastSubmit<8)return true;
                Power primary=POWERS.stream().filter(e->e.key.equals(data.get(1))).findFirst().orElse(null);
                if(primary==null||s.beacon.tier()<primary.tier){notice(p,s,"请选择当前基座等级可用的主效果");return true;}
                String mode=data.get(2);int secondary=switch(mode){case "none"->-1;case "regen"->PotionEffectType.REGENERATION.getId();case "double"->primary.type.getId();default->Integer.MIN_VALUE;};
                if(secondary==Integer.MIN_VALUE||secondary>=0&&s.beacon.tier()<4){notice(p,s,"四级基座才能启用辅助效果");return true;}
                if(!s.beacon.payment()){notice(p,s,"请先放入支付物品");return true;}
                var before=s.beacon;ItemStack payment=InventoryItems.copy(s.payment);s.lastSubmit=ticks;
                boolean invoked=beacons.apply(p,primary.type.getId(),secondary);p.updateInventory();refresh(p,s);
                boolean changed=!Objects.equals(before,s.beacon)||!Objects.equals(payment,s.payment);
                notice(p,s,invoked&&changed?"信标设置已更新，请查看当前效果":invoked?"未确认状态变化，请核对效果与支付槽":"操作未能完成，请核对效果与支付槽");push(p,s);
            }
            case "beacon_fill" -> {
                if(type!=WorkshopType.BEACON||data.size()<3||!writable(p,s))return true;
                refresh(p,s);
                if(!s.token.equals(data.get(2))||s.beacon==null){notice(p,s,"信标状态已变化，请重新选择");push(p,s);return true;}
                Material material;try{material=Material.valueOf(data.get(1));}catch(IllegalArgumentException e){return true;}
                if(!PAYMENTS.contains(material))return true;
                ItemStack desired=s.payment!=null&&s.payment.getType()==material?s.payment.clone():new ItemStack(material);desired.setAmount(1);
                String token=s.token;
                bridge.sendPacket(p,ui.apply(type),"placement_state",Map.of("busy",true,"message",""));
                planner.fillCustom(p,type,stock->new RecipeMaterialPlanner.CustomDemand(List.of(desired),()->p.getOpenInventory()==s.view&&s.token.equals(token)&&Objects.equals(s.beacon,beacons.state(p)),"BEACON "+material),message->{
                    if(p.isOnline()&&p.getOpenInventory()==s.view&&bridge.isUiOpen(p,ui.apply(type))){bridge.sendPacket(p,ui.apply(type),"placement_state",Map.of("busy",false,"message",message.isEmpty()?"支付物品已补足":message));push(p,s);warehouseRefresh.accept(p,ui.apply(type));}
                });
            }
            default -> {return false;}
        }return true;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void open(InventoryOpenEvent e){if(e.getPlayer() instanceof Player p)for(var type:WorkshopType.values())if(type.utility()&&config.enabled(type)&&type.matches(e.getView())){states.put(p.getUniqueId(),new State(e.getView(),type));break;}}
    @EventHandler public void close(InventoryCloseEvent e){states.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e){states.remove(e.getPlayer().getUniqueId());}
    @Override public void close(){if(task!=null){task.cancel();task=null;}states.clear();}
}
