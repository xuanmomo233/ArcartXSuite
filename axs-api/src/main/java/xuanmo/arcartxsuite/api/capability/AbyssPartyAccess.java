package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * Abyss 队伍系统的公开访问能力（查询 + 编程式管理 + 加入校验钩子）。
 * <p>
 * 供外部玩法插件/其他模块复用 abyss 的队伍数据结构与组队 UI：
 * 公会团本（{@code mode="guild"} 十人队 + 同公会校验）、战区摸金
 * （队伍坐标跟随）等场景经此接口创建自定义模式队伍。
 * <p>
 * 获取方式：模块内 {@code getCapability(AbyssPartyAccess.class)}，
 * 外部插件 {@code AxsCapabilities.get(AbyssPartyAccess.class)}；
 * abyss 模块未启用时返回 null。
 * <p>
 * 生命周期事件（EventBus topic）：
 * <ul>
 *   <li>{@code axs.abyss.party_created} — player=队长，payload: party/mode/max_size</li>
 *   <li>{@code axs.abyss.party_member_join} — player=新成员，payload: party/mode/size</li>
 *   <li>{@code axs.abyss.party_member_leave} — player=离开者（离线时为 null），payload: party/mode/reason(quit|kick)</li>
 *   <li>{@code axs.abyss.party_disbanded} — player=null，payload: party/mode</li>
 *   <li>{@code axs.abyss.party_leader_change} — player=新队长（离线时为 null），payload: party/mode</li>
 * </ul>
 */
@PublicCapability
public interface AbyssPartyAccess {

    // ═══ 查询 ═══

    /** 玩家所在队伍快照；不在队伍中返回 null。 */
    @Nullable PartySnapshot partyOf(@NotNull UUID player);

    /** 玩家是否在队伍中。 */
    boolean isInParty(@NotNull UUID player);

    /** 玩家是否是队长。 */
    boolean isLeader(@NotNull UUID player);

    /** 按队伍 id 查询快照。 */
    @Nullable PartySnapshot getParty(@NotNull UUID partyId);

    /** 组队大厅公开队伍列表。 */
    @NotNull List<PartySnapshot> publicParties();

    // ═══ 编程式管理 ═══

    /**
     * 创建指定模式/人数上限的队伍。
     *
     * @param leader  队长（玩家已有队伍时返回原队快照，不更改模式）
     * @param name    队伍名（null 自动生成）
     * @param mode    队伍模式："free"自由组队（默认）/"guild"公会组队/自定义命名空间；
     *                地牢模板 {@code requirements.party-mode} 可按此值限制进本队伍类型
     * @param maxSize 人数上限（<=0 取全局 party.max-members）
     */
    @NotNull PartySnapshot createParty(@NotNull Player leader, @Nullable String name,
                                       @NotNull String mode, int maxSize);

    /** 解散队伍。 */
    boolean disband(@NotNull UUID partyId);

    /**
     * 直加成员（跳过邀请流程；仍经过该 mode 的 {@link JoinValidator} 与人数上限检查）。
     *
     * @return null=成功；非 null=失败原因（"party not found"/"already in party"/"party full"/校验器拒绝原因）
     */
    @Nullable String addMember(@NotNull UUID partyId, @NotNull UUID player);

    /** 移除成员（支持离线）。 */
    boolean removeMember(@NotNull UUID partyId, @NotNull UUID player);

    /** 转让队长（目标须为成员）。 */
    boolean transferLeader(@NotNull UUID partyId, @NotNull UUID newLeader);

    // ═══ 加入校验钩子 ═══

    /**
     * 按队伍模式注册加入校验器：玩家通过邀请/大厅/审批/直加进入该模式队伍前回调。
     * <p>
     * 公会插件示例：{@code registerJoinValidator("guild", (party, applicant) ->
     *     sameGuild(party.leader(), applicant) ? null : "仅同公会成员可加入")}
     * <p>
     * 同 mode 重复注册覆盖旧值；插件禁用时请 {@link #unregisterJoinValidator}。
     */
    void registerJoinValidator(@NotNull String mode, @NotNull JoinValidator validator);

    /** 注销该模式的加入校验器。 */
    void unregisterJoinValidator(@NotNull String mode);

    /** 加入校验器。 */
    @FunctionalInterface
    interface JoinValidator {
        /**
         * @param party     目标队伍快照
         * @param applicant 待加入玩家
         * @return null=允许加入；非 null=拒绝原因（会发给申请人）
         */
        @Nullable String check(@NotNull PartySnapshot party, @NotNull UUID applicant);
    }

    /**
     * 队伍快照（不可变视图）。
     *
     * @param id        队伍 id
     * @param name      队伍名
     * @param mode      模式（"free"/"guild"/自定义）
     * @param leader    队长 UUID
     * @param members   成员 UUID 列表（含队长，顺序=加入顺序）
     * @param maxSize   人数上限
     * @param dungeonId 目标地牢 id（组队大厅字段，可空）
     */
    record PartySnapshot(
        @NotNull UUID id,
        @NotNull String name,
        @NotNull String mode,
        @NotNull UUID leader,
        @NotNull List<UUID> members,
        int maxSize,
        @Nullable String dungeonId
    ) {
        /** 在线成员 Player 列表。 */
        public @NotNull List<Player> onlineMembers() {
            return members.stream()
                .map(org.bukkit.Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .toList();
        }

        /** 队长是否在线。 */
        public @Nullable Player leaderPlayer() {
            return org.bukkit.Bukkit.getPlayer(leader);
        }
    }
}
