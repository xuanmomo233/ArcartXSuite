package xuanmo.arcartxsuite.auth.minecraft;

import com.google.gson.JsonParser;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Local process trust only. Remote ingress requires a signed forwarding adapter, never this API. */
public final class LocalHandshakeClient {
    private final URI endpoint;
    public LocalHandshakeClient(String gateway) {
        URI base=URI.create(gateway);
        if(!"http".equals(base.getScheme()) || !"127.0.0.1".equals(base.getHost()) || base.getPort()<1 || base.getPort()>65535
                || base.getRawUserInfo()!=null || base.getRawQuery()!=null || base.getFragment()!=null
                || !(base.getPath().isEmpty() || base.getPath().equals("/")))
            throw new IllegalArgumentException("Local handshake gateway must be http://127.0.0.1:port");
        endpoint=base.resolve("/axs-internal/claim-handshake");
    }
    public HandshakeReceipts.Claim claim(UUID profile,String name,UUID connection,String realm) throws Exception {
        var request=new com.google.gson.JsonObject();
        request.addProperty("profile",profile.toString()); request.addProperty("name",name);
        request.addProperty("challenge",connection.toString()); request.addProperty("realm",realm);
        var http=(HttpURLConnection)endpoint.toURL().openConnection();
        http.setConnectTimeout(2000); http.setReadTimeout(2000); http.setInstanceFollowRedirects(false);
        http.setRequestMethod("POST"); http.setDoOutput(true); http.setRequestProperty("Content-Type","application/json");
        byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8); http.setFixedLengthStreamingMode(bytes.length);
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
        try {
            try(var out=http.getOutputStream()) { out.write(bytes); }
            if(http.getResponseCode()!=200) throw new IllegalStateException("Handshake receipt missing or ambiguous; reconnect required");
            try(var input=http.getInputStream(); var response=new java.io.ByteArrayOutputStream()) {
                byte[] chunk=new byte[512]; int count;
                while((count=input.read(chunk))!=-1) {
                    if(System.nanoTime()>deadline || response.size()+count>4096) throw new IllegalArgumentException("Receipt exceeds bounds");
                    response.write(chunk,0,count);
                }
                byte[] reply=response.toByteArray();
                var json=JsonParser.parseString(new String(reply,StandardCharsets.UTF_8)).getAsJsonObject();
                var receipt=new HandshakeReceipts.Receipt(UUID.fromString(json.get("profile").getAsString()),json.get("name").getAsString(),
                    json.get("provider").getAsString(),UUID.fromString(json.get("receiptId").getAsString()),json.get("issuedAt").getAsLong(),json.get("expiresAt").getAsLong());
                long now=System.currentTimeMillis();
                if(!receipt.profile().equals(profile) || !receipt.name().equalsIgnoreCase(name) || receipt.issuedAt()>now+1000
                        || receipt.expiresAt()<=now || receipt.expiresAt()<=receipt.issuedAt() || receipt.expiresAt()-receipt.issuedAt()>30_000
                        || !connection.toString().equals(json.get("challenge").getAsString()) || !realm.equals(json.get("realm").getAsString())
                        || !java.util.Set.of("microsoft", "littleskin", "center").contains(receipt.provider()))
                    throw new IllegalArgumentException("Receipt binding mismatch");
                return new HandshakeReceipts.Claim(receipt,connection,realm);
            }
        } finally { http.disconnect(); }
    }
}
