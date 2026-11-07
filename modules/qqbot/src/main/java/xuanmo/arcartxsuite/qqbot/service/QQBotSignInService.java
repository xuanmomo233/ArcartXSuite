package xuanmo.arcartxsuite.qqbot.service;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendResult;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.config.QQBotPrizeConfig;
import xuanmo.arcartxsuite.qqbot.config.QQBotSignInConfig;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.PointAccount;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.MutationStatus;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.Redemption;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.RedemptionItemState;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.RedemptionResolution;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.RedemptionState;

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
        var outcome = repository.signInAtomic(qqId, LocalDate.now().toString(), cfg.basePoints(), cfg.streakBonus(), cfg.maxStreakBonus());
        if (outcome.status() == MutationStatus.ALREADY) {
            return config.messages().signInAlready()
                .replace("{streak}", String.valueOf(outcome.streak()))
                .replace("{balance}", String.valueOf(outcome.balance()));
        }
        if (outcome.status() != MutationStatus.SUCCESS) return "签到未完成，请查询积分和签到状态后重试";
        return config.messages().signInSuccess()
            .replace("{points}", String.valueOf(outcome.points()))
            .replace("{streak}", String.valueOf(outcome.streak()))
            .replace("{balance}", String.valueOf(outcome.balance()));
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
                    .replace("{cost}", String.valueOf(p.currentCost()))
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

        QQBotBinding binding = repository.findByQq(qqId);
        if (binding == null) return config.messages().redeemNotBound();
        String playerName = binding.playerName();
        MailDispatchable mail = mailProvider != null ? mailProvider.get() : null;
        if (mail == null) return "邮件系统暂不可用，本次未扣除积分";
        if (prize.mailPresets() == null || prize.mailPresets().isEmpty()) return "奖品尚未配置有效邮件，本次未扣除积分";
        int actualCost = prize.currentCost();
        boolean discounted = prize.isDiscountActive();
        var reservation = repository.reserveRedemption(qqId, prize.id(), binding.playerUuid(), playerName,
            actualCost, LocalDate.now().toString(), prize.limitPerDay(), prize.mailPresets());
        if (reservation.status() == MutationStatus.LIMIT) return config.messages().redeemLimitReached().replace("{name}", prize.name());
        if (reservation.status() == MutationStatus.INSUFFICIENT) {
            return config.messages().redeemNoPoints()
                .replace("{name}", prize.name())
                .replace("{cost}", String.valueOf(actualCost))
                .replace("{balance}", String.valueOf(repository.getPoints(qqId)));
        }
        if (reservation.status() == MutationStatus.REVIEW && reservation.redemption() != null) {
            return reviewMessage(reservation.redemption().id());
        }
        if (reservation.status() != MutationStatus.SUCCESS || reservation.redemption() == null) {
            return "兑换未完成，请查询积分并联系管理员核对兑换记录";
        }
        Redemption redemption = reservation.redemption();
        try {
            for (var item : redemption.items()) {
                if (!repository.beginRedemptionItem(redemption.id(), item.ordinal())) return reviewMessage(redemption.id());
                try {
                    MailSendResult result = mail.dispatchPreset(MailPresetRequest.builder(item.presetId(), redemption.playerName())
                        .sourceModule("qqbot")
                        .sourceDetail("redeem:" + redemption.id() + ":" + item.ordinal())
                        .build());
                    repository.recordRedemptionItem(redemption.id(), item.ordinal(),
                        result.success() ? RedemptionItemState.SENT : RedemptionItemState.FAILED, result.message());
                } catch (Exception e) {
                    // The remote write may already have succeeded. Keep the debit and freeze this order.
                    repository.recordRedemptionItem(redemption.id(), item.ordinal(), RedemptionItemState.UNKNOWN,
                        "邮件结果未确认：" + e.getClass().getSimpleName());
                    logger.warning("兑换 " + redemption.id() + " 发放待核对: " + e.getClass().getSimpleName());
                    break;
                }
            }
            redemption = repository.finishRedemption(redemption.id());
        } catch (QQBotRepository.StorageFailure e) {
            logger.warning("兑换 " + redemption.id() + " 状态持久化失败，需要人工核对");
            try { repository.freezeRedemption(redemption.id(), "发放调用已结束，但结果持久化未确认，请人工核对"); }
            catch (QQBotRepository.StorageFailure ignored) { /* Durable SENDING remains frozen until ownership recovery. */ }
            return reviewMessage(redemption.id());
        }
        if (redemption.state() == RedemptionState.REFUNDED) return config.messages().redeemMailFailed();
        if (redemption.state() != RedemptionState.COMPLETE) return reviewMessage(redemption.id());
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

    private static String reviewMessage(String id) {
        return "兑换记录 " + id + " 待管理员核对；积分与额度已保留，请勿重复兑换。管理员可使用 /axs qqbot redemptions 查看";
    }

    public List<Redemption> listRedemptions(int limit) { return repository.listRedemptions(limit); }

    public Redemption getRedemption(String id) { return repository.getRedemption(id); }

    public String resolveRedemption(String id, RedemptionResolution resolution, String note) {
        return switch (repository.resolveRedemption(id, resolution, note)) {
            case SUCCESS -> resolution == RedemptionResolution.CONFIRM_DELIVERED ? "已确认发放并结案" : "已确认全部未发并退款";
            case ALREADY -> "该兑换已经结案，未重复处理";
            case NOT_FOUND -> "兑换记录不存在";
            case INVALID -> "请提供有效兑换编号和至少4字的核对说明";
            case REVIEW -> "兑换仍在处理中或已有邮件确认发放，不能全额退款；请核对并补齐后确认发放";
            default -> "处理失败，请重新查询兑换状态后再操作";
        };
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
        var result = repository.transferPointsAtomic(fromQqId, toQqId, amount);
        if (result.status() == MutationStatus.INSUFFICIENT) return "积分不足！你当前只有 " + result.fromBalance() + " 积分";
        if (result.status() != MutationStatus.SUCCESS) return "转账未确认，请查询双方积分后联系管理员核对，勿立即重复转账";
        return "转账成功！向 QQ " + toQqId + " 转账 " + amount + " 积分\n你的剩余积分: " + result.fromBalance()
            + "\n对方当前积分: " + result.toBalance();
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
        long expireAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L;
        var result = repository.createRedPacketAtomic(senderQq, groupId, totalAmount, count, expireAt);
        if (result.status() == MutationStatus.INSUFFICIENT) return "积分不足！你当前只有 " + result.balance() + " 积分";
        if (result.status() != MutationStatus.SUCCESS) return "红包发送未确认，请查询积分与红包后联系管理员核对，勿立即重复发送";
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
