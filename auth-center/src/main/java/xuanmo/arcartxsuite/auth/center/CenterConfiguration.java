package xuanmo.arcartxsuite.auth.center;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import xuanmo.arcartxsuite.auth.protocol.*;

record CenterConfiguration(int port,Path database,Map<String,AuthWire.Policy> realms,Map<String,Node> nodes,ExternalConfiguration external) {
    record Node(String id,String realm,String audience,byte[] key,Set<String> scopes) {}
    static CenterConfiguration load(Path path) throws Exception {
        if(Files.size(path)>65536) throw new IllegalArgumentException("Center configuration too large");
        JsonObject root=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
        keys(root,Set.of("schemaVersion","port","database","realms","nodes","external"));
        if(integer(root,"schemaVersion")!=1) throw new IllegalArgumentException("Unsupported center schema");
        int port=integer(root,"port");
        if(port<1024 || port>65535) throw new IllegalArgumentException("Invalid center port");
        Path database=path.toAbsolutePath().getParent().resolve(string(root,"database")).normalize();
        if(!database.getFileName().toString().endsWith(".sqlite")) throw new IllegalArgumentException("P3 requires a SQLite database");
        Map<String,AuthWire.Policy> realms=new HashMap<>();
        for(JsonElement element:root.getAsJsonArray("realms")) {
            JsonObject value=element.getAsJsonObject();
            keys(value,Set.of("id","minLength","maxLength","requireTerms","termsVersion","termsLines","requireBinding","timezone","loginTimeoutSeconds","gamePasswordEnabled"));
            String id=identifier(string(value,"id"));
            int min=integer(value,"minLength"),max=integer(value,"maxLength"),timeout=integer(value,"loginTimeoutSeconds");
            boolean terms=bool(value,"requireTerms"),binding=bool(value,"requireBinding");
            String version=string(value,"termsVersion"),timezone=string(value,"timezone");
            java.time.ZoneId.of(timezone);
            List<String> lines=new ArrayList<>();
            for(JsonElement line:value.getAsJsonArray("termsLines")) {
                if(!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid terms");
                lines.add(line.getAsString());
            }
            if(min<8 || max<min || max>128 || timeout<30 || timeout>1800 || version.length()>64 || lines.size()>3
                    || lines.stream().anyMatch(line->line.isBlank() || line.length()>60) || terms && (version.isBlank() || lines.isEmpty()))
                throw new IllegalArgumentException("Invalid realm policy");
            if(realms.putIfAbsent(id,new AuthWire.Policy(min,max,terms,version,lines,binding,timezone,timeout,bool(value,"gamePasswordEnabled"),false))!=null)
                throw new IllegalArgumentException("Duplicate realm");
        }
        Map<String,Node> nodes=new HashMap<>();
        Set<String> nodeKeys=new HashSet<>();
        for(JsonElement element:root.getAsJsonArray("nodes")) {
            JsonObject value=element.getAsJsonObject();
            keys(value,Set.of("id","realm","audience","keyEnvironment","scopes"));
            String id=identifier(string(value,"id")),realm=identifier(string(value,"realm")),audience=identifier(string(value,"audience"));
            String environment=string(value,"keyEnvironment");
            if(!environment.matches("[A-Z][A-Z0-9_]{0,127}")) throw new IllegalArgumentException("Invalid node key environment name");
            Set<String> scopes=new HashSet<>();
            for(JsonElement scope:value.getAsJsonArray("scopes")) scopes.add(scope.getAsString());
            if(!Set.of("auth","binding-read","binding-write","account-revoke").containsAll(scopes) || !scopes.contains("auth") || !realms.containsKey(realm))
                throw new IllegalArgumentException("Invalid node realm or scopes");
            byte[] key=RpcAuthentication.key(System.getenv(environment));
            if(!nodeKeys.add(RpcAuthentication.digest(key))) throw new IllegalArgumentException("Each node requires its own key");
            if(nodes.putIfAbsent(id,new Node(id,realm,audience,key,Set.copyOf(scopes)))!=null)
                throw new IllegalArgumentException("Duplicate node identity");
        }
        if(realms.isEmpty() || realms.size()>64 || nodes.isEmpty() || nodes.size()>64) throw new IllegalArgumentException("Invalid realm/node count");
        var external=ExternalConfiguration.parse(root.get("external"),realms.keySet(),port);
        if(external!=null) realms.replaceAll((realm,policy)->new AuthWire.Policy(policy.minLength(),policy.maxLength(),
            policy.requireTerms(),policy.termsVersion(),policy.termsLines(),policy.requireBinding(),policy.timezone(),
            policy.loginTimeoutSeconds(),policy.gamePasswordEnabled(),external.providers().containsKey(realm)));
        return new CenterConfiguration(port,database,Map.copyOf(realms),Map.copyOf(nodes),external);
    }
    static String identifier(String value) { if(!value.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid identifier"); return value; }
    static void keys(JsonObject value,Set<String> keys) { if(!keys.equals(value.keySet())) throw new IllegalArgumentException("Missing or unknown configuration fields"); }
    static String string(JsonObject value,String key) {
        JsonElement element=value.get(key);
        if(element==null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string");
        return element.getAsString();
    }
    static boolean bool(JsonObject value,String key) {
        if(!value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException("Expected boolean");
        return value.get(key).getAsBoolean();
    }
    static int integer(JsonObject value,String key) {
        if(!value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isNumber() || !value.get(key).getAsString().matches("[0-9]{1,9}"))
            throw new IllegalArgumentException("Expected bounded integer");
        return value.get(key).getAsInt();
    }
}
