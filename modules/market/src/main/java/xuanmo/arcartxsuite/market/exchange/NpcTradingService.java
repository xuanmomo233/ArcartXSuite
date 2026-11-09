package xuanmo.arcartxsuite.market.exchange;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.bridge.*;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.inventory.*;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import java.util.function.Supplier;

/** NPC exchange and online catalog authoring share only public host bridges. */
public final class NpcTradingService implements Listener {
    public static final String ADMIN="arcartxsuite.market.exchange.admin";
    private static final class Buyer {final String shop,origin;final long revision;final String session=UUID.randomUUID().toString();String token=UUID.randomUUID().toString(),lastResult="";Buyer(String shop,String origin,long revision){this.shop=shop;this.origin=origin;this.revision=revision;}}
    private static final class Editor {
        final String session = UUID.randomUUID().toString();
        final Set<String> samples = new LinkedHashSet<>();
        String token = UUID.randomUUID().toString(), selected = "", selectedOffer = "";
    }
    private final Plugin plugin;
    private final File folder;
    private final PacketBridgeAPI bridge;
    private final CurrencyBridgeAPI currencies;
    private final ItemSourceRegistry sources;
    private final ItemBridgeAPI items;
    private final AdyeshachNpcBridgeAPI npcs;
    private final Supplier<WarehouseMaterialAccess> warehouse;
    private final TradeCatalog catalog;
    private final NpcTradeRepository repository;
    private final NpcExchange exchange;
    private final Logger logger;
    private final ItemPresentation previews;
    private final Gson gson=new Gson();
    private final Map<UUID,Buyer> buyers=new HashMap<>();
    private final Map<UUID,Editor> editors=new HashMap<>();
    private final Map<UUID,String> pending=new HashMap<>();
    private String buyerUi,editorUi;
    private boolean closed;
    public NpcTradingService(Plugin plugin,File folder,PacketBridgeAPI bridge,CurrencyBridgeAPI currencies,ItemSourceRegistry sources,ItemBridgeAPI items,VanillaItemNameBridge names,
                             AdyeshachNpcBridgeAPI npcs,Supplier<WarehouseMaterialAccess> warehouse,NpcTradeRepository repository,String buyerUi,String editorUi,Logger logger)throws IOException{
        this.plugin=plugin;this.folder=folder;this.bridge=bridge;this.currencies=currencies;this.sources=sources;this.items=items;this.npcs=npcs;this.warehouse=warehouse;this.repository=repository;
        this.buyerUi=buyerUi;this.editorUi=editorUi;this.logger=logger;catalog=new TradeCatalog(folder);exchange=new NpcExchange(plugin,repository,currencies,logger);previews=new ItemPresentation(items,names);bindNpcs();
    }
    public boolean busy(Player player){return InventoryOperation.busy(player)||pending.containsKey(player.getUniqueId());}
    public void warehouseChanged(){
        for(UUID uuid:List.copyOf(pending.keySet())){Player player=Bukkit.getPlayer(uuid);Buyer buyer=buyers.get(uuid);if(player!=null&&buyer!=null)send(player,buyerUi,"npc_result",Map.of("busy",false,"message","仓库服务已变化，请重新操作","token",buyer.token));}
        pending.clear();exchange.warehouseUnavailable();
    }
    private void report(Player player,String message){
        String ui=bridge.isUiOpen(player,buyerUi)?buyerUi:bridge.isUiOpen(player,editorUi)?editorUi:null;
        if(ui!=null)send(player,ui,"ui_notice",Map.of("message",ChatColor.stripColor(message)));
        else {
            for(String parent:List.of("AXS:vanilla_backpack","AXS:warehouse_storage","AXS:warehouse_manage","AXS:warehouse_bank"))if(bridge.isUiOpen(player,parent)){send(player,parent,"ui_notice",Map.of("message",ChatColor.stripColor(message)));return;}
            player.sendMessage(message);
        }
    }
    private Map<String,Object> assetData(TradeCatalog.Asset asset){return Map.of("kind",asset.kind(),"source",asset.source(),"id",asset.id(),"amount",asset.amount(),"snapshot",TradeCatalog.clean(asset.snapshot()));}
    private Map<String,Object> offerData(TradeCatalog.Offer offer){return Map.of("id",offer.id(),"name",offer.name(),"enabled",offer.enabled(),"permission",offer.permission(),"costs",offer.costs().stream().map(this::assetData).toList(),"rewards",offer.rewards().stream().map(this::assetData).toList());}

    private void validateEditorAsset(Editor editor, TradeCatalog.Asset asset) {
        if (asset == null) throw new IllegalArgumentException("资产不能为空");
        if (!"snapshot".equals(asset.source())) return;
        if (editor.samples.contains(asset.snapshot())) return;
        var shop = catalog.shop(editor.selected);
        if (shop != null && shop.offers().stream()
            .flatMap(offer -> java.util.stream.Stream.concat(offer.costs().stream(), offer.rewards().stream()))
            .anyMatch(saved -> "snapshot".equals(saved.source()) && Objects.equals(saved.snapshot(), asset.snapshot()))) return;
        throw new IllegalArgumentException("请重新从真实背包取样");
    }
    private boolean permission(Player player,String permission){return permission==null||permission.isBlank()||player.hasPermission(permission);}
    private boolean allowed(Player player,TradeCatalog.Shop shop){return shop!=null&&shop.enabled()&&permission(player,shop.permission());}
    private void bindNpcs(){
        if(npcs==null)return;
        if(!npcs.isAvailable()&&!npcs.initialize())return;
        boolean registered=npcs.registerInteractHandler((player,entity)->{
            for(String name:npcs.getEntityNames(entity))if(openBound(player,"ady:"+name))return;
        });
        if(registered)logger.info("NPC 兑换已绑定 Adyeshach 交互事件");
    }
    @EventHandler public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event){if("Adyeshach".equalsIgnoreCase(event.getPlugin().getName()))bindNpcs();}

    @EventHandler
    public void onPluginDisable(org.bukkit.event.server.PluginDisableEvent event) {
        if (npcs != null && "Adyeshach".equalsIgnoreCase(event.getPlugin().getName())) npcs.shutdown();
    }

    private boolean openBound(Player player,String key){
        for(var shop:catalog.shops())if(shop.npcs().stream().anyMatch(key::equalsIgnoreCase)){open(player,shop.id(),key);return true;}return false;
    }
    private boolean originValid(Player player, String origin) {
        if (!TradeCatalog.isAdyeshachBinding(origin) || npcs == null || !npcs.isAvailable()) return false;
        return npcs.locateByName(origin.substring(4))
            .filter(location -> Objects.equals(location.getWorld(), player.getWorld()))
            .filter(location -> location.distanceSquared(player.getLocation()) <= 64)
            .isPresent();
    }

    private boolean canUseExchange(Player player, TradeCatalog.Shop shop, String origin) {
        return allowed(player, shop) && origin != null && shop.npcs().stream().anyMatch(origin::equalsIgnoreCase)
            && originValid(player, origin);
    }

    private void open(Player player,String id,String origin){
        if (closed || bridge == null || !bridge.isAvailable()) return;
        if(busy(player)){report(player,"§e材料或兑换仍在处理，请稍候");return;}
        TradeCatalog.Shop shop=catalog.shop(id);
        if(!allowed(player,shop)){report(player,"§c兑换所未启用或没有使用权限");return;}
        String resolved = shop.npcs().stream()
            .filter(binding -> origin == null || origin.equalsIgnoreCase(binding))
            .filter(binding -> originValid(player, binding))
            .findFirst().orElse(null);
        if (resolved == null) {
            report(player, "§e请在绑定的 Adyeshach NPC 附近打开兑换");
            return;
        }
        player.closeInventory();
        Buyer buyer = new Buyer(id, resolved, shop.revision());
        buyers.put(player.getUniqueId(), buyer);
        editors.remove(player.getUniqueId());
        if (!bridge.openUi(player, buyerUi)) buyers.remove(player.getUniqueId(), buyer);
    }
    public void openEditor(Player player,String selected){
        if(!player.hasPermission(ADMIN)||busy(player)){report(player,"§c没有编辑权限或上一笔操作仍在处理");return;}
        player.closeInventory();Editor editor=new Editor();editor.selected=selected==null?"":selected;editors.put(player.getUniqueId(),editor);buyers.remove(player.getUniqueId());bridge.openUi(player,editorUi);
    }
    private void send(Player player,String ui,String packet,Map<String,Object> data){if(!closed&&player.isOnline()&&bridge.isUiOpen(player,ui))bridge.sendPacket(player,ui,packet,data);}
    private Map<String,Object> assetView(Player player,TradeCatalog.Asset asset){
        Map<String,Object> row=new LinkedHashMap<>();
        if(asset.currency()){
            var currencyDefinition=currencies.definition(asset.id());
            if(currencyDefinition==null||asset.quantity().stripTrailingZeros().scale()>currencyDefinition.scale())throw new IllegalArgumentException("货币金额精度或币种配置无效");
            var definition=currencies.definition(asset.id());row.put("name",definition==null?asset.id():definition.displayName());row.put("quantity",currencies.format(asset.id(),asset.quantity()));
            var money=currencies.bridge(asset.id());boolean available=money!=null&&money.available();row.put("available",available);
            ItemStack icon=new ItemStack(Material.GOLD_INGOT);var meta=icon.getItemMeta();meta.setDisplayName("§e"+ChatColor.translateAlternateColorCodes('&',definition.displayName()));meta.setLore(available?List.of("§7账户货币，不消耗背包物品"):List.of("§c此货币暂不可用，请联系服主"));icon.setItemMeta(meta);row.put("icon",previews.icon(icon));
        }else{
            ItemStack item=asset.create(sources,player);row.put("name",previews.name(item));row.put("icon",previews.icon(item));row.put("quantity",Integer.toString(asset.itemAmount()));
            row.put("available",true);
        }return row;
    }
    private String summary(Player player,List<TradeCatalog.Asset> assets){
        List<String> parts=new ArrayList<>();for(var asset:assets){try{var row=assetView(player,asset);parts.add(row.get("name")+" ×"+row.get("quantity"));}catch(RuntimeException e){parts.add("不可用物品");}}return String.join(" + ",parts);
    }
    private String definitionSummary(List<TradeCatalog.Asset> assets){List<String> values=new ArrayList<>();for(var asset:assets){var currency=asset.currency()?currencies.definition(asset.id()):null;values.add((currency==null?asset.id():currency.displayName())+" ×"+asset.amount());}return String.join(" + ",values);}
    private Map<String,Object> cachedAsset(Player player,TradeCatalog.Asset asset,Map<TradeCatalog.Asset,Map<String,Object>> cache){return cache.computeIfAbsent(asset,key->{try{return assetView(player,key);}catch(RuntimeException e){return Map.of("name","不可用资产","icon",previews.icon(new ItemStack(Material.BARRIER)),"quantity","—","available",false);}});}
    private String cachedSummary(Player player,List<TradeCatalog.Asset> assets,Map<TradeCatalog.Asset,Map<String,Object>> cache){List<String> values=new ArrayList<>();for(var asset:assets){var row=cachedAsset(player,asset,cache);values.add(row.get("name")+" ×"+row.get("quantity"));}return String.join(" + ",values);}
    private void buyerDetail(Player player,String id){
        Buyer buyer=buyers.get(player.getUniqueId());if(buyer==null)return;var shop=catalog.shop(buyer.shop);
        if(!canUseExchange(player,shop,buyer.origin)||shop.revision()!=buyer.revision){refresh(player);return;}
        var offer=shop.offers().stream().filter(row->row.id().equals(id)&&row.enabled()&&permission(player,row.permission())).findFirst().orElse(null);if(offer==null){refresh(player);return;}
        Map<String,Object> costs=assetRows(player,offer.costs()),rewards=assetRows(player,offer.rewards());boolean available=true;
        for(var rows:List.of(costs,rewards))for(Object row:rows.values())if(!Boolean.TRUE.equals(((Map<?,?>)row).get("available")))available=false;
        send(player,buyerUi,"npc_detail",Map.of("id",id,"revision",Long.toString(shop.revision()),"costRows",costs,"rewardRows",rewards,"available",available));
    }
    private Map<String,Object> assetRows(Player player,List<TradeCatalog.Asset> assets){Map<String,Object> rows=new LinkedHashMap<>();for(int i=0;i<assets.size();i++)try{rows.put(Integer.toString(i),assetView(player,assets.get(i)));}catch(RuntimeException e){rows.put(Integer.toString(i),Map.of("name","物品来源不可用","icon",previews.icon(new ItemStack(Material.BARRIER)),"quantity","—","available",false));}return rows;}
    private void refresh(Player player){
        if(closed)return;
        Buyer buyer=buyers.get(player.getUniqueId());if(buyer==null||busy(player))return;
        TradeCatalog.Shop shop=catalog.shop(buyer.shop);
        if(!canUseExchange(player,shop,buyer.origin)){report(player,"§e兑换配置或 NPC 状态已变化，请重新打开");return;}
        if(shop.revision()!=buyer.revision){
            buyers.put(player.getUniqueId(),new Buyer(shop.id(),buyer.origin,shop.revision()));refresh(player);return;
        }
        if(buyer.token.isEmpty())buyer.token=UUID.randomUUID().toString();
        MaterialStock.readAsync(player,warehouse.get(),stock->{
            if(closed||buyers.get(player.getUniqueId())!=buyer||busy(player)||!bridge.isUiOpen(player,buyerUi)
                ||catalog.shop(shop.id())!=shop||!canUseExchange(player,shop,buyer.origin))return;
            Map<String,Object> rows=new LinkedHashMap<>();
            Map<TradeCatalog.Asset,Map<String,Object>> renderCache=new HashMap<>();
            Map<List<TradeCatalog.Asset>,Boolean> affordability=new HashMap<>();
            Map<String,java.math.BigDecimal> wallet=new HashMap<>();List<String> balances=new ArrayList<>();
            for(String id:currencies.currencyIds())try{var money=currencies.bridge(id);var definition=currencies.definition(id);if(money!=null&&money.available()){var balance=money.balance(player);wallet.put(id,balance);balances.add((definition==null?id:definition.displayName())+" "+currencies.format(id,balance));}else balances.add((definition==null?id:definition.displayName())+" —");}catch(RuntimeException e){balances.add(id+" —");}
            for(var offer:shop.offers())if(offer.enabled()&&permission(player,offer.permission())){
                Map<String,Object> row=new LinkedHashMap<>();row.put("id",offer.id());row.put("name",offer.name());row.put("cost",cachedSummary(player,offer.costs(),renderCache));row.put("reward",cachedSummary(player,offer.rewards(),renderCache));row.put("costRows",Map.of());row.put("rewardRows",Map.of());
                String icon=previews.icon(new ItemStack(Material.GOLD_INGOT));for(var reward:offer.rewards())if(!reward.currency()){icon=(String)cachedAsset(player,reward,renderCache).get("icon");break;}row.put("icon",icon);
                boolean affordable=affordability.computeIfAbsent(offer.costs(),costList->{try{var plan=NpcTradePlan.create(player,new TradeCatalog.Offer(offer.id(),offer.name(),true,"",costList,List.of()),stock,sources);for(var debit:plan.debits().entrySet())if(!wallet.containsKey(debit.getKey())||wallet.get(debit.getKey()).compareTo(debit.getValue())<0)return false;return true;}catch(RuntimeException e){return false;}});
                for(var asset:offer.costs())if(!Boolean.TRUE.equals(cachedAsset(player,asset,renderCache).get("available")))affordable=false;
                for(var asset:offer.rewards())if(!Boolean.TRUE.equals(cachedAsset(player,asset,renderCache).get("available")))affordable=false;
                row.put("available",affordable);rows.put("offer:"+offer.id(),row);
            }
            send(player,buyerUi,"npc_catalog",Map.of("name",shop.name(),"rows",rows,"token",buyer.token,"balances",String.join(" · ",balances),"warehouse",stock.warehouse()!=null&&stock.warehouse().available(),"lastResult",buyer.lastResult,"admin",player.hasPermission(ADMIN),"revision",Long.toString(shop.revision()),"session",buyer.session));
        });
    }
    private void buy(Player player,String offerId,String token){
        Buyer buyer=buyers.get(player.getUniqueId());if(buyer==null||buyer.token.isEmpty()||!buyer.token.equals(token)||busy(player))return;
        TradeCatalog.Shop shop=catalog.shop(buyer.shop);if(!canUseExchange(player,shop,buyer.origin)||shop.revision()!=buyer.revision){send(player,buyerUi,"npc_result",Map.of("busy",false,"message","报价或 NPC 状态已变化，请刷新确认","token",buyer.token));refresh(player);return;}
        if(player.getGameMode()==GameMode.SPECTATOR){send(player,buyerUi,"npc_result",Map.of("busy",false,"message","旁观模式不能兑换","token",buyer.token));return;}
        var offer=shop.offers().stream().filter(o->o.id().equals(offerId)&&o.enabled()&&permission(player,o.permission())).findFirst().orElse(null);if(offer==null)return;
        String request=UUID.randomUUID().toString();buyer.token="";pending.put(player.getUniqueId(),request);
        send(player,buyerUi,"npc_result",Map.of("busy",true,"message","正在核对材料与费用","token",buyer.token));
        MaterialStock.readAsync(player,warehouse.get(),stock->{
            try{
                if(!pending.remove(player.getUniqueId(),request)||closed||buyers.get(player.getUniqueId())!=buyer||!bridge.isUiOpen(player,buyerUi))return;
                if(catalog.shop(shop.id())!=shop||!canUseExchange(player,shop,buyer.origin)){buyer.token=UUID.randomUUID().toString();send(player,buyerUi,"npc_result",Map.of("busy",false,"message","报价或 NPC 状态已变化，请刷新确认","token",buyer.token));return;}
                NpcTradePlan plan=NpcTradePlan.create(player,offer,stock,sources);
                TradeCatalog.validate(new TradeCatalog.Shop(shop.id(),shop.name(),shop.enabled(),shop.permission(),shop.revision(),shop.npcs(),List.of(offer)),currencies);
                exchange.execute(player,shop.id()+"@"+shop.revision(),offer.id(),plan,stock,()->buyers.get(player.getUniqueId())==buyer&&bridge.isUiOpen(player,buyerUi)&&catalog.shop(shop.id())==shop&&canUseExchange(player,shop,buyer.origin)&&permission(player,offer.permission())&&(plan.requests().isEmpty()||stock.access()==warehouse.get()),message->{
                    buyer.token=UUID.randomUUID().toString();buyer.lastResult=message;
                    send(player,buyerUi,"npc_result",Map.of("busy",false,"message",message,"token",buyer.token));refresh(player);
                });
            }catch(RuntimeException e){pending.remove(player.getUniqueId(),request);buyer.token=UUID.randomUUID().toString();send(player,buyerUi,"npc_result",Map.of("busy",false,"message",e.getMessage()==null?"兑换未执行":e.getMessage(),"token",buyer.token));}
        });
    }
    private static String operationId(String value){try{return UUID.fromString(value).toString();}catch(IllegalArgumentException e){throw new IllegalArgumentException("请输入有效的凭证 UUID");}}
    public boolean ownsPacket(String id) {
        return buyerUi.equalsIgnoreCase(id) || editorUi.equalsIgnoreCase(id)
            || "AXS:market_exchange".equalsIgnoreCase(id) || "AXS:market_exchange_editor".equalsIgnoreCase(id);
    }

    public boolean handle(Player player,String id,List<String> data){
        if(closed||data==null||data.isEmpty())return false;
        if(buyerUi.equalsIgnoreCase(id)||"AXS:market_exchange".equalsIgnoreCase(id)){
            if(!bridge.isUiOpen(player,buyerUi)||!buyers.containsKey(player.getUniqueId()))return true;
            switch(data.get(0)){
                case "refresh" -> refresh(player);
                case "detail" -> {if(data.size()>1)buyerDetail(player,data.get(1));}
                case "buy" -> {if(data.size()>2)buy(player,data.get(1),data.get(2));}
                case "editor" -> openEditor(player,buyers.get(player.getUniqueId()).shop);
                case "close" -> {if(data.size()>1&&buyers.get(player.getUniqueId()).session.equals(data.get(1)))buyers.remove(player.getUniqueId());}
                default -> { }
            }return true;
        }
        if(!editorUi.equalsIgnoreCase(id)&&!"AXS:market_exchange_editor".equalsIgnoreCase(id))return false;
        if(!player.hasPermission(ADMIN)||!bridge.isUiOpen(player,editorUi)||!editors.containsKey(player.getUniqueId()))return true;
        try{editorAction(player,data);}catch(JsonParseException e){editResult(player,false,"表单格式无效，请刷新后重新编辑");}catch(Exception e){editResult(player,false,e.getMessage()==null?"编辑操作未完成":e.getMessage());}return true;
    }
    private void editorAction(Player player,List<String> data)throws Exception{
        Editor editor=editors.get(player.getUniqueId());String action=data.get(0);
        if("list".equals(action)){if(data.size()>2)editor.selectedOffer="offer".equals(data.get(1))?data.get(2):"";editList(player);if(!editor.selected.isEmpty())editShop(player,editor.selected);return;}
        if("shop".equals(action)&&data.size()>1){editor.selectedOffer="";editShop(player,data.get(1));return;}
        if("offer".equals(action)&&data.size()>2){editor.selectedOffer=data.get(2);editOffer(player,data.get(1),data.get(2));return;}
        if("close".equals(action)){if(data.size()>1&&editor.session.equals(data.get(1)))editors.remove(player.getUniqueId());return;}
        if(data.size()<2||!editor.token.equals(data.get(data.size()-1)))throw new IllegalArgumentException("编辑会话已过期，请刷新");
        switch(action){
            case "inventory" -> send(player,editorUi,"editor_inventory",Map.of("inventory",inventoryRows(player)));
            case "ledger" -> ledger(player);
            case "export" -> {if(data.size()>2)export(player,data.get(1));}
            case "resolve" -> {if(data.size()>3&&"confirm".equals(data.get(2)))resolve(player,data.get(1));}
            case "preview_shop" -> {if(data.size()>2)open(player,data.get(1),null);}
            case "save_shop" -> {
                JsonObject form=JsonParser.parseString(bounded(data.get(1),131072)).getAsJsonObject();String id=text(form,"id"),name=text(form,"name");long revision=number(form,"revision");
                TradeCatalog.Shop old=catalog.shop(id);List<String> bindings=new ArrayList<>();for(String value:text(form,"npcs").split("[,，\\n]"))if(!value.isBlank()&&bindings.stream().noneMatch(value.trim()::equalsIgnoreCase))bindings.add(value.trim());
                var saved=catalog.save(new TradeCatalog.Shop(id,name,bool(form,"enabled"),text(form,"permission"),revision,bindings,old==null?List.of():old.offers()),revision,currencies);
                editor.token=UUID.randomUUID().toString();editor.selectedOffer="";editSaved(player,"兑换所已保存");editList(player);editShop(player,saved.id());
            }
            case "save_offer" -> {
                if(data.size()<4)return;String shopId=data.get(1);TradeCatalog.Shop old=catalog.shop(shopId);if(old==null)throw new IllegalArgumentException("请先保存兑换所");
                JsonObject form=JsonParser.parseString(bounded(data.get(2),524288)).getAsJsonObject();long revision=number(form,"revision");
                TradeCatalog.Offer offer=gson.fromJson(form,TradeCatalog.Offer.class);
                offer.costs().forEach(asset -> validateEditorAsset(editor, asset));
                offer.rewards().forEach(asset -> validateEditorAsset(editor, asset));
                List<TradeCatalog.Offer> offers=new ArrayList<>(old.offers());int position=-1;for(int i=0;i<offers.size();i++)if(offers.get(i).id().equals(offer.id()))position=i;if(position<0)offers.add(offer);else offers.set(position,offer);
                catalog.save(new TradeCatalog.Shop(old.id(),old.name(),old.enabled(),old.permission(),revision,old.npcs(),offers),revision,currencies);
                editor.token=UUID.randomUUID().toString();editor.selectedOffer=offer.id();editSaved(player,"兑换条目已保存");editShop(player,shopId);
            }
            case "delete_shop" -> {
                if(data.size()<5||!"confirm".equals(data.get(3)))return;catalog.delete(data.get(1),Long.parseLong(data.get(2)));editor.selected="";editor.selectedOffer="";editor.token=UUID.randomUUID().toString();editSaved(player,"兑换所已删除");send(player,editorUi,"editor_deleted",Map.of("kind","shop"));editList(player);
            }
            case "delete_offer" -> {
                if(data.size()<6||!"confirm".equals(data.get(4)))return;var shop=catalog.shop(data.get(1));if(shop==null)return;
                List<TradeCatalog.Offer> offers=new ArrayList<>(shop.offers());offers.removeIf(o->o.id().equals(data.get(2)));
                catalog.save(new TradeCatalog.Shop(shop.id(),shop.name(),shop.enabled(),shop.permission(),shop.revision(),shop.npcs(),offers),Long.parseLong(data.get(3)),currencies);
                editor.token=UUID.randomUUID().toString();editor.selectedOffer="";editSaved(player,"兑换条目已删除");editShop(player,shop.id());
            }
            case "pick" -> {
                if(data.size()<4)return;int slot=Integer.parseInt(data.get(1));if(slot< -1||slot>=36)return;
                ItemStack item=slot<0?player.getInventory().getItemInMainHand():player.getInventory().getItem(slot);if(InventoryItems.empty(item))throw new IllegalArgumentException("该位置没有物品");
                ItemStack one=item.clone();one.setAmount(1);String target=data.get(2);if(!target.matches("(?:cost|reward):[0-7]"))return;
                String snapshot=ItemSnapshots.encode(one);if(snapshot.length()>65536)throw new IllegalArgumentException("物品数据较大，请使用物品库 ID 配置");
                if (editor.samples.size() >= 128) editor.samples.remove(editor.samples.iterator().next());
                editor.samples.add(snapshot);
                var exact=new TradeCatalog.Asset("item","snapshot",one.getType().name(),"1",snapshot);
                String source="",sourceId="";
                if(sources.mmoBridgeAvailable()&&sources.isMmoItem(one)){source="mmoitems";sourceId=sources.mmoItemId(one);}
                else if(sources.mythicBridgeAvailable()&&sources.isMythicItem(one)){source="mythic";sourceId=sources.mythicItemId(one);}
                else if(sources.neigeBridgeAvailable()&&sources.isNeigeItem(one)){source="neige";sourceId=sources.neigeItemId(one);}
                else if(sources.overtureBridgeAvailable()&&sources.isOvertureItem(one)){source="overture";sourceId=sources.overtureItemId(one);}
                var asset=sourceId.isBlank()?exact:new TradeCatalog.Asset("item",source,sourceId,"1","");
                send(player,editorUi,"editor_pick",Map.of("target",target,"asset",assetData(asset),"exactAsset",assetData(exact),"icon",previews.icon(one),"name",previews.name(one)));
            }
            case "preview" -> {
                if(data.size()<4)return;TradeCatalog.Asset asset=gson.fromJson(bounded(data.get(1),270000),TradeCatalog.Asset.class);
                validateEditorAsset(editor, asset);
                TradeCatalog.validate(new TradeCatalog.Shop("preview","预览",false,"",1,List.of(),List.of(new TradeCatalog.Offer("preview","预览",true,"",List.of(asset),List.of(asset)))),currencies);
                send(player,editorUi,"editor_preview",Map.of("target",data.get(2),"row",assetView(player,asset)));
            }
            default -> { }
        }
    }
    private void editResult(Player player,boolean success,String message){Editor editor=editors.get(player.getUniqueId());if(editor!=null)send(player,editorUi,"editor_result",Map.of("success",success,"message",message,"token",editor.token));}
    private void editSaved(Player player,String message){Editor editor=editors.get(player.getUniqueId());if(editor!=null)send(player,editorUi,"editor_result",Map.of("success",true,"saved",true,"message",message,"token",editor.token));}
    private Map<String,Object> inventoryRows(Player player){Map<String,Object> inventory=new LinkedHashMap<>();for(int i=0;i<36;i++){ItemStack item=player.getInventory().getItem(i);if(!InventoryItems.empty(item))inventory.put(Integer.toString(i),Map.of("icon",previews.icon(item),"name",previews.name(item)));}return inventory;}
    private void ledger(Player player){exchange.query(()->repository.list(null),rows->{Map<String,Object> values=new LinkedHashMap<>();for(int i=0;i<rows.size();i++){var row=rows.get(i);values.put(Integer.toString(i),Map.of("id",row.id(),"player",row.player(),"state",row.state()));}send(player,editorUi,"editor_ledger",Map.of("rows",values,"count",rows.size()));},e->editResult(player,false,"交易记录读取失败"));}
    private void export(Player player,String raw){
        String id=operationId(raw);
        exchange.query(()->{
            var row=repository.get(id);if(row==null)throw new IllegalArgumentException("未找到凭证");
            Path directory=new File(folder,"npc-trade-evidence").toPath();Files.createDirectories(directory);Path file=directory.resolve(id+".json");
            Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("id",row.id(),"player",row.player(),"state",row.state(),"evidence",JsonParser.parseString(row.evidence()))),StandardCharsets.UTF_8);
            return file.toString();
        },path->{editResult(player,true,"凭证已导出");send(player,editorUi,"editor_export",Map.of("path",path,"id",id));},e->editResult(player,false,"凭证导出失败"));
    }
    private void resolve(Player player,String raw){
        String id=operationId(raw);
        exchange.query(()->{var row=repository.get(id);if(row==null||!"REVIEW".equals(row.state()))return false;return repository.state(id,"REVIEW","CONFIRMED",gson.toJson(Map.of("previous",JsonParser.parseString(row.evidence()),"confirmedBy",player.getUniqueId().toString(),"time",System.currentTimeMillis())));},ok->{editResult(player,ok,ok?"已人工结案，未发放或退回资产":"记录状态已变化，请刷新");ledger(player);},e->editResult(player,false,"凭证结案失败"));
    }
    private void editList(Player player){
        Editor editor=editors.get(player.getUniqueId());Map<String,Object> rows=new LinkedHashMap<>();int index=0;
        for(var shop:catalog.shops())rows.put(Integer.toString(index++),Map.of("id",shop.id(),"name",shop.name(),"enabled",shop.enabled(),"count",shop.offers().size()));
        Map<String,Object> currenciesData=new LinkedHashMap<>();index=0;for(var definition:currencies.definitions())currenciesData.put(Integer.toString(index++),Map.of("id",definition.id(),"name",definition.displayName(),"provider",definition.provider(),"available",currencies.bridge(definition.id())!=null&&currencies.bridge(definition.id()).available()));
        Map<String,Object> inventory=new LinkedHashMap<>();for(int i=0;i<36;i++){ItemStack item=player.getInventory().getItem(i);if(!InventoryItems.empty(item))inventory.put(Integer.toString(i),Map.of("icon",previews.icon(item),"name",previews.name(item)));}
        Map<String,Object> nearby=new LinkedHashMap<>();index=0;
        if(npcs!=null&&npcs.isAvailable())for(var npc:npcs.findNearby(player,12))nearby.put(Integer.toString(index++),Map.of("key","ady:"+npc.npcId(),"name",npc.label()));
        send(player,editorUi,"editor_list",Map.of("shops",rows,"shopCount",rows.size(),"token",editor.token,"currencies",currenciesData,"currencyCount",currenciesData.size(),"inventory",inventory,"nearby",nearby,"nearbyCount",nearby.size(),"session",editor.session));
    }
    private void editShop(Player player,String id){
        TradeCatalog.Shop shop=catalog.shop(id);if(shop==null)return;Editor editor=editors.get(player.getUniqueId());editor.selected=id;
        Map<String,Object> offers=new LinkedHashMap<>();for(int i=0;i<shop.offers().size();i++){var offer=shop.offers().get(i);offers.put(Integer.toString(i),Map.of("id",offer.id(),"name",offer.name(),"cost",definitionSummary(offer.costs()),"reward",definitionSummary(offer.rewards())));}
        send(player,editorUi,"editor_shop",Map.of("id",shop.id(),"name",shop.name(),"enabled",shop.enabled(),"permission",shop.permission(),"revision",Long.toString(shop.revision()),"npcs",String.join(",",shop.npcs()),"offers",offers,"offerCount",offers.size(),"token",editor.token,"selectOffer",editor.selectedOffer));
        if(!editor.selectedOffer.isEmpty())editOffer(player,id,editor.selectedOffer);
    }
    private static String text(JsonObject form,String key){return form.has(key)&&!form.get(key).isJsonNull()?form.get(key).getAsString():"";}
    private void editOffer(Player player,String shopId,String id){
        var shop=catalog.shop(shopId);Editor editor=editors.get(player.getUniqueId());if(shop==null||editor==null)return;
        var offer=shop.offers().stream().filter(row->row.id().equals(id)).findFirst().orElse(null);
        if(offer==null){editor.selectedOffer="";editResult(player,false,"条目已被修改或删除，请重新选择");editShop(player,shopId);return;}
        editor.selected=shopId;editor.selectedOffer=id;
        send(player,editorUi,"editor_offer",Map.of("shop",shopId,"revision",Long.toString(shop.revision()),"offer",offerData(offer),"costRows",assetRows(player,offer.costs()),"rewardRows",assetRows(player,offer.rewards()),"token",editor.token));
    }
    private static long number(JsonObject form,String key){return Long.parseLong(text(form,key));}
    private static boolean bool(JsonObject form,String key){return form.has(key)&&form.get(key).getAsBoolean();}
    private static String bounded(String value,int max){if(value.length()>max)throw new IllegalArgumentException("提交数据过大");return value;}
    public boolean command(CommandSender sender,String[] args){
        String action=args.length>1?args[1]:"help";
        if("open".equalsIgnoreCase(action)&&sender instanceof Player player){open(player,args.length>2?args[2]:"example",null);return true;}
        if(!sender.hasPermission(ADMIN)){sender.sendMessage("§c没有管理权限");return true;}
        if("edit".equalsIgnoreCase(action)&&sender instanceof Player player){openEditor(player,args.length>2?args[2]:null);return true;}
        if("ledger".equalsIgnoreCase(action)){
            UUID target=args.length>2?UUID.fromString(args[2]):null;exchange.query(()->repository.list(target),rows->{sender.sendMessage("§6待核对 NPC 兑换（最多 50 条）");for(var row:rows)sender.sendMessage("§e"+row.id()+" §f"+row.player()+" §7"+row.state());},e->sender.sendMessage("§c读取记录失败"));return true;
        }
        if("evidence".equalsIgnoreCase(action)&&args.length>2){
            String id=UUID.fromString(args[2]).toString();exchange.query(()->{var row=repository.get(id);if(row==null)throw new IllegalArgumentException("未找到凭证");Path directory=new File(folder,"npc-trade-evidence").toPath();Files.createDirectories(directory);Path file=directory.resolve(id+".json");Map<String,Object> value=new LinkedHashMap<>();value.put("id",row.id());value.put("player",row.player());value.put("state",row.state());value.put("evidence",JsonParser.parseString(row.evidence()));Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(value),StandardCharsets.UTF_8);return file.toString();},path->sender.sendMessage("§a已导出："+path),e->sender.sendMessage("§c"+e.getMessage()));return true;
        }
        if("confirm".equalsIgnoreCase(action)&&args.length>3&&"confirm".equals(args[3])){
            String id=UUID.fromString(args[2]).toString();exchange.query(()->{var row=repository.get(id);if(row==null||!"REVIEW".equals(row.state()))return false;return repository.state(id,"REVIEW","CONFIRMED",gson.toJson(Map.of("previous",JsonParser.parseString(row.evidence()),"confirmedBy",sender.getName(),"time",System.currentTimeMillis())));},ok->sender.sendMessage(ok?"§a已结案；此命令不发放或退回任何资产":"§e没有可确认的待核对记录"),e->sender.sendMessage("§c结案失败"));return true;
        }
        sender.sendMessage("§6/axs market exchange open <兑换所ID>；edit [兑换所ID]；ledger [玩家UUID]；evidence <凭证UUID>；confirm <凭证UUID> confirm");return true;
    }
    public List<String> shopIds(){return catalog.shops().stream().map(TradeCatalog.Shop::id).toList();}
    @EventHandler public void quit(PlayerQuitEvent event){buyers.remove(event.getPlayer().getUniqueId());editors.remove(event.getPlayer().getUniqueId());pending.remove(event.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.LOWEST)public void click(org.bukkit.event.inventory.InventoryClickEvent event){if(event.getWhoClicked() instanceof Player p&&busy(p))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST)public void drag(org.bukkit.event.inventory.InventoryDragEvent event){if(event.getWhoClicked() instanceof Player p&&busy(p))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST)public void drop(PlayerDropItemEvent event){if(busy(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST)public void swap(PlayerSwapHandItemsEvent event){if(busy(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)public void pickup(org.bukkit.event.entity.EntityPickupItemEvent event){if(event.getEntity() instanceof Player p&&busy(p))event.setCancelled(true);}
    public void close(){closed=true;pending.clear();exchange.close();if(npcs!=null)npcs.shutdown();buyers.clear();editors.clear();}
}
