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
    private record Viewer(String itemToken, int level, boolean creative, boolean hasItem) { }
    private record AnvilDisplay(Viewer viewer, String name, int cost, int maximum, int materialCost, boolean hasOutput) { }
    private record EnchantingDisplay(Viewer viewer, String offerToken, int lapis) { }

    private static final class State {
        final InventoryView view;final WorkshopType type;
        ItemStack gear;boolean initialized,explicitName;
        String itemToken="",offerToken="",name="",rawName="",pendingRenameAck="";
        EquipmentNativeAccess.Offers offers;
        AnvilDisplay sentAnvil;
        EnchantingDisplay sentEnchanting;
        long nextPush;
        State(InventoryView view,WorkshopType type){this.view=view;this.type=type;}

        void resetDisplay() {
            sentAnvil = null;
            sentEnchanting = null;
        }
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
            if(state==null||player==null||!player.isOnline()||player.getOpenInventory()!=state.view||!state.type.matches(state.view)){states.remove(uuid);continue;}
            if (ticks >= state.nextPush) push(player, state, "");
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
        ItemStack current = state.view.getTopInventory().getItem(0);
        if (InventoryItems.empty(current)) current = null;
        if(!state.initialized||!Objects.equals(current,state.gear)){
            state.gear=InventoryItems.copy(current);state.initialized=true;state.itemToken=UUID.randomUUID().toString();state.offerToken="";state.explicitName=false;state.name=display(state.gear);
            state.pendingRenameAck = "";
            if(state.type==WorkshopType.ANVIL&&state.gear!=null){
                String raw=state.gear.hasItemMeta()&&state.gear.getItemMeta().hasDisplayName()?state.name:"";
                nativeAccess.rename(state.view,raw);state.rawName=Objects.toString(((AnvilInventory)state.view.getTopInventory()).getRenameText(),"");
            }
        }
        if(state.type==WorkshopType.ENCHANTING){
            var offers=nativeAccess.offers(player, state.offers);
            if(!same(offers,state.offers)||state.offerToken.isEmpty()){state.offers=offers;state.offerToken=UUID.randomUUID().toString();}
        }
    }
    private void push(Player player,State state,String ack){
        state.nextPush = ticks + config.interval(state.type);
        String uiId = ui.apply(state.type);
        if (!bridge.isUiOpen(player, uiId)) {
            state.resetDisplay();
            state.pendingRenameAck = "";
            return;
        }
        refresh(player, state);
        var viewer = new Viewer(state.itemToken, player.getLevel(), player.getGameMode() == GameMode.CREATIVE, state.gear != null);
        if (state.type == WorkshopType.ANVIL) {
            if (!ack.isEmpty()) state.pendingRenameAck = ack;
            pushAnvil(player, state, uiId, viewer);
        } else {
            pushEnchanting(player, state, uiId, viewer);
        }
    }

    private void pushAnvil(Player player, State state, String uiId, Viewer viewer) {
        AnvilInventory inventory = (AnvilInventory) state.view.getTopInventory();
        String raw = Objects.toString(inventory.getRenameText(), "");
        if (!raw.equals(state.rawName)) {
            state.rawName = raw;
            state.name = raw.isEmpty() && !state.explicitName ? display(state.gear) : raw;
        }
        var display = new AnvilDisplay(viewer, state.name, inventory.getRepairCost(), inventory.getMaximumRepairCost(),
            inventory.getRepairCostAmount(), !InventoryItems.empty(inventory.getItem(2)));
        String ack = state.pendingRenameAck;
        if (ack.isEmpty() && display.equals(state.sentAnvil)) return;

        var packet = viewerFields(viewer);
        packet.put("name", display.name());
        packet.put("ack", ack);
        packet.put("cost", display.cost());
        packet.put("maximum", display.maximum());
        packet.put("materialCost", display.materialCost());
        packet.put("tooExpensive", !viewer.creative() && viewer.hasItem() && !display.hasOutput() && display.cost() >= display.maximum());
        packet.put("enough", viewer.creative() || viewer.level() >= display.cost());
        packet.put("hasOutput", display.hasOutput());
        if (bridge.sendPacket(player, uiId, "anvil_state", packet)) {
            state.sentAnvil = display;
            state.pendingRenameAck = "";
        }
    }

    private void pushEnchanting(Player player, State state, String uiId, Viewer viewer) {
        ItemStack secondary = state.view.getTopInventory().getItem(1);
        int lapis = !InventoryItems.empty(secondary) && secondary.getType() == Material.LAPIS_LAZULI ? secondary.getAmount() : 0;
        var display = new EnchantingDisplay(viewer, state.offerToken, lapis);
        if (display.equals(state.sentEnchanting)) return;

        var packet = viewerFields(viewer);
        packet.put("lapis", lapis);
        packet.put("offerToken", display.offerToken());
        packet.put("supported", state.offers != null);
        Map<String, Object> rows = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            int cost = state.offers == null ? 0 : state.offers.costs()[i];
            int clue = state.offers == null ? -1 : state.offers.clues()[i];
            int hintLevel = state.offers == null ? 0 : state.offers.levels()[i];
            String hint = cost <= 0 ? "放入可附魔的装备或书" : enchantmentName(nativeAccess.hint(clue)) + (hintLevel > 0 ? " " + hintLevel : "");
            rows.put(Integer.toString(i), Map.of("required", cost, "lapis", i + 1, "spentLevels", i + 1, "hint", hint,
                "enabled", cost > 0 && viewer.hasItem() && (viewer.creative() || viewer.level() >= cost && lapis >= i + 1)));
        }
        packet.put("offers", rows);
        if (bridge.sendPacket(player, uiId, "enchanting_state", packet)) state.sentEnchanting = display;
    }

    private static Map<String, Object> viewerFields(Viewer viewer) {
        var packet = new LinkedHashMap<String, Object>();
        packet.put("level", viewer.level());
        packet.put("creative", viewer.creative());
        packet.put("hasItem", viewer.hasItem());
        packet.put("itemToken", viewer.itemToken());
        return packet;
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
        State state=attach(player,type);
        switch(action){
            case "equipment_open","refresh_equipment" -> {state.resetDisplay();push(player,state,"");}
            case "rename" -> {
                if(type!=WorkshopType.ANVIL||data.size()<4)return true;
                String value=data.get(1),token=data.get(2),ack=data.get(3);
                if(ack.length()>32)return true;
                if(!writable(player,type)){push(player,state,ack);return true;}
                if(value.length()>50){notice(player,type,"物品名称最多 50 个字符");push(player,state,ack);return true;}
                refresh(player,state);
                if(!state.itemToken.equals(token)||InventoryItems.empty(state.gear)){push(player,state,"");return true;}
                String raw=!state.gear.getItemMeta().hasDisplayName()&&value.equals(display(state.gear))?"":value;
                if(!nativeAccess.rename(state.view,raw)){notice(player,type,"当前服务端不支持铁砧改名");push(player,state,ack);return true;}
                state.explicitName=true;state.rawName=Objects.toString(((AnvilInventory)state.view.getTopInventory()).getRenameText(),"");state.name=raw.isEmpty()&&!value.isEmpty()?display(state.gear):state.rawName;push(player,state,ack);
            }
            case "enchant" -> {
                if(type!=WorkshopType.ENCHANTING||data.size()<3||!writable(player,type))return true;
                refresh(player,state);
                int index;try{index=Integer.parseInt(data.get(1));}catch(NumberFormatException e){return true;}
                if(index<0||index>2||!state.offerToken.equals(data.get(2))||state.offers==null){notice(player,type,"附魔选项已变化，请重新选择");push(player,state,"");return true;}
                ItemStack before=InventoryItems.copy(state.view.getTopInventory().getItem(0));
                boolean applied=nativeAccess.enchant(player,index);player.updateInventory();
                boolean changed=!Objects.equals(before,state.view.getTopInventory().getItem(0));
                notice(player,type,applied&&changed?"附魔已完成":changed?"装备状态已变化，请核对槽位中的物品":"附魔未生效，请检查等级、青金石和装备状态");push(player,state,"");
            }
            case "lapis_fill" -> {
                if(type!=WorkshopType.ENCHANTING||data.size()<3||!writable(player,type))return true;
                refresh(player,state);
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
