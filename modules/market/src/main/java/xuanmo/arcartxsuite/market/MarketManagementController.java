package xuanmo.arcartxsuite.market;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration;
import xuanmo.arcartxsuite.market.recycle.RecyclePreferences;
import xuanmo.arcartxsuite.market.recycle.RecycleProtectionRule;
import xuanmo.arcartxsuite.market.recycle.RecycleProtectionService;
import xuanmo.arcartxsuite.market.storage.*;

/** Owner-scoped recycle settings and an administrator-only, read-only forensic panel. */
public final class MarketManagementController implements AutoCloseable {
    public interface Host{
        void open(Player player,String ui);void send(Player player,String ui,String handler,Map<String,Object> packet);
        void rotate(Player player,String ui);void recycle(Player player);void publish(String message);
        default void protectionChanged(Player player,RecyclePreferences preferences){}
        default void protectionUnavailable(Player player){}
    }
    private record GuardView(int page,String kind){}
    private record Pick(String token,RecycleProtectionRule rule,long version){}
    private final JavaPlugin plugin;
    private final MarketWork work;private final MarketRepository repository;private final MarketModuleConfiguration config;
    private final PacketBridgeAPI packets;private final ItemBridgeAPI items;private final AuctionItemSerializer serializer;private final Host host;private final Logger logger;
    private final RecycleProtectionService protection;
    private final Map<UUID,GuardView> guardViews=new ConcurrentHashMap<>();private final Map<UUID,Pick> picks=new ConcurrentHashMap<>();
    private final Map<UUID,Long> epochs=new ConcurrentHashMap<>();private final Map<String,Long> tickets=new ConcurrentHashMap<>();
    private final Map<UUID,MarketAuditFilter> filters=new ConcurrentHashMap<>(),backFilters=new ConcurrentHashMap<>();
    private final Map<UUID,String> selectedRows=new ConcurrentHashMap<>();private final AtomicLong sequence=new AtomicLong();
    private final Map<UUID,String> listRequests=new ConcurrentHashMap<>(),detailRequests=new ConcurrentHashMap<>();
    private final Map<UUID, Long> auditListsLoading = new ConcurrentHashMap<>();
    private final Set<String> runningReads=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public MarketManagementController(MarketWork work, JavaPlugin plugin,MarketRepository repository,MarketModuleConfiguration config,PacketBridgeAPI packets,
                                       ItemBridgeAPI items,AuctionItemSerializer serializer,VanillaItemNameBridge names,Host host,Logger logger){
        this.plugin=plugin;this.work = work;this.repository=repository;this.config=config;this.packets=packets;this.items=items;this.serializer=serializer;this.host=host;this.logger=logger;
        protection=new RecycleProtectionService(work, plugin,repository,config.recycle(),names,this::protectionReady,this::protectionUnavailable,logger);
    }
    public RecycleProtectionService protection(){return protection;}
    public boolean admin(Player player){return player.hasPermission("arcartxsuite.market.admin");}
    public void warm(Player player){
        if(closed)return;UUID id=player.getUniqueId();String name=player.getName();
        work.async(()->{try{if(!closed)repository.noteMarketPlayer(id,name);}catch(RuntimeException exception){logger.warning("[MarketAudit] 玩家名记录暂不可用");}});
        if(config.recycle().enabled())protection.request(player);
    }
    public void clear(UUID id){protection.clear(id);epochs.remove(id);picks.remove(id);guardViews.remove(id);filters.remove(id);backFilters.remove(id);selectedRows.remove(id);listRequests.remove(id);detailRequests.remove(id);auditListsLoading.remove(id);tickets.keySet().removeIf(key->key.startsWith(id+":"));}
    @Override public void close(){closed=true;protection.close();epochs.clear();picks.clear();guardViews.clear();filters.clear();backFilters.clear();selectedRows.clear();listRequests.clear();detailRequests.clear();tickets.clear();auditListsLoading.clear();runningReads.clear();}
    public void openProtection(Player player){if(!config.recycle().enabled()){player.sendMessage("§c回收商店未启用");return;}epochs.put(player.getUniqueId(),sequence.incrementAndGet());picks.remove(player.getUniqueId());host.open(player,config.ui().recycleProtectionId());}
    public void openAudit(Player player){if(!admin(player)){player.sendMessage("§c你没有交易管理权限");return;}epochs.put(player.getUniqueId(),sequence.incrementAndGet());host.open(player,config.ui().adminTradesId());}
    private boolean inGuard(Player player){return packets.isUiOpen(player,config.ui().recycleProtectionId());}
    private boolean inAudit(Player player){return packets.isUiOpen(player,config.ui().adminTradesId());}
    private boolean authorize(Player player){
        if(admin(player))return true;Map<String,Object> packet=new LinkedHashMap<>();packet.put("message","交易管理权限不足");host.send(player,config.ui().adminTradesId(),"admin_denied",packet);return false;
    }
    private static String field(List<String> data,int index){return data.size()>index && data.get(index)!=null?data.get(index):"";}
    private static int number(String value,int fallback){try{return Integer.parseInt(value.replaceFirst("\\.0+$",""));}catch(NumberFormatException exception){return fallback;}}
    private static String text(Map<String,Object> data,String key){return String.valueOf(data.getOrDefault(key,""));}
    private void result(Player player,String ui,String handler,boolean success,String message,boolean pending){
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("success",success);packet.put("message",message);packet.put("pending",pending);
        if("guard_result".equals(handler))packet.put("saving",protection.changing(player.getUniqueId()));
        host.send(player,ui,handler,packet);
    }
    private void auditFailure(Player player,String channel,String message){
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("channel",channel);packet.put("id",selectedRows.getOrDefault(player.getUniqueId(),""));packet.put("message",message);
        packet.put("request",("detail".equals(channel)?detailRequests:listRequests).getOrDefault(player.getUniqueId(),""));
        host.send(player,config.ui().adminTradesId(),"audit_result",packet);
    }
    private String icon(ItemStack item){try{return item==null || items==null?"":items.itemToPreviewJson(item).orElse("");}catch(RuntimeException exception){return "";}}

    public boolean handle(Player player,String action,List<String> data){
        if(action.startsWith("audit_") && (!authorize(player) || !inAudit(player)))return true;
        if(action.startsWith("guard_") && !"guard_ui_open".equals(action) && !inGuard(player))return true;
        if(action.startsWith("guard_") && !config.recycle().enabled()){
            result(player,config.ui().recycleProtectionId(),"guard_result",false,"回收功能已关闭",false);return true;
        }
        if(action.startsWith("audit_") && !"audit_cancel".equals(action)){
            int index=Set.of("audit_open","audit_back_filter").contains(action)?1:2;
            String request=field(data,index);if(request.length()>64)request="";
            ("audit_detail".equals(action)?detailRequests:listRequests).put(player.getUniqueId(),request);
        }
        if(Set.of("audit_open","audit_filter","audit_page","audit_group","audit_back_filter").contains(action)){
            tickets.put(player.getUniqueId()+":list",sequence.incrementAndGet());
            tickets.remove(player.getUniqueId()+":detail");auditListsLoading.remove(player.getUniqueId());
        }
        try{
            switch(action){
                case "guard_ui_open" -> openProtection(player);
                case "guard_open","guard_list","guard_refresh" -> {
                    String kind=field(data,2);if(!Set.of("ALL","EXCLUDE","FAVORITE").contains(kind))kind="ALL";
                    guardViews.put(player.getUniqueId(),new GuardView(Math.max(0,number(field(data,1),0)),kind));
                    if("guard_open".equals(action)){
                        epochs.put(player.getUniqueId(),sequence.incrementAndGet());picks.remove(player.getUniqueId());
                        host.rotate(player,config.ui().recycleProtectionId());
                    }
                    if(!"guard_list".equals(action))protection.request(player);
                    RecyclePreferences preferences=protection.cached(player);
                    if(preferences==null)result(player,config.ui().recycleProtectionId(),"guard_result",false,"保护资料读取中，请稍后刷新",true);
                    else renderGuard(player,preferences);
                }
                case "guard_back" -> host.recycle(player);
                case "guard_pick" -> pickRule(player,data);
                case "guard_add","guard_remove","guard_auto" -> changeGuard(player,action,data);
                case "audit_open" -> {
                    epochs.put(player.getUniqueId(),sequence.incrementAndGet());selectedRows.remove(player.getUniqueId());backFilters.remove(player.getUniqueId());
                    queryAudit(player,defaultFilter());
                }
                case "audit_filter" -> filterAudit(player,field(data,1));
                case "audit_page" -> {
                    MarketAuditFilter current=filters.getOrDefault(player.getUniqueId(),defaultFilter());
                    queryAudit(player,page(current,Math.max(0,Math.min(100000,number(field(data,1),0)))));
                }
                case "audit_detail" -> detail(player,field(data,1));
                case "audit_group" -> group(player,field(data,1));
                case "audit_cancel" -> {
                    String channel="detail".equals(field(data,1))?"detail":"list";
                    if(!field(data,2).equals(("detail".equals(channel)?detailRequests:listRequests).getOrDefault(player.getUniqueId(),"")))return true;
                    tickets.remove(player.getUniqueId()+":"+channel);
                    if("list".equals(channel)){auditListsLoading.remove(player.getUniqueId());tickets.remove(player.getUniqueId()+":detail");}
                    auditFailure(player,channel,"查询已停止，可重新读取");
                }
                case "audit_back_filter" -> queryAudit(player,backFilters.getOrDefault(player.getUniqueId(),defaultFilter()));
                default -> {return false;}
            }
        }catch(IllegalArgumentException exception){
            if(action.startsWith("audit_"))auditFailure(player,"audit_detail".equals(action)?"detail":"list",exception.getMessage());
            else result(player,config.ui().recycleProtectionId(),"guard_result",false,exception.getMessage(),false);
        }catch(RuntimeException exception){
            logger.warning("[MarketManagement] 操作中断 action="+action+": "+exception.getMessage());
            if(action.startsWith("audit_"))auditFailure(player,"audit_detail".equals(action)?"detail":"list","查询暂不可用，请重新读取");
            else{result(player,config.ui().recycleProtectionId(),"guard_result",false,"操作未完成，请刷新保护清单核对",false);protection.request(player);}
        }
        return true;
    }
    public void protectionReady(Player player,RecyclePreferences preferences){if(inGuard(player))renderGuard(player,preferences);host.protectionChanged(player,preferences);}
    private void protectionUnavailable(Player player){
        if(inGuard(player)){
            Map<String,Object> packet=new LinkedHashMap<>();packet.put("saving",protection.changing(player.getUniqueId()));
            packet.put("message",protection.changing(player.getUniqueId())?"正在保存保护设置，请等待":"保护资料需要重新读取，回收暂时暂停");
            host.send(player,config.ui().recycleProtectionId(),"guard_unavailable",packet);
        }
        host.protectionUnavailable(player);
    }
    private void renderGuard(Player player,RecyclePreferences preferences){
        GuardView selected=guardViews.getOrDefault(player.getUniqueId(),new GuardView(0,"ALL"));
        List<RecycleProtectionRule> filtered=preferences.rules().stream().filter(rule->"ALL".equals(selected.kind()) || selected.kind().equals(rule.kind())).toList();
        int pages=Math.max(1,(filtered.size()+11)/12),page=Math.min(selected.page(),pages-1);
        guardViews.put(player.getUniqueId(),new GuardView(page,selected.kind()));
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("version",String.valueOf(preferences.version()));
        packet.put("auto",preferences.autoEnabled());packet.put("effective",protection.automatic(player));
        packet.put("saving",protection.changing(player.getUniqueId()));
        packet.put("ready",protection.cached(player)!=null && !protection.changing(player.getUniqueId()));
        packet.put("autoStatus",!preferences.autoEnabled()?"个人自动回收已关闭":!config.recycle().allowAutoRecycle()?"个人已开，但服务器关闭自动回收":!player.hasPermission("axsmarket.autorecycle")?"个人已开，但你没有自动回收权限":protection.automatic(player)?"个人自动回收生效中":"自动回收暂不可用，请检查保护清单");
        packet.put("page",page);packet.put("pages",pages);packet.put("pageText",(page+1)+"/"+pages);packet.put("kind",selected.kind());
        packet.put("total",preferences.rules().size());packet.put("maximum",config.recycle().maxProtectionRules());
        Map<String,Object> rows=new LinkedHashMap<>();int index=0;
        for(RecycleProtectionRule rule:filtered.subList(page*12,Math.min(filtered.size(),page*12+12))){
            String key=String.valueOf(index++);Map<String,Object> row=new LinkedHashMap<>();row.put("id",rule.id());row.put("name",rule.displayName());row.put("kind",rule.kind());
            row.put("description","EXCLUDE".equals(rule.kind())?"排除此材质的所有变体":"收藏同款；忽略数量、耐久及修复惩罚");
            ItemStack template=protection.template(player.getUniqueId(),rule.id());
            if(template==null && "EXCLUDE".equals(rule.kind())){
                Material material=Material.matchMaterial(rule.material());if(material!=null && material.isItem() && !material.isAir())template=new ItemStack(material);
            }
            row.put("readable",template!=null);
            if(template==null && "FAVORITE".equals(rule.kind()))row.put("description","模板无法完整读取，回收已暂停；可移除后重选");
            if(!Set.of("EXCLUDE","FAVORITE").contains(rule.kind()))row.put("description","规则类型无法识别，回收已暂停；可移除后重新添加");
            packet.put("ruleIcon"+key,icon(template));rows.put(key,row);
        }
        packet.put("rules",rows);packet.put("count",index);host.send(player,config.ui().recycleProtectionId(),"guard_data",packet);
    }
    private void pickRule(Player player,List<String> data){
        RecyclePreferences preferences=protection.cached(player);
        if(preferences==null || protection.changing(player.getUniqueId()))throw new IllegalArgumentException("保护资料尚不可编辑，请等待刷新");
        int raw=number(field(data,1),-1);String kind=field(data,2);
        if(raw<9 || raw>44)throw new IllegalArgumentException("请选择主背包中的真实物品");
        ItemStack item=player.getInventory().getItem(raw>=36?raw-36:raw);
        if(item==null || item.getType().isAir())throw new IllegalArgumentException("槽位已没有物品，请重新选择");
        RecycleProtectionRule rule=protection.rule(player,item,kind);Pick pick=new Pick(UUID.randomUUID().toString(),rule,preferences.version());picks.put(player.getUniqueId(),pick);
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("token",pick.token());packet.put("name",rule.displayName());packet.put("kind",kind);packet.put("icon",icon(item));
        packet.put("description","EXCLUDE".equals(kind)?"将保护该材质的全部物品，包含自定义变体。":"将保护同款物品；不改物品NBT，不影响堆叠。");
        packet.put("version",String.valueOf(preferences.version()));
        host.send(player,config.ui().recycleProtectionId(),"guard_pick",packet);
    }
    private void changeGuard(Player player,String action,List<String> data){
        RecyclePreferences preferences=protection.cached(player);if(preferences==null)throw new IllegalArgumentException("保护资料尚未加载，请刷新");
        long version;try{version=Long.parseLong(field(data,2));}catch(NumberFormatException exception){throw new IllegalArgumentException("保护版本无效，请刷新");}
        RecycleProtectionRule rule=null;String mutation;boolean enabled=false;
        if("guard_auto".equals(action)){
            if(!Set.of("0","1").contains(field(data,1)))throw new IllegalArgumentException("个人开关状态无效");enabled="1".equals(field(data,1));mutation="auto";
        }else if("guard_add".equals(action)){
            Pick chosen=picks.get(player.getUniqueId());if(chosen==null || chosen.version()!=version || !chosen.token().equals(field(data,1)))throw new IllegalArgumentException("物品选择或保护版本已失效，请重新选择");rule=chosen.rule();mutation="add";
        }else{rule=preferences.rules().stream().filter(value->value.id().equals(field(data,1))).findFirst().orElseThrow(()->new IllegalArgumentException("规则已变化，请刷新"));mutation="remove";}
        host.rotate(player,config.ui().recycleProtectionId());long epoch=epochs.getOrDefault(player.getUniqueId(),0L);
        protection.update(player,version,mutation,enabled,rule,(success,message)->{
            if(success){if(epochs.getOrDefault(player.getUniqueId(),0L)==epoch)picks.remove(player.getUniqueId());host.publish("RECYCLE_PROTECTION_CHANGED:"+player.getUniqueId());}
            if(player.isOnline() && inGuard(player) && epochs.getOrDefault(player.getUniqueId(),0L)==epoch)result(player,config.ui().recycleProtectionId(),"guard_result",success,message,false);
        });
    }

    private <T> void readAdmin(Player player, String channel, Supplier<T> read, Consumer<T> render) {
        UUID id = player.getUniqueId();
        var admission = AuthAccess.snapshot(id);
        if (!admission.admitted() || !authorize(player) || !inAudit(player)) return;
        long epoch = epochs.computeIfAbsent(id, ignored -> sequence.incrementAndGet());
        long ticket = sequence.incrementAndGet();
        String key = id + ":" + channel;
        tickets.put(key, ticket);
        if (!runningReads.add(key)) {
            auditFailure(player, channel, "上一条查询尚未结束，请稍后重新读取");
            return;
        }
        boolean list = "list".equals(channel);
        if (list) {
            auditListsLoading.put(id, ticket);
            tickets.remove(id + ":detail");
        }
        work.async(() -> {
            T value = null;
            String failure = null;
            try {
                if (!closed) value = read.get();
            } catch (RuntimeException exception) {
                failure = exception instanceof IllegalArgumentException ? exception.getMessage() : "管理数据查询暂不可用，请重试";
                logger.warning("[MarketAudit] " + exception.getMessage());
            } finally {
                runningReads.remove(key);
            }
            if (closed) return;
            T loaded = value;
            String error = failure;
            work.main(() -> {
                if (list) auditListsLoading.remove(id, ticket);
                if (closed || Bukkit.getPlayer(id) != player || !player.isOnline()
                    || epochs.getOrDefault(id, 0L) != epoch || !Long.valueOf(ticket).equals(tickets.get(key))
                    || !inAudit(player)) return;
                var current = AuthAccess.snapshot(id);
                if (!current.admitted() || current.generation() != admission.generation()
                    || !Objects.equals(current.connectionId(), admission.connectionId()) || !authorize(player)) return;
                try {
                    if (error == null) render.accept(loaded);
                    else auditFailure(player, channel, error);
                } catch (RuntimeException exception) {
                    logger.warning("[MarketAudit] 页面回包失败: " + exception.getMessage());
                    auditFailure(player, channel, "管理页面未完成加载，请刷新");
                }
            });
        });
    }
    private MarketAuditFilter defaultFilter(){ZoneId zone=ZoneId.systemDefault();return new MarketAuditFilter(null,"",LocalDate.now(zone).minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli(),LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),"ALL","ALL","history",0,"");}
    private MarketAuditFilter page(MarketAuditFilter filter,int page){return new MarketAuditFilter(filter.player(),filter.item(),filter.from(),filter.until(),filter.type(),filter.state(),filter.scope(),page,filter.group());}
    private long date(String input,boolean end){
        if(input.isBlank())return end?Long.MAX_VALUE:0;
        try{ZoneId zone=ZoneId.systemDefault();
            if(input.length()==10){LocalDate day=LocalDate.parse(input,DateTimeFormatter.ISO_LOCAL_DATE);return (end?day.plusDays(1):day).atStartOfDay(zone).toInstant().toEpochMilli();}
            String pattern=input.length()==16?"uuuu-MM-dd HH:mm":"uuuu-MM-dd HH:mm:ss";
            long value=LocalDateTime.parse(input,DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)).atZone(zone).toInstant().toEpochMilli();
            return end?value+(input.length()==16?60000:1000):value;
        }catch(RuntimeException exception){throw new IllegalArgumentException("时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm[:ss]");}
    }
    private void filterAudit(Player player,String json){
        if(json.length()>4096)throw new IllegalArgumentException("筛选参数过长");Map<String,Object> form=xuanmo.arcartxsuite.api.util.SimpleJsonParser.parse(json);
        String actor=text(form,"player").trim(),item=text(form,"item").trim(),type=text(form,"type"),state=text(form,"state"),scope=text(form,"scope");
        if(actor.length()>64 || item.length()>100)throw new IllegalArgumentException("玩家或物品关键词过长");
        if(!Set.of("ALL","AUCTION","PSHOP","SHOP","RECYCLE","EXCHANGE","OTHER").contains(type))throw new IllegalArgumentException("交易类型无效");
        if(!Set.of("ALL","PENDING","ISSUES","PREPARED","READY","RUNNING","REVIEW","DONE","FAILED","CANCELLED","EXPIRED").contains(state))throw new IllegalArgumentException("记录状态无效");
        if(!Set.of("history","operations").contains(scope))throw new IllegalArgumentException("记录来源无效");
        if("EXCHANGE".equals(type) || Set.of("PENDING","ISSUES","PREPARED","READY","RUNNING","REVIEW","FAILED").contains(state))scope="operations";
        long from=date(text(form,"from").trim(),false),until=date(text(form,"until").trim(),true);if(from>=until)throw new IllegalArgumentException("开始时间必须早于结束时间");
        final long start=from,end=until;
        final String requestedScope=scope;
        readAdmin(player,"list",()->{
            UUID party=actor.isBlank()?null:repository.resolveMarketPlayer(actor);
            MarketAuditFilter filter=new MarketAuditFilter(party,item,start,end,type,state,requestedScope,0,"");
            return Map.entry(filter,repository.searchMarketAudit(filter));
        },entry->renderAudit(player,entry.getKey(),entry.getValue()));
    }
    private void queryAudit(Player player,MarketAuditFilter filter){readAdmin(player,"list",()->repository.searchMarketAudit(filter),page->renderAudit(player,filter,page));}
    private String category(String kind){return kind.startsWith("AUCTION") || Set.of("BID_WIN","BUY_NOW").contains(kind)?"拍卖":kind.startsWith("PSHOP")?"玩家店铺":kind.startsWith("SHOP")?"系统商店":kind.startsWith("RECYCLE")?"回收":"NPC_EXCHANGE".equals(kind)?"NPC 兑换":"其他";}
    private String stateText(MarketAuditRow row){
        if("HISTORY".equals(row.source()) && "DONE".equals(row.state()))return "业务已记录";
        String label=switch(row.state()){
            case "PREPARED" -> "已准备";case "READY" -> "待执行";case "RUNNING" -> "处理中";
            case "ITEM_SAVING" -> "待原子服保存";
            case "REVIEW" -> "需核对";case "DONE" -> "本条已完成";case "FAILED" -> "明确失败";
            case "APPLIED" -> "已交付";case "CONFIRMED" -> "已核对";
            case "CANCELLED" -> "已取消";case "EXPIRED" -> "已到期";default -> row.state();
        };
        return label+"（"+row.state()+"）";
    }
    private String name(UUID player,String value){return player==null?"—":value!=null && !value.isBlank()?value:player.equals(new UUID(0,0))?"系统/未记录":player.toString();}
    private String when(long time){return java.time.Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
    private String dateField(long value,boolean end){if(value==0 || value==Long.MAX_VALUE)return "";return when(end?value-1000:value);}
    private void renderAudit(Player player,MarketAuditFilter filter,MarketAuditPage page){
        MarketAuditFilter shown=page(filter,page.page());filters.put(player.getUniqueId(),shown);
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("page",page.page());packet.put("pages",page.pages());packet.put("pageText",(page.page()+1)+"/"+page.pages());packet.put("total",String.valueOf(page.total()));
        packet.put("request",listRequests.getOrDefault(player.getUniqueId(),""));
        packet.put("player",filter.player()==null?"":filter.player().toString());packet.put("item",filter.item());packet.put("from",dateField(filter.from(),false));packet.put("until",dateField(filter.until(),true));
        packet.put("type",filter.type());packet.put("state",filter.state());packet.put("scope",filter.scope());packet.put("group",filter.group());packet.put("readAt",when(System.currentTimeMillis()));packet.put("zone",ZoneId.systemDefault().toString());
        Map<String,Object> rows=new LinkedHashMap<>();int index=0;
        for(MarketAuditRow row:page.rows()){
            Map<String,Object> value=new LinkedHashMap<>();value.put("id",row.id());value.put("item",row.itemName().isBlank()?"（未记录物品名称）":row.itemName());
            value.put("kind",category(row.businessType())+" · "+row.kind());value.put("state",stateText(row));
            value.put("player",name(row.player(),row.playerName()));value.put("amount",row.currency().isBlank()?"—":row.amount()+" "+row.currency());value.put("time",when(row.createdAt()));rows.put(String.valueOf(index++),value);
        }
        packet.put("rows",rows);packet.put("count",index);host.send(player,config.ui().adminTradesId(),"audit_data",packet);
    }
    private void detail(Player player,String id){
        if(id.isBlank() || id.length()>100)throw new IllegalArgumentException("记录ID无效");
        if(auditListsLoading.containsKey(player.getUniqueId())){auditFailure(player,"detail","列表正在刷新，请稍后重新选择记录");return;}
        selectedRows.put(player.getUniqueId(),id);
        readAdmin(player,"detail",()->repository.marketAuditRow(id),row->{
            if(!id.equals(selectedRows.get(player.getUniqueId())))return;
            if(row==null){auditFailure(player,"detail","记录不存在或已清理，请刷新列表");return;}
            Map<String,Object> packet=new LinkedHashMap<>();packet.put("id",row.id());packet.put("group",row.tradeId());
            packet.put("request",detailRequests.getOrDefault(player.getUniqueId(),""));
            packet.put("title",row.itemName().isBlank()?category(row.businessType()):row.itemName());packet.put("kind",row.kind()+" / "+row.businessType());packet.put("state",stateText(row));packet.put("source","HISTORY".equals(row.source())?"业务历史":"处理流水");
            packet.put("player",name(row.player(),row.playerName()));packet.put("playerId",row.player()==null?"":row.player().toString());packet.put("other",name(row.otherPlayer(),row.otherName()));
            packet.put("otherId",row.otherPlayer()==null?"":row.otherPlayer().toString());
            packet.put("amount",row.currency().isBlank()?"—":row.amount()+" "+row.currency());packet.put("material",row.material());packet.put("time",when(row.createdAt()));
            packet.put("noteLines",wrap("状态更新时间："+when(row.updatedAt())+"\n"+row.note()+"\n"+(row.state().equals("REVIEW")?"结果不明，禁止盲目重试；使用核对命令前请确认实际钱物状态。":"历史记录不等于交付完成，请查看关联流水。")+(row.tradeId().isBlank()?"\n此旧记录未保存关联编号，请结合玩家、物品及时间查询处理流水。":"")));
            ItemStack item=null;try{if(!row.itemData().isBlank())item=serializer.deserialize(row.itemData());}catch(RuntimeException exception){logger.fine("审计物品快照无法预览: "+row.id());}
            packet.put("icon",icon(item));packet.put("quantity",item==null?0:item.getAmount());host.send(player,config.ui().adminTradesId(),"audit_detail_data",packet);
        });
    }
    private Map<String,String> wrap(String text){Map<String,String> result=new LinkedHashMap<>();int index=0;for(String line:text.split("\\R",-1)){StringBuilder part=new StringBuilder();int width=0;for(int cp:line.codePoints().toArray()){int units=cp>=0x2E80?2:1;if(width+units>56){result.put(String.valueOf(index++),part.toString());part.setLength(0);width=0;}part.appendCodePoint(cp);width+=units;}result.put(String.valueOf(index++),part.toString());}return result;}
    private void group(Player player,String id){
        if(id.isBlank() || id.length()>100)throw new IllegalArgumentException("记录ID无效");
        readAdmin(player,"list",()->repository.marketAuditRow(id),row->{
            if(row==null){auditFailure(player,"list","记录已清理，请刷新列表");return;}
            if(row.tradeId().isBlank()){auditFailure(player,"list","此记录没有关联编号，请按玩家、物品和时间查询流水");return;}
            MarketAuditFilter current=filters.getOrDefault(player.getUniqueId(),defaultFilter());
            if(current.group().isBlank())backFilters.put(player.getUniqueId(),current);
            queryAudit(player,new MarketAuditFilter(null,"",0,Long.MAX_VALUE,"ALL","ALL","all",0,row.tradeId()));
        });
    }
}
