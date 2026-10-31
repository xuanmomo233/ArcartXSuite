package xuanmo.arcartxsuite.abyss.reward;

import java.util.Locale;

/** One interpretation of item definitions for generation, matching and consumption. */
public record ItemDefinition(String source,String id,int amount) {
    public static ItemDefinition parse(String raw) {
        if(raw==null || raw.isBlank()) throw new IllegalArgumentException("物品定义不能为空");
        String[] parts=raw.trim().split(":",-1);
        String source=parts[0].toLowerCase(Locale.ROOT);
        if(!java.util.Set.of("material","item","minecraft","mythic","neige","overture","mmo","mmoitems").contains(source)) {
            int amount=parts.length==2?Integer.parseInt(parts[1]):1;
            return checked("minecraft",parts[0],amount);
        }
        if(parts.length<2)throw new IllegalArgumentException("物品定义缺少ID: "+raw);
        if(source.equals("mmo") || source.equals("mmoitems")) {
            if(parts[1].contains(";")) return checked("mmoitems",parts[1],parts.length>2?Integer.parseInt(parts[2]):1);
            if(parts.length<3)throw new IllegalArgumentException("MMOItems 需要 TYPE:ID 或 TYPE;ID");
            return checked("mmoitems",parts[1]+";"+parts[2],parts.length>3?Integer.parseInt(parts[3]):1);
        }
        if(source.equals("item") || source.equals("material"))source="minecraft";
        return checked(source,parts[1],parts.length>2?Integer.parseInt(parts[2]):1);
    }
    private static ItemDefinition checked(String source,String id,int amount) {
        if(id.isBlank() || amount<1 || amount>4096)throw new IllegalArgumentException("物品ID或数量无效");
        return new ItemDefinition(source,id,amount);
    }
}
