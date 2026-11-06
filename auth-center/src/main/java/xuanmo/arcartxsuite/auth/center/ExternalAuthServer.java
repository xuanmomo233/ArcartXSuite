package xuanmo.arcartxsuite.auth.center;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static xuanmo.arcartxsuite.auth.center.CenterConfiguration.*;
import static xuanmo.arcartxsuite.auth.center.ExternalConfiguration.PATH;
import static xuanmo.arcartxsuite.auth.protocol.AuthWire.JSON;

/** Public browser and launcher routes have their own listener and bounded worker queue. */
final class ExternalAuthServer implements AutoCloseable {
    private static final String COOKIE = "__Host-axs_external";
    private final ExternalConfiguration config;
    private final ExternalAuthorization authorization;
    private final HttpServer server;
    private final ExecutorService workers = new ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    private long window = System.nanoTime();
    private int requests;

    ExternalAuthServer(ExternalConfiguration config, CenterService center) throws IOException {
        this.config = config;
        authorization = new ExternalAuthorization(config,center);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",config.port()),32);
        server.setExecutor(workers);
        server.createContext(PATH,this::handle);
    }
    void start() { server.start(); }

    private synchronized boolean withinBudget() {
        long now = System.nanoTime();
        if (now - window >= 60_000_000_000L) { window = now; requests = 0; }
        if (requests >= 600) return false;
        requests++;
        return true;
    }

    private void handle(HttpExchange exchange) throws IOException {
        var deadline = deadlines.schedule(exchange::close,8,TimeUnit.SECONDS);
        try {
            var headers = exchange.getResponseHeaders();
            headers.set("Cache-Control","no-store");
            headers.set("Referrer-Policy","no-referrer");
            headers.set("Content-Security-Policy","default-src 'none'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
            headers.set("X-Content-Type-Options","nosniff");
            if (!withinBudget()) {
                headers.set("Retry-After","60");
                html(exchange,429,"请求过多，请稍候再试。");
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            String method = exchange.getRequestMethod();
            switch (method+" "+path) {
                case "GET "+PATH+"/verify" -> {
                    String realm = required(parameters(exchange.getRequestURI().getRawQuery()),"realm");
                    if (!config.providers().containsKey(realm)) throw new SecurityException("Unknown realm");
                    html(exchange,200,"<h1>游戏授权</h1><form method=\"post\" action=\""+PATH+"/verify\">"
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
                    html(exchange,200,"<h1>启动器设备授权</h1><form method=\"post\" action=\""+PATH+"/device\">"
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
                    var consent = authorization.callback(state,cookie(exchange),query.containsKey("error")?null:query.get("code"));
                    var request = consent.request();
                    String action = request.pairing()==null
                        ? "授权启动器 "+(request.device()==null?request.launcher().app().id():request.device().app.id())
                        : (request.pairing().link()?"关联官网身份到 ":"允许本次游戏连接 ")+request.pairing().context().name()
                            +"（"+request.realm()+" / "+request.pairing().context().audience()+"）";
                    html(exchange,200,"<h1>确认授权</h1><p>"+escape(action)+"</p>"
                        +"<form method=\"post\" action=\""+PATH+"/confirm\"><input type=\"hidden\" name=\"state\" value=\""+escape(state)+"\">"
                        +"<button name=\"decision\" value=\"approve\">确认</button> <button name=\"decision\" value=\"deny\">拒绝</button></form>");
                }
                case "POST "+PATH+"/confirm" -> {
                    var form = browserForm(exchange);
                    String decision = required(form,"decision");
                    if (!Set.of("approve","deny").contains(decision)) throw new IllegalArgumentException("Invalid decision");
                    var result = authorization.confirm(required(form,"state"),cookie(exchange),"approve".equals(decision));
                    headers.set("Set-Cookie",COOKIE+"=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax");
                    if (result.redirect()!=null) link(exchange,result.redirect(),"返回启动器");
                    else if(!result.approved()) html(exchange,200,"已拒绝授权。");
                    else if(result.confirmation().isEmpty()) html(exchange,200,"已批准设备授权，请返回启动器。");
                    else html(exchange,200,"授权核对码 <strong>"+escape(result.confirmation())+"</strong>。返回游戏刷新，核对后确认。");
                }
                case "POST "+PATH+"/launcher/start", "POST "+PATH+"/launcher/exchange", "POST "+PATH+"/launcher/pair",
                     "POST "+PATH+"/launcher/device/start", "POST "+PATH+"/launcher/device/poll", "POST "+PATH+"/launcher/device/cancel" -> launcher(exchange,path);
                default -> html(exchange,404,"未找到该入口。");
            }
        } catch (IllegalArgumentException | SecurityException e) {
            html(exchange,400,"授权请求无效、已过期或已使用。");
        } catch (Exception e) {
            html(exchange,503,"认证暂时不可用，请重新发起授权。");
        } finally { deadline.cancel(false); exchange.close(); }
    }

    private void launcher(HttpExchange exchange, String path) throws IOException {
        String requestId = "";
        try {
            String type = exchange.getRequestHeaders().getFirst("Content-Type");
            if (exchange.getRequestURI().getRawQuery()!=null || type==null || !type.equalsIgnoreCase("application/json"))
                throw new IllegalArgumentException("Invalid launcher request");
            JsonObject json = JsonParser.parseString(body(exchange)).getAsJsonObject();
            requestId = UUID.fromString(string(json,"requestId")).toString();
            if (integer(json,"version")!=1) { json(exchange,400,requestId,"UNSUPPORTED_VERSION",null); return; }
            String client = string(json,"clientId");
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
