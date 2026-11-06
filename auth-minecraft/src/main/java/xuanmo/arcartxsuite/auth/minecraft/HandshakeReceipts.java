package xuanmo.arcartxsuite.auth.minecraft;

import java.time.Clock;
import java.util.*;

/** Short-lived, single-consumer receipts. Multiple distinct pending handshakes fail closed instead of guessing. */
public final class HandshakeReceipts {
    public record Receipt(UUID profile,String name,String provider,UUID receiptId,long issuedAt,long expiresAt) {}
    public record Claim(Receipt receipt,UUID challenge,String realm) {}
    private record Observed(String serverHash,Receipt receipt) {}
    private final Map<UUID,List<Observed>> pending=new HashMap<>();
    private final Map<UUID,Claim> claims=new HashMap<>();
    private final Clock clock;
    public HandshakeReceipts() { this(Clock.systemUTC()); }
    public HandshakeReceipts(Clock clock) { this.clock=clock; }
    private void cleanup() {
        long now=clock.millis();
        pending.values().forEach(list->list.removeIf(item->item.receipt().expiresAt()<=now));
        pending.values().removeIf(List::isEmpty);
        claims.values().removeIf(claim->claim.receipt().expiresAt()<=now);
    }
    public synchronized boolean observe(YggdrasilVerifier.Result verified,String serverHash) {
        if(verified.status()!=YggdrasilVerifier.Status.VERIFIED || verified.profile()==null || serverHash==null)
            throw new IllegalArgumentException("Only a verified handshake can issue a receipt");
        cleanup();
        if(pending.size()+claims.size()>=4096) return false;
        var list=pending.computeIfAbsent(verified.profile(),ignored->new ArrayList<>());
        if(list.stream().anyMatch(item->item.serverHash().equals(serverHash) && item.receipt().provider().equals(verified.provider()))) return true;
        if(list.size()>=4) return false;
        long now=clock.millis();
        list.add(new Observed(serverHash,new Receipt(verified.profile(),verified.name(),verified.provider(),UUID.randomUUID(),now,now+30_000)));
        return true;
    }
    public synchronized Claim claim(UUID profile,String name,UUID connection,String realm) {
        if(profile==null || connection==null || name==null || realm==null || !realm.matches("[a-zA-Z0-9_-]{1,64}")) return null;
        cleanup();
        Claim previous=claims.get(connection);
        if(previous!=null) return previous.receipt().profile().equals(profile) && previous.receipt().name().equalsIgnoreCase(name)
            && previous.realm().equals(realm)?previous:null;
        var list=pending.remove(profile);
        if(list==null || list.size()!=1 || !list.get(0).receipt().name().equalsIgnoreCase(name)) return null;
        Claim claim=new Claim(list.get(0).receipt(),connection,realm); claims.put(connection,claim); return claim;
    }
}
