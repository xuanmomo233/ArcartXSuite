package xuanmo.arcartxsuite.auth.minecraft;

import com.google.gson.*;
import java.io.IOException;
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
                UserPrincipal current = currentUser(temp);
                if (!Files.getOwner(temp).equals(current)) {
                    Files.setOwner(temp, current);
                }
                var posix=Files.getFileAttributeView(temp,PosixFileAttributeView.class);
                var acl=Files.getFileAttributeView(temp,AclFileAttributeView.class);
                if(posix!=null) Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------"));
                else if(acl!=null) acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(Files.getOwner(temp)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
                else throw new IllegalStateException("Filesystem cannot protect proxy private key");
                requirePrivate(temp);
                Files.writeString(temp,json.toString(),StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
                try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)) { channel.force(true); }
                try { Files.createLink(file,temp); }
                catch(FileAlreadyExistsException ignored) { }
            } finally { Files.deleteIfExists(temp); }
        }
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>4096) throw new IllegalStateException("Invalid proxy key file");
        requirePrivate(file);
        var json=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
        var factory=KeyFactory.getInstance("Ed25519");
        var pub=factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(json.get("public").getAsString())));
        var key=factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(json.get("private").getAsString())));
        Signature check=Signature.getInstance("Ed25519"); check.initSign(key); check.update(new byte[]{1,2,3}); byte[] proof=check.sign();
        check.initVerify(pub); check.update(new byte[]{1,2,3});
        if(!check.verify(proof)) throw new IllegalStateException("Proxy key pair mismatch");
        return new KeyPair(pub,key);
    }
    /** Validate restored keys as strictly as newly created material; never silently change existing ACLs. */
    static void requirePrivate(Path file) throws Exception {
        var owner=Files.getOwner(file,LinkOption.NOFOLLOW_LINKS);
        var current=currentUser(file);
        if(!owner.equals(current)) throw new IllegalStateException("Proxy key must belong to the current service account");
        var posix=Files.getFileAttributeView(file,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(posix!=null) {
            var permissions=posix.readAttributes().permissions();
            if(!permissions.equals(PosixFilePermissions.fromString("rw-------")))
                throw new IllegalStateException("Proxy key requires owner-only read/write permissions (0600)");
            return;
        }
        var acl=Files.getFileAttributeView(file,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(acl==null) throw new IllegalStateException("Filesystem cannot verify proxy key permissions");
        requirePrivateAcl(owner,acl.getAcl());
    }
    private static UserPrincipal currentUser(Path file) throws IOException {
        return file.getFileSystem().getUserPrincipalLookupService()
            .lookupPrincipalByName(System.getProperty("user.name"));
    }
    static void requirePrivateAcl(UserPrincipal owner,List<AclEntry> entries) {
        for(var entry:entries) {
            if(entry.type()==AclEntryType.ALLOW && !entry.principal().equals(owner) && !entry.permissions().isEmpty())
                throw new IllegalStateException("Proxy key ACL grants access to another principal");
        }
    }
}
