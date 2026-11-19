package xuanmo.arcartxsuite.vanilla.service;

import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.ItemPresentation;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.*;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.*;

/** Presentation and guarded material filling; native slots remain authoritative. */
public final class ArtisanWorkbenchService implements Listener,AutoCloseable {
    private static final class State {
        final InventoryView view;final WorkshopType type;
        ItemStack[] inputs;
        ItemStack result;
        String token="",patternToken="",resultName="";
        int layers;
        ArtisanNativeAccess.Loom loom;
        Map<String,Object> previews=Map.of(), sent;
        long nextPush;
        State(InventoryView view,WorkshopType type){this.view=view;this.type=type;}
    }
    private final PacketBridgeAPI bridge;
    private final VanillaConfiguration config;
    private final RecipeMaterialPlanner planner;
    private final Function<WorkshopType,String> ui;
    private final Predicate<Player> busy;
    private final ItemPresentation items;
    private final ArtisanNativeAccess nativeAccess=new ArtisanNativeAccess();
    private final EquipmentNativeAccess reachability=new EquipmentNativeAccess();
    private final Map<UUID,State> states=new HashMap<>();
    private BukkitTask task;private long ticks;
    public ArtisanWorkbenchService(Plugin plugin,PacketBridgeAPI bridge,ItemBridgeAPI items,VanillaItemNameBridge names,VanillaConfiguration config,RecipeMaterialPlanner planner,Function<WorkshopType,String> ui,Predicate<Player> busy){
        this.bridge=bridge;this.config=config;this.planner=planner;this.ui=ui;this.busy=busy;
        this.items = new ItemPresentation(items, names);
        var enabled=Arrays.stream(WorkshopType.values()).filter(t->t.artisan()&&config.enabled(t)).toList();
        if(!enabled.isEmpty()){
            int interval=enabled.stream().mapToInt(config::interval).min().orElse(2);
            task=Bukkit.getScheduler().runTaskTimer(plugin,()->{ticks+=interval;poll();},interval,interval);
        }
    }
    private State attach(Player player,WorkshopType type){
        State s=states.get(player.getUniqueId());
        if(s==null||s.view!=player.getOpenInventory()||s.type!=type){s=new State(player.getOpenInventory(),type);states.put(player.getUniqueId(),s);}return s;
    }
    private void poll(){
        for(UUID id:List.copyOf(states.keySet())){
            State s=states.get(id);Player p=Bukkit.getPlayer(id);
            if(s==null||p==null||!p.isOnline()||p.getOpenInventory()!=s.view){states.remove(id);continue;}
            if (ticks >= s.nextPush) push(p, s);
        }
    }
    private static ItemStack copy(ItemStack i){return InventoryItems.copy(i);}
    private static boolean empty(ItemStack i){return InventoryItems.empty(i);}
    private void refresh(Player p,State s){
        Inventory inventory = s.view.getTopInventory();
        ItemStack[] inputs = s.inputs == null ? new ItemStack[s.type.inputCount()] : s.inputs;
        for (int i = 0; i < inputs.length; i++) {
            ItemStack item = inventory.getItem(i);
            if (empty(item)) item = null;
            if (!Objects.equals(inputs[i], item)) {
                if (inputs == s.inputs) inputs = inputs.clone();
                inputs[i] = copy(item);
            }
        }
        boolean changed = inputs != s.inputs;
        if (changed) {
            s.inputs = inputs;
            s.token = UUID.randomUUID().toString();
            if (s.type == WorkshopType.LOOM) {
                s.layers = inputs[0] != null && inputs[0].getItemMeta() instanceof BannerMeta meta ? meta.numberOfPatterns() : 0;
            }
        }
        if(s.type==WorkshopType.LOOM){
            var previous = s.loom == null ? null : s.loom.choices();
            s.loom = nativeAccess.loom(p, changed ? null : s.loom);
            var choices = s.loom == null ? null : s.loom.choices();
            if (changed || !Objects.equals(previous, choices) || s.patternToken.isEmpty()) {
                s.patternToken=UUID.randomUUID().toString();Map<String,Object> previews=new LinkedHashMap<>();
                if(s.loom!=null)for(var choice:s.loom.choices()){
                    ItemStack preview=copy(inputs[0]);
                    if(preview!=null&&preview.getItemMeta() instanceof BannerMeta meta&&choice.pattern()!=null&&inputs[1]!=null){
                        try{DyeColor dye=DyeColor.valueOf(inputs[1].getType().name().replace("_DYE",""));meta.addPattern(new Pattern(dye,choice.pattern()));preview.setItemMeta(meta);}catch(IllegalArgumentException ignored){ }
                    }
                    previews.put(Integer.toString(choice.index()),Map.of("index",choice.index(),"name",patternName(choice),"icon",items.icon(preview)));
                }
                s.previews=previews;
            }
        }
    }
    private void push(Player p,State s){
        s.nextPush = ticks + config.interval(s.type);
        String uiId = ui.apply(s.type);
        if (!bridge.isUiOpen(p, uiId)) {
            s.sent = null;
            return;
        }
        refresh(p,s);
        Inventory inv=s.view.getTopInventory();
        ItemStack first=s.inputs[0], result=inv.getItem(s.type.resultSlot());
        if (empty(result)) result = null;
        if (!Objects.equals(result, s.result)) {
            s.result = copy(result);
            s.resultName = items.name(s.result);
        }
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("token",s.token);packet.put("hasItem",!empty(first));packet.put("hasResult",result!=null);packet.put("resultName",s.resultName);
        if(s.type==WorkshopType.LOOM){
            packet.put("patterns",s.previews);packet.put("patternCount",s.previews.size());packet.put("patternToken",s.patternToken);packet.put("selected",s.loom==null?-1:s.loom.selected());packet.put("supported",s.loom!=null);
            packet.put("layers",s.layers);
            packet.put("status",s.loom==null?"当前服务端不支持纹样选择":empty(first)?"先放入旗帜，再选择染料":s.layers>=6?"旗帜已达原版纹样层数上限":empty(s.inputs[1])?"放入染料，或点击颜色补足":s.previews.isEmpty()?"没有可用纹样，请检查旗帜和染料":"选择纹样后，从真实产物槽领取");
        }else if(s.type==WorkshopType.CARTOGRAPHY){
            MapView map=first!=null&&first.getType()==Material.FILLED_MAP&&first.getItemMeta() instanceof MapMeta meta&&meta.hasMapView()?meta.getMapView():null;
            packet.put("mapInfo",map==null?"放入已绘制的地图":"地图 #"+map.getId()+" · 比例 "+map.getScale().ordinal()+" / 4 · "+(map.isLocked()?"已锁定":"未锁定"));
            packet.put("canExtend",map!=null&&!map.isLocked()&&map.getScale()!=MapView.Scale.FARTHEST);packet.put("canCopy",map!=null);packet.put("canLock",map!=null&&!map.isLocked());
            packet.put("status",result!=null?"从真实产物槽领取地图":map==null?"先放入已绘制的地图":"放入纸、空地图或玻璃板，或点击补足按钮");
        }else{
            packet.put("status",result!=null?"取出产物时按原版规则返还经验":empty(first)&&empty(s.inputs[1])?"放入装备或附魔书，可用同类物品修复":"当前输入没有可领取的产物，请检查物品组合");
        }
        if (!packet.equals(s.sent) && bridge.sendPacket(p, uiId, "artisan_state", packet)) s.sent = packet;
    }
    private void notice(Player p,State s,String message){bridge.sendPacket(p,ui.apply(s.type),"ui_notice",Map.of("message",message));}
    private boolean writable(Player p,State s){
        if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR){notice(p,s,"当前状态不能操作工作站");return false;}
        if(busy.test(p)){notice(p,s,"上一笔材料或兑换仍在处理，请稍候");return false;}
        if(!reachability.canUse(p)){notice(p,s,"工作站已不可用，请重新打开");return false;}
        return true;
    }
    public boolean handle(Player p,WorkshopType type,String action,List<String> data){
        if(!type.artisan()||!config.enabled(type)||!type.matches(p.getOpenInventory()))return false;
        State s=attach(p,type);
        switch(action){
            case "artisan_open","refresh_artisan" -> {s.sent=null;push(p,s);}
            case "loom_select" -> {
                if(type!=WorkshopType.LOOM||data.size()<3||!writable(p,s))return true;
                refresh(p,s);
                int index;try{index=Integer.parseInt(data.get(1));}catch(NumberFormatException e){return true;}
                if(!s.patternToken.equals(data.get(2))||s.loom==null||index<0||index>=s.loom.choices().size()){notice(p,s,"纹样或材料已变化，请重新选择");push(p,s);return true;}
                boolean selected=nativeAccess.select(p,index);p.updateInventory();
                if(!selected)notice(p,s,"未能选择该纹样，请检查当前材料");push(p,s);
            }
            case "loom_dye","map_fill" -> {
                if(data.size()<3||!writable(p,s))return true;
                refresh(p,s);
                if(!s.token.equals(data.get(2))||empty(s.inputs[0])){notice(p,s,"目标物品已变化，请重新选择");return true;}
                Material material;
                if(action.equals("loom_dye")&&type==WorkshopType.LOOM){
                    try{material=Material.valueOf(DyeColor.valueOf(data.get(1)).name()+"_DYE");}catch(IllegalArgumentException e){return true;}
                    if(!(s.inputs[0].getItemMeta() instanceof BannerMeta)){notice(p,s,"请先放入旗帜");return true;}
                }else if(action.equals("map_fill")&&type==WorkshopType.CARTOGRAPHY){
                    material=switch(data.get(1)){case "extend"->Material.PAPER;case "copy"->Material.MAP;case "lock"->Material.GLASS_PANE;default->null;};
                    if(material==null)return true;
                    if(s.inputs[0].getType()!=Material.FILLED_MAP){notice(p,s,"请先放入已绘制的地图");return true;}
                }else return false;
                fill(p,s,material);
            }
            default -> {return false;}
        }return true;
    }
    private void fill(Player p,State s,Material material){
        String token=s.token;ItemStack first=s.inputs[0].clone(),second=copy(s.inputs[1]);
        if(second==null||second.getType()!=material)second=new ItemStack(material);else second.setAmount(Math.max(1,second.getAmount()));
        List<ItemStack> desired=new ArrayList<>(Arrays.asList(first,second));if(s.type==WorkshopType.LOOM)desired.add(copy(s.inputs[2]));
        bridge.sendPacket(p,ui.apply(s.type),"placement_state",Map.of("busy",true,"message",""));
        planner.fillCustom(p,s.type,stock->new RecipeMaterialPlanner.CustomDemand(desired,()->p.getOpenInventory()==s.view&&s.token.equals(token)&&Objects.equals(first,copy(s.view.getTopInventory().getItem(0))),"ARTISAN "+material),message->{
            if(p.isOnline()&&p.getOpenInventory()==s.view&&bridge.isUiOpen(p,ui.apply(s.type))){bridge.sendPacket(p,ui.apply(s.type),"placement_state",Map.of("busy",false,"message",message.isEmpty()?"材料已补足":message));push(p,s);}
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void open(InventoryOpenEvent e){if(e.getPlayer() instanceof Player p)for(var type:WorkshopType.values())if(type.artisan()&&config.enabled(type)&&type.matches(e.getView())){states.put(p.getUniqueId(),new State(e.getView(),type));break;}}
    @EventHandler public void close(InventoryCloseEvent e){states.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e){states.remove(e.getPlayer().getUniqueId());}
    @Override public void close(){if(task!=null){task.cancel();task=null;}states.clear();}
    private static String patternName(ArtisanNativeAccess.Choice c){
        if(c.pattern()==null)return "纹样 "+c.code();
        return switch(c.pattern().name()){
            case "BASE"->"底色";case "SQUARE_BOTTOM_LEFT"->"右底方";case "SQUARE_BOTTOM_RIGHT"->"左底方";case "SQUARE_TOP_LEFT"->"右顶方";case "SQUARE_TOP_RIGHT"->"左顶方";
            case "STRIPE_BOTTOM"->"底横条";case "STRIPE_TOP"->"顶横条";case "STRIPE_LEFT"->"右竖条";case "STRIPE_RIGHT"->"左竖条";case "STRIPE_CENTER"->"中竖条";case "STRIPE_MIDDLE"->"中横条";
            case "STRIPE_DOWNRIGHT"->"左斜条";case "STRIPE_DOWNLEFT"->"右斜条";case "SMALL_STRIPES"->"竖条纹";case "CROSS"->"斜十字";case "STRAIGHT_CROSS"->"正十字";
            case "TRIANGLE_BOTTOM"->"底三角";case "TRIANGLE_TOP"->"顶三角";case "TRIANGLES_BOTTOM"->"底锯齿";case "TRIANGLES_TOP"->"顶锯齿";
            case "DIAGONAL_LEFT"->"左上三角";case "DIAGONAL_RIGHT"->"右下三角";case "DIAGONAL_LEFT_MIRROR"->"左下三角";case "DIAGONAL_RIGHT_MIRROR"->"右上三角";
            case "CIRCLE_MIDDLE"->"圆形";case "RHOMBUS_MIDDLE"->"菱形";case "HALF_VERTICAL"->"右半方";case "HALF_HORIZONTAL"->"上半方";case "HALF_VERTICAL_MIRROR"->"左半方";case "HALF_HORIZONTAL_MIRROR"->"下半方";
            case "BORDER"->"方框边";case "CURLY_BORDER"->"锯齿边";case "CREEPER"->"苦力怕盾徽";case "GRADIENT"->"自上渐淡";case "GRADIENT_UP"->"自下渐淡";case "BRICKS"->"砖纹";
            case "SKULL"->"骷髅盾徽";case "FLOWER"->"花朵盾徽";case "MOJANG"->"Mojang 徽标";case "GLOBE"->"地球";case "PIGLIN"->"猪鼻";default->c.pattern().name();
        };
    }
}
