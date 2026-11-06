package xuanmo.arcartxsuite.auth.center;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Protect SQLite, WAL, lock, and journal files through their containing private directory. */
final class PrivateStateDirectory {
    private PrivateStateDirectory() {}
    static void prepare(Path directory) throws Exception {
        boolean existing=Files.exists(directory,LinkOption.NOFOLLOW_LINKS);
        if(!existing) Files.createDirectories(directory);
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Center state must be a real directory");
        var posix=Files.getFileAttributeView(directory,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        var acl=Files.getFileAttributeView(directory,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(posix!=null) {
            if(!existing) posix.setPermissions(PosixFilePermissions.fromString("rwx------"));
            Set<PosixFilePermission> permissions=posix.readAttributes().permissions();
            if(permissions.stream().anyMatch(permission->permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_")))
                throw new IllegalStateException("Existing center state directory must be private to its owner");
        } else if(acl!=null) {
            var owner=Files.getOwner(directory,LinkOption.NOFOLLOW_LINKS);
            if(!existing) acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT).build()));
            for(AclEntry entry:acl.getAcl()) {
                if(entry.type()==AclEntryType.ALLOW && !entry.principal().equals(owner) && !entry.permissions().isEmpty())
                    throw new IllegalStateException("Existing center state directory ACL grants access beyond its owner");
            }
        } else throw new IllegalStateException("Cannot verify private center state directory");
    }
}
