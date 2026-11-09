package xuanmo.arcartxsuite.auth.center;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.function.LongSupplier;

/** Independent source/operation budgets. Starting a new flow cannot spend a callback's allowance. */
final class ExternalRequestBudget {
    private record Window(long started,int count) {}
    private final LinkedHashMap<String,Window> windows=new LinkedHashMap<>(256,0.75f,true);
    private final LongSupplier clock;
    ExternalRequestBudget() { this(System::nanoTime); }
    ExternalRequestBudget(LongSupplier clock) { this.clock=clock; }
    synchronized boolean acquire(String source,String operation,int limit) {
        long now=clock.getAsLong(); String key=source+"/"+operation;
        Window before=windows.get(key);
        if(before==null || now-before.started()>=60_000_000_000L) before=new Window(now,0);
        if(before.count()>=limit) return false;
        windows.put(key,new Window(before.started(),before.count()+1));
        if(windows.size()>8192) windows.remove(windows.keySet().iterator().next());
        return true;
    }
    static String source(String header) {
        // Only the local trusted reverse proxy may supply this overwritten header. Never perform DNS lookup.
        if(header==null || header.length()>45 || !(header.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}")
                || header.contains(":") && header.matches("[0-9a-fA-F:.]+"))) throw new SecurityException("Missing canonical client IP");
        if(!header.contains(":")) for(String part:header.split("\\.")) {
            if(Integer.parseInt(part)>255 || part.length()>1 && part.startsWith("0")) throw new SecurityException("Invalid client IP");
        }
        try { return InetAddress.getByName(header).getHostAddress(); }
        catch(java.net.UnknownHostException e) { throw new SecurityException("Invalid client IP",e); }
    }
}
