package xuanmo.arcartxsuite.auth.runtime;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.auth.minecraft.LocalHandshakeClient;
import xuanmo.arcartxsuite.auth.minecraft.ProxyProofs;

/** Validated immutable startup configuration; never reloaded through a presentation module. */
record AuthenticationSettings(String realm,java.time.ZoneId timezone,long loginTimeoutNanos,
        LocalHandshakeClient nativeGateway,List<ProxyProofs.Trust> trustedProxies,String audience,
        boolean passwordAllowed,PasswordPolicy passwordPolicy,boolean requireBinding,
        AuthRuntimeCapability.Terms terms,Performance performance,CenterEndpoint center) {
    record PasswordPolicy(int minLength,int maxLength,int maxAttempts,long cooldownMillis) {}
    record Performance(int workers,int queueCapacity,int renewalWorkers,int renewalQueueCapacity) {}
    record CenterEndpoint(String url,String nodeId,String keyEnvironment) {}
    AuthenticationSettings { trustedProxies=List.copyOf(trustedProxies); }
    static AuthenticationSettings load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "authentication.yml");
        if (!file.exists()) plugin.saveResource("authentication.yml", false);
        var config = YamlConfiguration.loadConfiguration(file);
        if (config.getInt("schema-version") != 1) throw new IllegalArgumentException("authentication.yml 仅接受 schema-version=1");
        if("center".equals(config.getString("backend"))) return center(config);
        if(!"standalone".equals(config.getString("backend"))) throw new IllegalArgumentException("不支持的认证后端");
        Set<String> allowed = Set.of("schema-version", "backend", "realm-id", "timezone", "password", "requirements", "performance", "terms", "ingress", "login-timeout-seconds");
        if (!allowed.containsAll(config.getKeys(false))) throw new IllegalArgumentException("authentication.yml 存在未知字段");
        String realm = config.getString("realm-id", "");
        String ingress=config.getString("ingress.type","password");
        if(!Set.of("password","local-mixed","signed-proxy").contains(ingress)) throw new IllegalArgumentException("不支持的 ingress.type");
        var childKeys=Map.of("password",Set.of("min-length","max-length","max-attempts","cooldown-seconds"),
            "requirements",Set.of("terms","qq-binding"),"performance",Set.of("workers","queue-capacity"),
            "terms",Set.of("version","lines"),"ingress",Set.of("type","gateway-url","audience","trusted-proxies","password-login"));
        for(var child:childKeys.entrySet()) {
            var section=config.getConfigurationSection(child.getKey());
            if(config.contains(child.getKey()) && (section==null || !child.getValue().containsAll(section.getKeys(false))))
                throw new IllegalArgumentException("不支持的认证配置字段: "+child.getKey());
        }
        for(String path:List.of("requirements.terms","requirements.qq-binding","ingress.password-login")) {
            if(config.contains(path) && !(config.get(path) instanceof Boolean)) throw new IllegalArgumentException(path+" 必须为布尔值");
        }
        for(String path:List.of("schema-version","login-timeout-seconds","password.min-length","password.max-length",
                "password.max-attempts","password.cooldown-seconds","performance.workers","performance.queue-capacity")) {
            Object value=config.get(path);
            if(value!=null && !(value instanceof Integer) && !(value instanceof Long)) throw new IllegalArgumentException(path+" 必须为整数");
            if(value instanceof Number number && (number.longValue()<Integer.MIN_VALUE || number.longValue()>Integer.MAX_VALUE))
                throw new IllegalArgumentException(path+" 超出整数范围");
        }
        for(String path:List.of("backend","realm-id","timezone","ingress.type","ingress.gateway-url","ingress.audience","terms.version")) {
            if(config.contains(path) && !(config.get(path) instanceof String)) throw new IllegalArgumentException(path+" 必须为字符串");
        }
        if(config.contains("terms.lines") && (!(config.get("terms.lines") instanceof List<?> lines) || lines.stream().anyMatch(line->!(line instanceof String))))
            throw new IllegalArgumentException("terms.lines 必须为字符串列表");
        boolean passwordAllowed=config.getBoolean("ingress.password-login",ingress.equals("password"));
        if(ingress.equals("password") && !passwordAllowed) throw new IllegalArgumentException("password 入口不能关闭唯一的身份验证方式");
        if(passwordAllowed && !Bukkit.getOnlineMode())
            throw new IllegalArgumentException("游戏内密码认证要求直连 online-mode=true 的加密连接；离线后端须关闭 password-login 并使用可信代理证明");
        String audience=config.getString("ingress.audience","");
        var trusts=new ArrayList<xuanmo.arcartxsuite.auth.minecraft.ProxyProofs.Trust>();
        if(ingress.equals("signed-proxy")) {
            if(!audience.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("signed-proxy 需要有效 audience");
            if(!(config.get("ingress.trusted-proxies") instanceof List<?> configured)
                    || configured.stream().anyMatch(value->!(value instanceof Map<?,?>)))
                throw new IllegalArgumentException("可信代理必须为对象列表");
            var values=config.getMapList("ingress.trusted-proxies");
            if(values.isEmpty() || values.size()>8) throw new IllegalArgumentException("需要 1 至 8 个可信代理公钥");
            for(var value:values) {
                try {
                    if(!Set.of("issuer","public-key","providers").containsAll(value.keySet())) throw new IllegalArgumentException();
                    String issuer=(String)value.get("issuer");
                    if(issuer==null || !issuer.matches("[a-zA-Z0-9_-]{1,64}") || !(value.get("providers") instanceof List<?> providers)) throw new IllegalArgumentException();
                    Set<String> permitted=new HashSet<>();
                    for(Object provider:providers) permitted.add((String)provider);
                    if(permitted.isEmpty() || !Set.of("microsoft","littleskin").containsAll(permitted)) throw new IllegalArgumentException();
                    trusts.add(new xuanmo.arcartxsuite.auth.minecraft.ProxyProofs.Trust(issuer,
                        xuanmo.arcartxsuite.auth.minecraft.ProxyProofs.publicKey((String)value.get("public-key")),permitted));
                } catch(Exception e) { throw new IllegalArgumentException("可信代理配置无效"); }
            }
        }
        List<xuanmo.arcartxsuite.auth.minecraft.ProxyProofs.Trust> trustedProxies=List.copyOf(trusts);
        if(ingress.equals("local-mixed") && !Bukkit.getOnlineMode())
            throw new IllegalArgumentException("local-mixed 仅支持同机直连且 online-mode=true，不能代替代理转发证明");
        xuanmo.arcartxsuite.auth.minecraft.LocalHandshakeClient nativeGateway=ingress.equals("local-mixed") ? new xuanmo.arcartxsuite.auth.minecraft.LocalHandshakeClient(
            config.getString("ingress.gateway-url","http://127.0.0.1:25599")) : null;
        if(nativeGateway!=null) xuanmo.arcartxsuite.auth.minecraft.LocalAgentBinding.require(
            config.getString("ingress.gateway-url","http://127.0.0.1:25599"),java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        java.time.ZoneId timezone = java.time.ZoneId.of(config.getString("timezone", "Asia/Shanghai"));
        long loginTimeout=config.getLong("login-timeout-seconds",300);
        if(loginTimeout<30 || loginTimeout>1800) throw new IllegalArgumentException("login-timeout-seconds 范围为 30 至 1800");
        long loginTimeoutNanos=TimeUnit.SECONDS.toNanos(loginTimeout);
        if (!realm.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("realm-id 无效");
        int minLength = config.getInt("password.min-length", 8);
        int maxLength = config.getInt("password.max-length", 128);
        int maxAttempts = config.getInt("password.max-attempts", 5);
        long cooldownSeconds=config.getLong("password.cooldown-seconds",300);
        if(cooldownSeconds<1 || cooldownSeconds>86400) throw new IllegalArgumentException("cooldown-seconds 范围为 1 至 86400");
        long cooldown = cooldownSeconds * 1000L;
        boolean requireTerms = config.getBoolean("requirements.terms", true);
        String termsVersion=config.getString("terms.version","");
        List<String> termsLines=config.getStringList("terms.lines");
        if(requireTerms && (termsVersion.isBlank() || termsVersion.length()>64 || termsLines.isEmpty() || termsLines.size()>3
                || termsLines.stream().anyMatch(line->line.isBlank() || line.length()>60)))
            throw new IllegalArgumentException("terms 必须提供版本及 1 至 3 行条款，每行不超过60字符");
        AuthRuntimeCapability.Terms terms=new AuthRuntimeCapability.Terms(requireTerms,termsVersion,termsLines);
        if (minLength < 1 || maxLength < minLength || maxLength > 128 || maxAttempts < 1 || maxAttempts > 20)
            throw new IllegalArgumentException("认证密码策略无效");
        boolean requireBinding = config.getBoolean("requirements.qq-binding");
        int workers = config.getInt("performance.workers", 2);
        int capacity = config.getInt("performance.queue-capacity", 128);
        if (workers < 1 || workers > 8 || capacity < 1 || capacity > 1024) throw new IllegalArgumentException("认证队列配置无效");
        return new AuthenticationSettings(realm,timezone,loginTimeoutNanos,nativeGateway,trustedProxies,audience,
            passwordAllowed,new PasswordPolicy(minLength,maxLength,maxAttempts,cooldown),requireBinding,terms,
            new Performance(workers,capacity,2,4096),null);
    }
    private static AuthenticationSettings center(YamlConfiguration config) {
        if(!Set.of("schema-version","backend","realm-id","ingress","performance","center").equals(config.getKeys(false)))
            throw new IllegalArgumentException("中心模式只接受节点配置；密码、条款、绑定和超时策略由中心提供");
        var ingress=config.getConfigurationSection("ingress");
        var remote=config.getConfigurationSection("center");
        var performance=config.getConfigurationSection("performance");
        if(ingress==null || remote==null || performance==null
                || !Set.of("type","audience").equals(ingress.getKeys(false))
                || !Set.of("url","node-id","key-environment").equals(remote.getKeys(false))
                || !Set.of("workers","queue-capacity","renewal-workers","renewal-queue-capacity").equals(performance.getKeys(false)))
            throw new IllegalArgumentException("中心节点配置缺少字段或包含未知字段");
        for(String path:List.of("backend","realm-id","ingress.type","ingress.audience","center.url","center.node-id","center.key-environment"))
            if(!(config.get(path) instanceof String)) throw new IllegalArgumentException(path+" 必须为字符串");
        if(!(config.get("schema-version") instanceof Integer) || !(config.get("performance.workers") instanceof Integer)
                || !(config.get("performance.queue-capacity") instanceof Integer) || !(config.get("performance.renewal-workers") instanceof Integer)
                || !(config.get("performance.renewal-queue-capacity") instanceof Integer)) throw new IllegalArgumentException("节点版本和队列策略必须为整数");
        if(!"password".equals(ingress.getString("type"))) throw new IllegalArgumentException("P3 中心节点当前仅支持 password 入口；混合入口不能隐式降级");
        String realm=config.getString("realm-id"),audience=ingress.getString("audience"),node=remote.getString("node-id"),environment=remote.getString("key-environment");
        if(!realm.matches("[a-zA-Z0-9_-]{1,64}") || !audience.matches("[a-zA-Z0-9_-]{1,64}") || !node.matches("[a-zA-Z0-9_-]{1,64}")
                || !environment.matches("[A-Z][A-Z0-9_]{0,127}")) throw new IllegalArgumentException("中心节点标识无效");
        int workers=performance.getInt("workers"),capacity=performance.getInt("queue-capacity");
        int renewalWorkers=performance.getInt("renewal-workers"),renewalCapacity=performance.getInt("renewal-queue-capacity");
        if(workers<1 || workers>8 || capacity<1 || capacity>1024) throw new IllegalArgumentException("认证队列配置无效");
        if(renewalWorkers<1 || renewalWorkers>16 || renewalCapacity<128 || renewalCapacity>16384) throw new IllegalArgumentException("租约续期队列配置无效");
        return new AuthenticationSettings(realm,java.time.ZoneId.of("UTC"),TimeUnit.SECONDS.toNanos(300),null,List.of(),audience,true,
            new PasswordPolicy(8,128,5,300_000),false,new AuthRuntimeCapability.Terms(false,"",List.of()),new Performance(workers,capacity,renewalWorkers,renewalCapacity),
            new CenterEndpoint(remote.getString("url"),node,environment));
    }
}
