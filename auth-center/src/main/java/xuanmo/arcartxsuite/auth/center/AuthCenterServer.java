package xuanmo.arcartxsuite.auth.center;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.auth.protocol.*;
import static xuanmo.arcartxsuite.auth.protocol.AuthWire.*;

/** Loopback origin behind a TLS reverse proxy. All node requests AND replies are authenticated. */
final class AuthCenterServer implements AutoCloseable {
    private final CenterConfiguration config;
    private final CenterService service;
    private final HttpServer server;
    private final ExecutorService workers=new ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor();
    private final Logger logger=Logger.getLogger("AXS-AuthCenter");
    private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
    AuthCenterServer(CenterConfiguration config,CenterService service) throws IOException {
        this.config=config; this.service=service;
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",config.port()),64);
        server.setExecutor(workers); server.createContext(PATH,this::handle);
    }
    void start() { server.start(); }
    private void handle(HttpExchange exchange) throws IOException {
        ScheduledFuture<?> deadline=deadlines.schedule(exchange::close,5,TimeUnit.SECONDS);
        byte[] body=null;
        try {
            if(!"POST".equals(exchange.getRequestMethod()) || !PATH.equals(exchange.getRequestURI().toString())) { reject(exchange,404); return; }
            var headers=exchange.getRequestHeaders();
            var node=config.nodes().get(single(headers,"X-AXS-Node"));
            if(node==null) { reject(exchange,401); return; }
            String type=single(headers,"Content-Type");
            if(type==null || !type.equalsIgnoreCase("application/json; charset=utf-8")) { reject(exchange,415); return; }
            long time=Long.parseLong(single(headers,"X-AXS-Time")),now=System.currentTimeMillis();
            UUID nonce=UUID.fromString(single(headers,"X-AXS-Nonce"));
            if(time<now-30_000 || time>now+30_000) { reject(exchange,401); return; }
            try(InputStream input=exchange.getRequestBody(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while((count=input.read(buffer))!=-1) {
                    if(count>32768-output.size()) { reject(exchange,413); return; }
                    output.write(buffer,0,count);
                }
                body=output.toByteArray();
            }
            if(!RpcAuthentication.matches(RpcAuthentication.request(node.key(),node.id(),time,nonce,body),single(headers,"X-AXS-Signature"))) { reject(exchange,401); return; }
            try {
                if(!service.claimNonce(node.id(),nonce)) { reject(exchange,409); return; }
            } catch(Exception e) { reject(exchange,503); return; }
            JsonObject json=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();
            CenterConfiguration.keys(json,Set.of("version","requestId","operation","context","data"));
            if(CenterConfiguration.integer(json,"version")!=1) { reject(exchange,400); return; }
            Request request=JSON.fromJson(json,Request.class);
            Reply reply;
            try { reply=service.execute(node,request); }
            catch(SecurityException e) { reply=Reply.fail("NODE_REQUEST_REJECTED"); }
            catch(IllegalArgumentException e) { reply=Reply.fail("INVALID_REQUEST"); }
            catch(Exception e) {
                logger.warning("中心请求未完成: node="+node.id()+" request="+request.requestId()+" error="+e.getClass().getSimpleName());
                reject(exchange,503); return;
            }
            json.remove("data");
            byte[] response=JSON.toJson(reply).getBytes(StandardCharsets.UTF_8);
            if(response.length>262144) { reject(exchange,503); return; }
            exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control","no-store");
            exchange.getResponseHeaders().set("X-AXS-Signature",RpcAuthentication.response(node.key(),nonce,response));
            exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response);
        } catch(RuntimeException e) { reject(exchange,400); }
        finally { if(body!=null) Arrays.fill(body,(byte)0); deadline.cancel(false); exchange.close(); }
    }
    private static String single(Headers headers,String name) {
        List<String> values=headers.get(name); return values!=null && values.size()==1?values.get(0):null;
    }
    private static void reject(HttpExchange exchange,int code) throws IOException { exchange.sendResponseHeaders(code,-1); }
    @Override public void close() {
        if(!closed.compareAndSet(false,true)) return;
        server.stop(0); workers.shutdownNow(); deadlines.shutdownNow();
        config.nodes().values().forEach(node->Arrays.fill(node.key(),(byte)0));
    }
}
