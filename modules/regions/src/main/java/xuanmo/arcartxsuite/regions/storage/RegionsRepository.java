package xuanmo.arcartxsuite.regions.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration.StorageConfig;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;

public final class RegionsRepository extends AbstractModuleRepository implements AutoCloseable {

    private final Logger logger;
    private final String prefix;
    private final boolean mysql;

    public RegionsRepository(File dataFolder, StorageConfig config, Logger logger) {
        super("AXS-Regions", dataFolder, config.toDescriptor(), logger);
        this.logger = logger;
        this.prefix = config.tablePrefix();
        this.mysql = config.dialect() == StorageConfig.Dialect.MYSQL;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            initTables(stmt);
        }
    }

    @Override
    protected List<String> playerDataTables() {
        // Regions 以区域 id+world 为主键，无玩家 UUID 列
        return List.of();
    }

    @Override
    protected List<String> allTables() {
        return List.of(prefix + "regions", prefix + "flags", prefix + "members");
    }

    private void initTables(Statement stmt) throws SQLException {
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "regions ("
            + "id VARCHAR(64) NOT NULL, "
            + "world VARCHAR(128) NOT NULL, "
            + "min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL, "
            + "max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL, "
            + "priority INT NOT NULL DEFAULT 0, "
            + "parent_id VARCHAR(64), "
            + "PRIMARY KEY (id, world))");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "flags ("
            + "region_id VARCHAR(64) NOT NULL, "
            + "world VARCHAR(128) NOT NULL, "
            + "flag VARCHAR(32) NOT NULL, "
            + "state VARCHAR(8) NOT NULL, "
            + "data TEXT, "
            + "PRIMARY KEY (region_id, world, flag))");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "members ("
            + "region_id VARCHAR(64) NOT NULL, "
            + "world VARCHAR(128) NOT NULL, "
            + "uuid VARCHAR(36), "
            + "group_name VARCHAR(64), "
            + "role VARCHAR(8) NOT NULL)");

        if (mysql) {
            try {
                stmt.executeUpdate("CREATE INDEX idx_rg_members ON " + prefix + "members (region_id, world)");
            } catch (SQLException ignored) {}
        }
    }

    // ─── 区域 CRUD ───

    public void saveRegion(Region region) throws SQLException {
        String sql = "REPLACE INTO " + prefix + "regions (id, world, min_x, min_y, min_z, max_x, max_y, max_z, priority, parent_id) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, region.id());
            ps.setString(2, region.world());
            ps.setInt(3, region.minX());
            ps.setInt(4, region.minY());
            ps.setInt(5, region.minZ());
            ps.setInt(6, region.maxX());
            ps.setInt(7, region.maxY());
            ps.setInt(8, region.maxZ());
            ps.setInt(9, region.priority());
            ps.setString(10, region.parentId());
            ps.executeUpdate();
        }
        saveFlags(region);
        saveMembers(region);
    }

    public void deleteRegion(String id, String world) throws SQLException {
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + prefix + "regions WHERE id=? AND world=?")) {
                ps.setString(1, id); ps.setString(2, world); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + prefix + "flags WHERE region_id=? AND world=?")) {
                ps.setString(1, id); ps.setString(2, world); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + prefix + "members WHERE region_id=? AND world=?")) {
                ps.setString(1, id); ps.setString(2, world); ps.executeUpdate();
            }
        }
    }

    public List<Region> loadAllRegions() throws SQLException {
        List<Region> regions = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + prefix + "regions")) {
            while (rs.next()) {
                Region region = new Region(
                    rs.getString("id"), rs.getString("world"),
                    rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                    rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z")
                );
                region.setPriority(rs.getInt("priority"));
                region.setParentId(rs.getString("parent_id"));
                regions.add(region);
            }
        }
        // 加载标志
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + prefix + "flags")) {
            while (rs.next()) {
                String id = rs.getString("region_id");
                String world = rs.getString("world");
                String flagKey = rs.getString("flag");
                String stateStr = rs.getString("state");
                String data = rs.getString("data");
                RegionFlag flag = RegionFlag.fromKey(flagKey);
                if (flag == null) continue;
                for (Region region : regions) {
                    if (region.id().equals(id) && region.world().equals(world)) {
                        region.setFlag(flag, RegionFlag.State.fromString(stateStr));
                        if (data != null && !data.isBlank()) {
                            region.setFlagData(flag, data);
                        }
                        break;
                    }
                }
            }
        }
        // 加载成员
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + prefix + "members")) {
            while (rs.next()) {
                String id = rs.getString("region_id");
                String world = rs.getString("world");
                String uuid = rs.getString("uuid");
                String group = rs.getString("group_name");
                String role = rs.getString("role");
                for (Region region : regions) {
                    if (region.id().equals(id) && region.world().equals(world)) {
                        if (uuid != null && !uuid.isBlank()) {
                            UUID u = UUID.fromString(uuid);
                            if ("owner".equalsIgnoreCase(role)) region.addOwner(u);
                            else region.addMember(u);
                        }
                        if (group != null && !group.isBlank()) {
                            if ("owner".equalsIgnoreCase(role)) region.addOwnerGroup(group);
                            else region.addMemberGroup(group);
                        }
                        break;
                    }
                }
            }
        }
        return regions;
    }

    private void saveFlags(Region region) throws SQLException {
        try (Connection conn = getConnection()) {
            try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM " + prefix + "flags WHERE region_id=? AND world=?")) {
                del.setString(1, region.id()); del.setString(2, region.world()); del.executeUpdate();
            }
            if (region.flags().isEmpty()) return;
            String sql = "INSERT INTO " + prefix + "flags (region_id, world, flag, state, data) VALUES (?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<RegionFlag, RegionFlag.State> entry : region.flags().entrySet()) {
                    ps.setString(1, region.id());
                    ps.setString(2, region.world());
                    ps.setString(3, entry.getKey().configKey());
                    ps.setString(4, entry.getValue().name().toLowerCase());
                    ps.setString(5, region.getFlagData(entry.getKey()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private void saveMembers(Region region) throws SQLException {
        try (Connection conn = getConnection()) {
            try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM " + prefix + "members WHERE region_id=? AND world=?")) {
                del.setString(1, region.id()); del.setString(2, region.world()); del.executeUpdate();
            }
            String sql = "INSERT INTO " + prefix + "members (region_id, world, uuid, group_name, role) VALUES (?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (UUID uuid : region.owners()) {
                    ps.setString(1, region.id()); ps.setString(2, region.world());
                    ps.setString(3, uuid.toString()); ps.setString(4, null); ps.setString(5, "owner");
                    ps.addBatch();
                }
                for (UUID uuid : region.members()) {
                    ps.setString(1, region.id()); ps.setString(2, region.world());
                    ps.setString(3, uuid.toString()); ps.setString(4, null); ps.setString(5, "member");
                    ps.addBatch();
                }
                for (String group : region.ownerGroups()) {
                    ps.setString(1, region.id()); ps.setString(2, region.world());
                    ps.setString(3, null); ps.setString(4, group); ps.setString(5, "owner");
                    ps.addBatch();
                }
                for (String group : region.memberGroups()) {
                    ps.setString(1, region.id()); ps.setString(2, region.world());
                    ps.setString(3, null); ps.setString(4, group); ps.setString(5, "member");
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }
}
