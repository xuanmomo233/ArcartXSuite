package xuanmo.arcartxsuite.abyss.reward;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository.RewardOffer;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.ui.AbyssUiService;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Registration;
import xuanmo.arcartxsuite.api.capability.AbyssRewardGameAccess;
import xuanmo.arcartxsuite.api.item.PendingRewardService;

public final class RewardGames implements AbyssRewardGameAccess {
    private static final Set<String> BUILT_INS = Set.of("flip", "wheel", "choose-three");
    private record Registered(boolean reveal, Strategy strategy) { }
    private record Display(RewardOffer offer, List<String> labels) { }
    private final AbyssService service;
    private final JdbcAbyssRepository repository;
    private final PlayerDataManager database;
    private final PendingRewardService pending;
    private final RewardGameClaims claims;
    private final Map<String, Registered> strategies = new LinkedHashMap<>();
    private final Set<UUID> opening = new HashSet<>();
    private final AtomicBoolean maintenance = new AtomicBoolean();
    private long nextExpiry;

    public RewardGames(AbyssService service, JdbcAbyssRepository repository, PlayerDataManager database, PendingRewardService pending) {
        this.service = service;
        this.repository = repository;
        this.database = database;
        this.pending = pending;
        this.claims = new RewardGameClaims(repository, pending);
    }

    @Override public Registration register(String id, boolean revealUnpicked, Strategy strategy) {
        requireMain();
        if (!service.running()) throw new IllegalStateException("Abyss unavailable");
        if (id == null || !id.matches("[a-z0-9_-]{1,64}") || BUILT_INS.contains(id))
            throw new IllegalArgumentException("Invalid or reserved reward strategy ID");
        var registration = new Registered(revealUnpicked, Objects.requireNonNull(strategy));
        if (strategies.putIfAbsent(id, registration) != null) throw new IllegalStateException("Reward strategy already registered");
        return () -> { requireMain(); strategies.remove(id, registration); };
    }

    public void open(Player player, AbyssInstance instance, String gameId) {
        var game = instance.getTemplate().rewards().games().get(gameId);
        if (game == null) throw new IllegalArgumentException("未知的奖励玩法: " + gameId);
        if (instance.designMode() || !instance.admitted()) throw new IllegalStateException("奖励玩法只用于正式副本");
        UUID playerId = player.getUniqueId();
        UUID offerId = UUID.nameUUIDFromBytes(("abyss-game/" + instance.getInstanceId() + "/" + playerId + "/" + gameId)
            .getBytes(StandardCharsets.UTF_8));
        if (!opening.add(offerId)) return;
        database.submitDatabase(() -> repository.loadRewardOffer(offerId, playerId)).whenComplete((existing, error) ->
            service.runOnMain(() -> {
                if (!service.running() || error != null) {
                    opening.remove(offerId);
                    if (error != null) message(player, "§c奖励读取失败，请稍后重试。");
                    return;
                }
                if (existing != null) {
                    opening.remove(offerId);
                    refresh(player, offerId);
                    return;
                }
                try {
                    var deck = service.rewards().rollGame(player, instance, game);
                    var expires = game.autoAfterSeconds() == 0 ? 0L : System.currentTimeMillis() + game.autoAfterSeconds() * 1000L;
                    var offer = new RewardOffer(offerId, playerId, game.strategy(), game.title(),
                        RewardGameClaims.encode(deck), game.picks(), expires, 0, 0, false);
                    database.submitDatabase(() -> {
                        repository.insertRewardOffer(offer);
                        return display(claims.process(repository.loadRewardOffer(offerId, playerId), System.currentTimeMillis()));
                    }).whenComplete((view, failure) -> service.runOnMain(() -> {
                        opening.remove(offerId);
                        if (failure != null) message(player, "§c奖励玩法保存失败，请稍后重试。");
                        else if (instance.isFinished()) {
                            message(player, "§e奖励玩法已保存，可用 /abyss rewards 打开。");
                        } else present(player, view, false);
                    }));
                } catch (RuntimeException failure) {
                    opening.remove(offerId);
                    message(player, "§c无法生成奖励玩法，请检查奖励池: " + gameId);
                }
            }));
    }

    @Override public void openPending(Player player) {
        requireMain();
        pending(player, true);
    }

    public void remind(Player player) { pending(player, false); }

    private void pending(Player player, boolean open) {
        UUID id = player.getUniqueId();
        database.submitDatabase(() -> {
            Display first = null;
            for (var row : repository.pendingRewardOffers(id, 16)) {
                var updated = claims.process(row, System.currentTimeMillis());
                if (updated != null && !updated.closed() && first == null) first = display(updated);
            }
            return first;
        }).whenComplete((view, error) -> service.runOnMain(() -> {
            if (!service.running() || !player.isOnline()) return;
            flush(player);
            if (error != null) {
                if (open) message(player, "§c奖励读取失败，请稍后重试。");
            } else if (view != null) {
                if (open) present(player, view, false);
                else message(player, "§e你有待领取的副本奖励，使用 /abyss rewards。");
            } else if (open) message(player, "§e没有待领取的奖励玩法。");
        }));
    }

    private void refresh(Player player, UUID offer) {
        UUID id = player.getUniqueId();
        complete(player, database.submitDatabase(() ->
            display(claims.process(repository.loadRewardOffer(offer, id), System.currentTimeMillis()))), false);
    }

    private void complete(Player player, CompletableFuture<Display> result, boolean update) {
        result.whenComplete((view, error) -> service.runOnMain(() -> {
            if (!service.running() || !player.isOnline()) return;
            if (error != null) message(player, "§c奖励尚未确认，请用 /abyss rewards 重试。");
            else {
                flush(player);
                present(player, view, update);
            }
        }));
    }

    private void present(Player player, Display display, boolean update) {
        if (!service.running() || !player.isOnline() || display == null) return;
        var row = display.offer();
        boolean builtIn = BUILT_INS.contains(row.strategy());
        var registered = strategies.get(row.strategy());
        if (!builtIn && registered == null) {
            message(player, "§e该奖励玩法暂不可用，领取资格已保留。");
            return;
        }
        boolean reveal = builtIn ? row.strategy().equals("choose-three") : registered.reveal();
        var cards = new ArrayList<Card>();
        for (int slot = 0; slot < display.labels().size(); slot++) {
            int bit = 1 << slot;
            boolean selected = (row.picked() & bit) != 0;
            cards.add(new Card(slot, reveal || selected ? display.labels().get(slot) : null,
                selected, (row.accepted() & bit) != 0));
        }
        var offer = new Offer(row.id(), row.strategy(), row.title(), row.limit() - Integer.bitCount(row.picked()),
            row.picked(), row.expiresAt(), cards);
        IntConsumer pick = slot -> {
            requireMain();
            if (!service.running() || !player.isOnline() || (!builtIn && strategies.get(row.strategy()) != registered)) return;
            complete(player, database.submitDatabase(() -> {
                var current = repository.loadRewardOffer(row.id(), row.player());
                return current == null ? null : display(claims.choose(current, row.picked(), slot, System.currentTimeMillis()));
            }), true);
        };
        if (builtIn && service.uiFacade() instanceof AbyssUiService ui) ui.openRewardGame(player, offer, pick, update);
        else if (!builtIn) {
            try { registered.strategy().present(player, offer, pick); }
            catch (RuntimeException failure) { message(player, "§c奖励界面打开失败，领取资格已保留。"); }
        } else message(player, "§e奖励玩法界面暂不可用，领取资格已保留。");
    }

    private static Display display(RewardOffer offer) throws Exception {
        return offer == null ? null : new Display(offer,
            RewardGameClaims.decode(offer).stream().map(RewardResolver.GamePrize::label).toList());
    }

    public void expire() {
        long now = System.currentTimeMillis();
        if (now < nextExpiry || !maintenance.compareAndSet(false, true)) return;
        nextExpiry = now + 10000L;
        database.submitMaintenance(() -> {
            var onlineRecipients = new HashSet<UUID>();
            for (var offer : repository.expiredRewardOffers(now, 8)) {
                try {
                    var updated = claims.process(offer, now);
                    if (updated != null && updated.accepted() != offer.accepted()) onlineRecipients.add(offer.player());
                } catch (Exception failure) {
                    java.util.logging.Logger.getLogger("Abyss").warning("Reward game expiry deferred: " + offer.id());
                }
            }
            return onlineRecipients;
        }).whenComplete((recipients, error) -> {
            maintenance.set(false);
            if (recipients != null && !recipients.isEmpty()) service.runOnMain(() -> {
                if (!service.running()) return;
                for (UUID id : recipients) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null && player.isOnline()) flush(player);
                }
            });
        });
    }

    private void flush(Player player) {
        try { pending.flushPlayer(player); }
        catch (RuntimeException failure) { message(player, "§e已登记的奖励将在宿主恢复后补发。"); }
    }

    private static void message(Player player, String text) { if (player.isOnline()) player.sendMessage(text); }
    private static void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Call Abyss reward games on the server thread");
    }
}
