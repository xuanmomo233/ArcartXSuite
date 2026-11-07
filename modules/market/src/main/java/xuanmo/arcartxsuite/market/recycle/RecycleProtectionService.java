package xuanmo.arcartxsuite.market.recycle;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.RecycleConfiguration;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/** Rules apply to every recycle entry point. Unloaded, changing or unreadable rules fail closed. */
public final class RecycleProtectionService implements AutoCloseable {
    private record Cached(RecyclePreferences preferences,Map<String,ItemStack> templates,boolean unreadable,long loadedAt) {}
    private final JavaPlugin plugin;
    private final MarketRepository repository;
    private final RecycleConfiguration config;
    private final VanillaItemNameBridge names;
    private final Logger logger;
    private final BiConsumer<Player,RecyclePreferences> refreshed;
    private final Consumer<Player> unavailable;
    private final Map<UUID,Cached> cache=new ConcurrentHashMap<>();
    private final Map<UUID,Long> generations=new ConcurrentHashMap<>();
    private final Map<UUID,Long> loading=new ConcurrentHashMap<>();
    private final Set<UUID> changing=ConcurrentHashMap.newKeySet();
    private final AtomicLong sequence=new AtomicLong();
    private volatile boolean closed;

    public RecycleProtectionService(JavaPlugin plugin,MarketRepository repository,RecycleConfiguration config,VanillaItemNameBridge names,
                                     BiConsumer<Player,RecyclePreferences> refreshed,Consumer<Player> unavailable,Logger logger){
        this.plugin=plugin;this.repository=repository;this.config=config;this.names=names;this.refreshed=refreshed;this.unavailable=unavailable;this.logger=logger;
    }
    public void clear(UUID player){cache.remove(player);generations.remove(player);}
    @Override public void close(){closed=true;cache.clear();generations.clear();loading.clear();changing.clear();}
    public RecyclePreferences cached(Player player){Cached value=current(player);return value==null?null:value.preferences();}
    public boolean changing(UUID player){return changing.contains(player);}
    private void notifyUnavailable(Player player){
        try{unavailable.accept(player);}catch(RuntimeException exception){logger.warning("[RecycleGuard] 暂停状态回显失败: "+exception.getMessage());}
    }

    private Cached current(Player player){
        Cached value=cache.get(player.getUniqueId());
        if(value==null || System.currentTimeMillis()-value.loadedAt()>5000)request(player);
        return value;
    }
    private ItemStack normalized(ItemStack source){
        ItemStack item=source.clone();item.setAmount(1);var meta=item.getItemMeta();
        if(meta instanceof Damageable damage)damage.setDamage(0);
        if(meta instanceof Repairable repair)repair.setRepairCost(0);
        if(meta!=null)item.setItemMeta(meta);return item;
    }
    private Cached prepare(RecyclePreferences preferences){
        Map<String,ItemStack> templates=new HashMap<>();boolean unreadable=false;
        for(var rule:preferences.rules()){
            if(!Set.of("EXCLUDE","FAVORITE").contains(rule.kind())){unreadable=true;continue;}
            if("EXCLUDE".equals(rule.kind()) && rule.itemData().isBlank())continue;
            try{ItemStack stack=ItemSerializer.deserializeStrict(Base64.getDecoder().decode(rule.itemData()));
                if(stack==null || stack.getType().isAir())throw new IllegalArgumentException("无物品快照");templates.put(rule.id(),normalized(stack));
            }catch(RuntimeException|LinkageError exception){if("FAVORITE".equals(rule.kind()))unreadable=true;logger.warning("[RecycleGuard] 保护模板无法读取 id="+rule.id());}
        }
        return new Cached(preferences,Map.copyOf(templates),unreadable,System.currentTimeMillis());
    }
    private void apply(Player player,RecyclePreferences preferences){
        Cached previous=cache.get(player.getUniqueId());if(previous!=null && previous.preferences().version()>preferences.version())return;
        cache.put(player.getUniqueId(),prepare(preferences));refreshed.accept(player,preferences);
    }
    public void request(Player player){
        UUID id=player.getUniqueId();if(closed || changing.contains(id))return;
        long generation=generations.computeIfAbsent(id,key->sequence.incrementAndGet());
        if(loading.putIfAbsent(id,generation)!=null)return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            RecyclePreferences preferences=null;
            try{if(!closed)preferences=repository.recyclePreferences(id,config.defaultAutoRecycle());}
            catch(RuntimeException exception){logger.warning("[RecycleGuard] "+exception.getMessage());}
            if(closed){loading.remove(id,generation);return;}final RecyclePreferences result=preferences;
            Bukkit.getScheduler().runTask(plugin,()->{
                loading.remove(id,generation);Player online=Bukkit.getPlayer(id);
                if(closed || online==null || !online.isOnline() || changing.contains(id))return;
                if(generations.getOrDefault(id,-1L)!=generation){request(online);return;}
                if(result!=null)apply(online,result);
                else{cache.remove(id);notifyUnavailable(online);}
            });
        });
    }
    public void invalidate(UUID player){
        cache.remove(player);Player online=Bukkit.getPlayer(player);
        if(online!=null && online.isOnline()){
            generations.put(player,sequence.incrementAndGet());request(online);notifyUnavailable(online);
        }else generations.remove(player);
    }
    public String denial(Player player,ItemStack item){
        if(changing.contains(player.getUniqueId()))return "回收保护设置正在更新";
        Cached value=current(player);if(value==null)return "回收保护资料读取中，请稍后刷新";
        if(value.unreadable())return "收藏保护模板无法读取，回收已暂停，请处理保护清单";
        ItemStack comparison=null;
        for(var rule:value.preferences().rules()){
            if("EXCLUDE".equals(rule.kind()) && rule.material().equals(item.getType().name()))return "材质排除保护";
            if("FAVORITE".equals(rule.kind())){
                ItemStack template=value.templates().get(rule.id());if(template==null)return "收藏保护模板不可用";
                if(comparison==null)comparison=normalized(item);if(template.isSimilar(comparison))return "收藏同款保护";
            }
        }
        return null;
    }
    public boolean automatic(Player player){
        Cached value=current(player);return config.enabled() && config.allowAutoRecycle() && player.hasPermission("axsmarket.autorecycle") && !changing.contains(player.getUniqueId())
            && value!=null && !value.unreadable() && value.preferences().autoEnabled();
    }
    public boolean currentVersion(Player player){
        Cached value=cache.get(player.getUniqueId());if(value==null || changing.contains(player.getUniqueId()))return false;
        try{if(repository.recyclePreferencesVersion(player.getUniqueId())==value.preferences().version())return true;}
        catch(RuntimeException exception){logger.warning("[RecycleGuard] 保护版本读取失败，暂停本次回收: "+exception.getMessage());}
        invalidate(player.getUniqueId());return false;
    }
    public ItemStack template(UUID player,String rule){Cached value=cache.get(player);ItemStack item=value==null?null:value.templates().get(rule);return item==null?null:item.clone();}
    public RecycleProtectionRule rule(Player player,ItemStack source,String kind){
        if(!Set.of("EXCLUDE","FAVORITE").contains(kind))throw new IllegalArgumentException("保护类型无效");
        ItemStack snapshot=source.clone();snapshot.setAmount(1);byte[] data;
        try{data=ItemSerializer.serialize(snapshot);}catch(RuntimeException exception){
            if("EXCLUDE".equals(kind))data=new byte[0];else throw new IllegalArgumentException("物品模板无法保存，请改用材质排除");
        }
        if(data.length>196608){if("EXCLUDE".equals(kind))data=new byte[0];else throw new IllegalArgumentException("物品模板过大，请改用材质排除");}
        String key=snapshot.getType().name();String name=names.resolveDisplayName(snapshot);
        if("EXCLUDE".equals(kind))name=names.translate(snapshot.getType())+"（全部同材质）";
        else{
            try{ItemStack roundTrip=ItemSerializer.deserializeStrict(data);if(roundTrip==null || !normalized(roundTrip).isSimilar(normalized(snapshot)))throw new IllegalArgumentException();}
            catch(RuntimeException|LinkageError exception){throw new IllegalArgumentException("物品模板无法完整保留，请改用材质排除");}
            Cached value=current(player);ItemStack wanted=normalized(snapshot);
            if(value!=null)for(var existing:value.preferences().rules()){
                ItemStack template=value.templates().get(existing.id());if("FAVORITE".equals(existing.kind()) && template!=null && template.isSimilar(wanted))return existing;
            }
            try{key=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(ItemSerializer.serialize(wanted)));}
            catch(java.security.GeneralSecurityException exception){throw new IllegalStateException(exception);}
        }
        if(name.length()>255)name=name.substring(0,255);
        return new RecycleProtectionRule(UUID.randomUUID().toString(),player.getUniqueId(),kind,key,snapshot.getType().name(),name,Base64.getEncoder().encodeToString(data),System.currentTimeMillis());
    }
    public void update(Player player,long version,String action,boolean enabled,RecycleProtectionRule rule,BiConsumer<Boolean,String> completed){
        UUID id=player.getUniqueId();Cached existing=cache.get(id);
        if(existing==null || existing.preferences().version()!=version){completed.accept(false,"保护资料已变化，请刷新后再操作");request(player);return;}
        if(!changing.add(id)){completed.accept(false,"保护设置正在更新，请等待");return;}
        generations.put(id,sequence.incrementAndGet());
        notifyUnavailable(player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            boolean success=false;String error=null;RecyclePreferences updated=null;
            try{success=repository.updateRecyclePreferences(id,version,action,enabled,rule,config.maxProtectionRules());updated=repository.recyclePreferences(id,config.defaultAutoRecycle());}
            catch(RuntimeException exception){error=exception instanceof IllegalArgumentException?exception.getMessage():"保存结果暂不可确认，请刷新核对";logger.warning("[RecycleGuard] "+exception.getMessage());}
            if(closed){changing.remove(id);return;}
            final boolean saved=success;final String failure=error;final RecyclePreferences result=updated;
            Bukkit.getScheduler().runTask(plugin,()->{
                Player online=Bukkit.getPlayer(id);if(closed){changing.remove(id);return;}
                boolean readable=false;
                try{
                    if(result==null)cache.remove(id);
                    else if(online!=null && online.isOnline()){cache.put(id,prepare(result));readable=true;}
                }catch(RuntimeException|LinkageError exception){cache.remove(id);logger.warning("[RecycleGuard] 保护资料加载中断: "+exception.getMessage());}
                finally{changing.remove(id);}
                // Publish committed changes even if the player left while SQL was in progress.
                completed.accept(saved,failure!=null?failure:saved?"回收保护设置已保存":"资料版本已变化，请刷新后再操作");
                if(online==null || !online.isOnline())return;
                if(readable)refreshed.accept(online,result);
                else{request(online);notifyUnavailable(online);}
            });
        });
    }
}
