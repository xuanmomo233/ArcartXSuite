package xuanmo.arcartxsuite.vanilla.service;

import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.*;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.*;

/** Native equipment menus expose their state; only resource filling uses escrow. */
public final class EquipmentWorkbenchService implements Listener,AutoCloseable {
    private static final class State {
        final InventoryView view;final WorkshopType type;
        ItemStack gear;boolean initialized,explicitName;
        String itemToken=UUID.randomUUID().toString(),offerToken="",name="",rawName="",sent="";
        EquipmentNativeAccess.Offers offers;long lastPush=-100;
        State(InventoryView view,WorkshopType type){this.view=view;this.type=type;}
    }
    private final Plugin plugin;
    private final PacketBridgeAPI bridge;
    private final VanillaItemNameBridge names;
    private final VanillaConfiguration config;
    private final RecipeMaterialPlanner planner;
    private final Function<WorkshopType,String> ui;
    private final Predicate<Player> busy;
    private final EquipmentNativeAccess nativeAccess=new EquipmentNativeAccess();
    private final Map<UUID,State> states=new HashMap<>();
    private BukkitTask task;private long ticks;
    public EquipmentWorkbenchService(Plugin plugin,PacketBridgeAPI bridge,VanillaItemNameBridge names,VanillaConfiguration config,RecipeMaterialPlanner planner,Function<WorkshopType,String> ui,Predicate<Player> busy){
        this.plugin=plugin;this.bridge=bridge;this.names=names;this.config=config;this.planner=planner;this.ui=ui;this.busy=busy;
        if(config.enabled(WorkshopType.ANVIL)||config.enabled(WorkshopType.ENCHANTING)){
            int interval=Arrays.stream(WorkshopType.values()).filter(t->t.equipment()&&config.enabled(t)).mapToInt(config::interval).min().orElse(2);
            task=Bukkit.getScheduler().runTaskTimer(plugin,()->{ticks+=interval;poll();},interval,interval);
        }
    }
    private void poll(){
        for(UUID uuid:List.copyOf(states.keySet())){
            Player player=Bukkit.getPlayer(uuid);State state=states.get(uuid);
            if(player==null||!player.isOnline()||player.getOpenInventory()!=state.view||!state.type.matches(state.view)){states.remove(uuid);continue;}
            if(ticks-state.lastPush>=config.interval(state.type)||state.sent.isEmpty()){state.lastPush=ticks;push(player,state,"");}
        }
    }
    private State attach(Player player,WorkshopType type){
        State state=states.get(player.getUniqueId());
        if(state==null||state.view!=player.getOpenInventory()||state.type!=type){state=new State(player.getOpenInventory(),type);states.put(player.getUniqueId(),state);}
        return state;
    }
    private static boolean same(EquipmentNativeAccess.Offers a,EquipmentNativeAccess.Offers b){return a==b||a!=null&&b!=null&&a.seed()==b.seed()&&Arrays.equals(a.costs(),b.costs())&&Arrays.equals(a.clues(),b.clues())&&Arrays.equals(a.levels(),b.levels());}
    private String display(ItemStack item){
        if(InventoryItems.empty(item))return "";
        if(item.hasItemMeta()&&item.getItemMeta().hasDisplayName())return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        return names==null?item.getType().name():names.translate(item.getType());
    }
    private void refresh(Player player,State state){
        ItemStack current=InventoryItems.copy(state.view.getTopInventory().getItem(0));
        if(!state.initialized||!Objects.equals(current,state.gear)){
            state.gear=current;state.initialized=true;state.itemToken=UUID.randomUUID().toString();state.offerToken="";state.explicitName=false;state.name=display(current);
            if(state.type==WorkshopType.ANVIL&&!InventoryItems.empty(current)){
                String raw=current.hasItemMeta()&&current.getItemMeta().hasDisplayName()?state.name:"";
                nativeAccess.rename(state.view,raw);state.rawName=Objects.toString(((AnvilInventory)state.view.getTopInventory()).getRenameText(),"");
            }
        }
        if(state.type==WorkshopType.ENCHANTING){
            var offers=nativeAccess.offers(player);
            if(!same(offers,state.offers)||state.offerToken.isEmpty()){state.offers=offers;state.offerToken=UUID.randomUUID().toString();}
        }
    }
    private void push(Player player,State state,String ack){
        if(!bridge.isUiOpen(player,ui.apply(state.type)))return;
        refresh(player,state);Map<String,Object> packet=new LinkedHashMap<>();
        boolean creative=player.getGameMode()==GameMode.CREATIVE;
        packet.put("level",player.getLevel());packet.put("creative",creative);packet.put("hasItem",!InventoryItems.empty(state.gear));packet.put("itemToken",state.itemToken);
        if(state.type==WorkshopType.ANVIL){
            AnvilInventory inv=(AnvilInventory)state.view.getTopInventory();String raw=Objects.toString(inv.getRenameText(),"");
            if(!raw.equals(state.rawName)){state.rawName=raw;state.name=raw.isEmpty()&&!state.explicitName?display(state.gear):raw;}
            int cost=inv.getRepairCost(),maximum=inv.getMaximumRepairCost();
            packet.put("name",state.name);packet.put("ack",ack);packet.put("cost",cost);packet.put("maximum",maximum);packet.put("materialCost",inv.getRepairCostAmount());
            boolean hasOutput=!InventoryItems.empty(inv.getItem(2));
            packet.put("tooExpensive",!creative&&!InventoryItems.empty(state.gear)&&!hasOutput&&cost>=maximum);packet.put("enough",creative||player.getLevel()>=cost);packet.put("hasOutput",hasOutput);
        }else{
            int lapis=0;ItemStack secondary=state.view.getTopInventory().getItem(1);if(!InventoryItems.empty(secondary)&&secondary.getType()==Material.LAPIS_LAZULI)lapis=secondary.getAmount();
            packet.put("lapis",lapis);packet.put("offerToken",state.offerToken);packet.put("supported",state.offers!=null);
            Map<String,Object> rows=new LinkedHashMap<>();
            for(int i=0;i<3;i++){
                int cost=state.offers==null?0:state.offers.costs()[i],clue=state.offers==null?-1:state.offers.clues()[i],hintLevel=state.offers==null?0:state.offers.levels()[i];
                rows.put(Integer.toString(i),Map.of("required",cost,"lapis",i+1,"spentLevels",i+1,"hint",cost<=0?"放入可附魔的装备或书":enchantmentName(nativeAccess.hint(clue))+(hintLevel>0?" "+hintLevel:""),"enabled",cost>0&&!InventoryItems.empty(state.gear)&&(creative||player.getLevel()>=cost&&lapis>=i+1)));
            }
            packet.put("offers",rows);
        }
        String signature=packet.toString();if(ack.isEmpty()&&signature.equals(state.sent))return;state.sent=signature;
        bridge.sendPacket(player,ui.apply(state.type),state.type==WorkshopType.ANVIL?"anvil_state":"enchanting_state",packet);
    }
    private void notice(Player player,WorkshopType type,String text){bridge.sendPacket(player,ui.apply(type),"ui_notice",Map.of("message",text));}
    private boolean writable(Player player,WorkshopType type){
        if(player.isDead()||player.getGameMode()==GameMode.SPECTATOR){notice(player,type,"当前状态不能操作装备");return false;}
        if(busy.test(player)){notice(player,type,"上一笔材料或兑换仍在处理，请稍候");return false;}
        if(!nativeAccess.canUse(player)){notice(player,type,"工作站已不可用，请重新打开");return false;}
        return true;
    }
    public boolean handle(Player player,WorkshopType type,String action,List<String> data){
        if(!type.equipment()||!type.matches(player.getOpenInventory())||!config.enabled(type))return false;
        State state=attach(player,type);refresh(player,state);
        switch(action){
            case "equipment_open","refresh_equipment" -> {state.sent="";push(player,state,"");}
            case "rename" -> {
                if(type!=WorkshopType.ANVIL||data.size()<4)return true;
                String value=data.get(1),token=data.get(2),ack=data.get(3);
                if(ack.length()>32)return true;
                if(!writable(player,type)){push(player,state,ack);return true;}
                if(value.length()>50){notice(player,type,"物品名称最多 50 个字符");push(player,state,ack);return true;}
                if(!state.itemToken.equals(token)||InventoryItems.empty(state.gear)){push(player,state,"");return true;}
                String raw=!state.gear.getItemMeta().hasDisplayName()&&value.equals(display(state.gear))?"":value;
                if(!nativeAccess.rename(state.view,raw)){notice(player,type,"当前服务端不支持铁砧改名");push(player,state,ack);return true;}
                state.explicitName=true;state.rawName=Objects.toString(((AnvilInventory)state.view.getTopInventory()).getRenameText(),"");state.name=raw.isEmpty()&&!value.isEmpty()?display(state.gear):state.rawName;push(player,state,ack);
            }
            case "enchant" -> {
                if(type!=WorkshopType.ENCHANTING||data.size()<3||!writable(player,type))return true;
                int index;try{index=Integer.parseInt(data.get(1));}catch(NumberFormatException e){return true;}
                if(index<0||index>2||!state.offerToken.equals(data.get(2))||state.offers==null){notice(player,type,"附魔选项已变化，请重新选择");push(player,state,"");return true;}
                ItemStack before=InventoryItems.copy(state.view.getTopInventory().getItem(0));
                boolean applied=nativeAccess.enchant(player,index);player.updateInventory();
                boolean changed=!Objects.equals(before,state.view.getTopInventory().getItem(0));
                notice(player,type,applied&&changed?"附魔已完成":changed?"装备状态已变化，请核对槽位中的物品":"附魔未生效，请检查等级、青金石和装备状态");push(player,state,"");
            }
            case "lapis_fill" -> {
                if(type!=WorkshopType.ENCHANTING||data.size()<3||!writable(player,type))return true;
                int amount;try{amount=Integer.parseInt(data.get(1));}catch(NumberFormatException e){return true;}
                if(amount<1||amount>3||!state.itemToken.equals(data.get(2))||InventoryItems.empty(state.gear)){notice(player,type,"请先放入目标装备或书");return true;}
                String itemToken=state.itemToken;ItemStack gear=state.gear.clone();gear.setAmount(1);
                ItemStack lapis=InventoryItems.copy(state.view.getTopInventory().getItem(1));
                if(lapis==null||lapis.getType()!=Material.LAPIS_LAZULI)lapis=new ItemStack(Material.LAPIS_LAZULI);
                lapis.setAmount(amount);ItemStack wantedLapis=lapis;
                bridge.sendPacket(player,ui.apply(type),"placement_state",Map.of("busy",true,"message",""));
                planner.fillCustom(player,type,stock->new RecipeMaterialPlanner.CustomDemand(List.of(gear,wantedLapis),()->player.getOpenInventory()==state.view&&state.itemToken.equals(itemToken)&&Objects.equals(state.gear,state.view.getTopInventory().getItem(0)),"LAPIS "+amount),message->{
                    if(player.isOnline()&&player.getOpenInventory()==state.view&&bridge.isUiOpen(player,ui.apply(type))){bridge.sendPacket(player,ui.apply(type),"placement_state",Map.of("busy",false,"message",message.isEmpty()?"青金石已补足":message));push(player,state,"");}
                });
            }
            default -> {return false;}
        }return true;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void open(InventoryOpenEvent event){if(!(event.getPlayer() instanceof Player player))return;for(var type:WorkshopType.values())if(type.equipment()&&config.enabled(type)&&type.matches(event.getView())){states.put(player.getUniqueId(),new State(event.getView(),type));break;}}
    @EventHandler public void close(InventoryCloseEvent event){states.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent event){states.remove(event.getPlayer().getUniqueId());}
    @Override public void close(){if(task!=null){task.cancel();task=null;}states.clear();}
    private static String enchantmentName(String key){
        return switch(key.replace("minecraft:","")){
            case "protection"->"保护";case "fire_protection"->"火焰保护";case "feather_falling"->"摔落缓冲";case "blast_protection"->"爆炸保护";case "projectile_protection"->"弹射物保护";
            case "respiration"->"水下呼吸";case "aqua_affinity"->"水下速掘";case "thorns"->"荆棘";case "depth_strider"->"深海探索者";case "frost_walker"->"冰霜行者";case "binding_curse"->"绑定诅咒";
            case "sharpness"->"锋利";case "smite"->"亡灵杀手";case "bane_of_arthropods"->"节肢杀手";case "knockback"->"击退";case "fire_aspect"->"火焰附加";case "looting"->"抢夺";case "sweeping"->"横扫之刃";
            case "efficiency"->"效率";case "silk_touch"->"精准采集";case "unbreaking"->"耐久";case "fortune"->"时运";case "power"->"力量";case "punch"->"冲击";case "flame"->"火矢";case "infinity"->"无限";
            case "luck_of_the_sea"->"海之眷顾";case "lure"->"饵钓";case "loyalty"->"忠诚";case "impaling"->"穿刺";case "riptide"->"激流";case "channeling"->"引雷";case "multishot"->"多重射击";case "quick_charge"->"快速装填";case "piercing"->"穿透";case "mending"->"经验修补";case "vanishing_curse"->"消失诅咒";case "soul_speed"->"灵魂疾行";case "swift_sneak"->"迅捷潜行";default->key;
        };
    }
}
