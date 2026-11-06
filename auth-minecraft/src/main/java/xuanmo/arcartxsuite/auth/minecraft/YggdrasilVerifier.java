package xuanmo.arcartxsuite.auth.minecraft;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Only hasJoined can authenticate; profile lookup must never call this verifier. */
public final class YggdrasilVerifier {
    public enum Status { VERIFIED, NO_MATCH, INVALID, UNAVAILABLE }
    public record Provider(String id, URI hasJoinedEndpoint) {
        public Provider {
            if(id==null || !id.matches("[a-z0-9_-]{1,64}") || !"https".equals(hasJoinedEndpoint.getScheme())
                    || hasJoinedEndpoint.getHost()==null || hasJoinedEndpoint.getRawQuery()!=null
                    || hasJoinedEndpoint.getRawUserInfo()!=null || hasJoinedEndpoint.getFragment()!=null)
                throw new IllegalArgumentException("Invalid configured identity provider");
        }
    }
    public record Result(Status status, String provider, UUID profile, String name, String response) {}
    private final java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(32);
    private final List<Provider> providers;
    public YggdrasilVerifier(List<Provider> providers) {
        this.providers=List.copyOf(providers);
    }
    public Result verify(String username,String serverHash,String requestedIp) {
        if(!permits.tryAcquire()) return new Result(Status.UNAVAILABLE,"",null,"","");
        try { return verifyBounded(username,serverHash,requestedIp); }
        finally { permits.release(); }
    }
    private Result verifyBounded(String username,String serverHash,String requestedIp) {
        if(username==null || !username.matches("[\\p{L}\\p{N}_]{1,16}") || serverHash==null || !serverHash.matches("-?[0-9a-f]{1,40}"))
            return new Result(Status.INVALID,"",null,"","");
        if(requestedIp!=null) {
            if(requestedIp.length()>64 || !requestedIp.matches("[0-9a-fA-F:.]+") || !(requestedIp.contains(":") || requestedIp.contains(".")))
                return new Result(Status.INVALID,"",null,"","");
            if(requestedIp.contains(":")) {
                try { URI.create("http://["+requestedIp+"]/"); }
                catch(IllegalArgumentException e) { return new Result(Status.INVALID,"",null,"",""); }
            } else {
                if(!requestedIp.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) return new Result(Status.INVALID,"",null,"","");
                for(String part:requestedIp.split("\\.")) if(Integer.parseInt(part)>255) return new Result(Status.INVALID,"",null,"","");
            }
        }
        boolean unavailable=false;
        for(Provider provider:providers) {
            URI uri=URI.create(provider.hasJoinedEndpoint()+"?username="+URLEncoder.encode(username,StandardCharsets.UTF_8)
                +"&serverId="+URLEncoder.encode(serverHash,StandardCharsets.UTF_8)
                +(requestedIp==null?"":"&ip="+URLEncoder.encode(requestedIp,StandardCharsets.UTF_8)));
            HttpURLConnection connection=null;
            try {
                long deadline=System.nanoTime()+Duration.ofSeconds(4).toNanos();
                connection=(HttpURLConnection)uri.toURL().openConnection();
                connection.setConnectTimeout(2000); connection.setReadTimeout(2000);
                connection.setInstanceFollowRedirects(false);
                int status=connection.getResponseCode();
                if(status==204 || status==404) continue;
                if(status!=200) { unavailable=true; continue; }
                try(InputStream body=connection.getInputStream(); var bytes=new java.io.ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192]; int count;
                    while((count=body.read(buffer))!=-1) {
                        if(System.nanoTime()>deadline || Thread.currentThread().isInterrupted() || bytes.size()+count>262144)
                            throw new java.io.IOException("Identity provider response exceeds bounds");
                        bytes.write(buffer,0,count);
                    }
                    Result result=validateResponse(provider.id(),username,bytes.toString(StandardCharsets.UTF_8));
                    if(result.status()==Status.VERIFIED) return result;
                    unavailable=true;
                }
            } catch(Exception e) { unavailable=true; }
            finally { if(connection!=null) connection.disconnect(); }
        }
        return new Result(unavailable?Status.UNAVAILABLE:Status.NO_MATCH,"",null,"","");
    }
    static Result validateResponse(String provider,String requestedName,String json) {
        try {
            var object=JsonParser.parseString(json).getAsJsonObject();
            if(!object.has("id") || !object.has("name") || !object.get("id").isJsonPrimitive() || !object.get("name").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("id").isString() || !object.getAsJsonPrimitive("name").isString())
                return new Result(Status.INVALID,provider,null,"","");
            String id=object.get("id").getAsString(), name=object.get("name").getAsString();
            if(!id.matches("[0-9a-fA-F]{32}") || !requestedName.equalsIgnoreCase(name))
                return new Result(Status.INVALID,provider,null,"","");
            UUID uuid=UUID.fromString(id.substring(0,8)+"-"+id.substring(8,12)+"-"+id.substring(12,16)+"-"+id.substring(16,20)+"-"+id.substring(20));
            return new Result(Status.VERIFIED,provider,uuid,name,json);
        } catch(RuntimeException e) { return new Result(Status.INVALID,provider,null,"",""); }
    }
}
