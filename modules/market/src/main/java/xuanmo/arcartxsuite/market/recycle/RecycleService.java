package xuanmo.arcartxsuite.market.recycle;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.MessagesConfiguration;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.RecycleConfiguration;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/**
 * 回收商店业务服务。
 */
public class RecycleService {
    private xuanmo.arcartxsuite.market.MarketLedger ledger;
    private RecycleProtectionService protection;
    public void setProtection(RecycleProtectionService protection){this.protection=protection;}
    public String protectionReason(Player player,ItemStack item){return protection==null?"回收保护尚未就绪":protection.denial(player,item);}
    public boolean canAutoRecycle(Player player,ItemStack item){return protection!=null && protection.automatic(player) && isRecyclable(item) && protection.denial(player,item)==null;}


    public void setLedger(xuanmo.arcartxsuite.market.MarketLedger ledger) { this.ledger = ledger; }


    private final RecycleConfiguration config;
    private final MessagesConfiguration messages;
    private final MarketRepository repository;
    private final CurrencyBridgeAPI currencyManager;
    private final ItemSourceRegistry itemSourceRegistry;
    private final File dataFolder;
    private final Logger logger;

    private final Map<String, RecycleEntry> entries = new LinkedHashMap<>();

    public RecycleService(RecycleConfiguration config, MessagesConfiguration messages,
                          MarketRepository repository, CurrencyBridgeAPI currencyManager,
                          ItemSourceRegistry itemSourceRegistry, File dataFolder, Logger logger) {
        this.config = config;
        this.messages = messages;
        this.repository = repository;
        this.currencyManager = currencyManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void start() {
        loadRecycleTables();
        logger.info("[Recycle] 回收表已加载 " + entries.size() + " 个条目");
    }

    public void shutdown() {
        entries.clear();
    }

    public void reload() {
        shutdown();
        start();
    }

    private void loadRecycleTables() {
        entries.clear();
        File recycleDir = new File(dataFolder, config.recycleDirectory());
        if (!recycleDir.exists()) {
            recycleDir.mkdirs();
            return;
        }
        File[] files = recycleDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection entriesSec = yaml.getConfigurationSection("entries");
                if (entriesSec == null) continue;

                for (String key : entriesSec.getKeys(false)) {
                    ConfigurationSection entrySec = entriesSec.getConfigurationSection(key);
                    if (entrySec == null) continue;
                    double price = entrySec.getDouble("price", 0);
                    if (!Double.isFinite(price) || price <= 0) {
                        logger.warning("[Recycle] 跳过无效价格条目: " + file.getName() + "/" + key);
                        continue;
                    }
                    entries.put(key, new RecycleEntry(
                        key,
                        entrySec.getString("source", "minecraft"),
                        entrySec.getString("item-id", "STONE"),
                        price,
                        entrySec.getString("currency", config.defaultCurrency())
                    ));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[Recycle] 加载回收表文件失败: " + file.getName(), e);
            }
        }
    }

    /**
     * 回收单个物品。
     */
    public RecycleResult recycle(Player player,ItemStack item) {
        if(item!=null)for(int slot=0;slot<36;slot++){
            ItemStack current=player.getInventory().getItem(slot);
            if(current!=null && current.isSimilar(item) && current.getAmount()==item.getAmount())return recycleSlot(player,slot);
        }
        return RecycleResult.fail("物品不在主背包中，请重新选择");
    }

    public RecycleResult recycleSlot(Player player,int slot){
        if(slot<0 || slot>=36)return RecycleResult.fail("槽位无效");
        ItemStack source=player.getInventory().getItem(slot);
        if(source==null || source.getType().isAir())return RecycleResult.fail("该槽位已没有物品");
        ItemStack snapshot=source.clone();
        return ledger.trade("RECYCLE",player.getUniqueId(),"slot="+slot,
            () -> recycleOwned(player,snapshot,()->takeSlot(player,slot,snapshot)),result -> result.success());
    }

    private void takeSlot(Player player,int slot,ItemStack expected){
        ItemStack current=player.getInventory().getItem(slot);
        if(current==null || !current.isSimilar(expected) || current.getAmount()!=expected.getAmount())throw new IllegalStateException("回收槽位已变化");
        player.getInventory().setItem(slot,null);
    }

    private String itemName(ItemStack item){
        return item.hasItemMeta() && item.getItemMeta().hasDisplayName()?item.getItemMeta().getDisplayName()
            :xuanmo.arcartxsuite.api.item.VanillaItemNameTable.translate(item.getType());
    }

    private RecycleResult recycleOwned(Player player,ItemStack snapshot,Runnable take){
        RecycleEntry entry=findEntry(snapshot);if(entry==null)return RecycleResult.fail("该物品不可回收");
        String data=java.util.Base64.getEncoder().encodeToString(xuanmo.arcartxsuite.api.util.ItemSerializer.serialize(snapshot));
        ledger.describe(itemName(snapshot),snapshot.getType().name(),data);
        String reason=protectionReason(player,snapshot);if(reason!=null)return RecycleResult.fail(reason);
        if(protection==null || !protection.currentVersion(player))return RecycleResult.fail("回收保护设置已变化，请刷新后重试");
        double multiplier=getMultiplier(player);
        if(!Double.isFinite(multiplier) || multiplier<=0)return RecycleResult.fail("回收倍率无效");
        BigDecimal total=BigDecimal.valueOf(entry.price()).multiply(BigDecimal.valueOf(snapshot.getAmount())).multiply(BigDecimal.valueOf(multiplier));
        var bridge=currencyManager.bridge(entry.currency());
        if(bridge==null || !bridge.available())return RecycleResult.fail("货币系统不可用，物品未回收");
        String task=ledger.prepareDelivery(xuanmo.arcartxsuite.market.MarketLedger.delivery(player.getUniqueId(),"CURRENCY",entry.currency(),total.toPlainString(),"","recycle_income"));
        take.run();
        ledger.activatePrepared(List.of(task),true,"已收取回收物品，等待入账");
        repository.addRecycleStats(player.getUniqueId(),entry.currency(),total.doubleValue(),snapshot.getAmount());
        recordHistory(player,snapshot,total.doubleValue(),entry.currency());
        return RecycleResult.success(total.doubleValue(),entry.currency(),snapshot.getAmount(),Map.of(entry.currency(),total.doubleValue()));
    }

    /** The pickup listener owns the entity and supplies the main-thread removal after the income snapshot is durable. */
    public RecycleResult recycleAuto(Player player,ItemStack item,Runnable take){
        if(!canAutoRecycle(player,item))return RecycleResult.fail("个人自动回收关闭或物品受保护");
        RecycleResult result=ledger.trade("RECYCLE_AUTO",player.getUniqueId(),"pickup",
            () -> recycleOwned(player,item.clone(),take),value -> value.success());
        if(result.success())player.sendMessage(ChatColor.translateAlternateColorCodes('&',messages.recycleSuccess().replace("{amount}",
            currencyManager.format(result.currency(),BigDecimal.valueOf(result.totalAmount())))));
        return result;
    }

    public RecycleResult recycleBatch(Player player){
        return ledger.trade("RECYCLE_BATCH",player.getUniqueId(),"backpack",()->{
            if(protection==null || protection.cached(player)==null)return RecycleResult.fail("回收保护资料读取中，请稍后刷新");
            Map<String,Double> earnings=new LinkedHashMap<>();int count=0,protectedCount=0;
            for(int slot=0;slot<36;slot++){
                ItemStack source=player.getInventory().getItem(slot);if(source==null || source.getType().isAir() || findEntry(source)==null)continue;
                if(protectionReason(player,source)!=null){protectedCount+=source.getAmount();continue;}
                final int selected=slot;final ItemStack snapshot=source.clone();
                RecycleResult result=recycleOwned(player,snapshot,()->takeSlot(player,selected,snapshot));
                if(result.success()){count+=result.itemCount();result.earningsByCurrency().forEach((currency,value)->earnings.merge(currency,value,Double::sum));}
            }
            if(protectedCount>0)player.sendMessage(ChatColor.GRAY+"已跳过 "+protectedCount+" 件受保护物品。");
            if(count==0)return RecycleResult.fail(protectedCount>0?"没有可安全回收的物品，受保护物品已保留":messages.recycleNothing());
            return RecycleResult.success(earnings.values().stream().mapToDouble(Double::doubleValue).sum(),config.defaultCurrency(),count,earnings);
        },result -> result.success());
    }

    /**
     * 检查物品是否可回收。
     */
    public boolean isRecyclable(ItemStack item) {
        return findEntry(item) != null;
    }

    /**
     * 获取物品回收价格。
     */
    public @Nullable String getRecycleCurrency(ItemStack item) {
        RecycleEntry entry = findEntry(item);
        return entry == null ? null : entry.currency();
    }

    public double getRecyclePrice(ItemStack item, Player player) {
        RecycleEntry entry = findEntry(item);
        if (entry == null) return 0;
        return entry.price() * getMultiplier(player);
    }

    public Map<String, RecycleEntry> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    private @Nullable RecycleEntry findEntry(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        for (RecycleEntry entry : entries.values()) {
            String source = entry.source();
            String itemId = identifyItemId(item, source);
            if (itemId != null && entry.itemId().equalsIgnoreCase(itemId)) return entry;
        }
        return null;
    }

    private @Nullable String identifyItemId(ItemStack item, String source) {
        if (source == null || source.isBlank() || "minecraft".equalsIgnoreCase(source)) {
            return item.getType().name();
        }
        if (itemSourceRegistry == null) return null;
        try {
            return switch (source.toLowerCase(java.util.Locale.ROOT)) {
                case "mythic", "mythicmobs" -> itemSourceRegistry.mythicBridgeAvailable()
                    ? itemSourceRegistry.mythicItemId(item) : null;
                case "neige", "neigeitems" -> itemSourceRegistry.neigeBridgeAvailable()
                    ? itemSourceRegistry.neigeItemId(item) : null;
                case "overture" -> itemSourceRegistry.overtureBridgeAvailable()
                    ? itemSourceRegistry.overtureItemId(item) : null;
                case "mmo", "mmoitems" -> itemSourceRegistry.mmoBridgeAvailable()
                    ? itemSourceRegistry.mmoItemId(item) : null;
                default -> null;
            };
        } catch (RuntimeException ex) {
            logger.log(Level.FINE, "[Recycle] item source identification failed: " + source, ex);
            return null;
        }
    }

    public double getMultiplier(Player player) {
        double bestMultiplier = 1.0;
        boolean matched = false;
        for (var entry : config.priceMultiplier().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                if (!matched || entry.getValue() > bestMultiplier) {
                    bestMultiplier = entry.getValue();
                }
                matched = true;
            }
        }
        return matched ? bestMultiplier : 1.0;
    }

    private void recordHistory(Player player, ItemStack item, double total, String currency) {
        try {
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
            ? item.getItemMeta().getDisplayName() : item.getType().name();
        String data = java.util.Base64.getEncoder().encodeToString(
            xuanmo.arcartxsuite.api.util.ItemSerializer.serialize(item.clone()));
        repository.insertHistory(new xuanmo.arcartxsuite.market.storage.AuctionHistory(
            0, 0, player.getUniqueId(), null, data, name, total, currency, 0,
            "RECYCLE", System.currentTimeMillis()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "[Recycle] 回收已结算，但历史写入失败: " + player.getUniqueId(), exception);
        }
    }

    // ─── 结果 ───────────────────────────────────────────────

    public record RecycleResult(boolean success, @Nullable String error, double totalAmount, @Nullable String currency, int itemCount, Map<String, Double> earningsByCurrency) {
        public static RecycleResult success(double amount, String currency, int count, Map<String, Double> earnings) {
            return new RecycleResult(true, null, amount, currency, count,
                Collections.unmodifiableMap(new LinkedHashMap<>(earnings)));
        }
        public static RecycleResult fail(String error) {
            return new RecycleResult(false, error, 0, null, 0, Map.of());
        }
    }
}
