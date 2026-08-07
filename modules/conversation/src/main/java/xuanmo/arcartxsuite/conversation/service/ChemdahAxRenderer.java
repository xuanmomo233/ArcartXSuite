package xuanmo.arcartxsuite.conversation.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import ink.ptms.chemdah.core.conversation.PlayerReply;
import ink.ptms.chemdah.core.conversation.Session;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.conversation.config.ConversationModuleConfiguration;
import java.util.logging.Logger;

/**
 * Chemdah 会话的 ArcartX UI 渲染器，负责将对话正文与回复列表渲染到客户端对话 UI。
 * <p>
 * 该类维护每个玩家的 {@link DialogState}、显示序号与已关闭会话等生命周期状态，
 * 处理回复选择 / 确认 / 取消、按键导航、UI 打开与关闭等交互，
 * 并通过 {@link PacketBridgeAPI} 向对话 UI 同步 payload。属于 Conversation 模块内部组件，
 * 由 {@link ConversationService} 持有。
 */
final class ChemdahAxRenderer {

    private static final String HANDLER_SYNC = "sync";
    private static final int DIALOG_VISIBLE_ROWS = 6;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConversationModuleConfiguration configuration;
    private final PacketBridgeAPI bridge;
    private final java.util.List<String> dialogUiIds;
    private final ConversationService owner;
    private final ConcurrentMap<UUID, DialogState> dialogStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> displaySequences = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Session> closedSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Session> uiClosedSessions = new ConcurrentHashMap<>();
    private final Set<UUID> dialogOpenedPlayers = ConcurrentHashMap.newKeySet();

    ChemdahAxRenderer(
        JavaPlugin plugin,
        Logger logger,
        ConversationModuleConfiguration configuration,
        PacketBridgeAPI bridge,
        java.util.List<String> dialogUiIds,
        ConversationService owner
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.bridge = bridge;
        this.dialogUiIds = dialogUiIds;
        this.owner = owner;
    }

    /**
     * 渲染对话正文与回复列表到玩家客户端。
     *
     * @param session  Chemdah 会话
     * @param lines    对话正文行
     * @param canReply 是否存在可选回复
     */
    void display(Session session, List<String> lines, boolean canReply) {
        if (session == null || session.getPlayer() == null) {
            return;
        }
        Player player = session.getPlayer();
        if (!player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (isLifecycleClosedSession(playerId, session)) {
            owner.logIgnoredLifecycle(player, "closed-session-display", session, "display");
            return;
        }
        if (!isSessionRenderable(player, session, "closed-session-display")) {
            return;
        }

        List<String> safeLines = ConversationService.safeLines(lines);
        long displaySequence = nextDisplaySequence(playerId);
        CompletableFuture<List<PlayerReply>> replyFuture = checkRepliesSafely(session);
        replyFuture.thenAccept(replies ->
            owner.runSyncIfEnabled("deliver-dialog", () -> deliverDialogState(player, session, safeLines, canReply, replies, displaySequence))
        );
    }

    /**
     * 关闭指定会话对应的对话 UI 并清理状态。
     *
     * @param session 待关闭的 Chemdah 会话
     */
    void close(Session session) {
        if (session == null || session.getPlayer() == null) {
            return;
        }
        Player player = session.getPlayer();
        UUID playerId = player.getUniqueId();
        closedSessions.put(playerId, session);
        DialogState state = dialogStates.get(playerId);
        if (state == null) {
            owner.logIgnoredLifecycle(player, "no-active-dialog", session, "close");
            return;
        }
        if (state.session() != session) {
            owner.logIgnoredLifecycle(player, "session-mismatch", session, "close");
            return;
        }
        displaySequences.merge(playerId, 1L, Long::sum);
        boolean uiAlreadyClosed = uiClosedSessions.remove(playerId, session);
        closeDialogView(player, state, !uiAlreadyClosed, !uiAlreadyClosed, "theme-close");
    }

    /**
     * 处理玩家通过 UI 点击选择某条回复的动作。
     *
     * @param player  玩家
     * @param replyId 回复 id
     */
    void handleSelectReply(Player player, String replyId) {
        DialogState state = dialogStates.get(player.getUniqueId());
        if (state == null || !state.selectReply(replyId)) {
            return;
        }
        syncDialogView(player, state, "select-reply");
    }

    /**
     * 处理玩家确认选择某条回复的动作，触发回复执行。
     *
     * @param player  玩家
     * @param replyId 回复 id，空白表示确认当前选中项
     */
    void handleConfirmReply(Player player, String replyId) {
        DialogState state = dialogStates.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (!replyId.isBlank()) {
            state.selectReply(replyId);
        }
        confirmReply(player, state, "confirm-reply");
    }

    /**
     * 处理玩家取消当前对话的动作，关闭会话。
     *
     * @param player 玩家
     */
    void handleCancelDialog(Player player) {
        DialogState state = dialogStates.get(player.getUniqueId());
        if (state == null) {
            owner.logIgnoredLifecycle(player, "no-active-dialog", null, "cancel");
            return;
        }
        state.session().close(true).exceptionally(throwable -> {
            this.logger.warning("ArcartXConversation 关闭会话失败: " + ConversationService.describeThrowable(throwable));
            return null;
        });
    }

    /**
     * 处理玩家客户端 UI 被关闭的回调，关闭对应会话。
     *
     * @param player 玩家
     */
    void handleUiClosed(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        dialogOpenedPlayers.remove(playerId);
        DialogState state = dialogStates.get(playerId);
        if (state == null) {
            owner.logIgnoredLifecycle(player, "no-active-dialog", null, "ui-close");
            return;
        }
        uiClosedSessions.put(playerId, state.session());
        state.session().close(true).exceptionally(throwable -> {
            this.logger.warning("ArcartXConversation 关闭会话失败: " + ConversationService.describeThrowable(throwable));
            return null;
        });
    }

    /**
     * 处理宿主全局确认按键，确认当前选中的回复。
     *
     * @param player 玩家
     * @return {@code true} 表示存在活跃对话且已处理
     */
    boolean handleConfirmKey(Player player) {
        DialogState dialogState = dialogStates.get(player.getUniqueId());
        if (dialogState == null) {
            return false;
        }
        confirmReply(player, dialogState, "key-confirm");
        return true;
    }

    /**
     * 处理宿主全局导航按键，按偏移量移动回复选中项。
     *
     * @param player 玩家
     * @param delta  移动步数，负数向前、正数向后
     * @return {@code true} 表示存在活跃对话且已处理
     */
    boolean handleNavigationKey(Player player, int delta) {
        DialogState dialogState = dialogStates.get(player.getUniqueId());
        if (dialogState == null) {
            return false;
        }
        if (dialogState.moveSelection(delta)) {
            syncDialogView(player, dialogState, delta < 0 ? "key-prev-reply" : "key-next-reply");
        }
        return true;
    }

    /**
     * 判断指定玩家是否存在活跃的对话状态。
     *
     * @param playerId 玩家 id
     * @return {@code true} 表示存在活跃对话
     */
    boolean hasDialog(UUID playerId) {
        return dialogStates.containsKey(playerId);
    }

    /**
     * 判断指定玩家的对话 UI 是否处于打开状态。
     *
     * @param playerId 玩家 id
     * @return {@code true} 表示对话 UI 已打开
     */
    boolean isDialogOpened(UUID playerId) {
        return dialogOpenedPlayers.contains(playerId);
    }

    /**
     * 统计当前活跃的对话数量。
     *
     * @return 活跃对话数
     */
    int activeConversationCount() {
        return dialogStates.size();
    }

    /**
     * 统计当前已打开对话 UI 的玩家数量。
     *
     * @return 已打开对话 UI 的玩家数
     */
    int openedPlayerCount() {
        return dialogOpenedPlayers.size();
    }

    /**
     * 移除指定玩家的全部对话状态，用于玩家退出等清理场景。
     *
     * @param playerId 玩家 id
     */
    void removePlayer(UUID playerId) {
        dialogStates.remove(playerId);
        displaySequences.remove(playerId);
        closedSessions.remove(playerId);
        uiClosedSessions.remove(playerId);
        dialogOpenedPlayers.remove(playerId);
    }

    /**
     * 关闭指定玩家的对话 UI 视图。
     *
     * @param player 玩家
     * @param reason 关闭原因，用于诊断日志
     */
    void closePlayerView(Player player, String reason) {
        if (player == null) {
            return;
        }
        closeDialogView(player, dialogStates.get(player.getUniqueId()), true, false, reason + "-dialog");
    }

    /**
     * 关闭所有已打开的对话 UI 视图，用于模块停服等场景。
     *
     * @param reason 关闭原因，用于诊断日志
     */
    void closeAllOpenViews(String reason) {
        for (UUID playerId : new ArrayList<>(dialogOpenedPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                closeDialogView(player, dialogStates.get(playerId), true, false, reason + "-dialog");
            }
        }
    }

    /**
     * 清空全部玩家的对话状态，用于模块重置。
     */
    void clear() {
        dialogStates.clear();
        displaySequences.clear();
        closedSessions.clear();
        uiClosedSessions.clear();
        dialogOpenedPlayers.clear();
    }

    /**
     * 返回指定玩家对话状态的简要摘要，用于诊断日志。
     *
     * @param playerId 玩家 id
     * @return 状态摘要字符串
     */
    String stateSummary(UUID playerId) {
        DialogState dialogState = dialogStates.get(playerId);
        String dialogSummary = dialogState == null
            ? "dialog=none"
            : "dialog=replies:" + dialogState.replyIds().size() + ",selected:" + dialogState.selectedReplyId();
        String displaySummary = "displaySeq=" + displaySequences.getOrDefault(playerId, 0L);
        return dialogSummary + " | " + displaySummary;
    }

    private CompletableFuture<List<PlayerReply>> checkRepliesSafely(Session session) {
        CompletableFuture<List<PlayerReply>> fallback = CompletableFuture.completedFuture(List.of());
        try {
            CompletableFuture<List<PlayerReply>> future = session.getConversation().getPlayerSide().checkReply(session);
            if (future == null) {
                return fallback;
            }
            return future.handle((replies, throwable) -> {
                if (throwable != null) {
                    this.logger.warning(
                        "ArcartXConversation 计算回复列表失败，已按无回复继续显示正文: "
                            + ConversationService.describeThrowable(throwable)
                    );
                    return List.of();
                }
                return replies == null ? List.of() : replies;
            });
        } catch (RuntimeException exception) {
            this.logger.warning(
                "ArcartXConversation 启动回复计算失败，已按无回复继续显示正文: "
                    + ConversationService.describeThrowable(exception)
            );
            return fallback;
        }
    }

    private void deliverDialogState(
        Player player,
        Session session,
        List<String> lines,
        boolean canReply,
        List<PlayerReply> replies,
        long displaySequence
    ) {
        if (!player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!isLatestDisplaySequence(playerId, displaySequence)) {
            owner.logIgnoredLifecycle(player, "stale-display-sequence", session, "display");
            return;
        }
        if (isLifecycleClosedSession(playerId, session)) {
            owner.logIgnoredLifecycle(player, "closed-session-display", session, "display");
            return;
        }
        if (!isSessionRenderable(player, session, "closed-session-display")) {
            return;
        }

        DialogState previous = dialogStates.get(playerId);
        List<PlayerReply> safeReplies = replies == null ? List.of() : List.copyOf(replies);
        LinkedHashMap<String, PlayerReply> repliesById = new LinkedHashMap<>();
        LinkedHashMap<String, ReplyView> replyViews = new LinkedHashMap<>();
        List<String> replyIds = new ArrayList<>();
        String preferredReplyId = previous == null ? "" : previous.selectedReplyId();
        if (preferredReplyId.isBlank()) {
            preferredReplyId = findPlayerSelectedReplyId(player, safeReplies);
        }

        for (PlayerReply reply : safeReplies) {
            String replyId = ConversationService.safeString(reply.getRid().toString());
            String replyType = ConversationReplySemanticsSupport.replyType(reply);
            repliesById.put(replyId, reply);
            replyViews.put(replyId, new ReplyView(replyId, ConversationService.safeString(reply.build(session)), replyType));
            replyIds.add(replyId);
        }

        ConversationInteractionStateSupport.SelectionState selectionState =
            ConversationInteractionStateSupport.preserveSelection(replyIds, preferredReplyId);
        DialogState state = new DialogState(
            session,
            ConversationService.safeString(session.getTitle()),
            lines,
            canReply,
            snapshotLinkedHashMap(repliesById),
            snapshotLinkedHashMap(replyViews),
            List.copyOf(replyIds),
            selectionState.selectedId(),
            selectionState.index(),
            previous == null ? 0L : previous.lastReplyActionAt(),
            owner.generation()
        );
        dialogStates.put(playerId, state);
        owner.closeSelectorForDialog(player, "dialog-open");
        syncDialogView(player, state, "theme-display");
    }

    private void confirmReply(Player player, DialogState state, String source) {
        if (!state.canNavigateReplies()) {
            return;
        }
        PlayerReply selectedReply = state.selectedReply();
        if (selectedReply == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - state.lastReplyActionAt() < configuration.interaction().replyDebounceMs()) {
            return;
        }

        state.setLastReplyActionAt(now);
        owner.suppressSelectorOpen(player.getUniqueId(), now);
        if (ConversationReplySemanticsSupport.isTalkType(ConversationReplySemanticsSupport.replyType(selectedReply))) {
            syncDialogView(player, state, source);
        }
        try {
            selectedReply.select(state.session()).exceptionally(throwable -> {
                this.logger.warning("ArcartXConversation 选择回复失败: " + ConversationService.describeThrowable(throwable));
                return null;
            });
        } catch (RuntimeException exception) {
            this.logger.warning("ArcartXConversation 选择回复失败: " + ConversationService.describeThrowable(exception));
        }
    }

    private void syncDialogView(Player player, DialogState state, String source) {
        if (player == null || !player.isOnline() || state == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        boolean needsOpen = !dialogOpenedPlayers.contains(playerId);
        if (needsOpen) {
            logDialogOpenRequest(player, source);
            if (!bridge.openUiAll(player, dialogUiIds)) {
                dialogOpenedPlayers.remove(playerId);
                dialogStates.remove(playerId, state);
                owner.restoreSelectorAfterDialog(player, source + "-dialog-open-failed");
                return;
            }
            dialogOpenedPlayers.add(playerId);
            scheduleDialogSync(playerId, state, source);
            return;
        }

        sendDialogSync(player, state, source);
    }

    private void scheduleDialogSync(UUID playerId, DialogState state, String source) {
        try {
            AxsScheduler.runTaskLater(plugin, () -> {
                if (!plugin.isEnabled()) {
                    return;
                }
                Player player = Bukkit.getPlayer(playerId);
                DialogState currentState = dialogStates.get(playerId);
                if (
                    player == null
                        || !player.isOnline()
                        || currentState != state
                        || !dialogOpenedPlayers.contains(playerId)
                ) {
                    return;
                }
                sendDialogSync(player, currentState, source + "-delayed-sync");
            }, 1L);
        } catch (RuntimeException exception) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                handleDialogSyncFailure(player, source + "-dialog-sync-schedule-failed");
            } else {
                dialogOpenedPlayers.remove(playerId);
                dialogStates.remove(playerId);
            }
            this.logger.warning(
                "ArcartXConversation 延迟同步对话 Menu 失败: " + ConversationService.describeThrowable(exception)
            );
        }
    }

    private void sendDialogSync(Player player, DialogState state, String source) {
        Map<String, Object> payload = buildDialogPayload(state);
        boolean success = bridge.sendPacketToAll(player, dialogUiIds, HANDLER_SYNC, payload);
        owner.logOutboundPacket(player, dialogUiIds.isEmpty() ? "" : dialogUiIds.get(0), HANDLER_SYNC, payload, "reply", state.replyIds().size(), state.selectedReplyId(), source, success);
        if (!success) {
            handleDialogSyncFailure(player, source + "-dialog-sync-failed");
        }
    }

    private void handleDialogSyncFailure(Player player, String reason) {
        UUID playerId = player.getUniqueId();
        dialogOpenedPlayers.remove(playerId);
        dialogStates.remove(playerId);
        bridge.closeUiAll(player, dialogUiIds);
        owner.restoreSelectorAfterDialog(player, reason);
    }

    private void logDialogOpenRequest(Player player, String source) {
        if (!configuration.debug()) {
            return;
        }
        this.logger.info(
            "ArcartXConversation 请求打开对话 Menu\n"
                + "  player: " + player.getName() + "\n"
                + "  ui: " + dialogUiIds + "\n"
                + "  mode: normal\n"
                + "  source: " + source
        );
    }

    private void closeDialogView(
        Player player,
        DialogState state,
        boolean closeUi,
        boolean restoreSelector,
        String reason
    ) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (state == null) {
            dialogStates.remove(playerId);
        } else {
            dialogStates.remove(playerId, state);
        }
        boolean opened = dialogOpenedPlayers.remove(playerId);
        if (closeUi && opened && player.isOnline()) {
            bridge.closeUiAll(player, dialogUiIds);
        }
        if (restoreSelector) {
            owner.restoreSelectorAfterDialog(player, reason);
        }
    }

    private Map<String, Object> buildDialogPayload(DialogState state) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", ConversationService.tokenFor(state.generation()));
        payload.put("packetId", configuration.clientPacketId());
        payload.put("speakerName", state.speakerName());
        payload.put("messageLines", state.messageLines());
        payload.put("hintText", state.canNavigateReplies() ? "NUMPAD_8/2 切换，F 确认" : "当前没有可选回复");
        payload.put("canReply", state.canReply());
        payload.put("selectedReplyId", state.selectedReplyId());
        payload.put("selectedReplyIndex", state.selectedReplyIndex());
        payload.put("replyCount", state.replyIds().size());
        payload.put(
            "replyScrollRatio",
            ConversationInteractionStateSupport.computeScrollRatio(state.replyIds().size(), state.selectedReplyIndex(), DIALOG_VISIBLE_ROWS)
        );
        payload.put("replyRows", buildReplyRows(state));
        return payload;
    }

    private LinkedHashMap<String, Object> buildReplyRows(DialogState state) {
        LinkedHashMap<String, Object> rows = new LinkedHashMap<>();
        for (int index = 0; index < state.replyIds().size(); index++) {
            String replyId = state.replyIds().get(index);
            ReplyView replyView = state.replyViews().get(replyId);
            rows.put(
                ConversationPayloadSupport.rowKey(index),
                ConversationPayloadSupport.flatRow(
                    "id", replyId,
                    "text", replyView == null ? "" : replyView.text(),
                    "replyType", replyView == null ? "" : replyView.replyType(),
                    "selected", replyId.equals(state.selectedReplyId()),
                    "index", index
                )
            );
        }
        return rows;
    }

    private long nextDisplaySequence(UUID playerId) {
        return displaySequences.merge(playerId, 1L, Long::sum);
    }

    private boolean isLatestDisplaySequence(UUID playerId, long displaySequence) {
        return displaySequences.getOrDefault(playerId, 0L) == displaySequence;
    }

    private boolean isSessionRenderable(Player player, Session session, String fallbackReason) {
        if (session == null) {
            owner.logIgnoredLifecycle(player, fallbackReason, null, "display");
            return false;
        }
        if (readSessionBoolean(session, "isClosed", false)) {
            owner.logIgnoredLifecycle(player, "closed-session-display", session, "display");
            return false;
        }
        if (!readSessionBoolean(session, "isValid", true)) {
            owner.logIgnoredLifecycle(player, "invalid-session-display", session, "display");
            return false;
        }
        return true;
    }

    private boolean isLifecycleClosedSession(UUID playerId, Session session) {
        if (playerId == null || session == null) {
            return false;
        }
        return closedSessions.get(playerId) == session;
    }

    private static String findPlayerSelectedReplyId(Player player, List<PlayerReply> replies) {
        for (PlayerReply reply : replies) {
            if (reply.isPlayerSelected(player)) {
                return ConversationService.safeString(reply.getRid().toString());
            }
        }
        return "";
    }

    private static <V> Map<String, V> snapshotLinkedHashMap(LinkedHashMap<String, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static boolean readSessionBoolean(Session session, String methodName, boolean fallback) {
        try {
            if ("isClosed".equals(methodName)) {
                return session.isClosed();
            } else if ("isValid".equals(methodName)) {
                return session.isValid();
            }
            return fallback;
        } catch (Exception | LinkageError ignored) {
            return fallback;
        }
    }

    /**
     * 回复展示视图，承载单条回复的渲染信息。
     *
     * @param replyId   回复 id
     * @param text      回复展示文本
     * @param replyType 回复语义类型（如 talk / close）
     */
    private record ReplyView(String replyId, String text, String replyType) {
    }

    /**
     * 单个玩家的对话状态快照，承载会话、正文、回复列表与选中项等渲染所需信息。
     * <p>
     * 选中项相关字段可变，其余字段不可变；通过 {@link #moveSelection} / {@link #selectReply}
     * 修改选中项，{@link #setLastReplyActionAt} 更新最近操作时间用于防抖。
     */
    private static final class DialogState {

        private final Session session;
        private final String speakerName;
        private final List<String> messageLines;
        private final boolean canReply;
        private final Map<String, PlayerReply> repliesById;
        private final Map<String, ReplyView> replyViews;
        private final List<String> replyIds;
        private final long generation;
        private String selectedReplyId;
        private int selectedReplyIndex;
        private long lastReplyActionAt;

        private DialogState(
            Session session,
            String speakerName,
            List<String> messageLines,
            boolean canReply,
            Map<String, PlayerReply> repliesById,
            Map<String, ReplyView> replyViews,
            List<String> replyIds,
            String selectedReplyId,
            int selectedReplyIndex,
            long lastReplyActionAt,
            long generation
        ) {
            this.session = session;
            this.speakerName = speakerName;
            this.messageLines = messageLines;
            this.canReply = canReply;
            this.repliesById = repliesById;
            this.replyViews = replyViews;
            this.replyIds = replyIds;
            this.selectedReplyId = ConversationService.safeString(selectedReplyId);
            this.selectedReplyIndex = selectedReplyIndex;
            this.lastReplyActionAt = lastReplyActionAt;
            this.generation = generation;
        }

        private Session session() {
            return session;
        }

        private String speakerName() {
            return speakerName;
        }

        private List<String> messageLines() {
            return messageLines;
        }

        private boolean canReply() {
            return canReply;
        }

        private Map<String, ReplyView> replyViews() {
            return replyViews;
        }

        private List<String> replyIds() {
            return replyIds;
        }

        private String selectedReplyId() {
            return selectedReplyId;
        }

        private int selectedReplyIndex() {
            return selectedReplyIndex;
        }

        private long lastReplyActionAt() {
            return lastReplyActionAt;
        }

        private void setLastReplyActionAt(long lastReplyActionAt) {
            this.lastReplyActionAt = lastReplyActionAt;
        }

        private long generation() {
            return generation;
        }

        private boolean canNavigateReplies() {
            return canReply && !replyIds.isEmpty();
        }

        private boolean moveSelection(int delta) {
            if (!canNavigateReplies()) {
                return false;
            }
            int nextIndex = ConversationInteractionStateSupport.wrapIndex(selectedReplyIndex, replyIds.size(), delta);
            if (nextIndex == selectedReplyIndex) {
                return false;
            }
            selectedReplyIndex = nextIndex;
            selectedReplyId = replyIds.get(nextIndex);
            return true;
        }

        private boolean selectReply(String replyId) {
            if (!canNavigateReplies() || replyId == null || replyId.isBlank()) {
                return false;
            }
            int index = replyIds.indexOf(replyId);
            if (index < 0) {
                return false;
            }
            selectedReplyIndex = index;
            selectedReplyId = replyIds.get(index);
            return true;
        }

        private PlayerReply selectedReply() {
            return repliesById.get(selectedReplyId);
        }
    }
}



