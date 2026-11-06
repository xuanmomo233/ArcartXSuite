package xuanmo.arcartxsuite.auth.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import static java.nio.charset.StandardCharsets.*;

/** A native launcher opens the system browser and owns the registered loopback listener. */
public final class LauncherAuthorizationClient {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final URI origin, redirect;
    private final String clientId;

    public static final class Attempt {
        private final URI authorizationUrl;
        private final String state, verifier;
        private Attempt(URI authorizationUrl, String state, String verifier) {
            this.authorizationUrl=authorizationUrl;
            this.state=state;
            this.verifier=verifier;
        }
        public URI authorizationUrl() { return authorizationUrl; }
    }
    public static final class DeviceAttempt {
        private final URI verificationUrl;
        private final String userCode, deviceCode;
        private final int expiresIn, interval;
        private DeviceAttempt(URI verificationUrl,String userCode,String deviceCode,int expiresIn,int interval) {
            this.verificationUrl=verificationUrl;
            this.userCode=userCode;
            this.deviceCode=deviceCode;
            this.expiresIn=expiresIn;
            this.interval=interval;
        }
        public URI verificationUrl() { return verificationUrl; }
        public String userCode() { return userCode; }
        public int expiresIn() { return expiresIn; }
        public int interval() { return interval; }
    }
    public static final class DeviceResult {
        private final String code, launchToken;
        private final int interval;
        private DeviceResult(String code,String launchToken,int interval) {
            this.code=code; this.launchToken=launchToken; this.interval=interval;
        }
        public String code() { return code; }
        public String launchToken() { return launchToken; }
        public int interval() { return interval; }
    }

    public LauncherAuthorizationClient(URI origin, String clientId, URI redirect) {
        if (!"https".equals(origin.getScheme()) || origin.getHost()==null || origin.getUserInfo()!=null
                || origin.getRawQuery()!=null || origin.getRawFragment()!=null || !origin.getPath().isEmpty()
                || origin.getPort()==0 || origin.getPort()>65535
                || !clientId.matches("[A-Za-z0-9_-]{1,64}")
                || !redirect.toString().matches("http://127\\.0\\.0\\.1:[0-9]{4,5}/[A-Za-z0-9/_-]{1,128}")
                || redirect.getPort()<1024 || redirect.getPort()>65535)
            throw new IllegalArgumentException("Expected HTTPS center and registered literal loopback redirect");
        this.origin=origin;
        this.clientId=clientId;
        this.redirect=redirect;
    }

    public Attempt start() throws IOException {
        String state=random(), verifier=random();
        JsonObject data = new JsonObject();
        data.addProperty("redirectUri",redirect.toString());
        data.addProperty("codeChallenge",challenge(verifier));
        data.addProperty("state",state);
        URI url=URI.create(call("/start",data));
        if (!"https".equals(url.getScheme()) || !Objects.equals(origin.getRawAuthority(),url.getRawAuthority())
                || !"/v1/external/launcher/authorize".equals(url.getRawPath()) || url.getRawFragment()!=null
                || url.getRawQuery()==null || !url.getRawQuery().matches("request=[A-Za-z0-9_-]{43}"))
            throw new IOException("Unexpected authorization URL");
        return new Attempt(url,state,verifier);
    }

    public String exchange(Attempt attempt, URI callback) throws IOException {
        if (callback.getRawFragment()!=null || callback.getRawQuery()==null || callback.getRawQuery().length()>512
                || !Objects.equals(redirect.getScheme(),callback.getScheme())
                || !Objects.equals(redirect.getRawAuthority(),callback.getRawAuthority())
                || !Objects.equals(redirect.getRawPath(),callback.getRawPath())) throw new IOException("Unexpected authorization callback");
        Map<String,String> fields=new HashMap<>();
        for (String entry:callback.getRawQuery().split("&",-1)) {
            int separator=entry.indexOf('=');
            if (separator<1 || fields.putIfAbsent(entry.substring(0,separator),entry.substring(separator+1))!=null)
                throw new IOException("Invalid authorization callback");
        }
        if (!attempt.state.equals(fields.get("state"))) throw new IOException("Authorization state mismatch");
        if (fields.keySet().equals(Set.of("state","error")) && "access_denied".equals(fields.get("error")))
            throw new IOException("Authorization denied");
        if (!fields.keySet().equals(Set.of("state","code")) || !fields.get("code").matches("[A-Za-z0-9_-]{43}"))
            throw new IOException("Invalid authorization code");
        JsonObject data=new JsonObject();
        data.addProperty("redirectUri",redirect.toString());
        data.addProperty("code",fields.get("code"));
        data.addProperty("codeVerifier",attempt.verifier);
        String token=call("/exchange",data);
        if (!token.matches("[A-Za-z0-9_-]{43}")) throw new IOException("Invalid launch token");
        return token;
    }

    /** Call after the player explicitly supplies the code displayed in the running game. */
    public String pair(String launchToken, String userCode) throws IOException {
        JsonObject data=new JsonObject();
        data.addProperty("launchToken",launchToken);
        data.addProperty("userCode",userCode);
        String confirmation=call("/pair",data);
        if (!confirmation.matches("[0-9A-F]{8}")) throw new IOException("Invalid game confirmation");
        return confirmation;
    }
    public DeviceAttempt startDevice() throws IOException {
        try {
            var value=JsonParser.parseString(call("/device/start",new JsonObject())).getAsJsonObject();
            URI url=URI.create(value.get("verificationUrl").getAsString());
            String user=value.get("userCode").getAsString(), device=value.get("deviceCode").getAsString();
            int expiry=value.get("expiresIn").getAsInt(), interval=value.get("interval").getAsInt();
            if (!"https".equals(url.getScheme()) || !Objects.equals(origin.getRawAuthority(),url.getRawAuthority())
                    || !"/v1/external/device".equals(url.getRawPath()) || !("client="+clientId).equals(url.getRawQuery())
                    || url.getRawFragment()!=null || !user.matches("[0-9A-F]{16}") || !device.matches("[A-Za-z0-9_-]{43}")
                    || expiry<1 || expiry>300 || interval<5 || interval>expiry) throw new IOException("Invalid device challenge");
            return new DeviceAttempt(url,user,device,expiry,interval);
        } catch (RuntimeException e) { throw new IOException("Invalid device challenge",e); }
    }

    /** Wait the current interval before each call and honor the returned interval on slow down. */
    public DeviceResult pollDevice(DeviceAttempt attempt) throws IOException {
        try {
            JsonObject data=new JsonObject();
            data.addProperty("deviceCode",attempt.deviceCode);
            var reply=request("/device/poll",data);
            var value=JsonParser.parseString(reply.get("value").getAsString()).getAsJsonObject();
            String code=reply.get("code").getAsString(), token=value.get("launchToken").getAsString();
            int interval=value.get("interval").getAsInt();
            if (!Set.of("OK","AUTHORIZATION_PENDING","SLOW_DOWN","ACCESS_DENIED","EXPIRED_TOKEN").contains(code)
                    || !code.equals(value.get("code").getAsString())
                    || ("OK".equals(code)?!token.matches("[A-Za-z0-9_-]{43}"):!token.isEmpty())
                    || (Set.of("AUTHORIZATION_PENDING","SLOW_DOWN").contains(code)?interval<5:interval!=0))
                throw new IOException("Invalid device poll reply");
            return new DeviceResult(code,token,interval);
        } catch (RuntimeException e) { throw new IOException("Invalid device poll reply",e); }
    }

    public void cancelDevice(DeviceAttempt attempt) throws IOException {
        JsonObject data=new JsonObject(); data.addProperty("deviceCode",attempt.deviceCode);
        call("/device/cancel",data);
    }

    private String call(String path, JsonObject data) throws IOException {
        var reply=request(path,data);
        if (!"OK".equals(reply.get("code").getAsString())) throw new IOException("Launcher request rejected");
        return reply.get("value").getAsString();
    }

    private JsonObject request(String path, JsonObject data) throws IOException {
        String requestId=UUID.randomUUID().toString();
        data.addProperty("version",1);
        data.addProperty("requestId",requestId);
        data.addProperty("clientId",clientId);
        var connection=(HttpURLConnection) origin.resolve("/v1/external/launcher"+path).toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json");
            byte[] body=AuthWire.JSON.toJson(data).getBytes(UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            long deadline=System.nanoTime()+5_000_000_000L;
            try (var output=connection.getOutputStream()) { output.write(body); }
            if (connection.getResponseCode()!=200) throw new IOException("Launcher request failed: HTTP "+connection.getResponseCode());
            try (var input=connection.getInputStream(); var response=new ByteArrayOutputStream()) {
                byte[] bytes=new byte[1024];
                int count;
                while ((count=input.read(bytes))!=-1) {
                    if (count>8192-response.size() || System.nanoTime()-deadline>=0) throw new IOException("Launcher response exceeded limits");
                    response.write(bytes,0,count);
                }
                var reply=JsonParser.parseString(response.toString(UTF_8)).getAsJsonObject();
                if (reply.get("version").getAsInt()!=1 || !requestId.equals(reply.get("requestId").getAsString())
                        || !reply.has("code") || !reply.has("value")) throw new IOException("Invalid launcher response");
                return reply;
            }
        } catch (RuntimeException e) { throw new IOException("Invalid launcher response",e); }
        finally { connection.disconnect(); }
    }

    private static String random() {
        byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(US_ASCII)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
