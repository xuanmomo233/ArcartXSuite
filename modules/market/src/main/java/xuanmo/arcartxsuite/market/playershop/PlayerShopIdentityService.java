package xuanmo.arcartxsuite.market.playershop;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.bridge.ContentModerator;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.PlayerShopConfiguration;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/** Owner-only branding. Prepared edits contain no client-provided item/NBT or owner identity. */
public final class PlayerShopIdentityService {
    public record Draft(PlayerShopProfile profile,boolean keepIcon) {}
    private final MarketRepository repository;
    private final PlayerShopConfiguration config;
    private final AuctionItemSerializer serializer;
    private final ContentModerator moderator;
    private final Map<UUID,String> pickedIcons=new ConcurrentHashMap<>();

    public PlayerShopIdentityService(MarketRepository repository,PlayerShopConfiguration config,AuctionItemSerializer serializer,ContentModerator moderator) {
        this.repository=repository;this.config=config;this.serializer=serializer;this.moderator=moderator;
    }

    public void clear(UUID player){pickedIcons.remove(player);}
    public void clear(){pickedIcons.clear();}

    public ItemStack pick(Player player,int slot) {
        if(slot<0 || slot>=36)throw new IllegalArgumentException("请选择主背包中的有效物品");
        ItemStack source=player.getInventory().getItem(slot);
        if(source==null || source.getType().isAir())throw new IllegalArgumentException("该槽位已没有物品，请重新选择");
        ItemStack icon=source.clone();icon.setAmount(1);
        String data=serializer.serialize(icon);
        if(data==null || data.length()>262144)throw new IllegalArgumentException("该物品图标数据过大，请换一个物品");
        pickedIcons.put(player.getUniqueId(),data);return icon;
    }

    private String text(String field,String value,int maximum,int maxLines,boolean required) {
        String input=value==null?"":value.trim();
        if(input.codePointCount(0,input.length())>maximum)throw new IllegalArgumentException(field+"不能超过 "+maximum+" 个字符");
        if(input.split("\\R",-1).length>maxLines)throw new IllegalArgumentException(field+"最多 "+maxLines+" 行");
        String clean=ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',input));
        clean=clean.replaceAll("[\\p{Cc}&&[^\\n\\t]]","").replaceAll("[\\u202a-\\u202e\\u2066-\\u2069]","");
        if(moderator!=null){
            var result=moderator.filter(clean);
            if(result.blocked())throw new IllegalArgumentException(field+"包含不允许的内容，请修改后再保存");
            clean=result.text();
        }
        clean=clean==null?"":clean.trim();
        if(required && clean.isBlank())throw new IllegalArgumentException(field+"不能为空");
        if(clean.codePointCount(0,clean.length())>maximum || clean.split("\\R",-1).length>maxLines)throw new IllegalArgumentException(field+"处理后超过长度或行数限制");
        return clean;
    }

    public Draft prepare(Player player,long expectedVersion,String name,String description,String announcement,String iconMode) {
        if(expectedVersion<1)throw new IllegalArgumentException("资料版本无效，请重新加载店铺资料");
        String title=text("店名",name,config.nameMaxLength(),1,true);
        String intro=text("简介",description,config.descriptionMaxLength(),8,false);
        String notice=text("公告",announcement,config.announcementMaxLength(),12,false);
        boolean keep="keep".equals(iconMode);String icon="";
        if("picked".equals(iconMode)){
            icon=pickedIcons.get(player.getUniqueId());
            if(icon==null)throw new IllegalArgumentException("背包图标选择已失效，请重新选择或保留原图标");
        }else if(!keep && !"clear".equals(iconMode))throw new IllegalArgumentException("图标模式无效");
        return new Draft(new PlayerShopProfile(player.getUniqueId(),player.getName(),title,intro,notice,icon,expectedVersion,true,0,0),keep);
    }

    public PlayerShopProfile commit(Draft draft) {
        PlayerShopProfile profile=draft.profile();
        if(!repository.savePlayerShopProfile(profile,profile.version(),draft.keepIcon()))return null;
        if(draft.keepIcon())pickedIcons.remove(profile.owner());else pickedIcons.remove(profile.owner(),profile.iconData());
        return repository.playerShopProfile(profile.owner());
    }
}
