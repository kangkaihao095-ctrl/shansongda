CREATE USER IF NOT EXISTS 'canal'@'%' IDENTIFIED BY 'canal';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;

CREATE DATABASE IF NOT EXISTS ssd_account DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ssd_activity DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ssd_leaf DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ssd_order_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ssd_order_1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ssd_order_2 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ssd_order_3 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ssd_account;

CREATE TABLE app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  avatar_url VARCHAR(512) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rider (
  user_id BIGINT PRIMARY KEY,
  online_status VARCHAR(20) NOT NULL,
  accept_status VARCHAR(20) NOT NULL,
  lat DOUBLE NOT NULL,
  lon DOUBLE NOT NULL,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 1,
  auto_report TINYINT(1) NOT NULL DEFAULT 0,
  auto_report_interval_sec INT NOT NULL DEFAULT 5,
  CONSTRAINT fk_rider_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE merchant (
  user_id BIGINT PRIMARY KEY,
  shop_name VARCHAR(128) NOT NULL,
  lat DOUBLE NOT NULL,
  lon DOUBLE NOT NULL,
  address VARCHAR(255) NOT NULL,
  online_status VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
  intro VARCHAR(512) NULL,
  phone VARCHAR(32) NULL,
  CONSTRAINT fk_merchant_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE user_address (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  lat DOUBLE NOT NULL,
  lon DOUBLE NOT NULL,
  detail VARCHAR(255) NOT NULL,
  INDEX idx_address_user (user_id)
);

INSERT INTO app_user (id, phone, password_hash, role, display_name, status) VALUES
  (1, '13800000001', 'CHANGE_ME', 'USER', '闪送达用户', 'ACTIVE'),
  (2, '13800000002', 'CHANGE_ME', 'RIDER', '闪送达骑手', 'ACTIVE'),
  (3, '13800000003', 'CHANGE_ME', 'MERCHANT', '闪送达鲜生店长', 'ACTIVE');

INSERT INTO rider (user_id, online_status, accept_status, lat, lon, version) VALUES
  (2, 'ONLINE', 'IDLE', 31.2304, 121.4737, 1);

CREATE TABLE rider_profile (
  user_id BIGINT PRIMARY KEY,
  bio VARCHAR(512) NOT NULL,
  started_on DATE NOT NULL,
  on_time_rate DECIMAL(5,4) NOT NULL DEFAULT 0.9200,
  tip_cents_total INT NOT NULL DEFAULT 0,
  rating_avg DECIMAL(4,2) NOT NULL DEFAULT 4.80,
  rating_count INT NOT NULL DEFAULT 0,
  completed_count INT NOT NULL DEFAULT 0
);

CREATE TABLE order_tip (
  order_id BIGINT PRIMARY KEY,
  rider_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  cents INT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  INDEX idx_tip_rider (rider_id)
);

INSERT INTO merchant (user_id, shop_name, lat, lon, address, online_status) VALUES
  (3, '闪送达鲜生·南京东路', 31.2380, 121.4840, '上海市黄浦区南京东路 100 号', 'ONLINE');

INSERT INTO user_address (id, user_id, lat, lon, detail) VALUES
  (1, 1, 31.2240, 121.4690, '上海市黄浦区外滩源 33 号');

CREATE TABLE rider_workday (
  user_id BIGINT NOT NULL,
  work_date DATE NOT NULL,
  worked_seconds INT NOT NULL DEFAULT 0,
  forced_offline TINYINT(1) NOT NULL DEFAULT 0,
  last_tick_at TIMESTAMP NULL,
  PRIMARY KEY (user_id, work_date)
);

USE ssd_activity;

CREATE TABLE activity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(20) NOT NULL,
  name VARCHAR(128) NOT NULL,
  start_at TIMESTAMP NOT NULL,
  end_at TIMESTAMP NOT NULL,
  status VARCHAR(20) NOT NULL
);

CREATE TABLE activity_sku (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  price_cents INT NOT NULL,
  origin_stock INT NOT NULL,
  INDEX idx_sku_activity (activity_id)
);

CREATE TABLE coupon_grant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  scene VARCHAR(32) NOT NULL,
  coupon_code VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_coupon (activity_id, user_id, scene)
);

CREATE TABLE seckill_idem (
  idem_key VARCHAR(128) PRIMARY KEY,
  order_id BIGINT NULL,
  payload JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE coupon_idem (
  idem_key VARCHAR(128) PRIMARY KEY,
  activity_id BIGINT NULL,
  user_id BIGINT NULL,
  payload JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO activity (id, type, name, start_at, end_at, status) VALUES
  (1, 'SECKILL', '午餐爆品秒杀', '2025-01-01 00:00:00', '2027-12-31 23:59:59', 'ONLINE'),
  (2, 'COUPON', '新客满减券', '2025-01-01 00:00:00', '2027-12-31 23:59:59', 'ONLINE');

INSERT INTO activity_sku (id, activity_id, sku_id, name, price_cents, origin_stock) VALUES
  (1, 1, 1001, '时令水果拼盘', 990, 20),
  (2, 2, 2001, '满 20 减 5 券', 0, 100);

USE ssd_leaf;

CREATE TABLE leaf_alloc (
  biz_tag VARCHAR(64) PRIMARY KEY,
  max_id BIGINT NOT NULL,
  step INT NOT NULL,
  description VARCHAR(128) NOT NULL,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO leaf_alloc (biz_tag, max_id, step, description) VALUES
  ('order_id', 10000, 1000, '闪送达订单号');

-- 4 库 × 8 表
DROP PROCEDURE IF EXISTS ssd_create_order_tables;
DELIMITER $$
CREATE PROCEDURE ssd_create_order_tables()
BEGIN
  DECLARE d INT DEFAULT 0;
  DECLARE t INT;
  DECLARE dbname VARCHAR(32);
  DECLARE tbl VARCHAR(32);
  WHILE d < 4 DO
    SET dbname = CONCAT('ssd_order_', d);
    SET t = 0;
    WHILE t < 8 DO
      SET tbl = CONCAT('t_order_', t);
      SET @ddl = CONCAT(
        'CREATE TABLE IF NOT EXISTS `', dbname, '`.`', tbl, '` (',
        'id BIGINT PRIMARY KEY,',
        'user_id BIGINT NOT NULL,',
        'merchant_id BIGINT NOT NULL,',
        'rider_id BIGINT NULL,',
        'status VARCHAR(20) NOT NULL,',
        'goods_amount_cents INT NOT NULL,',
        'freight_cents INT NOT NULL,',
        'freight_strategy VARCHAR(32) NOT NULL,',
        'penalty_cents INT NOT NULL DEFAULT 0,',
        'user_lat DOUBLE NOT NULL,',
        'user_lon DOUBLE NOT NULL,',
        'merchant_lat DOUBLE NOT NULL,',
        'merchant_lon DOUBLE NOT NULL,',
        'address_detail VARCHAR(255) NOT NULL,',
        'activity_id BIGINT NULL,',
        'sku_snapshot JSON NULL,',
        'idempotency_key VARCHAR(128) NOT NULL,',
        'created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,',
        'updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,',
        'version BIGINT NOT NULL DEFAULT 0,',
        'UNIQUE KEY uk_idem (idempotency_key),',
        'INDEX idx_user_id (user_id, id),',
        'INDEX idx_merchant_id (merchant_id, id),',
        'INDEX idx_rider_id (rider_id, id)',
        ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4'
      );
      PREPARE stmt FROM @ddl;
      EXECUTE stmt;
      DEALLOCATE PREPARE stmt;
      SET t = t + 1;
    END WHILE;
    SET d = d + 1;
  END WHILE;
END$$
DELIMITER ;
CALL ssd_create_order_tables();
DROP PROCEDURE ssd_create_order_tables;

GRANT ALL PRIVILEGES ON ssd_account.* TO 'shansuda'@'%';
GRANT ALL PRIVILEGES ON ssd_activity.* TO 'shansuda'@'%';
GRANT ALL PRIVILEGES ON ssd_leaf.* TO 'shansuda'@'%';
GRANT ALL PRIVILEGES ON ssd_order_0.* TO 'shansuda'@'%';
GRANT ALL PRIVILEGES ON ssd_order_1.* TO 'shansuda'@'%';
GRANT ALL PRIVILEGES ON ssd_order_2.* TO 'shansuda'@'%';
GRANT ALL PRIVILEGES ON ssd_order_3.* TO 'shansuda'@'%';
FLUSH PRIVILEGES;
