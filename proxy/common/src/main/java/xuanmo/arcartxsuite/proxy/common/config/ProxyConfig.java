package xuanmo.arcartxsuite.proxy.common.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

import xuanmo.arcartxsuite.proxy.common.model.YggdrasilSource;

/**
 * 代理端插件共享配置读取器。
 * 支持从 classpath 默认配置 + 运行时文件覆写。
 */
public class ProxyConfig {

    private final Logger logger;
    private final File dataFolder;

    private boolean debug = false;
    private boolean denyOffline = true;
    private String kickOfflineMessage = "&c本服务器仅支持正版/LittleSkin 账号登录";
    private boolean autoAssignUuid = true;
    private final List<YggdrasilSource> sources = new ArrayList<>();

    public ProxyConfig(Logger logger, File dataFolder) {
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    public void loadDefaults() {
        // 默认配置：Mojang 官方 + LittleSkin
        sources.clear();
        sources.add(new YggdrasilSource(
            "Mojang",
            "https://sessionserver.mojang.com/",
            true,
            false,
            null
        ));
        sources.add(new YggdrasilSource(
            "LittleSkin",
            "https://littleskin.cn/api/yggdrasil",
            true,
            false,
            null
        ));
    }

    public boolean debug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean denyOffline() {
        return denyOffline;
    }

    public void setDenyOffline(boolean denyOffline) {
        this.denyOffline = denyOffline;
    }

    public String kickOfflineMessage() {
        return kickOfflineMessage;
    }

    public void setKickOfflineMessage(String kickOfflineMessage) {
        this.kickOfflineMessage = kickOfflineMessage;
    }

    public boolean autoAssignUuid() {
        return autoAssignUuid;
    }

    public void setAutoAssignUuid(boolean autoAssignUuid) {
        this.autoAssignUuid = autoAssignUuid;
    }

    @NotNull
    public List<YggdrasilSource> sources() {
        return new ArrayList<>(sources);
    }

    public void addSource(YggdrasilSource source) {
        sources.add(source);
    }

    public void clearSources() {
        sources.clear();
    }

    /**
     * 根据玩家名判断应该路由到哪个 Yggdrasil 源。
     * 简单实现：优先尝试 LittleSkin，失败则 fallback 到 Mojang。
     * 可扩展为基于玩家名前缀、数据库映射等复杂规则。
     */
    public List<YggdrasilSource> resolveSourcesFor(String username) {
        List<YggdrasilSource> result = new ArrayList<>();
        for (YggdrasilSource source : sources) {
            if (source.enabled()) {
                result.add(source);
            }
        }
        return result;
    }

    /**
     * 从 classpath 复制默认配置到 dataFolder。
     */
    public void extractDefaultConfig(String resourceName) {
        File target = new File(dataFolder, resourceName);
        if (target.exists()) {
            return;
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                logger.warning("默认配置未找到: " + resourceName);
                return;
            }
            dataFolder.mkdirs();
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("已释放默认配置: " + target.getAbsolutePath());
        } catch (IOException e) {
            logger.warning("释放默认配置失败: " + e.getMessage());
        }
    }
}
