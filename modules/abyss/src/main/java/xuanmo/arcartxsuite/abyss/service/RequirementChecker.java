package xuanmo.arcartxsuite.abyss.service;

import java.math.BigDecimal;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.party.Party;
import xuanmo.arcartxsuite.abyss.template.EntryRequirements;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;

/**
 * 进本条件检查：人数/队伍/等级/冷却/货币/体力/权限/物品/条件表达式。
 */
public final class RequirementChecker {

    /** 检查结果。 */
    public record CheckResult(boolean ok, @Nullable String reason) {
        static CheckResult pass() { return new CheckResult(true, null); }
        static CheckResult fail(String r) { return new CheckResult(false, r); }
    }

    private final ScriptConditionEvaluator evaluator;
    private final CurrencyBridgeAPI currencyManager;
    private final xuanmo.arcartxsuite.abyss.stamina.StaminaManager stamina;
    private final CooldownManager cooldown;
    private final xuanmo.arcartxsuite.abyss.reward.RewardResolver rewards;

    public RequirementChecker(ScriptConditionEvaluator evaluator,
                              CurrencyBridgeAPI currencyManager,
                              xuanmo.arcartxsuite.abyss.stamina.StaminaManager stamina,
                              CooldownManager cooldown,
                              xuanmo.arcartxsuite.abyss.reward.RewardResolver rewards) {
        this.evaluator = evaluator;
        this.currencyManager = currencyManager;
        this.stamina = stamina;
        this.cooldown = cooldown;
        this.rewards = rewards;
    }

    /**
     * 检查玩家（及队伍）是否满足进本条件。队伍进入时逐人校验。
     *
     * @param members 参与进入的玩家列表
     * @return 第一个失败原因；通过返回 ok
     */
    public CheckResult check(AbyssTemplate template, List<Player> members,
                             @Nullable Party party) {
        EntryRequirements r = template.requirements();
        int n = members.size();
        if (n < r.minPlayers()) {
            return CheckResult.fail("人数不足（需要 " + r.minPlayers() + "，当前 " + n + "）");
        }
        if (n > r.maxPlayers()) {
            return CheckResult.fail("人数超出（最多 " + r.maxPlayers() + "，当前 " + n + "）");
        }
        if (r.partyRequired() && (party == null || party.size() < r.minPlayers())) {
            return CheckResult.fail("该地牢需要组队进入");
        }
        // 队伍模式限制（公会团本等）：party-mode 要求队伍 mode 匹配，自由队/单人队被拒
        if (r.partyMode() != null) {
            if (party == null) {
                return CheckResult.fail("该地牢需要 " + r.partyMode() + " 模式队伍进入");
            }
            if (!r.partyMode().equalsIgnoreCase(party.mode())) {
                return CheckResult.fail("该地牢仅接受 " + r.partyMode()
                    + " 模式队伍（当前队伍模式: " + party.mode() + "）");
            }
        }
        // 逐人校验；require-all-players=false 时只查队长（队伍为发起者或首个玩家）
        List<Player> toCheck = members;
        if (!r.requireAllPlayers()) {
            toCheck = List.of(members.get(0));
        }
        for (Player p : toCheck) {
            CheckResult single = checkSingle(template, p);
            if (!single.ok()) {
                return CheckResult.fail(p.getName() + ": " + single.reason());
            }
        }
        // 队长专属条件（有队伍取队长，否则取发起者）
        if (!r.leaderOnlyConditions().isEmpty()) {
            Player leader = members.get(0);
            if (party != null) {
                Player lp = Bukkit.getPlayer(party.leader());
                if (lp != null) leader = lp;
            }
            var failed = evaluator.firstFailed(leader, r.leaderOnlyConditions());
            if (failed != null) {
                return CheckResult.fail("队长条件不满足: "
                    + (failed.raw() != null ? failed.raw() : failed.serialize()));
            }
        }
        return CheckResult.pass();
    }

    private CheckResult checkSingle(AbyssTemplate template, Player p) {
        EntryRequirements r = template.requirements();
        // 等级
        if (r.minLevel() > 0 && p.getLevel() < r.minLevel()) {
            return CheckResult.fail("等级不足 " + r.minLevel());
        }
        if (r.maxLevel() > 0 && p.getLevel() > r.maxLevel()) {
            return CheckResult.fail("等级超出上限 " + r.maxLevel());
        }
        // 冷却
        if (r.cooldownSeconds() > 0) {
            long left = cooldown.remaining(p.getUniqueId(), template.id());
            if (left > 0) {
                return CheckResult.fail("冷却中，还需 " + left + " 秒");
            }
        }
        // 权限
        for (String perm : r.requiredPermissions()) {
            if (!p.hasPermission(perm)) {
                return CheckResult.fail("缺少权限 " + perm);
            }
        }
        // 货币（检查余额，扣款在进入成功后进行）
        if (r.moneyCost() > 0) {
            var bridge = currencyManager.bridge(r.currencyId());
            if (bridge == null || !bridge.available()
                || bridge.balance(p).compareTo(BigDecimal.valueOf(r.moneyCost())) < 0) {
                return CheckResult.fail("货币不足（需要 " + r.moneyCost() + " " + r.currencyId() + "）");
            }
        }
        // 体力
        if (r.staminaCost() > 0 && stamina.getStamina(p.getUniqueId()) < r.staminaCost()) {
            return CheckResult.fail("体力不足（需要 " + r.staminaCost()
                + "，当前 " + stamina.getStamina(p.getUniqueId()) + "）");
        }
        // 物品
        for (EntryRequirements.RequiredItem item : r.requiredItems()) {
            if (!hasEnough(p, item)) {
                return CheckResult.fail("缺少物品 " + item.id() + " x" + item.amount());
            }
        }
        // 条件表达式（PAPI/ARIA/JS/CONTEXT）
        var failed = evaluator.firstFailed(p, r.conditions());
        if (failed != null) {
            return CheckResult.fail("条件不满足: " + (failed.raw() != null ? failed.raw() : failed.serialize()));
        }
        return CheckResult.pass();
    }

    private boolean hasEnough(Player p, EntryRequirements.RequiredItem item) {
        int found = 0;
        String def = item.source() + ":" + item.id();
        for (var it : p.getInventory().getContents()) {
            if (it != null && rewards.matches(def, it)) {
                found += it.getAmount();
                if (found >= item.amount()) return true;
            }
        }
        return false;
    }

    /**
     * 扣费（进入世界成功后调用）：货币 + 体力 + 消耗物品 + 记录冷却。
     */
    public void charge(AbyssTemplate template, List<Player> members) {
        EntryRequirements r = template.requirements();
        for (Player p : members) {
            if (r.moneyCost() > 0) {
                var bridge = currencyManager.bridge(r.currencyId());
                if (bridge != null && bridge.available()) {
                    bridge.withdraw(p, BigDecimal.valueOf(r.moneyCost()));
                }
            }
            if (r.staminaCost() > 0) {
                stamina.consume(p.getUniqueId(), r.staminaCost());
            }
            for (EntryRequirements.RequiredItem item : r.requiredItems()) {
                rewards.takeItem(p, item.source() + ":" + item.id() + ":" + item.amount());
            }
            if (r.cooldownSeconds() > 0) {
                cooldown.setCooldown(p.getUniqueId(), template.id(), r.cooldownSeconds());
            }
        }
    }
}
