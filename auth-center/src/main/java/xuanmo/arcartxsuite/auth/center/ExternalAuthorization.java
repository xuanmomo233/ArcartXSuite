package xuanmo.arcartxsuite.auth.center;

import java.net.URI;
import java.util.*;
import static xuanmo.arcartxsuite.auth.center.OidcLogin.*;

/** Short-lived browser and launcher secrets are kept in memory and invalidated by restart. */
final class ExternalAuthorization {
    private static final int CAPACITY = 1024;
    record LauncherRequest(ExternalConfiguration.Launcher app, String challenge, String clientState) {}
    record BrowserRequest(String realm, GameExternalStore.Pairing pairing, LauncherRequest launcher, Device device,
                          String cookieHash, String nonce, String verifier) {}
    record BrowserConsent(BrowserRequest request, OidcLogin.Identity identity) {}
    record Grant(ExternalConfiguration.Launcher app, OidcLogin.Identity identity, GameExternalStore.Ownership ownership) {}
    private record ExchangeCode(LauncherRequest request, Grant grant) {}
    record DeviceStart(String verificationUrl, String userCode, String deviceCode, int expiresIn, int interval) {}
    record DevicePoll(String code, int interval, String launchToken) {}
    static final class Device {
        final ExternalConfiguration.Launcher app;
        final String userCode;
        final long expires;
        int interval=5;
        long nextPoll=System.nanoTime()+5_000_000_000L;
        boolean browserStarted, closed;
        Grant grant;
        Device(ExternalConfiguration.Launcher app,String userCode,long expires) {
            this.app=app; this.userCode=userCode; this.expires=expires;
        }
    }
    record BrowserStart(URI authorizationUrl, String cookie) {}
    record ConsentResult(URI redirect, boolean approved, String confirmation) {}
    private record Timed<T>(T value, long expires) {}

    private final ExternalConfiguration config;
    private final CenterService center;
    private final Map<String,Timed<LauncherRequest>> starts = new HashMap<>();
    private final Map<String,Timed<BrowserRequest>> browsers = new HashMap<>();
    private final Map<String,Timed<BrowserConsent>> confirmations = new HashMap<>();
    private final Map<String,Timed<ExchangeCode>> codes = new HashMap<>();
    private final Map<String,Timed<Grant>> grants = new HashMap<>();
    private final Map<String,Timed<Device>> devices = new HashMap<>();

    ExternalAuthorization(ExternalConfiguration config, CenterService center) {
        this.config = config;
        this.center = center;
    }

    synchronized URI startLauncher(String id, String redirect, String challenge, String clientState) {
        var app = launcher(id,redirect);
        if (!challenge.matches("[A-Za-z0-9_-]{43}") || !secret(clientState)) throw new IllegalArgumentException("Invalid launcher request");
        String handle = random();
        put(starts,handle,new LauncherRequest(app,challenge,clientState),300_000);
        return URI.create(config.url("/launcher/authorize")+"?request="+handle);
    }

    synchronized BrowserStart launcherBrowser(String handle) {
        var request = take(starts,handle);
        return startBrowser(request.app().realm(),null,request,null);
    }

    BrowserStart gameBrowser(String realm, String code) throws Exception {
        var pairing = pairing(realm,code);
        synchronized(this) { return startBrowser(realm,pairing,null,null); }
    }

    synchronized DeviceStart startDevice(String client) {
        var app=config.launchers().get(client);
        if(app==null) throw new SecurityException("Launcher rejected");
        String token=random(), userCode=GameExternalStore.hash(random()).substring(0,16).toUpperCase(Locale.ROOT);
        put(devices,token,new Device(app,userCode,System.currentTimeMillis()+300_000),300_000);
        return new DeviceStart(config.url("/device")+"?client="+client,userCode,token,300,5);
    }

    synchronized BrowserStart deviceBrowser(String client,String userCode) {
        String code=normalizeCode(userCode);
        long now=System.currentTimeMillis();
        for(var entry:devices.values()) {
            Device device=entry.value();
            if(entry.expires()>now && !device.closed && !device.browserStarted
                    && device.app.id().equals(client) && device.userCode.equals(code)) {
                BrowserStart start=startBrowser(device.app.realm(),null,null,device);
                device.browserStarted=true;
                return start;
            }
        }
        throw new SecurityException("Device authorization expired or already opened");
    }

    synchronized DevicePoll pollDevice(String client,String token) {
        if(!secret(token)) throw new SecurityException("Invalid device token");
        var entry=devices.get(GameExternalStore.hash(token));
        if(entry==null || entry.expires()<=System.currentTimeMillis()) return new DevicePoll("EXPIRED_TOKEN",0,"");
        Device device=entry.value();
        if(!device.app.id().equals(client)) throw new SecurityException("Device client mismatch");
        long now=System.nanoTime();
        if(now-device.nextPoll<0) {
            device.interval+=5;
            device.nextPoll=now+device.interval*1_000_000_000L;
            return new DevicePoll("SLOW_DOWN",device.interval,"");
        }
        device.nextPoll=now+device.interval*1_000_000_000L;
        if(device.closed && device.grant==null) return new DevicePoll("ACCESS_DENIED",0,"");
        if(device.grant==null) return new DevicePoll("AUTHORIZATION_PENDING",device.interval,"");
        String launchToken=issueLaunch(device.grant);
        devices.remove(GameExternalStore.hash(token));
        return new DevicePoll("OK",0,launchToken);
    }

    synchronized void cancelDevice(String client,String token) {
        Device device=peek(devices,token);
        if(!device.app.id().equals(client)) throw new SecurityException("Device client mismatch");
        device.closed=true;
        device.grant=null;
    }

    private BrowserStart startBrowser(String realm, GameExternalStore.Pairing pairing, LauncherRequest launcher, Device device) {
        String state=random(), cookie=random(), nonce=random(), verifier=random();
        put(browsers,state,new BrowserRequest(realm,pairing,launcher,device,GameExternalStore.hash(cookie),nonce,verifier),300_000);
        return new BrowserStart(authorize(config.providers().get(realm),config.url("/callback"),state,nonce,verifier),cookie);
    }

    BrowserConsent callback(String state, String cookie, String code) throws Exception {
        BrowserRequest request = takeBrowser(state,cookie);
        if (code == null || code.isBlank() || code.length() > 2048) throw new SecurityException("Authorization rejected");
        var identity = exchange(config.providers().get(request.realm()),config.url("/callback"),code,request.nonce(),request.verifier());
        var consent = new BrowserConsent(request,identity);
        synchronized(this) { put(confirmations,state,consent,90_000); }
        return consent;
    }

    ConsentResult denyBrowser(String state, String cookie) throws Exception {
        BrowserRequest request = takeBrowser(state,cookie);
        if (request.device()!=null) {
            synchronized(this) {
                var device=request.device();
                if(device.closed || device.expires<=System.currentTimeMillis()) throw new SecurityException("Device authorization expired");
                device.closed=true;
            }
        } else if (request.launcher()!=null) {
            return launcherResult(request.launcher(),"error=access_denied",false);
        } else if (!center.decidePairing(request.pairing(),null,false)) {
            throw new SecurityException("Game request expired");
        }
        return new ConsentResult(null,false,"");
    }

    private synchronized BrowserRequest takeBrowser(String state, String cookie) {
        BrowserRequest request = peek(browsers,state);
        verifyCookie(request,cookie);
        browsers.remove(GameExternalStore.hash(state));
        return request;
    }

    ConsentResult confirm(String state, String cookie, boolean approved) throws Exception {
        BrowserConsent consent;
        synchronized(this) {
            consent = peek(confirmations,state);
            verifyCookie(consent.request(),cookie);
            confirmations.remove(GameExternalStore.hash(state));
        }
        var launcher = consent.request().launcher();
        Device device=consent.request().device();
        if(device!=null) {
            var ownership=approved?center.externalOwnership(device.app.realm(),consent.identity()):null;
            synchronized(this) {
                if(device.closed || device.expires<=System.currentTimeMillis()) throw new SecurityException("Device authorization expired");
                device.closed=true;
                if(approved) device.grant=new Grant(device.app,consent.identity(),ownership);
            }
            return new ConsentResult(null,approved,"");
        }
        if (launcher == null) {
            if (!center.decidePairing(consent.request().pairing(),consent.identity(),approved)) throw new SecurityException("Game request expired");
            return new ConsentResult(null,approved,approved?GameExternalStore.confirmation(consent.request().pairing(),consent.identity()):"");
        }
        String result = "error=access_denied";
        if (approved) {
            var ownership = center.externalOwnership(launcher.app().realm(),consent.identity());
            String code = random();
            synchronized(this) { put(codes,code,new ExchangeCode(launcher,new Grant(launcher.app(),consent.identity(),ownership)),60_000); }
            result = "code=" + code;
        }
        return launcherResult(launcher,result,approved);
    }

    private static ConsentResult launcherResult(LauncherRequest launcher,String result,boolean approved) {
        return new ConsentResult(URI.create(launcher.app().redirect()+"?"+result+"&state="+launcher.clientState()),approved,"");
    }

    synchronized String exchangeLauncher(String id, String redirect, String code, String verifier) {
        var app = launcher(id,redirect);
        var exchange = peek(codes,code);
        if (!exchange.request().app().equals(app) || !verifier.matches("[A-Za-z0-9._~-]{43,128}")
                || !exchange.request().challenge().equals(challenge(verifier))) throw new SecurityException("Launcher exchange rejected");
        String token=issueLaunch(exchange.grant());
        codes.remove(GameExternalStore.hash(code));
        return token;
    }

    private String issueLaunch(Grant grant) {
        String token = random();
        put(grants,token,grant,120_000);
        return token;
    }

    String pairLauncher(String client, String token, String userCode) throws Exception {
        var app = config.launchers().get(client);
        if (app == null) throw new SecurityException("Launcher rejected");
        var pairing = pairing(app.realm(),userCode);
        Grant grant = takeLaunch(app,token);
        if (!center.attachLauncher(pairing,grant.identity(),grant.ownership())) throw new SecurityException("Game authorization rejected");
        return GameExternalStore.confirmation(pairing,grant.identity());
    }

    GameExternalStore.MinecraftIdentity minecraftIdentity(String client, String token) throws Exception {
        var app = config.launchers().get(client);
        if (!config.minecraftSessions() || app == null) throw new SecurityException("Minecraft authorization disabled");
        Grant grant = takeLaunch(app,token);
        return center.minecraftIdentity(app.realm(),grant.identity(),grant.ownership());
    }

    private synchronized Grant takeLaunch(ExternalConfiguration.Launcher app, String token) {
        Grant grant = peek(grants,token);
        if (!grant.app().equals(app)) throw new SecurityException("Launcher rejected");
        grants.remove(GameExternalStore.hash(token));
        return grant;
    }

    private GameExternalStore.Pairing pairing(String realm, String code) throws Exception {
        if (!config.providers().containsKey(realm)) throw new SecurityException("Unknown realm");
        var pairing = center.lookupPairing(realm,normalizeCode(code));
        if (pairing == null) throw new SecurityException("Game code expired or invalid");
        return pairing;
    }
    private static String normalizeCode(String code) { return code.strip().replace("-","").toUpperCase(Locale.ROOT); }

    private ExternalConfiguration.Launcher launcher(String id, String redirect) {
        var app = config.launchers().get(id);
        if (app == null || !app.redirect().toString().equals(redirect)) throw new SecurityException("Launcher redirect rejected");
        return app;
    }

    private static void verifyCookie(BrowserRequest request, String cookie) {
        if (!secret(cookie) || !request.cookieHash().equals(GameExternalStore.hash(cookie))) throw new SecurityException("Browser session mismatch");
    }
    private static boolean secret(String value) { return value != null && value.matches("[A-Za-z0-9_-]{43}"); }

    private static <T> T take(Map<String,Timed<T>> entries, String token) {
        T value = peek(entries,token);
        entries.remove(GameExternalStore.hash(token));
        return value;
    }
    private static <T> T peek(Map<String,Timed<T>> entries, String token) {
        if (!secret(token)) throw new SecurityException("Invalid authorization token");
        var entry = entries.get(GameExternalStore.hash(token));
        if (entry == null || entry.expires() <= System.currentTimeMillis()) throw new SecurityException("Authorization expired");
        return entry.value();
    }
    private static <T> void put(Map<String,Timed<T>> entries, String token, T value, long duration) {
        long now = System.currentTimeMillis();
        entries.values().removeIf(entry -> entry.expires() <= now);
        if (entries.size() >= CAPACITY) throw new IllegalStateException("External authorization capacity reached");
        entries.put(GameExternalStore.hash(token),new Timed<>(value,now+duration));
    }
}
