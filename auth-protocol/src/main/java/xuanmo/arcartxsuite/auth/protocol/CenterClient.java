package xuanmo.arcartxsuite.auth.protocol;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static xuanmo.arcartxsuite.auth.protocol.AuthWire.*;

/** Blocking bounded transport; callers use their bounded worker, never a game thread. */
public final class CenterClient implements AutoCloseable {
    private final URI endpoint;
    private final String node;
    private final byte[] key;
    private volatile boolean closed;
    public CenterClient(String baseUrl,String node,byte[] key) {
        URI base=URI.create(baseUrl);
        if(base.getHost()==null || base.getPort()==0 || base.getPort()>65535 || base.getUserInfo()!=null || base.getRawQuery()!=null || base.getFragment()!=null
                || !(base.getPath().isEmpty() || base.getPath().equals("/"))
                || !("https".equals(base.getScheme()) || "http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost()))
                || node==null || !node.matches("[a-zA-Z0-9_-]{1,64}") || key.length!=32)
            throw new IllegalArgumentException("Center requires HTTPS, or literal IPv4 loopback HTTP, and a valid node identity");
        this.endpoint=base.resolve(PATH); this.node=node; this.key=key.clone();
    }
    public Reply call(Request request) throws IOException {
        if(closed) throw new IOException("Center client closed");
        byte[] body=JSON.toJson(request).getBytes(StandardCharsets.UTF_8);
        if(body.length>32768) { Arrays.fill(body,(byte)0); throw new IOException("Request too large"); }
        HttpURLConnection connection=null;
        long deadline=System.nanoTime()+4_000_000_000L;
        UUID nonce=UUID.randomUUID();
        try {
            connection=(HttpURLConnection)endpoint.toURL().openConnection(Proxy.NO_PROXY);
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(2000); connection.setReadTimeout(2000);
            connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setFixedLengthStreamingMode(body.length);
            long time=System.currentTimeMillis();
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("X-AXS-Node",node); connection.setRequestProperty("X-AXS-Time",Long.toString(time));
            connection.setRequestProperty("X-AXS-Nonce",nonce.toString());
            connection.setRequestProperty("X-AXS-Signature",RpcAuthentication.request(key,node,time,nonce,body));
            try(OutputStream out=connection.getOutputStream()) { out.write(body); }
            if(connection.getResponseCode()!=200) throw new IOException("Center request rejected: HTTP "+connection.getResponseCode());
            byte[] response;
            try(InputStream input=connection.getInputStream(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while((count=input.read(buffer))!=-1) {
                    if(System.nanoTime()-deadline>=0 || count>262144-output.size()) throw new IOException("Response limit exceeded");
                    output.write(buffer,0,count);
                }
                if(System.nanoTime()-deadline>=0) throw new IOException("Center deadline exceeded");
                response=output.toByteArray();
            }
            try {
                if(!RpcAuthentication.matches(RpcAuthentication.response(key,nonce,response),connection.getHeaderField("X-AXS-Signature")))
                    throw new IOException("Center response authentication failed");
                Reply reply=JSON.fromJson(new String(response,StandardCharsets.UTF_8),Reply.class);
                if(reply==null || reply.code()==null) throw new IOException("Invalid center reply");
                return reply;
            } finally { Arrays.fill(response,(byte)0); }
        } catch(RuntimeException e) { throw new IOException("Invalid center response",e); }
        finally { Arrays.fill(body,(byte)0); if(connection!=null) connection.disconnect(); }
    }
    @Override public void close() { closed=true; Arrays.fill(key,(byte)0); }
}
