package xuanmo.arcartxsuite.api.security;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块 UI 动作令牌守卫：为"客户端回包触发写操作"的处理器提供统一 token 机制。
 * <p>
 * 使用模式（与 market 模块既有实现一致）：
 * <ol>
 *   <li>服务端在推送 UI 数据时调用 {@link #issue(UUID, String)} 生成 token，
 *       并通过 packet 字段 {@code action_token} 下发给客户端；</li>
 *   <li>UI 端将 token 存入变量，动作按钮 {@code Packet.send} 时把 token 作为参数回传；</li>
 *   <li>处理器对写操作调用 {@link #begin(UUID, String, String)} 校验 token 并
 *       占用 in-flight 槽位（同玩家同一时间只允许一个在途写操作）；</li>
 *   <li>操作结束（无论成败）调用 {@link #finish(UUID)} 释放 in-flight 槽位，
 *       需要防重放时调用 {@link #rotate(UUID, String)} 并重新下发新 token。</li>
 * </ol>
 * {@code scope} 通常为 UI id，表示 token 的命名空间；同一玩家在不同 UI 上的
 * token 互不影响。
 * <p>
 * 玩家退出时必须调用 {@link #clear(UUID)} 清理其 token 与在途标记；
 * 模块卸载时调用 {@link #clearAll()}。
 * <p>
 * 线程安全：可在主线程与异步线程使用。
 */
public final class ActionTokenGuard {

    private final Map<String, Map<UUID, String>> tokens = new ConcurrentHashMap<>();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * 为玩家在指定 scope 下签发新 token（覆盖旧 token）。
     *
     * @param playerId 玩家 UUID
     * @param scope    token 命名空间（通常为 UI id）
     * @return 新签发的 token 字符串（UUID 形式）
     */
    public String issue(UUID playerId, String scope) {
        String token = UUID.randomUUID().toString();
        scopeMap(scope).put(playerId, token);
        return token;
    }

    /**
     * 校验玩家提交的 token 是否与当前签发值一致。
     *
     * @param playerId 玩家 UUID
     * @param scope    token 命名空间
     * @param supplied 客户端回传的 token
     * @return {@code true} 表示匹配
     */
    public boolean verify(UUID playerId, String scope, String supplied) {
        if (supplied == null || supplied.isEmpty()) {
            return false;
        }
        String expected = scopeMap(scope).get(playerId);
        return expected != null && expected.equals(supplied);
    }

    /**
     * 校验 token 并占用玩家的在途写操作槽位。
     * <p>
     * 返回 {@code true} 后必须配对调用 {@link #finish(UUID)}（建议 finally），
     * 否则该玩家的后续写操作会被 in-flight 拒绝。
     *
     * @param playerId 玩家 UUID
     * @param scope    token 命名空间
     * @param supplied 客户端回传的 token
     * @return {@code true} 表示 token 匹配且成功占用在途槽位
     */
    public boolean begin(UUID playerId, String scope, String supplied) {
        return verify(playerId, scope, supplied) && inFlight.add(playerId);
    }

    /**
     * 释放玩家的在途写操作槽位。
     *
     * @param playerId 玩家 UUID
     */
    public void finish(UUID playerId) {
        inFlight.remove(playerId);
    }

    /**
     * 轮换 token：签发新值使旧 token 立即失效（防重放）。
     *
     * @param playerId 玩家 UUID
     * @param scope    token 命名空间
     * @return 新 token（需重新下发给客户端后才可被使用）
     */
    public String rotate(UUID playerId, String scope) {
        return issue(playerId, scope);
    }

    /**
     * 清理玩家的全部 token 与在途标记（玩家退出时调用）。
     *
     * @param playerId 玩家 UUID
     */
    public void clear(UUID playerId) {
        tokens.values().forEach(map -> map.remove(playerId));
        inFlight.remove(playerId);
    }

    /** 清空所有 token 与在途标记（模块卸载时调用）。 */
    public void clearAll() {
        tokens.clear();
        inFlight.clear();
    }

    private Map<UUID, String> scopeMap(String scope) {
        return tokens.computeIfAbsent(scope, ignored -> new ConcurrentHashMap<>());
    }
}
