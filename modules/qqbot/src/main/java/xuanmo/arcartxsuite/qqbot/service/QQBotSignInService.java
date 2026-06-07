package xuanmo.arcartxsuite.qqbot.service;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.config.QQBotPrizeConfig;
import xuanmo.arcartxsuite.qqbot.config.QQBotSignInConfig;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.PointAccount;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.SignInRecord;

/**
 * QQ 群签到打卡 + 积分 + 兑换商店服务。
 * <p>
 * 全部读写均通过 {@link QQBotRepository}，无 Bukkit 主线程依赖，可在 WS 异步线程安全调用。
 * 兑换奖品通过 {@link MailDispatchable} 邮件预设发放给绑定玩家。
 */
public final class QQBotSignInService {

    private final QQBotConfiguration config;
    private final QQBotRepository repository;
    private final Supplier<MailDispatchable> mailProvider;
    private final Logger logger;

    public QQBotSignInService(
        QQBotConfiguration config,
        QQBotRepository repository,
        Supplier<MailDispatchable> mailProvider,
        Logger logger
    ) {
        this.config = config;
        this.repository = repository;
        this.mailProvider = mailProvider;
        this.logger = logger;
    }

    // ─── 签到 ────────────────────────────────────────────

    /**
     * 执行一次签到。
     *
     * @return 签到结果消息（已套用 messages 模板）
     */
    public String signIn(long qqId) {
        QQBotSignInConfig cfg = config.signin();
        String today = LocalDate.now().toString();

        if (repository.hasSignedOn(qqId, today)) {
            SignInRecord last = repository.getLastSignIn(qqId);
            int streak = last != null ? last.streak() : 1;
            int balance = repository.getPoints(qqId);
            return config.messages().signInAlready()
                .replace("{streak}", String.valueOf(streak))
                .replace("{balance}", String.valueOf(balance));
        }

        // 计算连续签到天数
        int streak = 1;
        SignInRecord last = repository.getLastSignIn(qqId);
        if (last != null) {
            String yesterday = LocalDate.now().minusDays(1).toString();
            if (yesterday.equals(last.date())) {
                streak = last.streak() + 1;
            }
        }

        int bonus = Math.min((streak - 1) * cfg.streakBonus(), cfg.maxStreakBonus());
        int points = cfg.basePoints() + bonus;

        repository.recordSignIn(qqId, today, streak, System.currentTimeMillis());
        int balance = repository.addPoints(qqId, points, "signin", "每日签到 streak=" + streak);

        return config.messages().signInSuccess()
            .replace("{points}", String.valueOf(points))
            .replace("{streak}", String.valueOf(streak))
            .replace("{balance}", String.valueOf(balance));
    }

    // ─── 积分查询 ─────────────────────────────────────────

    public String queryPoints(long qqId) {
        PointAccount acc = repository.getPointAccount(qqId);
        int balance = acc != null ? acc.balance() : 0;
        int earned = acc != null ? acc.totalEarned() : 0;
        int spent = acc != null ? acc.totalSpent() : 0;
        return config.messages().pointsQuery()
            .replace("{balance}", String.valueOf(balance))
            .replace("{earned}", String.valueOf(earned))
            .replace("{spent}", String.valueOf(spent));
    }

    // ─── 兑换商店 ─────────────────────────────────────────

    public String buildShopList(long qqId) {
        List<QQBotPrizeConfig> prizes = config.prizes();
        StringBuilder sb = new StringBuilder();
        sb.append(config.messages().shopHeader());
        if (prizes.isEmpty()) {
            sb.append("\n（暂无可兑换奖品）");
        } else {
            for (QQBotPrizeConfig p : prizes) {
                sb.append("\n").append(config.messages().shopItem()
                    .replace("{id}", p.id())
                    .replace("{name}", p.name())
                    .replace("{cost}", String.valueOf(p.cost()))
                    .replace("{desc}", p.description() == null ? "" : p.description()));
            }
        }
        int balance = repository.getPoints(qqId);
        sb.append("\n").append(config.messages().shopFooter()
            .replace("{balance}", String.valueOf(balance)));
        return sb.toString();
    }

    /**
     * 兑换奖品：校验 → 扣分 → 发邮件 → 失败退款。
     *
     * @return 已套用模板的结果消息
     */
    public String redeem(long qqId, String prizeId) {
        QQBotPrizeConfig prize = config.findPrize(prizeId);
        if (prize == null) {
            return config.messages().redeemNotFound().replace("{id}", prizeId);
        }

        // 解析绑定玩家
        QQBotBinding binding = repository.findByQq(qqId);
        String playerName = binding != null ? binding.playerName() : null;

        if (prize.requireBind() && playerName == null) {
            return config.messages().redeemNotBound();
        }

        // 邮件发放需要玩家名
        if (playerName == null) {
            return config.messages().redeemNotBound();
        }

        int actualCost = prize.currentCost();
        boolean discounted = prize.isDiscountActive();

        // 每日限购
        String today = LocalDate.now().toString();
        if (prize.hasDailyLimit()) {
            int count = repository.countRedeemToday(qqId, prizeId, today);
            if (count >= prize.limitPerDay()) {
                return config.messages().redeemLimitReached().replace("{name}", prize.name());
            }
        }

        // 余额检查
        int balance = repository.getPoints(qqId);
        if (balance < actualCost) {
            return config.messages().redeemNoPoints()
                .replace("{name}", prize.name())
                .replace("{cost}", String.valueOf(actualCost))
                .replace("{balance}", String.valueOf(balance));
        }

        // 扣分（原子操作，余额不足返回 false）
        if (!repository.deductPoints(qqId, actualCost, "redeem", prizeId)) {
            int cur = repository.getPoints(qqId);
            return config.messages().redeemNoPoints()
                .replace("{name}", prize.name())
                .replace("{cost}", String.valueOf(actualCost))
                .replace("{balance}", String.valueOf(cur));
        }

        // 发放邮件
        MailDispatchable mail = mailProvider != null ? mailProvider.get() : null;
        boolean dispatched = false;
        if (mail != null && prize.mailPresetId() != null && !prize.mailPresetId().isBlank()) {
            try {
                dispatched = mail.dispatchPreset(prize.mailPresetId(), playerName, "QQBot-Redeem");
            } catch (Exception e) {
                logger.warning("[QQBot] 兑换邮件发放异常: " + e.getMessage());
            }
        }

        if (!dispatched) {
            // 退款
            int refunded = repository.addPoints(qqId, actualCost, "refund", "兑换失败退款 " + prizeId);
            logger.warning("[QQBot] 兑换 " + prizeId + " 邮件发放失败，已退款 " + actualCost + " 积分给 QQ " + qqId);
            return config.messages().redeemMailFailed();
        }

        // 记录流水
        repository.logRedeem(qqId, prizeId, actualCost, today);
        int remaining = repository.getPoints(qqId);
        String msg = config.messages().redeemSuccess()
            .replace("{name}", prize.name())
            .replace("{player}", playerName)
            .replace("{cost}", String.valueOf(actualCost))
            .replace("{balance}", String.valueOf(remaining));
        if (discounted) {
            msg += "\n（限时折扣中，原价 " + prize.cost() + " 积分）";
        }
        return msg;
    }

    // ─── 积分转账 ─────────────────────────────────────────

    public String transfer(long fromQqId, long toQqId, int amount) {
        if (amount <= 0) {
            return "转账数量必须大于 0";
        }
        if (fromQqId == toQqId) {
            return "不能转账给自己";
        }
        // 检查接收方是否已绑定（即是否有账户记录）
        QQBotBinding toBinding = repository.findByQq(toQqId);
        if (toBinding == null) {
            return "对方尚未绑定游戏账号，无法转账";
        }
        // 先扣减
        if (!repository.deductPoints(fromQqId, amount, "transfer", "转账给 QQ " + toQqId)) {
            int balance = repository.getPoints(fromQqId);
            return "积分不足！你当前只有 " + balance + " 积分";
        }
        // 再增加
        int toBalance = repository.addPoints(toQqId, amount, "transfer", "来自 QQ " + fromQqId + " 的转账");
        int fromBalance = repository.getPoints(fromQqId);
        return "转账成功！向 QQ " + toQqId + " 转账 " + amount + " 积分\n你的剩余积分: " + fromBalance
            + "\n对方当前积分: " + toBalance;
    }

    // ─── 积分排行榜 ───────────────────────────────────────

    public List<PointAccount> leaderboard(int limit) {
        return repository.getPointsLeaderboard(limit);
    }

    // ─── 拼手气红包 ───────────────────────────────────────

    public String sendRedPacket(long senderQq, long groupId, int totalAmount, int count) {
        if (totalAmount <= 0 || count <= 0) {
            return "积分数量和份数必须大于 0";
        }
        if (count > totalAmount) {
            return "份数不能大于总积分（每人至少 1 积分）";
        }
        if (!repository.deductPoints(senderQq, totalAmount, "redpacket", "群发红包")) {
            return "积分不足！你当前只有 " + repository.getPoints(senderQq) + " 积分";
        }
        long expireAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L;
        long id = repository.createRedPacket(senderQq, groupId, totalAmount, count, expireAt);
        if (id < 0) {
            repository.addPoints(senderQq, totalAmount, "redpacket_refund", "红包创建失败退款");
            return "红包创建失败，积分已退还";
        }
        return "🧧 红包已发出！总积分 " + totalAmount + "，共 " + count + " 份\n发送 #抢红包 来抢红包吧~";
    }

    public String grabRedPacket(long claimerQq, long groupId) {
        var rp = repository.getActiveRedPacket(groupId);
        if (rp == null) {
            return "当前群没有可抢的红包";
        }
        int result = repository.tryClaimRedPacket(rp.id(), claimerQq);
        if (result == -2) {
            return "你已经抢过这个红包了";
        }
        if (result == -3) {
            return "红包已过期或已被抢完";
        }
        if (result == -1) {
            return "手慢了，红包已经被抢完了";
        }
        if (result > 0) {
            return "🎉 恭喜你抢到了 " + result + " 积分！（剩余 " + (rp.count() - rp.claimedCount() - 1) + " 份）";
        }
        return "抢红包失败，请重试";
    }

    public void refundExpiredRedPackets() {
        repository.refundExpiredRedPackets();
    }

    // ─── 群活跃度 ─────────────────────────────────────────

    public void recordActivity(long qqId, long groupId) {
        String today = java.time.LocalDate.now().toString();
        repository.recordActivity(qqId, groupId, today);
    }

    public String buildActivityLeaderboard(String mode, int limit) {
        java.time.LocalDate now = java.time.LocalDate.now();
        String start, end;
        if ("week".equalsIgnoreCase(mode) || "本周".equals(mode)) {
            java.time.DayOfWeek dow = now.getDayOfWeek();
            start = now.minusDays(dow.getValue() - 1).toString();
            end = now.toString();
        } else {
            start = now.withDayOfMonth(1).toString();
            end = now.toString();
        }
        var top = repository.getActivityLeaderboard(start, end, limit);
        StringBuilder sb = new StringBuilder();
        sb.append("═══ 群活跃度排行（").append("week".equalsIgnoreCase(mode) || "本周".equals(mode) ? "本周" : "本月").append("）═══");
        if (top.isEmpty()) {
            sb.append("\n（暂无数据）");
        } else {
            int rank = 1;
            for (var rec : top) {
                var binding = repository.findByQq(rec.qqId());
                String name = binding != null ? binding.playerName() : String.valueOf(rec.qqId());
                sb.append("\n").append(rank++).append(". ").append(name)
                    .append(" - ").append(rec.messageCount()).append("条消息");
            }
        }
        return sb.toString();
    }
}
