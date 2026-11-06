package xuanmo.arcartxsuite.auth.minecraft;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** Proxy-only private material. Existing keys are never silently replaced. */
public final class ProxyKeys {
    private ProxyKeys() {}
    public static KeyPair loadOrCreate(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path file=directory.resolve("auth-forwarding-key.json");
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {
            KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            var json=new JsonObject();
            json.addProperty("public",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            json.addProperty("private",Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            Path temp=Files.createTempFile(directory,".auth-key-",".tmp");
            try {
                var posix=Files.getFileAttributeView(temp,PosixFileAttributeView.class);
                var acl=Files.getFileAttributeView(temp,AclFileAttributeView.class);
                if(posix!=null) Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------"));
                else if(acl!=null) acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(Files.getOwner(temp)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
                else throw new IllegalStateException("Filesystem cannot protect proxy private key");
                Files.writeString(temp,json.toString(),StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
                try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)) { channel.force(true); }
                try { Files.createLink(file,temp); }
                catch(FileAlreadyExistsException ignored) { }
            } finally { Files.deleteIfExists(temp); }
        }
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>4096) throw new IllegalStateException("Invalid proxy key file");
        var json=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
        var factory=KeyFactory.getInstance("Ed25519");
        var pub=factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(json.get("public").getAsString())));
        var key=factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(json.get("private").getAsString())));
        Signature check=Signature.getInstance("Ed25519"); check.initSign(key); check.update(new byte[]{1,2,3}); byte[] proof=check.sign();
        check.initVerify(pub); check.update(new byte[]{1,2,3});
        if(!check.verify(proof)) throw new IllegalStateException("Proxy key pair mismatch");
        return new KeyPair(pub,key);
    }
}
