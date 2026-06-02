-- EntityTracker 数据库初始化脚本
-- 版本: 1.1.0
-- 创建时间: 2026-05-19

-- 1. Boss击杀记录表
CREATE TABLE IF NOT EXISTS boss_kill_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    boss_id VARCHAR(100) NOT NULL,
    boss_display_name VARCHAR(200),
    kill_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    server_name VARCHAR(100),
    participants TEXT,
    drops TEXT,
    total_damage INTEGER DEFAULT 0,
    duration_seconds INTEGER DEFAULT 0,
    world_name VARCHAR(100),
    location_x DOUBLE,
    location_y DOUBLE,
    location_z DOUBLE
);

-- 2. 掉落统计表
CREATE TABLE IF NOT EXISTS boss_drop_statistics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    boss_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    item_name VARCHAR(200),
    drop_count INTEGER DEFAULT 0,
    kill_count INTEGER DEFAULT 0,
    drop_rate DECIMAL(5,4) DEFAULT 0.0000,
    last_drop_time TIMESTAMP,
    server_name VARCHAR(100),
    UNIQUE (boss_id, item_id, server_name)
);

-- 3. DKP积分表
CREATE TABLE IF NOT EXISTS player_dkp (
    player_uuid VARCHAR(36) PRIMARY KEY,
    player_name VARCHAR(100),
    total_points INTEGER DEFAULT 0,
    earned_points INTEGER DEFAULT 0,
    spent_points INTEGER DEFAULT 0,
    last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    server_name VARCHAR(100)
);

-- 4. DKP积分记录表
CREATE TABLE IF NOT EXISTS dkp_transaction_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(100),
    transaction_type VARCHAR(20) NOT NULL,
    points INTEGER NOT NULL,
    reason VARCHAR(500),
    boss_kill_id INTEGER,
    transaction_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    server_name VARCHAR(100),
    FOREIGN KEY (boss_kill_id) REFERENCES boss_kill_records(id)
);

-- 5. 掉落分配记录表
CREATE TABLE IF NOT EXISTS drop_allocation_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    boss_kill_id INTEGER NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    item_name VARCHAR(200),
    item_amount INTEGER DEFAULT 1,
    allocation_type VARCHAR(20) NOT NULL,
    winner_uuid VARCHAR(36),
    winner_name VARCHAR(100),
    points_cost INTEGER DEFAULT 0,
    roll_value INTEGER,
    priority_score INTEGER,
    allocation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    server_name VARCHAR(100),
    FOREIGN KEY (boss_kill_id) REFERENCES boss_kill_records(id)
);

-- 6. ROLL参与记录表
CREATE TABLE IF NOT EXISTS roll_participation_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    allocation_record_id INTEGER NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(100),
    roll_type VARCHAR(20) NOT NULL,
    roll_value INTEGER,
    roll_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    server_name VARCHAR(100),
    FOREIGN KEY (allocation_record_id) REFERENCES drop_allocation_records(id)
);

-- 7. 玩家Boss最高伤害记录表
CREATE TABLE IF NOT EXISTS player_boss_best_damage (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(100),
    boss_id VARCHAR(100) NOT NULL,
    boss_display_name VARCHAR(200),
    best_damage INTEGER NOT NULL,
    damage_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    server_name VARCHAR(100),
    world_name VARCHAR(100),
    location_x DOUBLE,
    location_y DOUBLE,
    location_z DOUBLE,
    UNIQUE (player_uuid, boss_id)
);

-- 8. 跨服Boss排行缓存表
CREATE TABLE IF NOT EXISTS cross_server_boss_rankings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ranking_type VARCHAR(50) NOT NULL,
    boss_id VARCHAR(100),
    ranking_data TEXT NOT NULL,
    last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expire_time TIMESTAMP
);

-- 9. 排行榜奖励配置表
CREATE TABLE IF NOT EXISTS ranking_reward_configs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    reward_type VARCHAR(20) NOT NULL,
    ranking_type VARCHAR(50) NOT NULL,
    boss_id VARCHAR(100),
    rank_start INTEGER NOT NULL,
    rank_end INTEGER NOT NULL,
    reward_name VARCHAR(200),
    reward_description TEXT,
    reward_items TEXT,
    reward_commands TEXT,
    reward_money INTEGER DEFAULT 0,
    reward_dkp INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10. 排行榜奖励发放记录表
CREATE TABLE IF NOT EXISTS ranking_reward_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    reward_config_id INTEGER NOT NULL,
    reward_type VARCHAR(20) NOT NULL,
    ranking_type VARCHAR(50) NOT NULL,
    boss_id VARCHAR(100),
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(100),
    rank INTEGER NOT NULL,
    score INTEGER NOT NULL,
    reward_items TEXT,
    reward_commands TEXT,
    reward_money INTEGER DEFAULT 0,
    reward_dkp INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'pending',
    issued_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    failure_reason TEXT,
    retry_count INTEGER DEFAULT 0,
    server_name VARCHAR(100),
    FOREIGN KEY (reward_config_id) REFERENCES ranking_reward_configs(id)
);

-- 11. 排行榜统计周期表
CREATE TABLE IF NOT EXISTS ranking_periods (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    period_type VARCHAR(20) NOT NULL,
    ranking_type VARCHAR(50) NOT NULL,
    boss_id VARCHAR(100),
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_participants INTEGER DEFAULT 0,
    rewards_sent INTEGER DEFAULT 0,
    rewards_failed INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'active',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (period_type, ranking_type, boss_id, period_start)
);

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_boss_kill_records_boss_time ON boss_kill_records(boss_id, kill_time DESC);
CREATE INDEX IF NOT EXISTS idx_boss_kill_records_server_time ON boss_kill_records(server_name, kill_time DESC);
CREATE INDEX IF NOT EXISTS idx_boss_drop_statistics_boss_rate ON boss_drop_statistics(boss_id, drop_rate DESC);
CREATE INDEX IF NOT EXISTS idx_player_dkp_server_points ON player_dkp(player_uuid, server_name, total_points DESC);
CREATE INDEX IF NOT EXISTS idx_drop_allocation_records_boss_time ON drop_allocation_records(boss_kill_id, allocation_time DESC);
CREATE INDEX IF NOT EXISTS idx_player_boss_best_damage_boss ON player_boss_best_damage(boss_id, best_damage DESC);
CREATE INDEX IF NOT EXISTS idx_player_boss_best_damage_player ON player_boss_best_damage(player_uuid, best_damage DESC);
CREATE INDEX IF NOT EXISTS idx_player_boss_best_damage_server ON player_boss_best_damage(server_name, best_damage DESC);
CREATE INDEX IF NOT EXISTS idx_cross_server_rankings_type ON cross_server_boss_rankings(ranking_type);
CREATE INDEX IF NOT EXISTS idx_cross_server_rankings_boss ON cross_server_boss_rankings(boss_id, ranking_type);
CREATE INDEX IF NOT EXISTS idx_ranking_reward_configs_type_ranking ON ranking_reward_configs(reward_type, ranking_type);
CREATE INDEX IF NOT EXISTS idx_ranking_reward_configs_boss_ranking ON ranking_reward_configs(boss_id, ranking_type);
CREATE INDEX IF NOT EXISTS idx_ranking_reward_configs_rank_range ON ranking_reward_configs(rank_start, rank_end);
CREATE INDEX IF NOT EXISTS idx_ranking_reward_records_config ON ranking_reward_records(reward_config_id);
CREATE INDEX IF NOT EXISTS idx_ranking_reward_records_player_period ON ranking_reward_records(player_uuid, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_ranking_reward_records_ranking_period ON ranking_reward_records(ranking_type, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_ranking_reward_records_status ON ranking_reward_records(status);
CREATE INDEX IF NOT EXISTS idx_ranking_periods_type ON ranking_periods(period_type);
CREATE INDEX IF NOT EXISTS idx_ranking_periods_ranking ON ranking_periods(ranking_type);
CREATE INDEX IF NOT EXISTS idx_ranking_periods_range ON ranking_periods(period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_ranking_periods_status ON ranking_periods(status);

-- 插入默认奖励配置
INSERT OR IGNORE INTO ranking_reward_configs (
    reward_type, ranking_type, rank_start, rank_end, reward_name, 
    reward_description, reward_items, reward_commands, reward_dkp
) VALUES 
-- 周奖励配置
('weekly', 'best_damage', 1, 1, '周冠军奖励', '本周Boss伤害冠军奖励',
 '{"items":[{"item_id":"diamond_sword","item_name":"冠军之剑","amount":1,"enchantments":["sharpness:7","unbreaking:5"],"lore":["&6&l周冠军专属","&f{period} Boss伤害冠军"]}]}',
 '["title {player} title &6&l周冠军","broadcast &6&l恭喜 {player} 获得本周Boss伤害冠军！"]',
 100),
('weekly', 'best_damage', 2, 2, '周亚军奖励', '本周Boss伤害亚军奖励',
 '{"items":[{"item_id":"diamond_pickaxe","item_name":"亚军之镐","amount":1,"enchantments":["efficiency:6","unbreaking:4"],"lore":["&e&l周亚军专属","&f{period} Boss伤害亚军"]}]}',
 '',
 50),
('weekly', 'best_damage', 3, 3, '周季军奖励', '本周Boss伤害季军奖励',
 '{"items":[{"item_id":"diamond_axe","item_name":"季军之斧","amount":1,"enchantments":["sharpness:6","unbreaking:3"],"lore":["&b&l周季军专属","&f{period} Boss伤害季军"]}]}',
 '',
 25),

-- 月奖励配置
('monthly', 'best_damage', 1, 1, '月冠军奖励', '本月Boss伤害冠军奖励',
 '{"items":[{"item_id":"netherite_sword","item_name":"月冠军神剑","amount":1,"enchantments":["sharpness:8","unbreaking:6","mending:1"],"lore":["&6&l&n月冠军专属","&f{period} Boss伤害冠军","&7传奇级武器"]}]}',
 '["title {player} title &6&l&n月冠军","broadcast &6&l&n恭喜 {player} 获得本月Boss伤害冠军！","lp user {player} permission set ranking.monthly.champion"]',
 500),
('monthly', 'best_damage', 2, 2, '月亚军奖励', '本月Boss伤害亚军奖励',
 '{"items":[{"item_id":"netherite_pickaxe","item_name":"月亚军神镐","amount":1,"enchantments":["efficiency:7","unbreaking:5","mending:1"],"lore":["&e&l月亚军专属","&f{period} Boss伤害亚军"]}]}',
 '',
 250),
('monthly', 'best_damage', 3, 3, '月季军奖励', '本月Boss伤害季军奖励',
 '{"items":[{"item_id":"netherite_axe","item_name":"月季军神斧","amount":1,"enchantments":["sharpness:7","unbreaking:4","mending:1"],"lore":["&b&l月季军专属","&f{period} Boss伤害季军"]}]}',
 '',
 100),
('monthly', 'best_damage', 4, 5, '月优胜奖励', '本月Boss伤害优胜奖励',
 '{"items":[{"item_id":"diamond","item_name":"钻石奖励","amount":32,"lore":["&a月优胜奖励","&f{period} Boss伤害第{rank}名"]}]}',
 '',
 50);

-- 离线奖励存储表
CREATE TABLE IF NOT EXISTS offline_reward_storage (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(50),
    reward_type VARCHAR(20) NOT NULL,
    reward_data TEXT NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    claimed INTEGER DEFAULT 0,
    claimed_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_offline_reward_player ON offline_reward_storage(player_uuid, claimed);

-- DKP交易记录表
CREATE TABLE IF NOT EXISTS dkp_transaction_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(50),
    transaction_type VARCHAR(20) NOT NULL,
    points INTEGER NOT NULL DEFAULT 0,
    reason VARCHAR(200),
    transaction_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dkp_transaction_player ON dkp_transaction_records(player_uuid);

-- 创建版本记录表
CREATE TABLE IF NOT EXISTS entitytracker_version (
    version VARCHAR(20) PRIMARY KEY,
    upgrade_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入当前版本
INSERT OR REPLACE INTO entitytracker_version (version) VALUES ('1.1.0');
