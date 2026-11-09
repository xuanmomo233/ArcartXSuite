package xuanmo.arcartxsuite.api.message;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;

/** Translates message-bearing UI packets with the module's messages.yml. */
public final class UiNoticeBridge {
    private record Template(String key,Pattern pattern) { }
    private final MessageProvider messages;
    private final List<Template> templates=new ArrayList<>();
    private UiNoticeBridge(MessageProvider messages,File folder){
        this.messages=messages;
        var section=YamlConfiguration.loadConfiguration(new File(folder,"messages.yml")).getConfigurationSection("ui-notices");
        if(section!=null)for(String source:section.getKeys(false))if(source.contains("{0}"))templates.add(new Template("ui-notices."+source,Pattern.compile("^"+Pattern.quote(source).replace("{0}","\\E(.*)\\Q")+"$",Pattern.DOTALL)));
        templates.sort(Comparator.comparingInt((Template t)->t.key.length()).reversed());
    }
    private String translate(String raw){
        if(messages==null||raw.isBlank())return raw;
        String plain=ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',raw)).trim(),key="ui-notices."+plain;
        String translated=messages.get(key);if(!translated.equals(key))return translated;
        for(Template t:templates){Matcher matcher=t.pattern.matcher(plain);if(matcher.matches()){translated=messages.get(t.key,matcher.group(1));if(!translated.equals(t.key))return translated;}}
        return raw;
    }
    public static PacketBridgeAPI wrap(PacketBridgeAPI delegate,MessageProvider messages,File folder){
        if(delegate==null)return null;
        UiNoticeBridge notices=new UiNoticeBridge(messages,folder);
        return (PacketBridgeAPI)Proxy.newProxyInstance(PacketBridgeAPI.class.getClassLoader(),new Class<?>[]{PacketBridgeAPI.class},(proxy,method,args)->{
            if((method.getName().equals("sendPacket")||method.getName().equals("sendPacketToAll"))&&args!=null&&args.length==4&&args[3] instanceof Map<?,?> payload&&payload.get("message") instanceof String message){
                var copy=new LinkedHashMap<Object,Object>(payload);copy.put("message",notices.translate(message));args=args.clone();args[3]=copy;
            }
            try{return method.invoke(delegate,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
    }
}
