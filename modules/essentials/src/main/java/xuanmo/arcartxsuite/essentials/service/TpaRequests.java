package xuanmo.arcartxsuite.essentials.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.essentials.config.EssentialsFeatures.Tpa;

/** 主线程上的待处理请求。每个发送者与目标之间最多一条。 */
final class TpaRequests {
    private final Tpa config;
    private final Map<UUID, Map<UUID, Request>> incoming = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    TpaRequests(Tpa config) {
        this.config = config;
    }

    SendResult send(AuthSnapshot senderAdmission, AuthSnapshot targetAdmission, boolean here, long now, long timeout) {
        UUID sender = senderAdmission.playerId();
        UUID target = targetAdmission.playerId();
        if (cooldowns.getOrDefault(sender, 0L) > now) return SendResult.COOLDOWN;
        List<Request> received = received(target, now);
        boolean replacing = received.stream().anyMatch(request -> request.sender().equals(sender));
        if (!replacing && received.size() >= config.maxIncoming()) return SendResult.INCOMING_FULL;
        if (!replacing && incoming.values().stream().map(requests -> requests.get(sender))
            .filter(request -> request != null && request.expiresAt() > now).count() >= config.maxOutgoing()) {
            return SendResult.OUTGOING_FULL;
        }
        incoming.computeIfAbsent(target, ignored -> new LinkedHashMap<>())
            .put(sender, new Request(senderAdmission, targetAdmission, here, now + timeout));
        cooldowns.put(sender, now + config.cooldownSeconds() * 1000L);
        return SendResult.SENT;
    }

    List<Request> received(UUID target, long now) {
        Map<UUID, Request> requests = incoming.get(target);
        return requests == null ? List.of() : requests.values().stream()
            .filter(request -> request.expiresAt() > now).toList();
    }

    void remove(Request request) {
        Map<UUID, Request> requests = incoming.get(request.target());
        if (requests != null && requests.remove(request.sender(), request) && requests.isEmpty()) {
            incoming.remove(request.target());
        }
    }

    void expire(long now, Consumer<Request> expired) {
        List<Request> requests = incoming.values().stream().flatMap(map -> map.values().stream())
            .filter(request -> request.expiresAt() <= now).toList();
        requests.forEach(request -> {
            remove(request);
            expired.accept(request);
        });
        cooldowns.values().removeIf(until -> until <= now);
    }

    List<Request> cancelOutgoing(UUID sender, UUID target, long now) {
        List<Request> requests = incoming.values().stream().map(map -> map.get(sender))
            .filter(request -> request != null && (target == null || target.equals(request.target()))).toList();
        requests.forEach(this::remove);
        return requests.stream().filter(request -> request.expiresAt() > now).toList();
    }

    void clearIncoming(UUID target) {
        incoming.remove(target);
    }

    void clear() {
        incoming.clear();
        cooldowns.clear();
    }

    enum SendResult { SENT, COOLDOWN, INCOMING_FULL, OUTGOING_FULL }
    record Request(AuthSnapshot senderAdmission, AuthSnapshot targetAdmission, boolean here, long expiresAt) {
        UUID sender() { return senderAdmission.playerId(); }
        UUID target() { return targetAdmission.playerId(); }
    }
}
