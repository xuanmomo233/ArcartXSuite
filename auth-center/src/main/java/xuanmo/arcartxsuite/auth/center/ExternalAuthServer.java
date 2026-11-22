package xuanmo.arcartxsuite.auth.center;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import xuanmo.arcartxsuite.auth.protocol.MinecraftSessionProtocol;
import static java.nio.charset.StandardCharsets.UTF_8;
import static xuanmo.arcartxsuite.auth.center.CenterConfiguration.*;
import static xuanmo.arcartxsuite.auth.center.ExternalConfiguration.PATH;
import static xuanmo.arcartxsuite.auth.protocol.AuthWire.JSON;

/** Public browser and launcher routes have their own listener and bounded worker queue. */
final class ExternalAuthServer implements AutoCloseable {
    private static final String COOKIE = "__Host-axs_external";
    private final ExternalConfiguration config;
    private final ExternalAuthorization authorization;
    private final CenterService center;
    private final MinecraftSessions minecraft;
    private final HttpServer server;
    private final ExecutorService workers = new ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    private final ExternalRequestBudget budget=new ExternalRequestBudget();
    private final Semaphore starts=new Semaphore(2);
    private final Semaphore completions=new Semaphore(2);
    private final Semaphore minecraftWork=new Semaphore(2);
    private final Set<String> inFlight=ConcurrentHashMap.newKeySet();

    ExternalAuthServer(ExternalConfiguration config, CenterService center) throws IOException {
        this(config,center,new ExternalAuthorization(config,center));
    }

    ExternalAuthServer(ExternalConfiguration config, CenterService center, ExternalAuthorization authorization) throws IOException {
        this.config = config;
        this.center = center;
        this.authorization = authorization;
        minecraft = config.minecraftSessions()
            ? new MinecraftSessions(config.origin().resolve(MinecraftSessionProtocol.API_ROOT),
                center::minecraftIdentityCurrent, System::currentTimeMillis) : null;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",config.port()),32);
        server.setExecutor(workers);
        server.createContext(PATH,this::handle);
    }
    void start() { server.start(); }
    int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        var deadline = deadlines.schedule(exchange::close,8,TimeUnit.SECONDS);
        Semaphore slot=null;
        String sourceSlot=null;
        try {
            var headers = exchange.getResponseHeaders();
            headers.set("Cache-Control","no-store");
            headers.set("Referrer-Policy","no-referrer");
            headers.set("Content-Security-Policy","default-src 'none'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
            headers.set("X-Content-Type-Options","nosniff");
            String path = exchange.getRequestURI().getRawPath();
            String method = exchange.getRequestMethod();
            boolean minecraftPath = minecraft != null && minecraftPath(path);
            if(!minecraftPath && !Set.of(PATH+"/verify",PATH+"/device",PATH+"/launcher/authorize",PATH+"/callback",PATH+"/confirm",
                    PATH+"/launcher/start",PATH+"/launcher/exchange",PATH+"/launcher/pair",PATH+"/launcher/device/start",
                    PATH+"/launcher/device/poll",PATH+"/launcher/device/cancel",MinecraftSessionProtocol.LAUNCHER_EXCHANGE)
                    .contains(path)) { html(exchange,404,"未找到该入口。"); return; }
            var forwarded=exchange.getRequestHeaders().get("X-AXS-Client-IP");
            if(!exchange.getRemoteAddress().getAddress().isLoopbackAddress() || forwarded==null || forwarded.size()!=1)
                throw new SecurityException("Trusted proxy client address required");
            String source=ExternalRequestBudget.source(forwarded.get(0));
            String group=minecraftPath
                ? path.equals(MinecraftSessionProtocol.JOIN) ? "minecraft-join" : "minecraft-query"
                : path.endsWith("/callback") || path.endsWith("/confirm") || path.endsWith("/exchange") || path.endsWith("/pair")
                || path.endsWith("/cancel") || path.equals(MinecraftSessionProtocol.LAUNCHER_EXCHANGE)
                ? "complete" : path.endsWith("/poll") ? "poll" : "start";
            // Completion routes are already bounded by single-use state/code/token ownership. Anonymous input
            // must never spend a valid flow's minute allowance, including clients behind the same NAT.
            int limit = group.equals("minecraft-query") ? 1200 : group.equals("poll") ? 240 : 60;
            if (!group.equals("complete") && !budget.acquire(source,group,limit)) {
                headers.set("Retry-After","60");
                html(exchange,429,"请求过多，请稍候再试。");
                return;
            }
            if (!minecraftPath) {
                sourceSlot=source+"/"+group;
                if(!inFlight.add(sourceSlot)) { sourceSlot=null; headers.set("Retry-After","1"); html(exchange,429,"当前来源已有正在处理的请求。"); return; }
            }
            slot=minecraftPath ? minecraftWork : group.equals("complete") ? completions : starts;
            if(!slot.tryAcquire()) { slot=null; headers.set("Retry-After","1"); html(exchange,429,"认证繁忙，请稍后重试。"); return; }
            if (minecraftPath) { minecraftRequest(exchange,path,source); return; }
            switch (method+" "+path) {
                case "GET "+PATH+"/verify" -> {
                    String realm = required(parameters(exchange.getRequestURI().getRawQuery()),"realm");
                    if (!config.providers().containsKey(realm)) throw new SecurityException("Unknown realm");
                    browserFormPage(exchange,"<h1>游戏授权</h1><form method=\"post\" action=\""+PATH+"/verify\">"
                        +"<input type=\"hidden\" name=\"realm\" value=\""+escape(realm)+"\">"
                        +"<label>游戏授权码 <input name=\"user_code\" required maxlength=\"20\" autocomplete=\"off\"></label>"
                        +" <button>通过官网登录</button></form>");
                }
                case "POST "+PATH+"/verify" -> {
                    var form = browserForm(exchange);
                    begin(exchange,authorization.gameBrowser(required(form,"realm"),required(form,"user_code")));
                }
                case "GET "+PATH+"/device" -> {
                    String client=required(parameters(exchange.getRequestURI().getRawQuery()),"client");
                    var app=config.launchers().get(client);
                    if(app==null) throw new SecurityException("Unknown launcher");
                    browserFormPage(exchange,"<h1>启动器设备授权</h1><form method=\"post\" action=\""+PATH+"/device\">"
                        +"<input type=\"hidden\" name=\"client\" value=\""+escape(client)+"\">"
                        +"<label>设备用户码 <input name=\"user_code\" required maxlength=\"20\" autocomplete=\"off\"></label>"
                        +" <button>通过官网登录</button></form>");
                }
                case "POST "+PATH+"/device" -> {
                    var form=browserForm(exchange);
                    begin(exchange,authorization.deviceBrowser(required(form,"client"),required(form,"user_code")));
                }
                case "GET "+PATH+"/launcher/authorize" -> begin(exchange,authorization.launcherBrowser(
                    required(parameters(exchange.getRequestURI().getRawQuery()),"request")));
                case "GET "+PATH+"/callback" -> {
                    var query = parameters(exchange.getRequestURI().getRawQuery());
                    String state = required(query,"state");
                    if ("access_denied".equals(query.get("error")) && !query.containsKey("code")) {
                        finishBrowser(exchange,authorization.denyBrowser(state,cookie(exchange)));
                        return;
                    }
                    var consent = authorization.callback(state,cookie(exchange),query.containsKey("error")?null:query.get("code"));
                    var request = consent.request();
                    String action = request.pairing()==null
                        ? "授权启动器 "+(request.device()==null?request.launcher().app().id():request.device().app.id())
                        : (request.pairing().link()?"关联官网身份到 ":"允许本次游戏连接 ")+request.pairing().context().name()
                            +"（"+request.realm()+" / "+request.pairing().context().audience()+"）";
                    browserFormPage(exchange,"<h1>确认授权</h1><p>"+escape(action)+"</p>"
                        +"<form method=\"post\" action=\""+PATH+"/confirm\"><input type=\"hidden\" name=\"state\" value=\""+escape(state)+"\">"
                        +"<button name=\"decision\" value=\"approve\">确认</button> <button name=\"decision\" value=\"deny\">拒绝</button></form>");
                }
                case "POST "+PATH+"/confirm" -> {
                    var form = browserForm(exchange);
                    String decision = required(form,"decision");
                    if (!Set.of("approve","deny").contains(decision)) throw new IllegalArgumentException("Invalid decision");
                    finishBrowser(exchange,authorization.confirm(required(form,"state"),cookie(exchange),"approve".equals(decision)));
                }
                case "POST "+PATH+"/launcher/start", "POST "+PATH+"/launcher/exchange", "POST "+PATH+"/launcher/pair",
                     "POST "+PATH+"/launcher/device/start", "POST "+PATH+"/launcher/device/poll", "POST "+PATH+"/launcher/device/cancel",
                     "POST "+MinecraftSessionProtocol.LAUNCHER_EXCHANGE -> launcher(exchange,path,source);
                default -> html(exchange,404,"未找到该入口。");
            }
        } catch (IllegalArgumentException | SecurityException e) {
            html(exchange,400,"授权请求无效、已过期或已使用。");
        } catch (Exception e) {
            html(exchange,503,"认证暂时不可用，请重新发起授权。");
        } finally { if(slot!=null) slot.release(); if(sourceSlot!=null) inFlight.remove(sourceSlot); deadline.cancel(false); exchange.close(); }
    }

    private static boolean minecraftPath(String path) {
        String root = MinecraftSessionProtocol.API_ROOT;
        return path.equals(root) || path.equals(root.substring(0, root.length() - 1)) ||
            path.equals(MinecraftSessionProtocol.JOIN) || path.equals(MinecraftSessionProtocol.HAS_JOINED) ||
            path.startsWith(MinecraftSessionProtocol.PROFILE);
    }

    private void minecraftRequest(HttpExchange exchange, String path, String source) throws IOException {
        String method = exchange.getRequestMethod();
        String query = exchange.getRequestURI().getRawQuery();
        try {
            if (path.equals(MinecraftSessionProtocol.JOIN) && method.equals("POST")) {
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                if (query != null || type == null || !type.split(";",2)[0].strip().equalsIgnoreCase("application/json"))
                    throw new IllegalArgumentException("Expected a Minecraft join request");
                var value = JsonParser.parseString(body(exchange));
                if (!value.isJsonObject()) throw new IllegalArgumentException("Expected an object");
                var data = value.getAsJsonObject();
                keys(data,Set.of("accessToken","selectedProfile","serverId"));
                if (!minecraft.join(string(data,"accessToken"),string(data,"selectedProfile"),string(data,"serverId"),source)) {
                    send(exchange,403,"application/json","{\"error\":\"ForbiddenOperationException\",\"errorMessage\":\"Invalid Minecraft session\"}");
                } else exchange.sendResponseHeaders(204,-1);
                return;
            }
            if (!method.equals("GET")) { exchange.sendResponseHeaders(405,-1); return; }
            if (path.equals(MinecraftSessionProtocol.HAS_JOINED)) {
                if (query == null || query.length() > 2048) throw new IllegalArgumentException("Invalid Minecraft session query");
                var args = parameters(query);
                if (!Set.of("username","serverId","ip").containsAll(args.keySet()))
                    throw new IllegalArgumentException("Unknown Minecraft session fields");
                String ip = args.containsKey("ip") ? ExternalRequestBudget.source(args.get("ip")) : null;
                minecraftProfile(exchange,minecraft.hasJoined(required(args,"username"),required(args,"serverId"),ip));
                return;
            }
            if (path.startsWith(MinecraftSessionProtocol.PROFILE)) {
                String id = path.substring(MinecraftSessionProtocol.PROFILE.length());
                if (!id.matches("[a-fA-F0-9]{32}")) { exchange.sendResponseHeaders(204,-1); return; }
                var uuid = UUID.fromString(id.substring(0,8)+"-"+id.substring(8,12)+"-"+id.substring(12,16)+"-"
                    +id.substring(16,20)+"-"+id.substring(20));
                minecraftProfile(exchange,center.minecraftProfile(uuid));
                return;
            }
            if (path.equals(MinecraftSessionProtocol.JOIN)) { exchange.sendResponseHeaders(405,-1); return; }
            send(exchange,200,"application/json","{\"meta\":{\"serverName\":\"Suite AuthCenter\"},\"skinDomains\":[]}");
        } catch (IllegalArgumentException | SecurityException | JsonParseException e) {
            exchange.sendResponseHeaders(400,-1);
        } catch (java.sql.SQLException e) {
            exchange.sendResponseHeaders(503,-1);
        }
    }

    private static void minecraftProfile(HttpExchange exchange, GameExternalStore.MinecraftProfile profile) throws IOException {
        if (profile == null) { exchange.sendResponseHeaders(204,-1); return; }
        var json = new JsonObject();
        json.addProperty("id",MinecraftSessionProtocol.profileId(profile.id()));
        json.addProperty("name",profile.name());
        json.add("properties",new JsonArray());
        send(exchange,200,"application/json",JSON.toJson(json));
    }

    private void finishBrowser(HttpExchange exchange, ExternalAuthorization.ConsentResult result) throws IOException {
        exchange.getResponseHeaders().set("Set-Cookie",COOKIE+"=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax");
        if (result.redirect()!=null) link(exchange,result.redirect(),"返回启动器");
        else if(!result.approved()) html(exchange,200,"已拒绝授权。");
        else if(result.confirmation().isEmpty()) html(exchange,200,"已批准设备授权，请返回启动器。");
        else html(exchange,200,"授权核对码 <strong>"+escape(result.confirmation())+"</strong>。返回游戏刷新，核对后确认。");
    }

    private void launcher(HttpExchange exchange, String path,String source) throws IOException {
        String requestId = "";
        try {
            String type = exchange.getRequestHeaders().getFirst("Content-Type");
            if (exchange.getRequestURI().getRawQuery()!=null || type==null || !type.equalsIgnoreCase("application/json"))
                throw new IllegalArgumentException("Invalid launcher request");
            JsonObject json = JsonParser.parseString(body(exchange)).getAsJsonObject();
            requestId = UUID.fromString(string(json,"requestId")).toString();
            if (integer(json,"version")!=1) { json(exchange,400,requestId,"UNSUPPORTED_VERSION",null); return; }
            String client = string(json,"clientId");
            if(!config.launchers().containsKey(client)) throw new SecurityException("Unknown launcher");
            if(path.endsWith("/start") && !budget.acquire(source+"/client:"+client,"start",30)) {
                exchange.getResponseHeaders().set("Retry-After","60"); json(exchange,429,requestId,"RATE_LIMITED",null); return;
            }
            String value;
            switch (path) {
                case PATH+"/launcher/start" -> {
                    keys(json,Set.of("version","requestId","clientId","redirectUri","codeChallenge","state"));
                    value = authorization.startLauncher(client,string(json,"redirectUri"),string(json,"codeChallenge"),string(json,"state")).toString();
                }
                case PATH+"/launcher/exchange" -> {
                    keys(json,Set.of("version","requestId","clientId","redirectUri","code","codeVerifier"));
                    value = authorization.exchangeLauncher(client,string(json,"redirectUri"),string(json,"code"),string(json,"codeVerifier"));
                }
                case PATH+"/launcher/pair" -> {
                    keys(json,Set.of("version","requestId","clientId","launchToken","userCode"));
                    value = authorization.pairLauncher(client,string(json,"launchToken"),string(json,"userCode"));
                }
                case MinecraftSessionProtocol.LAUNCHER_EXCHANGE -> {
                    if (minecraft == null) throw new SecurityException("Minecraft authorization disabled");
                    keys(json,Set.of("version","requestId","clientId","launchToken"));
                    value = JSON.toJson(minecraft.issue(authorization.minecraftIdentity(client,string(json,"launchToken"))));
                }
                case PATH+"/launcher/device/start" -> {
                    keys(json,Set.of("version","requestId","clientId"));
                    value=JSON.toJson(authorization.startDevice(client));
                }
                case PATH+"/launcher/device/poll" -> {
                    keys(json,Set.of("version","requestId","clientId","deviceCode"));
                    var result=authorization.pollDevice(client,string(json,"deviceCode"));
                    json(exchange,200,requestId,result.code(),JSON.toJson(result));
                    return;
                }
                case PATH+"/launcher/device/cancel" -> {
                    keys(json,Set.of("version","requestId","clientId","deviceCode"));
                    authorization.cancelDevice(client,string(json,"deviceCode"));
                    value="";
                }
                default -> throw new IllegalArgumentException("Unknown launcher operation");
            }
            json(exchange,200,requestId,"OK",value);
        } catch (IllegalArgumentException | SecurityException | JsonParseException e) {
            json(exchange,400,requestId,"AUTHORIZATION_REJECTED",null);
        } catch (Exception e) {
            json(exchange,503,requestId,"UNAVAILABLE",null);
        }
    }

    private Map<String,String> browserForm(HttpExchange exchange) throws IOException {
        if (!config.origin().toString().equals(exchange.getRequestHeaders().getFirst("Origin"))
                || !"application/x-www-form-urlencoded".equals(exchange.getRequestHeaders().getFirst("Content-Type")))
            throw new SecurityException("Browser origin rejected");
        return parameters(body(exchange));
    }
    private static String body(HttpExchange exchange) throws IOException {
        try (var input = exchange.getRequestBody()) {
            byte[] bytes = input.readNBytes(8193);
            if (bytes.length > 8192) throw new IllegalArgumentException("Request too large");
            return new String(bytes,UTF_8);
        }
    }
    private static Map<String,String> parameters(String raw) {
        if (raw==null || raw.length()>8192) throw new IllegalArgumentException("Invalid parameters");
        Map<String,String> values = new HashMap<>();
        for (String entry : raw.split("&",-1)) {
            int separator = entry.indexOf('=');
            if (separator<1 || values.putIfAbsent(URLDecoder.decode(entry.substring(0,separator),UTF_8),
                    URLDecoder.decode(entry.substring(separator+1),UTF_8))!=null) throw new IllegalArgumentException("Invalid parameters");
        }
        return values;
    }
    private static String required(Map<String,String> parameters, String key) {
        String value = parameters.get(key);
        if (value==null || value.isBlank()) throw new IllegalArgumentException("Missing parameter");
        return value;
    }
    private static String cookie(HttpExchange exchange) {
        String raw = exchange.getRequestHeaders().getFirst("Cookie");
        if (raw==null) return null;
        for (String item : raw.split(";")) {
            String value = item.strip();
            if (value.startsWith(COOKIE+"=")) return value.substring(COOKIE.length()+1);
        }
        return null;
    }
    private static void begin(HttpExchange exchange, ExternalAuthorization.BrowserStart start) throws IOException {
        exchange.getResponseHeaders().set("Set-Cookie",COOKIE+"="+start.cookie()+"; Path=/; Max-Age=390; Secure; HttpOnly; SameSite=Lax");
        if ("POST".equals(exchange.getRequestMethod())) link(exchange,start.authorizationUrl(),"前往官网登录");
        else redirect(exchange,start.authorizationUrl());
    }
    private static void link(HttpExchange exchange,URI target,String label) throws IOException {
        html(exchange,200,"<p><a href=\""+escape(target.toASCIIString())+"\">"+escape(label)+"</a></p>");
    }
    private static void redirect(HttpExchange exchange, URI target) throws IOException {
        exchange.getResponseHeaders().set("Location",target.toASCIIString());
        exchange.sendResponseHeaders(303,-1);
    }
    private static String escape(String value) {
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private static void browserFormPage(HttpExchange exchange,String content) throws IOException {
        // Preserve Origin on same-origin form POSTs without sending callback parameters in Referer.
        exchange.getResponseHeaders().set("Referrer-Policy","strict-origin");
        html(exchange,200,content);
    }
    private static void html(HttpExchange exchange, int status, String content) throws IOException {
        send(exchange,status,"text/html; charset=utf-8","<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>AXS 授权</title><body>"+content+"</body></html>");
    }
    private static void json(HttpExchange exchange, int status, String requestId, String code, String value) throws IOException {
        var result = new JsonObject();
        result.addProperty("version",1); result.addProperty("requestId",requestId); result.addProperty("code",code);
        if (value!=null) result.addProperty("value",value);
        send(exchange,status,"application/json; charset=utf-8",JSON.toJson(result));
    }
    private static void send(HttpExchange exchange, int status, String type, String content) throws IOException {
        byte[] bytes = content.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type",type);
        exchange.sendResponseHeaders(status,bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false,true)) return;
        server.stop(0); workers.shutdownNow(); deadlines.shutdownNow();
    }
}
