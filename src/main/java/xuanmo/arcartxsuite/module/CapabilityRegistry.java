package xuanmo.arcartxsuite.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Capability 注册表：管理模块提供的能力实例、玩家数据清除器和数据库迁移器。
 * <p>
 * 从 {@link ModuleRegistry} 拆分而来，职责单一（SRP）。
 * 线程安全：内部使用 {@link ConcurrentHashMap} 和 {@link CopyOnWriteArrayList}。
 */
final class CapabilityRegistry {

    private static final Logger LOGGER = new PluginConsoleLogger("AXS-CapabilityRegistry", null);

    /** Capability 全局注册表（类型 -> 实现） */
    private final ConcurrentHashMap<Class<?>, Object> capabilities = new ConcurrentHashMap<>();
    /** 模块注册的 Capability 类型（用于按模块卸载） */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Class<?>>> moduleCapabilityTypes = new ConcurrentHashMap<>();
    /** 多实例 capability：玩家数据清除注册表 */
    private final CopyOnWriteArrayList<xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable> purgeables = new CopyOnWriteArrayList<>();
    /** 多实例 capability：数据库一键迁移注册表 */
    private final CopyOnWriteArrayList<xuanmo.arcartxsuite.api.capability.DatabaseMigratable> migratables = new CopyOnWriteArrayList<>();

    /** 直接访问底层 capabilities map（供 GlobalBridgeFactory 注册 core capability） */
    ConcurrentHashMap<Class<?>, Object> capabilitiesMap() {
        return capabilities;
    }

    @SuppressWarnings("unchecked")
    <T> void register(String moduleId, Class<T> capabilityType, T implementation) {
        capabilities.put(capabilityType, implementation);
        moduleCapabilityTypes.computeIfAbsent(moduleId, ignored -> new CopyOnWriteArrayList<>()).add(capabilityType);
        if (implementation instanceof xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable p) {
            purgeables.removeIf(existing -> existing.moduleId().equalsIgnoreCase(p.moduleId()));
            purgeables.add(p);
        }
        if (implementation instanceof xuanmo.arcartxsuite.api.capability.DatabaseMigratable m) {
            migratables.removeIf(existing -> existing.moduleId().equalsIgnoreCase(m.moduleId()));
            migratables.add(m);
        }
    }

    @SuppressWarnings("unchecked")
    <T> void registerCore(Class<T> capabilityType, T implementation) {
        capabilities.put(capabilityType, implementation);
    }

    @SuppressWarnings("unchecked")
    <T> T get(Class<T> capabilityType) {
        return (T) capabilities.get(capabilityType);
    }

    void removeByModule(String moduleId) {
        purgeables.removeIf(p -> p.moduleId().equalsIgnoreCase(moduleId));
        migratables.removeIf(m -> m.moduleId().equalsIgnoreCase(moduleId));
        CopyOnWriteArrayList<Class<?>> types = moduleCapabilityTypes.remove(moduleId);
        if (types != null) {
            for (Class<?> capabilityType : types) {
                capabilities.remove(capabilityType);
            }
        }
    }

    void clearAll() {
        moduleCapabilityTypes.clear();
        capabilities.clear();
        purgeables.clear();
        migratables.clear();
    }

    // ─── 玩家数据清除 ─────────────────────────────────────────

    java.util.Map<String, Integer> purgePlayerData(java.util.UUID playerUuid) {
        java.util.LinkedHashMap<String, Integer> result = new java.util.LinkedHashMap<>();
        for (var p : purgeables) {
            try {
                int rows = p.purgePlayerData(playerUuid);
                result.put(p.moduleId(), rows);
            } catch (Exception e) {
                LOGGER.warning("清除玩家数据失败: module=" + p.moduleId() + " error=" + e.getMessage());
                result.put(p.moduleId(), -1);
            }
        }
        return result;
    }

    java.util.Map<String, Integer> purgePlayerData(java.util.UUID playerUuid, String moduleId) {
        java.util.LinkedHashMap<String, Integer> result = new java.util.LinkedHashMap<>();
        for (var p : purgeables) {
            if (p.moduleId().equalsIgnoreCase(moduleId)) {
                try {
                    int rows = p.purgePlayerData(playerUuid);
                    result.put(p.moduleId(), rows);
                } catch (Exception e) {
                    LOGGER.warning("清除玩家数据失败: module=" + p.moduleId() + " error=" + e.getMessage());
                    result.put(p.moduleId(), -1);
                }
                return result;
            }
        }
        return result;
    }

    java.util.Map<String, Integer> purgeAllPlayerData() {
        java.util.LinkedHashMap<String, Integer> result = new java.util.LinkedHashMap<>();
        for (var p : purgeables) {
            try {
                int rows = p.purgeAllPlayerData();
                result.put(p.moduleId(), rows);
            } catch (Exception e) {
                LOGGER.warning("清空玩家数据失败: module=" + p.moduleId() + " error=" + e.getMessage());
                result.put(p.moduleId(), -1);
            }
        }
        return result;
    }

    java.util.Map<String, Integer> purgeAllPlayerData(String moduleId) {
        java.util.LinkedHashMap<String, Integer> result = new java.util.LinkedHashMap<>();
        for (var p : purgeables) {
            if (p.moduleId().equalsIgnoreCase(moduleId)) {
                try {
                    int rows = p.purgeAllPlayerData();
                    result.put(p.moduleId(), rows);
                } catch (Exception e) {
                    LOGGER.warning("清空玩家数据失败: module=" + p.moduleId() + " error=" + e.getMessage());
                    result.put(p.moduleId(), -1);
                }
                return result;
            }
        }
        return result;
    }

    List<String> purgeableModuleIds() {
        List<String> ids = new ArrayList<>(purgeables.size());
        for (var p : purgeables) {
            ids.add(p.moduleId());
        }
        return ids;
    }

    // ─── 数据库迁移 ───────────────────────────────────────────

    List<String> migratableModuleIds() {
        List<String> ids = new ArrayList<>();
        for (var m : migratables) {
            ids.add(m.moduleId());
        }
        return ids;
    }

    Optional<xuanmo.arcartxsuite.api.capability.DatabaseMigratable> getMigratable(String moduleId) {
        for (var m : migratables) {
            if (m.moduleId().equalsIgnoreCase(moduleId)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
