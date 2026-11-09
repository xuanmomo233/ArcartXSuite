package xuanmo.arcartxsuite.market.exchange;

import com.google.gson.Gson;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.inventory.*;

/** Immutable catalog snapshots; revision checks protect both editors and buyers. */
public final class TradeCatalog {
    public record Asset(String kind,String source,String id,String amount,String snapshot) {
        public Asset{
            kind=kind==null?null:kind.trim().toLowerCase(Locale.ROOT);source=source==null?null:source.trim().toLowerCase(Locale.ROOT);
            id=id==null?null:id.trim();amount=amount==null?null:amount.trim();snapshot=snapshot==null?"":snapshot.trim();
            if("currency".equals(kind)&&id!=null)id=id.toLowerCase(Locale.ROOT);
        }
        public boolean currency(){return "currency".equals(kind);}
        public BigDecimal quantity(){return new BigDecimal(amount);}
        public int itemAmount(){return quantity().intValueExact();}
        public ItemStack create(ItemSourceRegistry sources,Player player){
            ItemStack item;
            try{item="snapshot".equals(source)?ItemSnapshots.decode(snapshot):sources.generateItem(source,id,1,null,player);}catch(RuntimeException e){throw new IllegalArgumentException("物品来源不可用："+source+":"+id,e);}
            if(InventoryItems.empty(item))throw new IllegalArgumentException("物品来源不可用："+source+":"+id);
            item=item.clone();item.setAmount(itemAmount());return item;
        }
        public java.util.function.Predicate<ItemStack> matcher(ItemSourceRegistry sources,Player player){
            ItemStack prototype=create(sources,player);
            if(Set.of("minecraft","snapshot").contains(source))return prototype::isSimilar;
            String canonical=libraryId(prototype,sources);
            if(canonical==null||canonical.isBlank())throw new IllegalArgumentException("无法识别物品库标识："+source+":"+id);
            return item->!InventoryItems.empty(item)&&canonical.equals(libraryId(item,sources));
        }
        private String libraryId(ItemStack item,ItemSourceRegistry sources){
            return switch(source){
                case "mythic" -> sources.mythicBridgeAvailable()&&sources.isMythicItem(item)?sources.mythicItemId(item):"";
                case "neige" -> sources.neigeBridgeAvailable()?sources.neigeItemId(item):"";
                case "overture" -> sources.overtureBridgeAvailable()&&sources.isOvertureItem(item)?sources.overtureItemId(item):"";
                case "mmoitems" -> sources.mmoBridgeAvailable()&&sources.isMmoItem(item)?sources.mmoItemId(item):"";
                default -> "";
            };
        }
    }
    public record Offer(String id,String name,boolean enabled,String permission,List<Asset> costs,List<Asset> rewards) {
        public Offer{costs=costs==null?null:List.copyOf(costs);rewards=rewards==null?null:List.copyOf(rewards);}
    }
    public record Shop(String id,String name,boolean enabled,String permission,long revision,List<String> npcs,List<Offer> offers) {
        public Shop{npcs=npcs==null?null:List.copyOf(npcs);offers=offers==null?null:List.copyOf(offers);}
    }
    private final File file;
    private final Gson gson=new Gson();
    private Map<String,Shop> shops=new LinkedHashMap<>();
    private String diskHash;
    public TradeCatalog(File folder) throws IOException {
        file=new File(folder,"npc-trades.yml");
        if(!file.exists()){
            Shop example=new Shop("example","旅人兑换所",true,"",1,List.of(),List.of(new Offer("iron","绿宝石换铁锭",true,"",List.of(new Asset("item","minecraft","EMERALD","1","")),List.of(new Asset("item","minecraft","IRON_INGOT","4","")))));
            persist(Map.of(example.id(),example));
        }
        reload();
    }
    public Collection<Shop> shops(){return List.copyOf(shops.values());}
    public Shop shop(String id){return shops.get(id);}
    public void reload() throws IOException {
        YamlConfiguration yaml=new YamlConfiguration();
        try{yaml.load(file);}catch(org.bukkit.configuration.InvalidConfigurationException e){throw new IOException("NPC 兑换配置格式无效，保留原文件",e);}
        if(!yaml.isList("shops"))throw new IOException("npc-trades.yml 的 shops 必须是列表，保留原文件");
        Map<String,Shop> loaded=new LinkedHashMap<>();Set<String> bound=new HashSet<>();
        for(Map<?,?> value:yaml.getMapList("shops")){
            Shop shop=gson.fromJson(gson.toJson(value),Shop.class);
            if (shop != null && shop.npcs() != null) {
                List<String> bindings = shop.npcs().stream().filter(TradeCatalog::isAdyeshachBinding).toList();
                if (bindings.size() != shop.npcs().size()) {
                    shop = new Shop(shop.id(), shop.name(), shop.enabled(), shop.permission(),
                        shop.revision(), bindings, shop.offers());
                }
            }
            validate(shop,null);if(loaded.put(shop.id(),shop)!=null)throw new IllegalArgumentException("重复的兑换所 ID");
            for(String npc:shop.npcs())if(!bound.add(npc.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("NPC 存在重复绑定："+npc);
        }
        shops=loaded;diskHash=hash();
    }
    public synchronized Shop save(Shop draft,long expected,CurrencyBridgeAPI currencies) throws IOException {
        if(!Objects.equals(diskHash,hash()))throw new IllegalArgumentException("文件已被外部修改，请重载后重新编辑");
        Shop old=shops.get(draft.id());
        if((old==null?0:old.revision())!=expected)throw new IllegalArgumentException("其他管理员已保存，请刷新后重试");
        Shop next=new Shop(draft.id(),draft.name(),draft.enabled(),draft.permission(),expected+1,List.copyOf(draft.npcs()),List.copyOf(draft.offers()));
        validate(next,currencies);Map<String,Shop> updated=new LinkedHashMap<>(shops);updated.put(next.id(),next);
        for(Shop other:shops.values())if(!other.id().equals(next.id()))for(String npc:next.npcs())
            if(other.npcs().stream().anyMatch(npc::equalsIgnoreCase))throw new IllegalArgumentException("NPC 已绑定兑换所："+other.name());
        if(updated.size()>128)throw new IllegalArgumentException("兑换所数量超过上限");
        persist(updated);shops=updated;diskHash=hash();return next;
    }
    public synchronized void delete(String id,long expected) throws IOException {
        if(!Objects.equals(diskHash,hash()))throw new IllegalArgumentException("文件已修改，请重载后编辑");
        Shop old=shops.get(id);if(old==null||old.revision()!=expected)throw new IllegalArgumentException("版本已变化，请刷新");
        Map<String,Shop> updated=new LinkedHashMap<>(shops);updated.remove(id);persist(updated);shops=updated;diskHash=hash();
    }
    private String hash() throws IOException{return ItemSnapshots.hash(Files.readString(file.toPath(),StandardCharsets.UTF_8));}
    private void persist(Map<String,Shop> value) throws IOException {
        file.getParentFile().mkdirs();YamlConfiguration yaml=new YamlConfiguration();
        List<Object> rows=new ArrayList<>();for(Shop shop:value.values())rows.add(Map.of("id",shop.id(),"name",shop.name(),"enabled",shop.enabled(),"permission",shop.permission(),"revision",shop.revision(),"npcs",shop.npcs(),"offers",shop.offers().stream().map(TradeCatalog::offerData).toList()));
        yaml.set("shops",rows);
        Path temp=Files.createTempFile(file.getParentFile().toPath(),"npc-trades-",".tmp");
        try{
            Files.writeString(temp,yaml.saveToString(),StandardCharsets.UTF_8);
            if(file.exists())Files.copy(file.toPath(),file.toPath().resolveSibling("npc-trades.before-save.yml"),StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    public static String clean(String value){return value==null?"":value;}
    public static Map<String,Object> assetData(Asset asset){return Map.of("kind",asset.kind(),"source",asset.source(),"id",asset.id(),"amount",asset.amount(),"snapshot",clean(asset.snapshot()));}
    public static Map<String,Object> offerData(Offer offer){return Map.of("id",offer.id(),"name",offer.name(),"enabled",offer.enabled(),"permission",offer.permission(),"costs",offer.costs().stream().map(TradeCatalog::assetData).toList(),"rewards",offer.rewards().stream().map(TradeCatalog::assetData).toList());}
    static boolean isAdyeshachBinding(String binding) {
        return binding != null && binding.length() <= 128 && binding.startsWith("ady:")
            && !binding.substring(4).isBlank();
    }

    public static void validate(Shop shop,CurrencyBridgeAPI currencies){
        if(shop==null||shop.id()==null||!shop.id().matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("兑换所 ID 使用小写字母、数字、下划线或横线");
        if(shop.name()==null||shop.name().isBlank()||shop.name().length()>64)throw new IllegalArgumentException("兑换所名称需为 1～64 字符");
        if(shop.permission()==null||shop.permission().length()>128||shop.npcs()==null||shop.npcs().size()>64||shop.offers()==null||shop.offers().size()>128)throw new IllegalArgumentException("兑换所字段无效");
        for(String npc:shop.npcs()){
            if (!isAdyeshachBinding(npc)) throw new IllegalArgumentException("请使用 ady:实体ID 绑定 Adyeshach NPC");
        }
        Set<String> ids=new HashSet<>();
        for(Offer offer:shop.offers()){
            if(offer==null||offer.id()==null||!offer.id().matches("[a-z0-9_-]{1,48}")||!ids.add(offer.id()))throw new IllegalArgumentException("兑换条目 ID 无效或重复");
            if(offer.name()==null||offer.name().isBlank()||offer.name().length()>64||offer.permission()==null||offer.permission().length()>128)throw new IllegalArgumentException("条目名称或权限无效");
            if(offer.costs()==null||offer.costs().isEmpty()||offer.costs().size()>8||offer.rewards()==null||offer.rewards().isEmpty()||offer.rewards().size()>8)throw new IllegalArgumentException("每条兑换需 1～8 项成本和奖励");
            for(Asset asset:concat(offer.costs(),offer.rewards())){
                if(asset==null||asset.amount()==null||asset.kind()==null||asset.source()==null||asset.id()==null)throw new IllegalArgumentException("资产定义不完整");
                BigDecimal amount;try{amount=asset.quantity();}catch(RuntimeException e){throw new IllegalArgumentException("数量或金额不是有效数字");}
                if(amount.signum()<=0||amount.scale()>8||amount.precision()>20||amount.compareTo(new BigDecimal("1000000000000"))>0)throw new IllegalArgumentException("数量或金额超出范围");
                if(asset.currency()){
                    if(asset.id().isBlank()||asset.id().length()>64||currencies!=null&&currencies.definition(asset.id())==null)throw new IllegalArgumentException("货币 ID 未配置");
                    if(currencies!=null&&amount.stripTrailingZeros().scale()>currencies.definition(asset.id()).scale())throw new IllegalArgumentException("金额精度超过该货币设置");
                }else{
                    if(!"item".equals(asset.kind())||!Set.of("minecraft","mythic","neige","overture","mmoitems","snapshot").contains(asset.source())||asset.id().length()>128)throw new IllegalArgumentException("物品来源无效");
                    try{if(asset.itemAmount()<1||asset.itemAmount()>64)throw new IllegalArgumentException();}catch(RuntimeException e){throw new IllegalArgumentException("单项物品数量需为 1～64 的整数");}
                    if("snapshot".equals(asset.source())&&(asset.snapshot()==null||asset.snapshot().isEmpty()||asset.snapshot().length()>262144))throw new IllegalArgumentException("请重新从真实背包选物");
                }
            }
        }
    }
    private static List<Asset> concat(List<Asset> a,List<Asset> b){List<Asset> result=new ArrayList<>(a);result.addAll(b);return result;}
}
