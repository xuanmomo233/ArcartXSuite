package xuanmo.arcartxsuite.abyss.template;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/** Per-template settlement policy; gameplay time limits end before this independent phase. */
public record SettlementConfig(Policy complete,Policy failure) {
    public enum ExitMode { IMMEDIATE,COUNTDOWN,MANUAL }
    public record Policy(ExitMode mode,int seconds,boolean showStats,boolean showAfterExit,
                         int manualTimeoutSeconds,boolean protectPlayers,boolean revivePlayers,int scriptTimeoutSeconds) {
        public long deadline(long now) {
            long delay=mode==ExitMode.IMMEDIATE?0:mode==ExitMode.COUNTDOWN?seconds:manualTimeoutSeconds;
            return mode==ExitMode.MANUAL && delay==0?Long.MAX_VALUE:Math.addExact(now,delay*1000L);
        }
        public long remaining(long now,long deadline) { return deadline==Long.MAX_VALUE?-1:Math.max(0,(deadline-now+999)/1000); }
    }
    public static final SettlementConfig DEFAULT=new SettlementConfig(
        new Policy(ExitMode.COUNTDOWN,60,true,false,0,true,true,30),
        new Policy(ExitMode.COUNTDOWN,10,true,false,0,true,true,30));
    public static SettlementConfig load(@Nullable ConfigurationSection section) {
        if(section==null)return DEFAULT;
        return new SettlementConfig(policy(section,DEFAULT.complete),policy(section.getConfigurationSection("failure"),DEFAULT.failure));
    }
    private static Policy policy(ConfigurationSection section,Policy fallback) {
        if(section==null)return fallback;
        ExitMode mode=ExitMode.valueOf(section.getString("exit-mode",fallback.mode.name()).toUpperCase(Locale.ROOT));
        int seconds=section.getInt("exit-delay-seconds",fallback.seconds),manual=section.getInt("manual-timeout-seconds",fallback.manualTimeoutSeconds);
        int script=section.getInt("script-timeout-seconds",fallback.scriptTimeoutSeconds);
        if(seconds<0 || seconds>86400 || manual<0 || manual>86400)throw new IllegalArgumentException("结算时间必须为0～86400秒");
        if(script<0 || script>600)throw new IllegalArgumentException("结算脚本收尾时间必须为0～600秒");
        return new Policy(mode,seconds,section.getBoolean("show-stats",fallback.showStats),
            section.getBoolean("show-stats-after-exit",fallback.showAfterExit),manual,
            section.getBoolean("protect-players",fallback.protectPlayers),section.getBoolean("revive-players",fallback.revivePlayers),script);
    }
}
