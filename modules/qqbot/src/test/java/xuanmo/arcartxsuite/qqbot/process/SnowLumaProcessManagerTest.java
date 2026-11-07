package xuanmo.arcartxsuite.qqbot.process;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.qqbot.config.QQBotSnowLumaConfig;

class SnowLumaProcessManagerTest {
    @TempDir Path root;
    private static final String OWNER = "6b2d26b1-c5ea-45df-8c37-561491450bd9";

    private QQBotSnowLumaConfig config(String mode) {
        return new QQBotSnowLumaConfig(mode, "managed", false, "", false,
            "axs-test", "snowluma:test", 5099, 3001, 3000, 6081, "", false);
    }
    private void marker() throws Exception {
        Files.createDirectories(root.resolve("managed"));
        Files.writeString(root.resolve("managed/.axs-qqbot-owner"), OWNER, StandardCharsets.UTF_8);
    }
    private SnowLumaProcessManager manager(String mode, SnowLumaProcessManager.CommandRunner runner) {
        return new SnowLumaProcessManager(root, config(mode), Logger.getAnonymousLogger(), () -> false, runner);
    }
    private static JsonObject info(String id, String owner, boolean running) {
        JsonObject item = new JsonObject(), cfg = new JsonObject(), labels = new JsonObject(), state = new JsonObject();
        if (owner != null) {
            labels.addProperty(SnowLumaProcessManager.OWNER_LABEL, owner);
            labels.addProperty(SnowLumaProcessManager.MANAGED_LABEL, "true");
        }
        cfg.add("Labels", labels); state.addProperty("Running", running);
        item.add("Config", cfg); item.add("State", state);
        item.addProperty("Id", id); item.addProperty("Image", "sha256:previous");
        return item;
    }
    private static SnowLumaProcessManager.CommandResult ok(String output) {
        return new SnowLumaProcessManager.CommandResult(0, output);
    }
    private static SnowLumaProcessManager.CommandResult missing() {
        return new SnowLumaProcessManager.CommandResult(1, "Error: No such container: axs-test");
    }

    @Test void externalNeverExecutesCommandsOrCreatesMetadata() throws Exception {
        List<List<String>> calls = new CopyOnWriteArrayList<>();
        var manager = manager("external", (args, timeout, cleanup) -> { calls.add(args); return ok(""); });
        manager.init(); manager.start(); manager.stop(); manager.statusReport();
        manager.logs(); manager.webUiPasswordReport(); manager.resetWebUiPassword();
        manager.installAsync(null).get(5, TimeUnit.SECONDS);
        manager.checkUpdateAsync().get(5, TimeUnit.SECONDS);
        manager.shutdownAsync().get(5, TimeUnit.SECONDS);
        assertTrue(calls.isEmpty());
        assertFalse(Files.exists(root.resolve("managed")));
    }

    @Test void refusesExternalContainerBeforePullingOrMutating() throws Exception {
        marker();
        List<List<String>> calls = new CopyOnWriteArrayList<>();
        JsonArray external = new JsonArray(); external.add(info("external-id", null, true));
        var manager = manager("docker", (args, timeout, cleanup) -> { calls.add(args); return ok(external.toString()); });
        assertTrue(manager.installAsync(null).get(5, TimeUnit.SECONDS).contains("不属于本模块"));
        manager.shutdownAsync().get(5, TimeUnit.SECONDS);
        assertTrue(calls.stream().allMatch(args -> args.subList(0, 3).equals(List.of("docker", "container", "inspect"))));
    }

    @Test void requiresPrivateOwnerAndManagedLabels() {
        assertFalse(SnowLumaProcessManager.hasOwnership(info("external", null, true), OWNER));
        assertFalse(SnowLumaProcessManager.hasOwnership(info("other", UUID.randomUUID().toString(), true), OWNER));
        assertFalse(SnowLumaProcessManager.hasOwnership(info("owned", OWNER, true), null));
        assertTrue(SnowLumaProcessManager.hasOwnership(info("owned", OWNER, true), OWNER));
    }

    @Test void updateRecreatesOnlyOwnedIdAndRetainsNamedVolumes() throws Exception {
        marker();
        List<List<String>> calls = new CopyOnWriteArrayList<>();
        JsonObject[] current = {info("old-id", OWNER, true)};
        var manager = manager("docker", (args, timeout, cleanup) -> {
            calls.add(args);
            if (args.size() > 2 && args.get(1).equals("container")) {
                if (current[0] == null) return missing();
                JsonArray out = new JsonArray(); out.add(current[0]); return ok(out.toString());
            }
            switch (args.get(1)) {
                case "rm" -> { assertEquals("old-id", args.get(2)); current[0] = null; }
                case "create" -> current[0] = info("new-id", OWNER, false);
                case "start" -> { assertEquals("new-id", args.get(2)); current[0].getAsJsonObject("State").addProperty("Running", true); }
            }
            return ok("");
        });
        assertTrue(manager.installAsync(null).get(5, TimeUnit.SECONDS).contains("更新重建"));
        List<String> create = calls.stream().filter(args -> args.get(1).equals("create")).findFirst().orElseThrow();
        assertTrue(create.contains("axs-test-data:/app/data"));
        assertTrue(create.contains("127.0.0.1:3001:3001"));
        assertTrue(create.contains(SnowLumaProcessManager.OWNER_LABEL + "=" + OWNER));
        assertFalse(calls.stream().anyMatch(args -> args.contains("--volumes") || args.contains("-v") && args.get(1).equals("rm")));
        manager.shutdownAsync().get(5, TimeUnit.SECONDS);
    }

    @Test void shutdownDuringPullPreventsLaterCreateAndStart() throws Exception {
        marker();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<List<String>> calls = new CopyOnWriteArrayList<>();
        var manager = manager("docker", (args, timeout, cleanup) -> {
            calls.add(args);
            if (args.get(1).equals("container")) return missing();
            if (args.get(1).equals("pull")) { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
            return ok("");
        });
        CompletableFuture<String> install = manager.installAsync(null);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        CompletableFuture<Void> shutdown = manager.shutdownAsync();
        release.countDown();
        assertTrue(install.get(5, TimeUnit.SECONDS).contains("关闭"));
        shutdown.get(5, TimeUnit.SECONDS);
        manager.start();
        assertFalse(calls.stream().anyMatch(args -> Set.of("create", "start").contains(args.get(1))));
    }

    @Test void resetMovesActualDataFileAndFailureIsNotSuccess() throws Exception {
        marker();
        List<List<String>> calls = new CopyOnWriteArrayList<>();
        var manager = manager("docker", (args, timeout, cleanup) -> {
            calls.add(args);
            if (args.get(1).equals("container")) {
                JsonArray out = new JsonArray(); out.add(info("owned-id", OWNER, true)); return ok(out.toString());
            }
            if (args.get(1).equals("exec")) return new SnowLumaProcessManager.CommandResult(1, "mv: source missing");
            return ok("");
        });
        assertTrue(manager.resetWebUiPassword().contains("失败"));
        List<String> mv = calls.stream().filter(args -> args.get(1).equals("exec")).findFirst().orElseThrow();
        assertEquals("owned-id", mv.get(2));
        assertEquals("/app/data/config/webui.json", mv.get(4));
        assertFalse(calls.stream().anyMatch(args -> args.get(1).equals("restart")));
        manager.shutdownAsync().get(5, TimeUnit.SECONDS);
    }

    @Test void nodeVersionAndArchitectureSelectionFailClosed() throws Exception {
        String range = "^24.22.1 || >=26.12.1";
        assertFalse(SnowLumaProcessManager.supportsNode("v22.13.0", range));
        assertFalse(SnowLumaProcessManager.supportsNode("v24.22.0", range));
        assertTrue(SnowLumaProcessManager.supportsNode("v24.22.1", range));
        assertFalse(SnowLumaProcessManager.supportsNode("v25.0.0", range));
        assertFalse(SnowLumaProcessManager.supportsNode("v26.12.0", range));
        assertTrue(SnowLumaProcessManager.supportsNode("v26.12.1", range));
        assertFalse(SnowLumaProcessManager.supportsNode("v26.12.1", "unsupported-range"));
        JsonArray assets = new JsonArray();
        for (String suffix : List.of("linux-arm64.tar.gz", "win-x64-lite.zip", "win-x64.zip")) {
            JsonObject asset = new JsonObject();
            asset.addProperty("browser_download_url", "https://github.com/SnowLuma/SnowLuma/releases/download/v2/SnowLuma-v2-" + suffix);
            assets.add(asset);
        }
        JsonObject release = new JsonObject(); release.add("assets", assets);
        assertTrue(SnowLumaProcessManager.downloadUrl(release, "Windows 11", "amd64").endsWith("-win-x64.zip"));
        assertNull(SnowLumaProcessManager.downloadUrl(release, "Windows 11", "arm64"));
        assertNull(SnowLumaProcessManager.downloadUrl(release, "Mac OS X", "aarch64"));
        assertThrows(java.io.IOException.class, () -> SnowLumaProcessManager.safeEntry(root, "../outside"));
    }

    private QQBotSnowLumaConfig networkConfig(String address, String password) {
        return new QQBotSnowLumaConfig("docker", "managed", false, "", false,
            "axs-test", "snowluma:test", 5099, 3001, 3000, 6081, password, false, address);
    }

    @Test void oldConstructorDefaultsToLoopbackAndRejectsNonLiteralAddresses() {
        assertEquals("127.0.0.1", config("docker").dockerBindAddress());
        assertTrue(config("docker").dockerLoopbackBinding());
        assertTrue(networkConfig("::1", "").dockerLoopbackBinding());
        assertFalse(networkConfig("0.0.0.0", "secret").dockerLoopbackBinding());
        for (String address : List.of("localhost", "example.com", "1.2.3", "256.0.0.1",
            "::1%eth0", " 127.0.0.1", "", "127.0.0.1;stop", "$(hostname)")) {
            assertThrows(IllegalArgumentException.class, () -> networkConfig(address, "secret"), address);
        }
    }

    @Test void nonLoopbackWithoutPasswordCannotInstallStartOrRestartContainer() throws Exception {
        marker();
        List<List<String>> calls = new CopyOnWriteArrayList<>();
        var manager = new SnowLumaProcessManager(root, networkConfig("0.0.0.0", ""),
            Logger.getAnonymousLogger(), () -> false, (args, timeout, cleanup) -> { calls.add(args); return missing(); });
        assertTrue(manager.start().contains("vnc-password"));
        assertTrue(manager.installAsync(null).get(5, TimeUnit.SECONDS).contains("vnc-password"));
        assertTrue(manager.resetWebUiPassword().contains("vnc-password"));
        assertTrue(calls.isEmpty(), "unsafe configuration must fail before invoking Docker");
        manager.shutdownAsync().get(5, TimeUnit.SECONDS);
    }

    @Test void explicitLiteralPublishingRequiresPasswordAndFormatsIpv6Correctly() throws Exception {
        marker();
        var publicManager = new SnowLumaProcessManager(root, networkConfig("0.0.0.0", "chosen-secret"),
            Logger.getAnonymousLogger(), () -> false, (args, timeout, cleanup) -> missing());
        List<String> publish = publicManager.dockerCreateArgs("snowluma:test");
        assertTrue(publish.contains("0.0.0.0:5099:5099"));
        assertTrue(publish.contains("0.0.0.0:6081:6081"));
        assertTrue(publish.contains("VNC_PASSWD=chosen-secret"));
        assertEquals("http://127.0.0.1:5099", publicManager.webUiUrl());
        var ipv6 = new SnowLumaProcessManager(root, networkConfig("::1", ""),
            Logger.getAnonymousLogger(), () -> false, (args, timeout, cleanup) -> missing());
        assertTrue(ipv6.dockerCreateArgs("snowluma:test").stream().anyMatch(value -> value.startsWith("[") && value.endsWith("]:3001:3001")));
        assertTrue(ipv6.webUiUrl().startsWith("http://["));
        publicManager.shutdownAsync().get(5, TimeUnit.SECONDS);
        ipv6.shutdownAsync().get(5, TimeUnit.SECONDS);
    }
}
