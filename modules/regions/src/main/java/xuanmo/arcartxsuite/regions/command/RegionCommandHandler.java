package xuanmo.arcartxsuite.regions.command;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.model.Selection;
import xuanmo.arcartxsuite.regions.service.RegionManager;

/**
 * 区域管理命令处理器。
 * 命令格式: /axs regions <action> [args...]
 */
public final class RegionCommandHandler implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of(
        "help", "status", "reload",
        "define", "remove", "redefine", "rename",
        "list", "info", "select", "pos1", "pos2",
        "flag", "removeflag", "flags",
        "addowner", "removeowner", "addmember", "removemember",
        "priority", "parent", "tp"
    );

    private final RegionManager manager;
    private final RegionsConfiguration config;
    private final MessageProvider messages;

    public RegionCommandHandler(RegionManager manager, RegionsConfiguration config, MessageProvider messages) {
        this.manager = manager;
        this.config = config;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "regions"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "define" -> handleDefine(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "redefine" -> handleRedefine(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "pos1" -> handlePos(sender, 1);
            case "pos2" -> handlePos(sender, 2);
            case "flag" -> handleFlag(sender, args);
            case "removeflag" -> handleRemoveFlag(sender, args);
            case "flags" -> handleFlags(sender, args);
            case "addowner" -> handleAddMember(sender, args, "owner");
            case "removeowner" -> handleRemoveMember(sender, args, "owner");
            case "addmember" -> handleAddMember(sender, args, "member");
            case "removemember" -> handleRemoveMember(sender, args, "member");
            case "priority" -> handlePriority(sender, args);
            case "parent" -> handleParent(sender, args);
            case "tp" -> handleTeleport(sender, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3) {
            return switch (args[1].toLowerCase()) {
                case "remove", "redefine", "info", "flag", "removeflag", "flags",
                     "addowner", "removeowner", "addmember", "removemember",
                     "priority", "parent", "tp" -> filterRegions(args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && ("flag".equalsIgnoreCase(args[1]) || "removeflag".equalsIgnoreCase(args[1]))) {
            return filterFlags(args[3]);
        }
        if (args.length == 5 && "flag".equalsIgnoreCase(args[1])) {
            return List.of("allow", "deny");
        }
        if (args.length == 4 && ("addowner".equalsIgnoreCase(args[1]) || "addmember".equalsIgnoreCase(args[1])
            || "removeowner".equalsIgnoreCase(args[1]) || "removemember".equalsIgnoreCase(args[1]))) {
            return filterOnline(args[3]);
        }
        return List.of();
    }

    // ─── 命令实现 ───

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " regions";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.define", cmd));
        sender.sendMessage(fullMsg("help.remove", cmd));
        sender.sendMessage(fullMsg("help.redefine", cmd));
        sender.sendMessage(fullMsg("help.list", cmd));
        sender.sendMessage(fullMsg("help.info", cmd));
        sender.sendMessage(fullMsg("help.flag", cmd));
        sender.sendMessage(fullMsg("help.removeflag", cmd));
        sender.sendMessage(fullMsg("help.flags", cmd));
        sender.sendMessage(fullMsg("help.addowner", cmd));
        sender.sendMessage(fullMsg("help.removeowner", cmd));
        sender.sendMessage(fullMsg("help.priority", cmd));
        sender.sendMessage(fullMsg("help.parent", cmd));
        sender.sendMessage(fullMsg("help.tp", cmd));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.loaded", manager.getAllRegions().size()));
        sender.sendMessage(fullMsg("status.flags", RegionFlag.values().length));
    }

    private void handleDefine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.define.usage")); return; }
        String name = args[2].toLowerCase(Locale.ROOT);

        Selection sel = manager.getSelection(player.getUniqueId());
        if (!sel.isComplete()) {
            sender.sendMessage(prefix() + fmtMsg(config.messages().noSelection()));
            return;
        }
        if (sel.volume() > config.selection().maxVolume()) {
            sender.sendMessage(prefix() + fmtMsg(config.messages().selectionTooLarge().replace("{max}", String.valueOf(config.selection().maxVolume()))));
            return;
        }
        if (manager.getRegion(name, sel.world()) != null) {
            sender.sendMessage(prefix() + fmtMsg(config.messages().regionExists().replace("{name}", name)));
            return;
        }
        int maxPer = config.selection().maxRegionsPerPlayer();
        if (maxPer > 0 && !player.hasPermission("axs.regions.bypass.limit")) {
            if (manager.countRegionsByOwner(player.getUniqueId()) >= maxPer) {
                sender.sendMessage(prefix() + fmtMsg(config.messages().maxRegionsReached().replace("{max}", String.valueOf(maxPer))));
                return;
            }
        }

        try {
            Region region = manager.createRegion(name, sel);
            region.addOwner(player.getUniqueId());
            manager.saveRegion(region);
            sender.sendMessage(prefix() + fmtMsg(config.messages().regionCreated().replace("{name}", name)));
            manager.clearSelection(player.getUniqueId());
        } catch (SQLException e) {
            sender.sendMessage(prefix() + ChatColor.RED + "保存失败: " + e.getMessage());
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.remove.usage")); return; }
        String name = args[2];
        Region region = manager.getRegion(name);
        if (region == null) {
            sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", name)));
            return;
        }
        if (!hasRegionPermission(sender, region)) return;
        try {
            manager.deleteRegion(region);
            sender.sendMessage(prefix() + fmtMsg(config.messages().regionDeleted().replace("{name}", name)));
        } catch (SQLException e) {
            sender.sendMessage(prefix() + ChatColor.RED + "删除失败: " + e.getMessage());
        }
    }

    private void handleRedefine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.redefine.usage")); return; }
        String name = args[2];
        Region region = manager.getRegion(name);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", name))); return; }
        if (!hasRegionPermission(sender, region)) return;

        Selection sel = manager.getSelection(player.getUniqueId());
        if (!sel.isComplete()) { sender.sendMessage(prefix() + fmtMsg(config.messages().noSelection())); return; }
        if (sel.volume() > config.selection().maxVolume()) {
            sender.sendMessage(prefix() + fmtMsg(config.messages().selectionTooLarge().replace("{max}", String.valueOf(config.selection().maxVolume()))));
            return;
        }

        region.redefine(sel.x1(), sel.y1(), sel.z1(), sel.x2(), sel.y2(), sel.z2());
        try {
            manager.saveRegion(region);
            sender.sendMessage(prefix() + ChatColor.GREEN + "区域 " + name + " 已重新定义。");
        } catch (SQLException e) {
            sender.sendMessage(prefix() + ChatColor.RED + "保存失败: " + e.getMessage());
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        String world = args.length >= 3 ? args[2] : (sender instanceof Player p ? p.getWorld().getName() : null);
        List<Region> list = world != null ? manager.getRegionsInWorld(world) : manager.getAllRegions();
        if (list.isEmpty()) {
            sender.sendMessage(fullMsg("admin.list.empty"));
            return;
        }
        sender.sendMessage(fullMsg("admin.list.title", (world != null ? world : "所有世界"), list.size()));
        for (Region r : list) {
            sender.sendMessage(prefix() + ChatColor.GRAY + "- " + ChatColor.WHITE + r.id()
                + ChatColor.GRAY + " [P:" + r.priority() + "] "
                + ChatColor.DARK_GRAY + "(" + r.minX() + "," + r.minY() + "," + r.minZ()
                + " → " + r.maxX() + "," + r.maxY() + "," + r.maxZ() + ")");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.info.usage")); return; }
        Region region = manager.getRegion(args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }

        sender.sendMessage(fullMsg("admin.info.title", region.id()));
        sender.sendMessage(fullMsg("admin.info.world", region.world()));
        sender.sendMessage(fullMsg("admin.info.bounds", region.minX() + "," + region.minY() + "," + region.minZ(), region.maxX() + "," + region.maxY() + "," + region.maxZ()));
        sender.sendMessage(fullMsg("admin.info.volume", region.volume()));
        sender.sendMessage(fullMsg("admin.info.priority", region.priority()));
        sender.sendMessage(fullMsg("admin.info.parent", (region.parentId() != null ? region.parentId() : "无")));
        sender.sendMessage(fullMsg("admin.info.owners", uuidSetToNames(region.owners())));
        sender.sendMessage(fullMsg("admin.info.members", uuidSetToNames(region.members())));
        sender.sendMessage(fullMsg("admin.info.flags", region.flags().size()));
    }

    private void handlePos(CommandSender sender, int pos) {
        if (!(sender instanceof Player player)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        Selection sel = manager.getSelection(player.getUniqueId());
        if (pos == 1) sel.setPos1(player.getLocation());
        else sel.setPos2(player.getLocation());
        String msg = (pos == 1 ? config.messages().wandPos1() : config.messages().wandPos2())
            .replace("{x}", String.valueOf(player.getLocation().getBlockX()))
            .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
            .replace("{z}", String.valueOf(player.getLocation().getBlockZ()));
        player.sendMessage(prefix() + fmtMsg(msg));
    }

    private void handleFlag(CommandSender sender, String[] args) {
        if (args.length < 5) { sender.sendMessage(fullMsg("admin.flag.usage")); return; }
        Region region = manager.getRegion(args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }
        if (!hasRegionPermission(sender, region)) return;

        RegionFlag flag = RegionFlag.fromKey(args[3]);
        if (flag == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().flagUnknown().replace("{flag}", args[3]))); return; }

        RegionFlag.State state = RegionFlag.State.fromString(args[4]);
        region.setFlag(flag, state);

        // 额外数据（如 greeting/farewell 的自定义文本）
        if (args.length > 5) {
            StringBuilder data = new StringBuilder();
            for (int i = 5; i < args.length; i++) {
                if (data.length() > 0) data.append(" ");
                data.append(args[i]);
            }
            region.setFlagData(flag, data.toString());
        }

        try {
            manager.saveRegion(region);
            sender.sendMessage(prefix() + fmtMsg(config.messages().flagSet()
                .replace("{region}", region.id())
                .replace("{flag}", flag.configKey())
                .replace("{value}", state.name().toLowerCase())));
        } catch (SQLException e) {
            sender.sendMessage(prefix() + ChatColor.RED + "保存失败: " + e.getMessage());
        }
    }

    private void handleRemoveFlag(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.removeflag.usage")); return; }
        Region region = manager.getRegion(args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }
        if (!hasRegionPermission(sender, region)) return;

        RegionFlag flag = RegionFlag.fromKey(args[3]);
        if (flag == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().flagUnknown().replace("{flag}", args[3]))); return; }

        region.setFlag(flag, RegionFlag.State.NONE);
        region.setFlagData(flag, null);
        try {
            manager.saveRegion(region);
            sender.sendMessage(prefix() + fmtMsg(config.messages().flagRemoved()
                .replace("{region}", region.id()).replace("{flag}", flag.configKey())));
        } catch (SQLException e) {
            sender.sendMessage(prefix() + ChatColor.RED + "保存失败: " + e.getMessage());
        }
    }

    private void handleFlags(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(prefix() + ChatColor.YELLOW + "用法: /axs regions flags <区域>"); return; }
        Region region = manager.getRegion(args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }

        Map<RegionFlag, RegionFlag.State> flags = region.flags();
        if (flags.isEmpty()) {
            sender.sendMessage(fullMsg("admin.flags.empty", region.id()));
            return;
        }
        sender.sendMessage(fullMsg("admin.flags.title", region.id()));
        for (var entry : flags.entrySet()) {
            String data = region.getFlagData(entry.getKey());
            String extra = data != null ? ChatColor.DARK_GRAY + " (" + data + ")" : "";
            ChatColor color = entry.getValue() == RegionFlag.State.ALLOW ? ChatColor.GREEN : ChatColor.RED;
            sender.sendMessage(prefix() + ChatColor.GRAY + "  " + entry.getKey().configKey() + ": "
                + color + entry.getValue().name().toLowerCase() + extra);
        }
    }

    private void handleAddMember(CommandSender sender, String[] args, String role) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.addowner.usage")); return; }
        Region region = manager.getRegion(args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }
        if (!hasRegionPermission(sender, region)) return;

        String targetName = args[3];
        // 检查是否为权限组 (以 g: 开头)
        if (targetName.startsWith("g:")) {
            String group = targetName.substring(2);
            if ("owner".equals(role)) region.addOwnerGroup(group);
            else region.addMemberGroup(group);
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if ("owner".equals(role)) region.addOwner(target.getUniqueId());
            else region.addMember(target.getUniqueId());
        }
        try {
            manager.saveRegion(region);
            String roleDisplay = "owner".equals(role) ? "所有者" : "成员";
            sender.sendMessage(prefix() + fmtMsg(config.messages().memberAdded()
                .replace("{player}", targetName).replace("{region}", region.id()).replace("{role}", roleDisplay)));
        } catch (SQLException e) {
            sender.sendMessage(prefix() + ChatColor.RED + "保存失败: " + e.getMessage());
        }
    }

    private void handleRemoveMember(CommandSender sender, String[] args, String role) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.removeowner.usage")); return; }
        Region region = manager.getRegion(args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }
        if (!hasRegionPermission(sender, region)) return;

        String targetName = args[3];
        if (targetName.startsWith("g:")) {
            String group = targetName.substring(2);
            if ("owner".equals(role)) region.removeOwnerGroup(group);
            else region.removeMemberGroup(group);
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if ("owner".equals(role)) region.removeOwner(target.getUniqueId());
            else region.removeMember(target.getUniqueId());
        }
        try {
            manager.saveRegion(region);
            sender.sendMessage(prefix() + fmtMsg(config.messages().memberRemoved()
                .replace("{player}", targetName).replace("{region}", region.id())));
        } catch (SQLException e) {
            sender.sendMessage(prefix() + ChatColor.RED + "保存失败: " + e.getMessage());
        }
    }

    private void handlePriority(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.priority.usage")); return; }
        Region region = manager.getRegion(args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }
        if (!hasRegionPermission(sender, region)) return;

        try {
            int priority = Integer.parseInt(args[3]);
            region.setPriority(priority);
            manager.saveRegion(region);
            sender.sendMessage(fullMsg("admin.priority.success", region.id(), priority));
        } catch (NumberFormatException e) {
            sender.sendMessage(fullMsg("admin.priority.invalid", args[3]));
        } catch (SQLException e) {
            sender.sendMessage(prefix() + ChatColor.RED + "保存失败: " + e.getMessage());
        }
    }

    private void handleParent(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.parent.usage")); return; }
        Region region = manager.getRegion(args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }
        if (!hasRegionPermission(sender, region)) return;

        String parentName = args[3];
        if ("none".equalsIgnoreCase(parentName)) {
            region.setParentId(null);
        } else {
            Region parent = manager.getRegion(parentName);
            if (parent == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", parentName))); return; }
            region.setParentId(parent.id());
        }
        try {
            manager.saveRegion(region);
            sender.sendMessage(fullMsg("admin.parent.success", region.id(), parentName));
        } catch (SQLException e) {
            sender.sendMessage(prefix() + ChatColor.RED + "保存失败: " + e.getMessage());
        }
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.tp.usage")); return; }
        Region region = manager.getRegion(args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }

        var world = Bukkit.getWorld(region.world());
        if (world == null) { sender.sendMessage(fullMsg("admin.tp.world-offline", region.world())); return; }
        int cx = (region.minX() + region.maxX()) / 2;
        int cz = (region.minZ() + region.maxZ()) / 2;
        int cy = world.getHighestBlockYAt(cx, cz) + 1;
        player.teleport(new org.bukkit.Location(world, cx + 0.5, cy, cz + 0.5));
        sender.sendMessage(fullMsg("admin.tp.success", region.id()));
    }

    // ─── 工具方法 ───

    private boolean hasRegionPermission(CommandSender sender, Region region) {
        if (sender.hasPermission("axs.regions.admin")) return true;
        if (sender instanceof Player player && region.isOwner(player.getUniqueId())) return true;
        sender.sendMessage(prefix() + fmtMsg(config.messages().noPermissionRegion()));
        return false;
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', config.messages().prefix());
    }

    private String fmtMsg(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private String uuidSetToNames(java.util.Set<UUID> uuids) {
        if (uuids.isEmpty()) return "无";
        StringJoiner sj = new StringJoiner(", ");
        for (UUID uuid : uuids) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            sj.add(op.getName() != null ? op.getName() : uuid.toString().substring(0, 8));
        }
        return sj.toString();
    }

    private List<String> filterRegions(String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Region r : manager.getAllRegions()) {
            if (r.id().toLowerCase(Locale.ROOT).startsWith(n)) result.add(r.id());
        }
        return result;
    }

    private List<String> filterFlags(String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (RegionFlag flag : RegionFlag.values()) {
            if (flag.configKey().startsWith(n)) result.add(flag.configKey());
        }
        return result;
    }

    private List<String> filterOnline(String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(n)) result.add(p.getName());
        }
        return result;
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
