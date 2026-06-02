package xuanmo.arcartxsuite.qqbot.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.qqbot.config.QQBotBindingConfig;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;

public final class QQBotBindService {

    private final QQBotBindingConfig config;
    private final QQBotRepository repository;

    // 验证码 -> PendingBind
    private final Map<String, PendingBind> pendingCodes = new ConcurrentHashMap<>();

    public QQBotBindService(QQBotBindingConfig config, QQBotRepository repository) {
        this.config = config;
        this.repository = repository;
    }

    public record PendingBind(
        long qqId,
        String playerName,
        long createdAt
    ) {
        public boolean expired(int expireSeconds) {
            return System.currentTimeMillis() - createdAt > expireSeconds * 1000L;
        }
    }

    public record BindResult(boolean success, String message) {}

    /**
     * QQ 群发起绑定请求，生成验证码。
     */
    public BindResult requestBind(long qqId, String playerName) {
        if (!config.enabled()) {
            return new BindResult(false, "绑定功能未启用");
        }

        // 检查 QQ 是否已绑定
        int count = repository.countByQq(qqId);
        if (count >= config.maxBindingsPerQq()) {
            QQBotBinding existing = repository.findByQq(qqId);
            String existingName = existing != null ? existing.playerName() : "未知";
            return new BindResult(false, "该QQ已绑定玩家 " + existingName + "，如需换绑请先解绑");
        }

        // 检查玩家名是否已被绑定
        QQBotBinding byName = repository.findByPlayerName(playerName);
        if (byName != null) {
            return new BindResult(false, "玩家 " + playerName + " 已被 QQ " + byName.qqId() + " 绑定");
        }

        // 生成 6 位验证码
        String code = generateCode();
        pendingCodes.put(code, new PendingBind(qqId, playerName, System.currentTimeMillis()));

        return new BindResult(true, code);
    }

    /**
     * 玩家在游戏内确认绑定。
     */
    public BindResult confirmBind(Player player, String code) {
        if (!config.enabled()) {
            return new BindResult(false, "绑定功能未启用");
        }

        PendingBind pending = pendingCodes.remove(code);
        if (pending == null) {
            return new BindResult(false, "验证码无效或已过期");
        }
        if (pending.expired(config.codeExpireSeconds())) {
            return new BindResult(false, "验证码已过期，请重新获取");
        }
        // 验证玩家名是否匹配
        if (!player.getName().equalsIgnoreCase(pending.playerName())) {
            // 放回验证码
            pendingCodes.put(code, pending);
            return new BindResult(false, "该验证码不是为你生成的");
        }

        // 执行绑定
        repository.insertBinding(pending.qqId(), player.getUniqueId(), player.getName());
        return new BindResult(true, String.valueOf(pending.qqId()));
    }

    /**
     * QQ 群发起解绑。
     */
    public BindResult unbindByQq(long qqId) {
        QQBotBinding binding = repository.findByQq(qqId);
        if (binding == null) {
            return new BindResult(false, "该QQ尚未绑定");
        }
        repository.deleteBindingByQq(qqId);
        return new BindResult(true, binding.playerName());
    }

    /**
     * 玩家在游戏内解绑。
     */
    public BindResult unbindByPlayer(UUID playerUuid) {
        QQBotBinding binding = repository.findByPlayer(playerUuid);
        if (binding == null) {
            return new BindResult(false, "你尚未绑定QQ");
        }
        repository.deleteBindingByPlayer(playerUuid);
        return new BindResult(true, String.valueOf(binding.qqId()));
    }

    /**
     * 游戏内发起绑定请求（为当前玩家生成验证码，需在 QQ 群确认）。
     */
    public BindResult requestBindFromGame(String playerName) {
        if (!config.enabled()) {
            return new BindResult(false, "绑定功能未启用");
        }
        // 检查玩家是否已绑定
        QQBotBinding existing = repository.findByPlayerName(playerName);
        if (existing != null) {
            return new BindResult(false, "你已绑定 QQ " + existing.qqId());
        }
        // 生成验证码（这里的 qqId 暂为 0，需在 QQ 群中通过验证码关联）
        String code = generateCode();
        pendingCodes.put(code, new PendingBind(0L, playerName, System.currentTimeMillis()));
        return new BindResult(true, code);
    }

    /**
     * 通过玩家名解绑。
     */
    public BindResult unbindByPlayer(String playerName) {
        QQBotBinding binding = repository.findByPlayerName(playerName);
        if (binding == null) {
            return new BindResult(false, "该玩家尚未绑定");
        }
        repository.deleteBindingByPlayer(binding.playerUuid());
        return new BindResult(true, String.valueOf(binding.qqId()));
    }

    @Nullable
    public QQBotBinding findByQq(long qqId) {
        return repository.findByQq(qqId);
    }

    @Nullable
    public QQBotBinding findByPlayer(UUID playerUuid) {
        return repository.findByPlayer(playerUuid);
    }

    @Nullable
    public QQBotBinding findByPlayerName(String playerName) {
        return repository.findByPlayerName(playerName);
    }

    public void cleanupExpiredCodes() {
        pendingCodes.entrySet().removeIf(entry ->
            entry.getValue().expired(config.codeExpireSeconds()));
    }

    private String generateCode() {
        int num = ThreadLocalRandom.current().nextInt(100000, 999999);
        String code = String.valueOf(num);
        // 防碰撞
        while (pendingCodes.containsKey(code)) {
            num = ThreadLocalRandom.current().nextInt(100000, 999999);
            code = String.valueOf(num);
        }
        return code;
    }
}
