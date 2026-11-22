package xuanmo.arcartxsuite.market.playershop;

import xuanmo.arcartxsuite.market.MarketWork;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.ContentModerator;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/** Store directory, storefront and owner editing have separate sessions and shared UUID identity. */
public final class PlayerShopController implements AutoCloseable {
    public interface Host {
        void open(Player player,String ui);
        void send(Player player,String ui,String handler,Map<String,Object> packet);
        void rotate(Player player,String ui);
        void market(Player player);
        default void backMarket(Player player){market(player);}
        void sell(Player player);
        void publish(String message);
    }
    private record DirectoryPage(List<PlayerShopCard> shops,int page,int pages,String keyword,boolean favorites) {}
    private record StoreQuery(int page,String keyword) {}
    private record DirectoryQuery(int page,String keyword,boolean favorites) {}
    private final JavaPlugin plugin;
    private final MarketWork work;
    private final MarketRepository repository;
    private final MarketModuleConfiguration config;
    private final PacketBridgeAPI packets;
    private final ItemBridgeAPI items;
    private final AuctionItemSerializer serializer;
    private final CurrencyBridgeAPI currencies;
    private final Host host;
    private final Logger logger;
    private final PlayerShopIdentityService identities;
    private final AtomicLong sequence=new AtomicLong();
    private final Map<UUID,Long> tickets=new ConcurrentHashMap<>();
    private final Map<UUID,Long> epochs=new ConcurrentHashMap<>();
    private final Set<UUID> reading=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Runnable> pendingReads=new ConcurrentHashMap<>();
    private final Map<UUID,UUID> stores=new ConcurrentHashMap<>();
    private final Map<UUID,String> origins=new ConcurrentHashMap<>();
    private final Map<UUID,StoreQuery> storeQueries=new ConcurrentHashMap<>();
    private final Map<UUID, Long> sharedListings = new ConcurrentHashMap<>();
    private final Map<UUID,DirectoryQuery> directoryQueries=new ConcurrentHashMap<>();
    private final Set<UUID> directoryRestore=ConcurrentHashMap.newKeySet();
    private final Set<UUID> saving=ConcurrentHashMap.newKeySet();
    private final Set<UUID> following=ConcurrentHashMap.newKeySet();
    private final Set<UUID> checkoutReading=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Long> checkoutTickets=new ConcurrentHashMap<>();
    private volatile boolean closed;

    public PlayerShopController(MarketWork work, JavaPlugin plugin,MarketRepository repository,MarketModuleConfiguration config,PacketBridgeAPI packets,
                                 ItemBridgeAPI items,AuctionItemSerializer serializer,CurrencyBridgeAPI currencies,ContentModerator moderator,Host host,Logger logger) {
        this.plugin=plugin;this.work = work;this.repository=repository;this.config=config;this.packets=packets;this.items=items;
        this.serializer=serializer;this.currencies=currencies;this.host=host;this.logger=logger;
        identities=new PlayerShopIdentityService(repository,config.playerShop(),serializer,moderator);
    }

    public void clear(UUID player) {
        tickets.remove(player);epochs.remove(player);pendingReads.remove(player);stores.remove(player);origins.remove(player);storeQueries.remove(player);directoryQueries.remove(player);
        sharedListings.remove(player);
        directoryRestore.remove(player);identities.clear(player);checkoutTickets.remove(player);
    }
    @Override public void close(){closed=true;tickets.clear();epochs.clear();pendingReads.clear();reading.clear();stores.clear();origins.clear();storeQueries.clear();directoryQueries.clear();identities.clear();directoryRestore.clear();saving.clear();following.clear();checkoutReading.clear();checkoutTickets.clear();sharedListings.clear();}
    public boolean isStoreOpen(Player player){return packets.isUiOpen(player,config.ui().playerStoreId());}
    public UUID selectedOwner(Player player){return stores.get(player.getUniqueId());}
    public String tradeUi(Player player){return isStoreOpen(player)?config.ui().playerStoreId():config.ui().playerShopId();}
    public String followUi(Player player){return isStoreOpen(player)?config.ui().playerStoreId():config.ui().playerShopsId();}

    private void newView(Player player){long epoch=sequence.incrementAndGet();epochs.put(player.getUniqueId(),epoch);tickets.put(player.getUniqueId(),epoch);pendingReads.remove(player.getUniqueId());}
    public void openDirectory(Player player){newView(player);host.open(player,config.ui().playerShopsId());}
    public boolean openStore(Player player,UUID owner){
        return openStore(player, owner, null);
    }

    public boolean openSharedListing(Player player, PlayerShopListing listing) {
        return openStore(player, listing.getSeller(), listing.getId());
    }

    private boolean openStore(Player player, UUID owner, Long sharedListing) {
        if(owner==null)return false;
        UUID viewer=player.getUniqueId();
        if (sharedListing == null) sharedListings.remove(viewer);
        else sharedListings.put(viewer, sharedListing);
        if(packets.isUiOpen(player,config.ui().playerShopId()))origins.put(viewer,"market");
        else if(packets.isUiOpen(player,config.ui().playerShopsId()))origins.put(viewer,"directory");
        else origins.putIfAbsent(viewer,"directory");
        stores.put(viewer,owner);storeQueries.put(viewer,new StoreQuery(0,""));newView(player);
        host.open(player,config.ui().playerStoreId());return true;
    }
    public void openEditor(Player player){identities.clear(player.getUniqueId());newView(player);host.open(player,config.ui().playerStoreEditId());}

    private boolean open(Player player,String ui){return packets.isUiOpen(player,ui);}
    private static int number(List<String> data,int at,int fallback){try{return Integer.parseInt(data.get(at).replaceFirst("\\.0+$",""));}catch(RuntimeException exception){return fallback;}}
    private static String field(List<String> data,int at){return data.size()>at && data.get(at)!=null?data.get(at):"";}
    private static String query(String value){return value.length()>100?value.substring(0,100):value;}
    private static String value(Map<String,Object> form,String key){Object value=form.get(key);return value==null?"":String.valueOf(value);}

    private <T> void read(Player player,String ui,Supplier<T> work,Consumer<T> render,String errorHandler) {
        submit(player,ui,work,render,errorHandler,false);
    }
    private <T> void write(Player player,String ui,Supplier<T> work,Consumer<T> render,String errorHandler) {
        submit(player,ui,work,render,errorHandler,true);
    }
    private <T> void submit(Player player,String ui,Supplier<T> work,Consumer<T> render,String errorHandler,boolean mutation) {
        if(closed || !player.isOnline() || !open(player,ui)){if(mutation)releaseWrite(player.getUniqueId(),errorHandler);return;}
        UUID viewer=player.getUniqueId();long epoch=epochs.computeIfAbsent(viewer,key->sequence.incrementAndGet());
        if(!mutation && !reading.add(viewer)){
            tickets.put(viewer,sequence.incrementAndGet());
            pendingReads.put(viewer,()->{if(epochs.getOrDefault(viewer,0L)==epoch)submit(player,ui,work,render,errorHandler,false);});
            return;
        }
        long ticket=sequence.incrementAndGet();tickets.put(viewer,ticket);
        if(mutation)pendingReads.remove(viewer);
        this.work.async(()->{
            T result=null;String failure=null;
            try{if(!closed)result=work.get();}catch(RuntimeException exception){failure="店铺数据处理暂不可用，请重新加载核对";logger.warning("[PlayerShop] "+exception.getMessage());}
            if(closed){if(!mutation)reading.remove(viewer);else releaseWrite(viewer,errorHandler);return;}
            final T loaded=result;final String message=failure;
            this.work.main(()->{
                Player online=Bukkit.getPlayer(viewer);
                if(mutation)releaseWrite(viewer,errorHandler);
                if(!mutation)reading.remove(viewer);
                Runnable next=mutation?null:pendingReads.remove(viewer);
                if(next!=null && !closed)next.run();
                if(closed || online==null || !online.isOnline() || epochs.getOrDefault(viewer,0L)!=epoch
                    || (!mutation && !Long.valueOf(ticket).equals(tickets.get(viewer))) || !open(online,ui))return;
                try{
                    if(message==null)render.accept(loaded);else result(online,ui,errorHandler,false,message,false);
                }catch(RuntimeException exception){
                    logger.warning("[PlayerShop] 页面响应未完成: "+exception.getMessage());
                    result(online,ui,errorHandler,false,"店铺页面未完成加载，请刷新重试",false);
                }
            });
        });
    }

    private void releaseWrite(UUID player,String handler){if("profile_result".equals(handler))saving.remove(player);else following.remove(player);}

    private void result(Player player,String ui,String handler,boolean success,String message,boolean pending) {
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("success",success);packet.put("message",message);packet.put("pending",pending);
        packet.put("packetId",config.ui().packetId());host.send(player,ui,handler,packet);
    }
    private String icon(PlayerShopProfile profile){
        try{if(!profile.iconData().isBlank()){String json=itemJson(serializer.deserialize(profile.iconData()));if(!json.isBlank())return json;}}
        catch(RuntimeException exception){logger.fine("店铺图标暂不可用: "+profile.owner());}
        return itemJson(new ItemStack(Material.CHEST));
    }
    private String itemJson(ItemStack stack){return stack==null || items==null?"":items.itemToPreviewJson(stack).orElse("");}
    private String price(PlayerShopListing listing){
        if(!Double.isFinite(listing.getPrice()) || listing.getPrice()<0)return "价格异常";
        java.math.BigDecimal amount=java.math.BigDecimal.valueOf(listing.getPrice());
        try{return currencies.format(listing.getCurrency(),amount);}
        catch(RuntimeException exception){return amount.toPlainString()+" "+listing.getCurrency();}
    }

    public boolean handle(Player player,String action,List<String> data) {
        switch(action){
            case "pshop_directory_ui_open" -> openDirectory(player);
            case "pshop_market_ui_open" -> host.market(player);
            case "pshop_store_ui_open" -> {
                try{openStore(player,"self".equals(field(data,1)) || field(data,1).isBlank()?player.getUniqueId():UUID.fromString(field(data,1)));}
                catch(IllegalArgumentException exception){player.sendMessage("§c店铺标识必须为玩家 UUID");}
            }
            case "pshop_profile_ui_open" -> openEditor(player);
            case "pshop_directory_open","pshop_directory_list" -> {
                if(open(player,config.ui().playerShopsId()))loadDirectory(player,data);
            }
            case "pshop_store_open","pshop_store_list" -> {
                if(isStoreOpen(player))loadStore(player,data);
            }
            case "pshop_store_back" -> {
                if("market".equals(origins.get(player.getUniqueId())))host.backMarket(player);
                else {directoryRestore.add(player.getUniqueId());openDirectory(player);}
            }
            case "pshop_store_share" -> {
                if(isStoreOpen(player) && selectedOwner(player)!=null)player.sendMessage("§b店铺地址：§f/market store "+selectedOwner(player));
            }
            case "pshop_store_quote" -> {if(isStoreOpen(player))checkout(player,data);}
            case "pshop_profile_back" -> {if(open(player,config.ui().playerStoreEditId()))openStore(player,player.getUniqueId());}
            case "pshop_store_sell" -> {if(isStoreOpen(player) && player.getUniqueId().equals(selectedOwner(player)))host.sell(player);}
            case "pshop_profile_open" -> {if(open(player,config.ui().playerStoreEditId()))loadProfile(player);}
            case "pshop_profile_status" -> {if(open(player,config.ui().playerStoreEditId()))saveStatus(player);}
            case "pshop_profile_pick" -> {if(open(player,config.ui().playerStoreEditId()))pickIcon(player,data);}
            case "pshop_profile_save" -> {if(open(player,config.ui().playerStoreEditId()))saveProfile(player,data);}
            case "pshop_store_follow" -> {if(open(player,config.ui().playerShopsId()) || isStoreOpen(player))follow(player,data);}
            default -> {return false;}
        }
        return true;
    }

    private void loadDirectory(Player player,List<String> data){
        UUID viewer=player.getUniqueId();
        if("pshop_directory_open".equals(field(data,0)))host.rotate(player,config.ui().playerShopsId());
        DirectoryQuery request=new DirectoryQuery(Math.max(0,Math.min(100000,number(data,1,0))),query(field(data,2)),"favorites".equals(field(data,3)));
        if(directoryRestore.remove(viewer))request=directoryQueries.getOrDefault(viewer,request);
        final DirectoryQuery requested=request;
        read(player,config.ui().playerShopsId(),()->{
            int total=repository.countPlayerShopDirectory(viewer,requested.keyword(),requested.favorites());
            int pages=Math.max(1,(total+11)/12),page=Math.min(requested.page(),pages-1);
            return new DirectoryPage(repository.playerShopDirectory(viewer,requested.keyword(),requested.favorites(),page*12,12),page,pages,requested.keyword(),requested.favorites());
        },page->{
            Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("page",page.page());packet.put("pages",page.pages());
            packet.put("keyword",page.keyword());packet.put("mode",page.favorites()?"favorites":"all");packet.put("pageText",(page.page()+1)+"/"+page.pages());
            Map<String,Object> rows=new LinkedHashMap<>();int index=0;
            for(PlayerShopCard card:page.shops()){
                PlayerShopProfile profile=card.profile();Map<String,Object> row=new LinkedHashMap<>();String key=String.valueOf(index++);
                row.put("owner",profile.owner().toString());row.put("ownerName",profile.ownerName());row.put("name",profile.shopName());
                String intro=profile.description().replace('\n',' ');var teaser=lines(intro);StringBuilder brief=new StringBuilder();
                for(int line=0;line<Math.min(2,teaser.size());line++){if(line>0)brief.append('\n');brief.append(teaser.get(String.valueOf(line)));}
                if(teaser.size()>2)brief.append('…');row.put("intro",brief.toString());
                row.put("listings",card.activeListings());row.put("followers",card.followers());row.put("followed",card.followed());row.put("isMine",profile.owner().equals(viewer));
                packet.put("shopIcon"+key,icon(profile));rows.put(key,row);
            }
            packet.put("shops",rows);packet.put("count",index);host.send(player,config.ui().playerShopsId(),"directory_data",packet);
            directoryQueries.put(viewer,new DirectoryQuery(page.page(),page.keyword(),page.favorites()));
        },"store_result");
    }

    private void loadStore(Player player,List<String> data){
        UUID viewer=player.getUniqueId(),owner=selectedOwner(player);String name=player.getName();
        if("pshop_store_open".equals(field(data,0)))host.rotate(player,config.ui().playerStoreId());
        if(owner==null){result(player,config.ui().playerStoreId(),"store_result",false,"店铺选择已失效，请返回目录",false);return;}
        StoreQuery request=("pshop_store_open".equals(field(data,0)) || "pshop_store_refresh".equals(field(data,0)))?storeQueries.getOrDefault(viewer,new StoreQuery(0,""))
            :new StoreQuery(Math.max(0,Math.min(100000,number(data,1,0))),query(field(data,2)));
        read(player,config.ui().playerStoreId(),()->{
            if(owner.equals(viewer))repository.ensurePlayerShopProfile(viewer,name,true);
            return repository.playerShopView(owner,viewer,request.keyword(),request.page(),12);
        },view->{
            if(!owner.equals(selectedOwner(player)))return;
            Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("found",view!=null);
            if(view==null){packet.put("message","此店铺尚未开店或资料已被清理");host.send(player,config.ui().playerStoreId(),"storefront_data",packet);return;}
            PlayerShopCard card=view.shop();PlayerShopProfile profile=card.profile();
            packet.put("owner",owner.toString());packet.put("ownerName",profile.ownerName());packet.put("shopName",profile.shopName());packet.put("description",profile.description());
            Player ownerOnline=Bukkit.getPlayer(owner);packet.put("ownerOnline",ownerOnline!=null && ownerOnline.isOnline());
            packet.put("announcement",profile.announcement());packet.put("iconJson",icon(profile));packet.put("isOwner",owner.equals(viewer));packet.put("followed",card.followed());
            packet.put("followers",card.followers());packet.put("activeCount",card.activeListings());packet.put("sales",view.completedSales());
            packet.put("page",view.page());packet.put("pages",view.totalPages());packet.put("pageText",(view.page()+1)+"/"+view.totalPages());packet.put("keyword",view.keyword());
            packet.put("created",new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(profile.createdAt())));
            packet.put("descriptionLines",lines(profile.description()));packet.put("announcementLines",lines(profile.announcement()));
            Map<String,Object> listings=new LinkedHashMap<>();int index=0;
            for(PlayerShopListing listing:view.listings()){
                String key=String.valueOf(index++);Map<String,Object> row=new LinkedHashMap<>();
                row.put("id",String.valueOf(listing.getId()));row.put("name",listing.getItemDisplayName());row.put("message",listing.getMessage());row.put("isMine",owner.equals(viewer));
                row.put("price",price(listing));int amount=0;String json="";
                try{ItemStack item=serializer.deserialize(listing.getItemData());if(item!=null && !item.getType().isAir() && item.getAmount()>0){amount=item.getAmount();json=itemJson(item);}}catch(RuntimeException exception){logger.fine("店铺物品预览失败: "+listing.getId());}
                boolean available=amount>0 && Double.isFinite(listing.getPrice()) && listing.getPrice()>=0;
                row.put("available",available);
                row.put("actionText",owner.equals(viewer)?"&c下架商品":available?"&a整组购买":"&7暂不可购买");
                row.put("amount",amount);row.put("expires",new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date(listing.getExpiresAt())));
                packet.put("itemJson"+key,json);listings.put(key,row);
            }
            Long sharedListing = sharedListings.remove(viewer);
            packet.put("sharedListingId", sharedListing == null ? "" : Long.toString(sharedListing));
            packet.put("listings",listings);packet.put("count",index);host.send(player,config.ui().playerStoreId(),"storefront_data",packet);
            storeQueries.put(viewer,new StoreQuery(view.page(),view.keyword()));
        },"store_result");
    }
    private Map<String,String> lines(String value){
        Map<String,String> rows=new LinkedHashMap<>();int index=0;
        for(String line:value.split("\\R",-1)){
            StringBuilder part=new StringBuilder();int width=0;
            for(int cp:line.codePoints().toArray()){
                int units=cp>=0x2E80?2:1;
                if(width+units>26){rows.put(String.valueOf(index++),part.toString());part.setLength(0);width=0;}
                part.appendCodePoint(cp);width+=units;
            }
            rows.put(String.valueOf(index++),part.toString());
        }
        return rows;
    }

    public void refresh(Player player){if(isStoreOpen(player))loadStore(player,List.of("pshop_store_refresh"));}
    public void changed(){
        Runnable update=()->{if(closed)return;for(UUID viewer:stores.keySet()){Player player=Bukkit.getPlayer(viewer);if(player!=null && player.isOnline() && isStoreOpen(player))refresh(player);}};
        if(Bukkit.isPrimaryThread())update.run();else work.main(update);
    }

    private void saveStatus(Player player){
        UUID owner=player.getUniqueId();String ui=config.ui().playerStoreEditId();
        if(saving.contains(owner)){
            Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("pending",true);
            packet.put("message","保存请求仍在处理，请等待；不会自动重复提交");host.send(player,ui,"profile_save_status",packet);return;
        }
        read(player,ui,()->repository.playerShopProfile(owner),profile->{
            Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("pending",false);
            packet.put("version",profile==null?"":String.valueOf(profile.version()));
            packet.put("message",profile==null?"服务器尚无店铺资料，请重新加载核对":"当前服务器资料版本 "+profile.version()+"；保留草稿，请按需重新加载核对");
            host.send(player,ui,"profile_save_status",packet);
        },"profile_result");
    }

    private void checkout(Player player,List<String> data){
        UUID viewer=player.getUniqueId(),owner=selectedOwner(player);long id;
        try{id=Long.parseLong(field(data,1));}catch(NumberFormatException exception){id=0;}
        if(owner==null || id<=0){checkoutFailure(player,owner,id,"商品选择已失效，请刷新店铺");return;}
        if(!checkoutReading.add(viewer)){checkoutFailure(player,owner,id,"商品正在核对，请稍后重新核对");return;}
        final long listingId=id,epoch=epochs.computeIfAbsent(viewer,key->sequence.incrementAndGet()),ticket=sequence.incrementAndGet();
        checkoutTickets.put(viewer,ticket);
        work.async(()->{
            PlayerShopListing listing=null;
            try{if(!closed)listing=repository.getPlayerShopListing(listingId);}
            catch(RuntimeException exception){logger.warning("[PlayerShop] 商品核对暂不可用: "+exception.getMessage());}
            if(closed){checkoutReading.remove(viewer);return;}
            final PlayerShopListing loaded=listing;
            work.main(()->{
                checkoutReading.remove(viewer);Player online=Bukkit.getPlayer(viewer);
                if(closed || online==null || !online.isOnline() || epochs.getOrDefault(viewer,0L)!=epoch
                    || !Long.valueOf(ticket).equals(checkoutTickets.get(viewer)) || !isStoreOpen(online) || !owner.equals(selectedOwner(online)))return;
                try{renderCheckout(online,owner,listingId,loaded);}
                catch(RuntimeException exception){checkoutFailure(online,owner,listingId,"商品核对暂不可用，请刷新后重试");}
            });
        });
    }

    private void checkoutFailure(Player player,UUID owner,long id,String message){
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("owner",owner==null?"":owner.toString());
        packet.put("id",String.valueOf(id));packet.put("canBuy",false);packet.put("canCancel",false);packet.put("message",message);
        host.send(player,config.ui().playerStoreId(),"checkout_data",packet);
    }

    private void renderCheckout(Player player,UUID owner,long id,PlayerShopListing listing){
        if(listing==null || !listing.isActive() || !owner.equals(listing.getSeller())){
            checkoutFailure(player,owner,id,"商品已不可购买或不属于当前店铺。\n请刷新后重新选择。");return;
        }
        boolean mine=owner.equals(player.getUniqueId()),readable=false;int amount=0;
        try{ItemStack stack=serializer.deserialize(listing.getItemData());readable=stack!=null && !stack.getType().isAir() && stack.getAmount()>0;if(readable)amount=stack.getAmount();}
        catch(RuntimeException exception){logger.fine("商品核对无法解析物品: "+id);}
        boolean priced=Double.isFinite(listing.getPrice()) && listing.getPrice()>=0;
        var bridge=currencies.bridge(listing.getCurrency());
        boolean funded=!mine && priced && bridge!=null && bridge.available() && bridge.balance(player).compareTo(java.math.BigDecimal.valueOf(listing.getPrice()))>=0;
        String price=price(listing);
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("owner",owner.toString());packet.put("id",String.valueOf(id));
        packet.put("canBuy",!mine && readable && funded);packet.put("canCancel",mine);packet.put("amount",amount);packet.put("price",price);
        packet.put("message",mine?"确认下架商品？\n退还物品将进入持久交付队列。":!readable?"物品数据暂不可读取，不能购买。\n请联系店主，本次不扣款。"
            :!funded?"余额不足或货币暂不可用。\n当前整组价格 "+price:"整组 ×"+amount+"，总价 "+price+"\n确认后扣款，物品进入持久交付队列。");
        host.send(player,config.ui().playerStoreId(),"checkout_data",packet);
    }

    private void loadProfile(Player player){
        UUID owner=player.getUniqueId();String name=player.getName();
        if(saving.contains(owner)){result(player,config.ui().playerStoreEditId(),"profile_result",false,"资料正在保存，请等待结果",true);return;}
        host.rotate(player,config.ui().playerStoreEditId());
        identities.clear(owner);
        read(player,config.ui().playerStoreEditId(),()->{repository.ensurePlayerShopProfile(owner,name,true);return repository.playerShopProfile(owner);},profile->renderProfile(player,profile),"profile_result");
    }
    private void renderProfile(Player player,PlayerShopProfile profile){
        if(profile==null){result(player,config.ui().playerStoreEditId(),"profile_result",false,"店铺资料无法读取，请重新加载",false);return;}
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("name",profile.shopName());packet.put("description",profile.description());
        packet.put("announcement",profile.announcement());packet.put("version",String.valueOf(profile.version()));packet.put("iconJson",icon(profile));
        packet.put("nameMax",config.playerShop().nameMaxLength());packet.put("descriptionMax",config.playerShop().descriptionMaxLength());packet.put("announcementMax",config.playerShop().announcementMaxLength());
        packet.put("defaultIconJson",itemJson(new ItemStack(Material.CHEST)));
        host.send(player,config.ui().playerStoreEditId(),"profile_data",packet);
    }
    private void pickIcon(Player player,List<String> data){
        if(saving.contains(player.getUniqueId())){result(player,config.ui().playerStoreEditId(),"profile_result",false,"保存中不能更换图标",true);return;}
        int raw=number(data,1,-1);
        try{
            if(raw<9 || raw>44)throw new IllegalArgumentException("背包槽位无效");
            ItemStack icon=identities.pick(player,raw>=36?raw-36:raw);
            Map<String,Object> packet=new LinkedHashMap<>();packet.put("iconJson",itemJson(icon));packet.put("packetId",config.ui().packetId());host.send(player,config.ui().playerStoreEditId(),"profile_icon",packet);
        }catch(IllegalArgumentException exception){result(player,config.ui().playerStoreEditId(),"profile_result",false,exception.getMessage(),false);}
    }
    private void saveProfile(Player player,List<String> data){
        UUID owner=player.getUniqueId();String ui=config.ui().playerStoreEditId();
        if(saving.contains(owner)){result(player,ui,"profile_result",false,"已有保存请求正在处理",true);return;}
        PlayerShopIdentityService.Draft draft;
        try{
            Map<String,Object> form=xuanmo.arcartxsuite.api.util.SimpleJsonParser.parse(field(data,1));
            draft=identities.prepare(player,Long.parseLong(value(form,"version")),value(form,"name"),value(form,"description"),value(form,"announcement"),value(form,"iconMode"));
        }catch(IllegalArgumentException exception){result(player,ui,"profile_result",false,"资料未保存："+exception.getMessage(),false);return;}
        saving.add(owner);host.rotate(player,ui);
        write(player,ui,()->{
            PlayerShopProfile profile=identities.commit(draft);if(profile!=null)host.publish("PSHOP_PROFILE_UPDATED:"+owner);return profile;
        },profile->{
            if(profile==null){result(player,ui,"profile_result",false,"资料版本已变化，草稿已保留；请重新加载后再编辑",false);return;}
            result(player,ui,"profile_result",true,"店铺资料已保存",false);renderProfile(player,profile);
        },"profile_result");
    }
    private void follow(Player player,List<String> data){
        UUID viewer=player.getUniqueId(),owner;String ui=followUi(player),name=player.getName();
        if(!List.of("0","1").contains(field(data,3))){result(player,ui,"store_result",false,"收藏操作已更新，请刷新页面后重试",false);return;}
        boolean desired="1".equals(field(data,3));
        try{owner=UUID.fromString(field(data,1));}catch(IllegalArgumentException exception){result(player,ui,"store_result",false,"店铺标识无效",false);return;}
        if(isStoreOpen(player) && !owner.equals(selectedOwner(player))){result(player,ui,"store_result",false,"只能收藏当前打开的店铺",false);return;}
        if(owner.equals(viewer)){result(player,ui,"store_result",false,"自己的店铺可从“我的店铺”进入，无需收藏",false);return;}
        if(!following.add(viewer)){result(player,ui,"store_result",false,"收藏请求正在处理",true);return;}
        host.rotate(player,ui);
        write(player,ui,()->{
            repository.ensurePlayerShopProfile(viewer,name,false);return repository.setPlayerShopFollow(viewer,owner,desired,config.playerShop().maxShopFavorites());
        },state->{
            String message=state==1?"已收藏店铺，可在收藏店铺中查看":state==0?"已取消店铺收藏":state==-2?"收藏店铺数量已达上限":"店铺尚未开店或已被清理";
            result(player,ui,"store_result",state>=0,message,false);
            if(state>=0){
                Map<String,Object> confirmed=new LinkedHashMap<>();confirmed.put("packetId",config.ui().packetId());
                confirmed.put("owner",owner.toString());confirmed.put("followed",state==1);
                host.send(player,ui,"store_follow_state",confirmed);
            }
            if(isStoreOpen(player))refresh(player);else {
                var last=directoryQueries.getOrDefault(viewer,new DirectoryQuery(0,"",false));
                loadDirectory(player,List.of("pshop_directory_list",String.valueOf(last.page()),last.keyword(),last.favorites()?"favorites":"all"));
            }
        },"store_result");
    }
}
