package xuanmo.arcartxsuite.auth.center;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.auth.engine.JdbcAuthStore;
import xuanmo.arcartxsuite.auth.protocol.MinecraftSessionProtocol;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static xuanmo.arcartxsuite.auth.protocol.AuthWire.*;

class CenterMinecraftSessionTest {
    private static final String CLIENT = "fixture-launcher";
    private static final String OTHER_CLIENT = "other-launcher";
    private static final String IP = "192.0.2.1";
    private static final String REALM = "realm";
    private static final URI ISSUER = URI.create("https://identity.example.invalid");
    private static final URI ORIGIN = URI.create("https://center.example.invalid");
    private static final URI REDIRECT = URI.create("http://127.0.0.1:43981/callback");
    private static final OidcLogin.Identity IDENTITY = new OidcLogin.Identity(ISSUER.toString(),"enrolled-subject");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    @TempDir Path directory;

    @Test void authenticatedLauncherCanCompleteNativeJoinWithoutAnotherSessionProvider() throws Exception {
        var config = configuration(true);
        var service = service(config);
        var auth = new ExternalAuthorization(config.external(),service);
        try (var server = new ExternalAuthServer(config.external(),service,auth)) {
            server.start();
            int port = server.port();
            assertEquals(200,get(port,MinecraftSessionProtocol.API_ROOT).statusCode());
            String launchToken = authorize(auth,config.external());
            assertEquals(400,exchange(port,OTHER_CLIENT,launchToken).statusCode());
            var nativeCredentials = credentials(exchange(port,CLIENT,launchToken));
            assertEquals(400,exchange(port,CLIENT,launchToken).statusCode());
            assertEquals(ORIGIN.resolve(MinecraftSessionProtocol.API_ROOT).toString(),nativeCredentials.get("sessionApi").getAsString());
            String name = nativeCredentials.get("name").getAsString();
            String id = nativeCredentials.get("profileId").getAsString();
            String access = nativeCredentials.get("accessToken").getAsString();
            assertTrue(MinecraftSessionProtocol.isCenterName(name));
            assertEquals(403,join(port,access,"0".repeat(32),"abcdef").statusCode());
            assertEquals(204,get(port,verify(name,"abcdef",IP)).statusCode(), "failed join cannot register a session");
            assertEquals(204,join(port,access,id,"abcdef").statusCode());
            assertEquals(403,join(port,access,id,"123").statusCode(), "access token is single-use");
            assertEquals(204,get(port,verify("AXS_000000000000","abcdef",IP)).statusCode());
            assertEquals(204,get(port,verify(name,"abcdef","192.0.2.2")).statusCode());
            assertEquals(204,get(port,verify(name,"123",IP)).statusCode());
            var verified = get(port,verify(name,"abcdef",IP));
            assertEquals(200,verified.statusCode());
            var profile = JsonParser.parseString(verified.body()).getAsJsonObject();
            assertEquals(id,profile.get("id").getAsString());
            assertEquals(name,profile.get("name").getAsString());
            assertTrue(profile.getAsJsonArray("properties").isEmpty());
            assertEquals(204,get(port,verify(name,"abcdef",IP)).statusCode(), "handshake cannot be verified twice");
            assertEquals(200,get(port,MinecraftSessionProtocol.PROFILE+id+"?unsigned=false").statusCode());

            var next = credentials(exchange(port,CLIENT,authorize(auth,config.external())));
            assertEquals(id,next.get("profileId").getAsString());
            String newAccess = next.get("accessToken").getAsString();
            assertEquals(403,join(port,newAccess,id,"abcdef").statusCode(), "fresh token cannot re-arm a consumed handshake");
            assertEquals(204,join(port,newAccess,id,"cafe").statusCode());
            var owner = service.externalOwnership(REALM,IDENTITY);
            var data = new JsonObject();
            data.addProperty("account",owner.account().toString());
            var node = config.nodes().get("lobby");
            var context = new Context(REALM,node.audience(),UUID.randomUUID(),"AXSNode",UUID.randomUUID());
            assertTrue(service.execute(node,new Request(1,UUID.randomUUID(),Operation.REVOKE,context,data)).success());
            assertEquals(204,get(port,verify(name,"cafe",IP)).statusCode(), "revocation also applies after join");
        }
    }

    @Test void profilePersistsAcrossCenterRestartsAndDoesNotReuseAnExistingExternalProfile() throws Exception {
        var config = configuration(true);
        var service = service(config);
        UUID oldProfile = UUID.randomUUID();
        var store = new JdbcAuthStore(connections(),REALM);
        var account = store.createIdentity("ExistingOnline",oldProfile,"","oidc");
        try (var c = connections().open()) {
            new GameExternalStore(connections()).link(c,REALM,IDENTITY,account.id());
        }
        var owner = service.externalOwnership(REALM,IDENTITY);
        var nativeIdentity = service.minecraftIdentity(REALM,IDENTITY,owner);
        assertNotEquals(oldProfile,nativeIdentity.profile().id());
        assertNotEquals("ExistingOnline",nativeIdentity.profile().name());
        assertEquals(account.id(),nativeIdentity.owner().account());
        assertThrows(SecurityException.class,() -> service.minecraftIdentity(REALM,IDENTITY,null));
        var restarted = service(config);
        assertEquals(nativeIdentity.profile(),restarted.minecraftIdentity(REALM,IDENTITY,owner).profile());
    }

    @Test void nativeSessionRoutesAreOptIn() throws Exception {
        var config = configuration(false);
        var service = service(config);
        try (var server = new ExternalAuthServer(config.external(),service)) {
            server.start();
            assertEquals(404,get(server.port(),MinecraftSessionProtocol.API_ROOT).statusCode());
            assertEquals(404,get(server.port(),verify("AXS_000000000000","abc",null)).statusCode());
        }
    }

    @Test void authorizationFormsPreserveOriginWithoutLeakingThePageUrl() throws Exception {
        var config = configuration(true);
        try (var server = new ExternalAuthServer(config.external(),service(config))) {
            server.start();
            for (String path : List.of("/v1/external/verify?realm="+REALM, "/v1/external/device?client="+CLIENT)) {
                var response = get(server.port(),path);
                assertEquals(200,response.statusCode());
                assertEquals("strict-origin",response.headers().firstValue("Referrer-Policy").orElseThrow());
            }
            var metadata = get(server.port(),MinecraftSessionProtocol.API_ROOT);
            assertEquals("no-referrer",metadata.headers().firstValue("Referrer-Policy").orElseThrow());
        }
    }

    private String authorize(ExternalAuthorization auth, ExternalConfiguration config) throws Exception {
        String verifier = OidcLogin.random();
        URI start = auth.startLauncher(CLIENT,REDIRECT.toString(),OidcLogin.challenge(verifier),OidcLogin.random());
        var browser = auth.launcherBrowser(parameter(start,"request"));
        String state = parameter(browser.authorizationUrl(),"state");
        try (var oidc = mockStatic(OidcLogin.class)) {
            oidc.when(() -> OidcLogin.exchange(eq(config.providers().get(REALM)),eq(config.url("/callback")),
                eq("issuer-test-code"),anyString(),anyString())).thenReturn(IDENTITY);
            auth.callback(state,browser.cookie(),"issuer-test-code");
        }
        var consent = auth.confirm(state,browser.cookie(),true);
        return auth.exchangeLauncher(CLIENT,REDIRECT.toString(),parameter(consent.redirect(),"code"),verifier);
    }

    private static String parameter(URI uri,String key) {
        for (String field : uri.getRawQuery().split("&")) {
            String[] pair = field.split("=",2);
            if (pair[0].equals(key)) return URLDecoder.decode(pair[1],StandardCharsets.UTF_8);
        }
        throw new AssertionError("Missing test parameter: "+key);
    }

    private CenterConfiguration configuration(boolean minecraftSessions) {
        var nodes = Map.of("lobby",new CenterConfiguration.Node("lobby",REALM,"node",new byte[32],Set.of("auth","account-revoke")));
        var provider = new ExternalConfiguration.Provider(ISSUER,ISSUER.resolve("/authorize"),ISSUER.resolve("/token"),
            "center","fixture","key",null);
        var launchers = Map.of(CLIENT,new ExternalConfiguration.Launcher(CLIENT,REALM,REDIRECT),
            OTHER_CLIENT,new ExternalConfiguration.Launcher(OTHER_CLIENT,REALM,REDIRECT));
        var config = new ExternalConfiguration(0,ORIGIN,Map.of(REALM,provider),launchers,minecraftSessions);
        var policy = new Policy(8,128,false,"",List.of(),false,"UTC",300,false,true);
        return new CenterConfiguration(25600,directory.resolve("test.sqlite"),Map.of(REALM,policy),nodes,config,120000,1000000);
    }
    private JdbcAuthStore.Connections connections() {
        return () -> DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("test.sqlite"));
    }
    private CenterService service(CenterConfiguration config) throws Exception { return new CenterService(config,connections()); }

    private HttpResponse<String> exchange(int port,String client,String launchToken) throws Exception {
        var data = new JsonObject();
        data.addProperty("version",1); data.addProperty("requestId",UUID.randomUUID().toString());
        data.addProperty("clientId",client); data.addProperty("launchToken",launchToken);
        return http.send(request(port,MinecraftSessionProtocol.LAUNCHER_EXCHANGE).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(data))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private static JsonObject credentials(HttpResponse<String> reply) {
        assertEquals(200,reply.statusCode());
        var result = JsonParser.parseString(reply.body()).getAsJsonObject();
        assertEquals("OK",result.get("code").getAsString());
        return JsonParser.parseString(result.get("value").getAsString()).getAsJsonObject();
    }
    private HttpResponse<String> join(int port,String token,String id,String hash) throws Exception {
        var data = new JsonObject();
        data.addProperty("accessToken",token); data.addProperty("selectedProfile",id); data.addProperty("serverId",hash);
        return post(port,MinecraftSessionProtocol.JOIN,data);
    }
    private static String verify(String name,String hash,String ip) {
        return MinecraftSessionProtocol.HAS_JOINED+"?username="+name+"&serverId="+hash+(ip==null?"":"&ip="+ip);
    }
    private HttpResponse<String> get(int port,String path) throws Exception {
        return http.send(request(port,path).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(int port,String path,JsonObject body) throws Exception {
        return http.send(request(port,path).header("Content-Type","application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private static HttpRequest.Builder request(int port,String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .header("X-AXS-Client-IP",IP).timeout(Duration.ofSeconds(6));
    }
}
