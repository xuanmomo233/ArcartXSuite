package xuanmo.arcartxsuite.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.symphony.api.attribute.AttributeKey;
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier;
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation;
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey;
import priv.seventeen.artist.symphony.api.damage.DamageService;
import priv.seventeen.artist.symphony.api.level.LevelService;
import priv.seventeen.artist.symphony.api.level.LevelSnapshot;
import priv.seventeen.artist.symphony.api.power.CombatPowerSnapshot;
import priv.seventeen.artist.symphony.api.service.AuraView;
import priv.seventeen.artist.symphony.api.service.CombatStateView;
import priv.seventeen.artist.symphony.api.service.MetadataService;
import priv.seventeen.artist.symphony.api.service.RuntimeStatusView;
import priv.seventeen.artist.symphony.api.service.SymphonyApi;
import priv.seventeen.artist.symphony.api.source.AttributeSourceService;
import xuanmo.arcartxsuite.api.attribute.SymphonyAura;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge;
import xuanmo.arcartxsuite.api.attribute.SymphonyCombatPower;
import xuanmo.arcartxsuite.api.attribute.SymphonyCombatState;
import xuanmo.arcartxsuite.api.attribute.SymphonyLevelSnapshot;
import xuanmo.arcartxsuite.api.attribute.SymphonyStatusEffect;

/**
 * {@link SymphonyBridge} 的默认实现，桥接 Symphony 属性插件。
 * <p>
 * Symphony 为可选依赖，运行时通过插件管理器检测可用性。
 * 通过 {@link SymphonyApi} 接口（ServicesManager 定位）操作属性，
 * 使用 {@link AttributeSourceService#replaceSource} / {@link AttributeSourceService#removeSource} 管理属性来源。
 */
public final class DefaultSymphonyBridge implements SymphonyBridge {

    private final JavaPlugin plugin;

    private boolean available;
    private SymphonyApi symphonyApi;
    private AttributeSourceService sourceService;
    private DamageService damageService;
    private LevelService levelService;
    private MetadataService metadataService;

    public DefaultSymphonyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reset();

        Plugin symphony = Bukkit.getPluginManager().getPlugin("Symphony");
        if (symphony == null || !symphony.isEnabled()) {
            return;
        }

        try {
            symphonyApi = Bukkit.getServicesManager().load(SymphonyApi.class);
            if (symphonyApi == null) {
                return;
            }
            sourceService = symphonyApi.getSources();
            if (sourceService == null) {
                return;
            }
            damageService = symphonyApi.getDamage();
            levelService = symphonyApi.getLevels();
            metadataService = symphonyApi.getMetadata();
            available = true;
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[Symphony] 桥接初始化成功");
        } catch (Exception | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Symphony] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override public boolean available() { return available; }

    // ─── 属性写操作 ──────────────────────────────────────────

    @Override
    public void setAttribute(Player player, String attributeId, boolean percent, double value, String sourceKey) {
        if (!available || player == null || attributeId == null || sourceKey == null) return;
        try {
            AttributeKey key = parseAttributeKey(attributeId);
            AttributeOperation operation = percent ? AttributeOperation.MULTIPLY_TOTAL : AttributeOperation.ADD;
            AttributeModifier modifier = new AttributeModifier(
                sourceKey, key, operation, value, 0, false, null, null
            );
            AttributeSourceKey source = parseSourceKey(sourceKey);
            sourceService.replaceSource(player, source, Collections.singletonList(modifier));
        } catch (Exception | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Symphony] setAttribute 失败: " + e.getMessage());
        }
    }

    @Override
    public void removeAttribute(Player player, String sourceKey) {
        if (!available || player == null || sourceKey == null) return;
        try {
            AttributeSourceKey source = parseSourceKey(sourceKey);
            sourceService.removeSource(player, source);
        } catch (Exception | LinkageError ignored) {
        }
    }

    @Override
    public void recalculate(Player player) {
        if (!available || player == null) return;
        try {
            symphonyApi.getAttributes().recalculate(player);
        } catch (Exception | LinkageError ignored) {
        }
    }

    // ─── 属性查询 ────────────────────────────────────────────

    @Override
    public double attributeValue(LivingEntity entity, String attributeId) {
        if (!available || entity == null || attributeId == null) return 0.0;
        try {
            AttributeKey key = parseAttributeKey(attributeId);
            return symphonyApi.getAttributes().value(entity, key);
        } catch (Exception | LinkageError e) {
            return 0.0;
        }
    }

    // ─── 战力查询 ────────────────────────────────────────────

    @Override
    public SymphonyCombatPower combatPower(LivingEntity entity) {
        if (!available || entity == null) return null;
        try {
            CombatPowerSnapshot snapshot = symphonyApi.getCombatPower().snapshot(entity);
            return new SymphonyCombatPower(
                snapshot.getRawValue(),
                snapshot.getValue(),
                snapshot.getFormatted(),
                snapshot.getCalculatedAtMillis(),
                snapshot.getError()
            );
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    // ─── 等级查询 ────────────────────────────────────────────

    @Override
    public SymphonyLevelSnapshot level(LivingEntity entity) {
        if (!available || entity == null || levelService == null) return null;
        try {
            LevelSnapshot snapshot = levelService.snapshot(entity);
            if (snapshot == null) return null;
            return new SymphonyLevelSnapshot(
                snapshot.getProviderName(),
                snapshot.getLevel(),
                snapshot.getExperience(),
                snapshot.getExperienceForNextLevel(),
                snapshot.getCharacterId(),
                snapshot.getCharacterName()
            );
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    // ─── 护盾 ────────────────────────────────────────────────

    @Override
    public double shield(LivingEntity entity) {
        if (!available || entity == null || damageService == null) return 0.0;
        try {
            return damageService.shield(entity);
        } catch (Exception | LinkageError e) {
            return 0.0;
        }
    }

    @Override
    public double setShield(LivingEntity entity, double amount) {
        if (!available || entity == null || damageService == null) return 0.0;
        try {
            return damageService.setShield(entity, amount);
        } catch (Exception | LinkageError e) {
            return 0.0;
        }
    }

    // ─── 战斗状态 ────────────────────────────────────────────

    @Override
    public SymphonyCombatState combatState(LivingEntity entity) {
        if (!available || entity == null || metadataService == null) {
            return new SymphonyCombatState(false, 0L);
        }
        try {
            CombatStateView view = metadataService.combatState(entity);
            return new SymphonyCombatState(view.getActive(), view.getRemainingMillis());
        } catch (Exception | LinkageError e) {
            return new SymphonyCombatState(false, 0L);
        }
    }

    @Override
    public List<SymphonyStatusEffect> statuses(LivingEntity entity) {
        if (!available || entity == null || metadataService == null) return Collections.emptyList();
        try {
            List<RuntimeStatusView> views = metadataService.statuses(entity);
            if (views == null || views.isEmpty()) return Collections.emptyList();
            List<SymphonyStatusEffect> result = new ArrayList<>(views.size());
            for (RuntimeStatusView view : views) {
                result.add(new SymphonyStatusEffect(
                    view.getId().toString(),
                    view.getStacks(),
                    view.getRemainingMillis()
                ));
            }
            return result;
        } catch (Exception | LinkageError e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<SymphonyAura> auras(LivingEntity entity) {
        if (!available || entity == null || metadataService == null) return Collections.emptyList();
        try {
            List<AuraView> views = metadataService.auras(entity);
            if (views == null || views.isEmpty()) return Collections.emptyList();
            List<SymphonyAura> result = new ArrayList<>(views.size());
            for (AuraView view : views) {
                result.add(new SymphonyAura(
                    view.getChannel(),
                    view.getGauge(),
                    view.getRemainingMillis()
                ));
            }
            return result;
        } catch (Exception | LinkageError e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Integer> activeSets(LivingEntity entity) {
        if (!available || entity == null || metadataService == null) return Collections.emptyMap();
        try {
            Map<NamespacedKey, Integer> raw = metadataService.activeSets(entity);
            if (raw == null || raw.isEmpty()) return Collections.emptyMap();
            Map<String, Integer> result = new HashMap<>(raw.size());
            for (Map.Entry<NamespacedKey, Integer> entry : raw.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
            return result;
        } catch (Exception | LinkageError e) {
            return Collections.emptyMap();
        }
    }

    // ─── 内部工具 ────────────────────────────────────────────

    private AttributeSourceKey parseSourceKey(String sourceKey) {
        int idx = sourceKey.indexOf(':');
        if (idx > 0) {
            return new AttributeSourceKey(sourceKey.substring(0, idx), sourceKey.substring(idx + 1));
        }
        return new AttributeSourceKey("axs", sourceKey);
    }

    private AttributeKey parseAttributeKey(String attributeId) {
        if (attributeId.indexOf(':') > 0) {
            return new AttributeKey(attributeId);
        }
        return AttributeKey.Companion.symphony(attributeId);
    }

    private void reset() {
        available = false;
        symphonyApi = null;
        sourceService = null;
        damageService = null;
        levelService = null;
        metadataService = null;
    }
}
