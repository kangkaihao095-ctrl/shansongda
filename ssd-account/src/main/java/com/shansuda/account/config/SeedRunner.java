package com.shansuda.account.config;

import com.shansuda.account.domain.AppUser;
import com.shansuda.account.domain.Coupon;
import com.shansuda.account.domain.Merchant;
import com.shansuda.account.domain.MerchantSku;
import com.shansuda.account.domain.Rider;
import com.shansuda.account.repo.AppUserRepo;
import com.shansuda.account.repo.CouponRepo;
import com.shansuda.account.repo.MerchantRepo;
import com.shansuda.account.repo.MerchantSkuRepo;
import com.shansuda.account.repo.RiderRepo;
import com.shansuda.account.repo.UserAddressRepo;
import com.shansuda.common.catalog.CatalogImages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@Order(1)
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final DataSource dataSource;
    private final AppUserRepo userRepo;
    private final MerchantRepo merchantRepo;
    private final MerchantSkuRepo skuRepo;
    private final CouponRepo couponRepo;
    private final UserAddressRepo addressRepo;
    private final RiderRepo riderRepo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SeedRunner(DataSource dataSource, AppUserRepo userRepo, MerchantRepo merchantRepo,
                      MerchantSkuRepo skuRepo, CouponRepo couponRepo, UserAddressRepo addressRepo,
                      RiderRepo riderRepo) {
        this.dataSource = dataSource;
        this.userRepo = userRepo;
        this.merchantRepo = merchantRepo;
        this.skuRepo = skuRepo;
        this.couponRepo = couponRepo;
        this.addressRepo = addressRepo;
        this.riderRepo = riderRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureSchema();
        } catch (Exception ex) {
            log.warn("补齐账号表结构失败: {}", ex.getMessage());
        }
        try {
            polishCopy();
        } catch (Exception ex) {
            log.warn("更新正式文案失败: {}", ex.getMessage());
        }
        try {
            String hash = encoder.encode("demo123456");
            for (AppUser user : userRepo.findAll()) {
                if (!isSeedPhone(user.getPhone())) {
                    continue;
                }
                if ("CHANGE_ME".equals(user.getPasswordHash()) || !encoder.matches("demo123456", user.getPasswordHash())) {
                    user.setPasswordHash(hash);
                    userRepo.save(user);
                }
            }
        } catch (Exception ex) {
            log.warn("种子密码更新失败: {}", ex.getMessage());
        }
        try {
            seedShops();
            seedSkus();
            seedCoupons();
        } catch (Exception ex) {
            log.warn("补充商家商品失败: {}", ex.getMessage());
        }
        try {
            seedDemoRiders();
        } catch (Exception ex) {
            log.warn("演示骑手种子失败: {}", ex.getMessage());
        }
        try {
            seedRiderProfiles();
        } catch (Exception ex) {
            log.warn("骑手简介种子失败: {}", ex.getMessage());
        }
    }

    private void ensureSchema() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            safe(s, "ALTER TABLE member_sub ADD COLUMN auto_renew TINYINT(1) NOT NULL DEFAULT 0");
            safe(s, "ALTER TABLE app_user ADD COLUMN avatar_url VARCHAR(512) NULL");
            safe(s, "ALTER TABLE merchant ADD COLUMN category VARCHAR(32) NULL");
            safe(s, "ALTER TABLE merchant ADD COLUMN cover_url VARCHAR(255) NULL");
            safe(s, "ALTER TABLE merchant ADD COLUMN rating DOUBLE NULL");
            safe(s, "ALTER TABLE merchant ADD COLUMN promo VARCHAR(128) NULL");
            safe(s, "ALTER TABLE merchant ADD COLUMN online_status VARCHAR(20) NOT NULL DEFAULT 'ONLINE'");
            safe(s, "ALTER TABLE merchant_sku ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ONLINE'");
            safe(s, "ALTER TABLE merchant ADD COLUMN rating_avg DOUBLE NULL");
            safe(s, "ALTER TABLE merchant ADD COLUMN rating_count INT NOT NULL DEFAULT 0");
            safe(s, "ALTER TABLE merchant ADD COLUMN completed_count INT NOT NULL DEFAULT 0");
            safe(s, "ALTER TABLE merchant ADD COLUMN auto_accept TINYINT(1) NOT NULL DEFAULT 0");
            safe(s, "ALTER TABLE merchant ADD COLUMN intro VARCHAR(512) NULL");
            safe(s, "ALTER TABLE merchant ADD COLUMN phone VARCHAR(32) NULL");
            safe(s, "ALTER TABLE rider ADD COLUMN auto_report TINYINT(1) NOT NULL DEFAULT 0");
            safe(s, "ALTER TABLE rider ADD COLUMN auto_report_interval_sec INT NOT NULL DEFAULT 5");
            safe(s, "ALTER TABLE merchant_sku ADD COLUMN description VARCHAR(255) NULL");
            safe(s, "ALTER TABLE merchant_sku ADD COLUMN detail VARCHAR(512) NULL");
            safe(s, "ALTER TABLE merchant_sku ADD COLUMN month_sales INT NOT NULL DEFAULT 0");
            safe(s, "ALTER TABLE merchant_sku ADD COLUMN like_count INT NOT NULL DEFAULT 0");
            safe(s, "ALTER TABLE merchant_sku ADD COLUMN sku_key VARCHAR(160) NULL");
            safe(s, "CREATE UNIQUE INDEX uk_merchant_sku_key ON merchant_sku (sku_key)");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS merchant_review (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      merchant_id BIGINT NOT NULL,
                      order_id BIGINT NOT NULL,
                      user_id BIGINT NOT NULL,
                      score TINYINT NOT NULL,
                      content VARCHAR(512) NOT NULL,
                      sku_names VARCHAR(255) NULL,
                      like_count INT NOT NULL DEFAULT 0,
                      created_at TIMESTAMP NOT NULL,
                      UNIQUE KEY uk_review_order (order_id),
                      INDEX idx_review_merchant (merchant_id, created_at)
                    )
                    """);
            s.execute("""
                    CREATE TABLE IF NOT EXISTS review_like (
                      user_id BIGINT NOT NULL,
                      review_id BIGINT NOT NULL,
                      created_at TIMESTAMP NOT NULL,
                      PRIMARY KEY (user_id, review_id)
                    )
                    """);
            s.execute("""
                    CREATE TABLE IF NOT EXISTS rider_workday (
                      user_id BIGINT NOT NULL,
                      work_date DATE NOT NULL,
                      worked_seconds INT NOT NULL DEFAULT 0,
                      forced_offline TINYINT(1) NOT NULL DEFAULT 0,
                      last_tick_at TIMESTAMP NULL,
                      PRIMARY KEY (user_id, work_date)
                    )
                    """);
            safe(s, "CREATE INDEX idx_rider_lat_lon ON rider (lat, lon)");
            safe(s, "CREATE INDEX idx_rider_status_geo ON rider (online_status, accept_status, lat, lon)");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS merchant_sku (
                      id BIGINT PRIMARY KEY,
                      merchant_id BIGINT NOT NULL,
                      name VARCHAR(128) NOT NULL,
                      group_name VARCHAR(64) NOT NULL,
                      price_cents INT NOT NULL,
                      origin_price_cents INT NOT NULL,
                      image_url VARCHAR(255) NOT NULL,
                      spec VARCHAR(64) NOT NULL,
                      stock INT NOT NULL,
                      status VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
                      description VARCHAR(255) NULL,
                      detail VARCHAR(512) NULL,
                      month_sales INT NOT NULL DEFAULT 0,
                      like_count INT NOT NULL DEFAULT 0,
                      sku_key VARCHAR(160) NULL,
                      INDEX idx_sku_merchant (merchant_id),
                      UNIQUE KEY uk_merchant_sku_key (sku_key)
                    )
                    """);
            s.execute("""
                    CREATE TABLE IF NOT EXISTS coupon (
                      id BIGINT PRIMARY KEY,
                      name VARCHAR(128) NOT NULL,
                      merchant_id BIGINT NULL,
                      activity_id BIGINT NULL,
                      type VARCHAR(20) NOT NULL,
                      min_spend_cents INT NOT NULL,
                      discount_cents INT NOT NULL DEFAULT 0,
                      percent_off INT NOT NULL DEFAULT 0,
                      stock INT NOT NULL,
                      start_at TIMESTAMP NOT NULL,
                      end_at TIMESTAMP NOT NULL,
                      status VARCHAR(20) NOT NULL
                    )
                    """);
            s.execute("""
                    CREATE TABLE IF NOT EXISTS user_coupon (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      coupon_id BIGINT NOT NULL,
                      user_id BIGINT NOT NULL,
                      status VARCHAR(20) NOT NULL,
                      claimed_at TIMESTAMP NOT NULL,
                      used_order_id BIGINT NULL,
                      UNIQUE KEY uk_user_coupon (coupon_id, user_id)
                    )
                    """);
            s.execute("""
                    CREATE TABLE IF NOT EXISTS rider_profile (
                      user_id BIGINT PRIMARY KEY,
                      bio VARCHAR(512) NOT NULL,
                      started_on DATE NOT NULL,
                      on_time_rate DECIMAL(5,4) NOT NULL DEFAULT 0.9200,
                      tip_cents_total INT NOT NULL DEFAULT 0,
                      rating_avg DECIMAL(4,2) NOT NULL DEFAULT 4.80,
                      rating_count INT NOT NULL DEFAULT 0,
                      completed_count INT NOT NULL DEFAULT 0
                    )
                    """);
            s.execute("""
                    CREATE TABLE IF NOT EXISTS order_tip (
                      order_id BIGINT PRIMARY KEY,
                      rider_id BIGINT NOT NULL,
                      user_id BIGINT NOT NULL,
                      cents INT NOT NULL,
                      created_at TIMESTAMP NOT NULL,
                      INDEX idx_tip_rider (rider_id)
                    )
                    """);
            safe(s, "ALTER TABLE merchant MODIFY cover_url VARCHAR(512) NULL");
            safe(s, "ALTER TABLE merchant_sku MODIFY image_url VARCHAR(512) NOT NULL");
            safe(s, "ALTER TABLE app_user ADD COLUMN last_login_at TIMESTAMP NULL");
            safe(s, "ALTER TABLE coupon ADD COLUMN code VARCHAR(80) NULL");
            safe(s, "ALTER TABLE coupon ADD COLUMN member_only TINYINT(1) NOT NULL DEFAULT 0");
            safe(s, "ALTER TABLE coupon ADD COLUMN icon VARCHAR(32) NULL");
            safe(s, "ALTER TABLE coupon_template ADD COLUMN icon VARCHAR(32) NULL");
            safe(s, "ALTER TABLE coupon_template ADD COLUMN valid_seconds INT NULL");
            safe(s, "ALTER TABLE user_address ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0");
            safe(s, "ALTER TABLE merchant_review ADD COLUMN photo_urls VARCHAR(2048) NULL");
            safe(s, "ALTER TABLE merchant_review ADD COLUMN rider_score TINYINT NULL");
            safe(s, "ALTER TABLE merchant_review MODIFY content VARCHAR(512) NULL");
            safe(s, "ALTER TABLE order_tip ADD COLUMN gift_code VARCHAR(32) NULL");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS coupon_template (
                      code VARCHAR(64) PRIMARY KEY,
                      name VARCHAR(128) NOT NULL,
                      scene VARCHAR(32) NOT NULL,
                      type VARCHAR(20) NOT NULL,
                      min_spend_cents INT NOT NULL DEFAULT 0,
                      discount_cents INT NOT NULL DEFAULT 0,
                      percent_off INT NOT NULL DEFAULT 0,
                      member_only TINYINT(1) NOT NULL DEFAULT 0,
                      icon VARCHAR(32) NULL,
                      valid_seconds INT NULL
                    )
                    """);
            s.execute("""
                    CREATE TABLE IF NOT EXISTS coupon_grant_log (
                      grant_key VARCHAR(128) PRIMARY KEY,
                      user_id BIGINT NOT NULL,
                      scene VARCHAR(32) NOT NULL,
                      created_at TIMESTAMP NOT NULL,
                      INDEX idx_grant_user (user_id)
                    )
                    """);
            s.execute("""
                    CREATE TABLE IF NOT EXISTS user_cart (
                      user_id BIGINT PRIMARY KEY,
                      merchant_id BIGINT NULL,
                      shop_name VARCHAR(128) NULL,
                      cover_url VARCHAR(512) NULL,
                      items_json TEXT,
                      updated_at TIMESTAMP NULL
                    )
                    """);
            s.execute("""
                    CREATE TABLE IF NOT EXISTS merchant_promo (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      merchant_id BIGINT NOT NULL,
                      min_spend_cents INT NOT NULL DEFAULT 0,
                      off_cents INT NOT NULL DEFAULT 0,
                      status VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
                      UNIQUE KEY uk_merchant_promo (merchant_id)
                    )
                    """);
            s.execute("""
                    CREATE TABLE IF NOT EXISTS member_sub (
                      user_id BIGINT PRIMARY KEY,
                      plan VARCHAR(16) NOT NULL,
                      level INT NOT NULL DEFAULT 1,
                      paid_cents_net INT NOT NULL DEFAULT 0,
                      expire_at TIMESTAMP NOT NULL,
                      status VARCHAR(20) NOT NULL,
                      year_member TINYINT(1) NOT NULL DEFAULT 0,
                      auto_renew TINYINT(1) NOT NULL DEFAULT 0,
                      updated_at TIMESTAMP NOT NULL
                    )
                    """);
            safe(s, "ALTER TABLE member_sub ADD COLUMN auto_renew TINYINT(1) NOT NULL DEFAULT 0");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS member_pay_log (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      user_id BIGINT NOT NULL,
                      plan VARCHAR(16) NOT NULL,
                      channel VARCHAR(16) NOT NULL,
                      cents INT NOT NULL,
                      auto_renew TINYINT(1) NOT NULL DEFAULT 0,
                      created_at TIMESTAMP NOT NULL,
                      INDEX idx_member_pay_user (user_id, created_at)
                    )
                    """);
        }
    }

    private void polishCopy() {
        userRepo.findByPhone("13800000001").ifPresent(u -> renameIfDemo(u, "闪送达用户"));
        userRepo.findByPhone("13800000002").ifPresent(u -> renameIfDemo(u, "闪送达骑手"));
        userRepo.findByPhone("13800000003").ifPresent(u -> renameIfDemo(u, "闪送达鲜生店长"));
        addressRepo.findByUserId(1L).forEach(addr -> {
            String detail = addr.getDetail() == null ? "" : addr.getDetail();
            if (!detail.contains("外滩源") && !detail.contains("徐家汇") && !detail.contains("五角场")
                    && !detail.contains("陆家嘴")) {
                addr.setDetail("上海市黄浦区外滩源 33 号");
                addr.setLat(31.2397);
                addr.setLon(121.4903);
            }
            if (Boolean.TRUE.equals(addr.getIsDefault()) || detail.contains("外滩源")) {
                addr.setIsDefault(true);
            }
            addressRepo.save(addr);
        });
        seedLandmarkAddresses();
    }

    private void renameIfDemo(AppUser user, String name) {
        String current = user.getDisplayName() == null ? "" : user.getDisplayName();
        if (current.isBlank() || current.contains("演示") || current.contains("闪速达")
                || "闪速鲜生店长".equals(current)
                || current.equals(user.getPhone())) {
            user.setDisplayName(name);
            userRepo.save(user);
        }
    }

    private void seedShops() {
        List<Map<String, Object>> shops = List.of(
                shop("13800000003", "闪送达鲜生·南京东路", 31.2380, 121.4840, "上海市黄浦区南京东路 100 号",
                        "fresh", "/images/shop-fresh.jpg", 4.9, "满 30 减 8"),
                shop("13800000011", "黄埔小馆·外滩", 31.2336, 121.4904, "上海市黄浦区中山东一路 12 号",
                        "food", "/images/shop-food.jpg", 4.8, "本店招牌红烧肉"),
                shop("13800000012", "弄堂生煎·人民广场", 31.2312, 121.4756, "上海市黄浦区西藏中路 268 号",
                        "food", "/images/shop-shengjian.jpg", 4.7, "现煎出锅"),
                shop("13800000013", "茶百道·淮海中路", 31.2208, 121.4662, "上海市黄浦区淮海中路 138 号",
                        "dessert", "/images/shop-tea.jpg", 4.8, "第二杯优惠"),
                shop("13800000014", "鲜果时刻·陆家嘴", 31.2394, 121.5051, "上海市浦东新区陆家嘴西路 168 号",
                        "fresh", "/images/shop-fruit.jpg", 4.6, "当日鲜切"),
                shop("13800000015", "深夜食堂·福州路", 31.2341, 121.4808, "上海市黄浦区福州路 399 号",
                        "food", "/images/shop-night.jpg", 4.5, "夜间出餐"),
                shop("13800000016", "闪送达便利·南京西路", 31.2298, 121.4588, "上海市静安区南京西路 1266 号",
                        "market", "/images/shop-market.jpg", 4.7, "便利速达"),
                shop("13800000017", "康安大药房·西藏中路", 31.2322, 121.4768, "上海市黄浦区西藏中路 180 号",
                        "pharma", "/images/shop-pharma.jpg", 4.9, "处方外配"),
                shop("13800000018", "花田喜事·淮海路", 31.2216, 121.4674, "上海市黄浦区淮海中路 300 号",
                        "flower", "/images/shop-flower.jpg", 4.8, "当季花束"),
                shop("13800000019", "午后漫记·思南公馆", 31.2158, 121.4706, "上海市黄浦区思南路 39 号",
                        "tea", "/images/shop-cafe.jpg", 4.7, "咖啡配蛋糕"),
                shop("13800000020", "闪送达跑腿·外滩", 31.2362, 121.4908, "上海市黄浦区中山东一路 1 号",
                        "errand", "/images/shop-errand.jpg", 4.6, "同城代办")
        );
        String hash = encoder.encode("demo123456");
        for (Map<String, Object> row : shops) {
            String phone = (String) row.get("phone");
            AppUser user = userRepo.findByPhone(phone).orElseGet(() -> {
                AppUser created = new AppUser();
                created.setPhone(phone);
                created.setPasswordHash(hash);
                created.setRole("MERCHANT");
                created.setDisplayName((String) row.get("name"));
                created.setStatus("ACTIVE");
                created.setCreatedAt(Instant.now());
                return userRepo.save(created);
            });
            Merchant merchant = merchantRepo.findById(user.getId()).orElseGet(() -> {
                Merchant m = new Merchant();
                m.setUserId(user.getId());
                return m;
            });
            boolean created = merchant.getShopName() == null || merchant.getShopName().isBlank();
            if (created) {
                merchant.setShopName((String) row.get("name"));
                merchant.setLat((Double) row.get("lat"));
                merchant.setLon((Double) row.get("lon"));
                merchant.setAddress((String) row.get("address"));
                merchant.setCategory((String) row.get("category"));
                merchant.setCoverUrl(CatalogImages.shopCover(user.getId(), (String) row.get("category")));
                merchant.setRating((Double) row.get("rating"));
                merchant.setPromo((String) row.get("promo"));
            }
            if (merchant.getIntro() == null || merchant.getIntro().isBlank()) {
                merchant.setIntro(row.get("promo") + "。闪送达同城配送，出餐后按约定路线送达。");
            }
            if (merchant.getPhone() == null || merchant.getPhone().isBlank()) {
                merchant.setPhone("021-63" + phone.substring(Math.max(0, phone.length() - 4)));
            }
            if (merchant.getCategory() == null || merchant.getCategory().isBlank()) {
                merchant.setCategory((String) row.get("category"));
            }
            if (merchant.getOnlineStatus() == null || merchant.getOnlineStatus().isBlank()) {
                merchant.setOnlineStatus("ONLINE");
            }
            merchantRepo.save(merchant);
        }
    }

    private void seedSkus() {
        if (skuRepo.count() == 0) {
            long fresh = shopId("13800000003");
            long huangpu = shopId("13800000011");
            long shengjian = shopId("13800000012");
            long tea = shopId("13800000013");
            long fruit = shopId("13800000014");
            long night = shopId("13800000015");
            long market = shopId("13800000016");
            long pharma = shopId("13800000017");
            long flower = shopId("13800000018");
            long cafe = shopId("13800000019");
            long errand = shopId("13800000020");
            saveSku(1001, fresh, "时令水果拼盘", "鲜切水果", 1990, 2590, CatalogImages.skuImage(1001, "fresh", "鲜切水果", "时令水果拼盘"), "大份", 40);
            saveSku(1002, fresh, "有机生菜", "蔬菜", 680, 880, CatalogImages.skuImage(1002, "fresh", "蔬菜", "有机生菜"), "250g", 80);
            saveSku(1003, fresh, "鲜切西瓜", "鲜切水果", 990, 1290, CatalogImages.skuImage(1003, "fresh", "鲜切水果", "鲜切西瓜"), "盒", 50);
            saveSku(1101, huangpu, "红烧肉套餐", "热菜", 3280, 3880, CatalogImages.skuImage(1101, "food", "热菜", "红烧肉套餐"), "1 人份", 30);
            saveSku(1102, huangpu, "清炒时蔬", "热菜", 1680, 1980, CatalogImages.skuImage(1102, "food", "热菜", "清炒时蔬"), "份", 40);
            saveSku(1103, huangpu, "白米饭", "主食", 200, 200, CatalogImages.skuImage(1103, "food", "主食", "白米饭"), "碗", 99);
            saveSku(1201, shengjian, "招牌生煎", "点心", 1880, 2280, CatalogImages.skuImage(1201, "food", "点心", "招牌生煎"), "8 只", 60);
            saveSku(1202, shengjian, "小笼包", "点心", 1680, 1980, CatalogImages.skuImage(1202, "food", "点心", "小笼包"), "8 只", 60);
            saveSku(1301, tea, "杨枝甘露", "饮品", 1800, 2200, CatalogImages.skuImage(1301, "dessert", "饮品", "杨枝甘露"), "大杯", 80);
            saveSku(1302, tea, "珍珠奶茶", "饮品", 1400, 1600, CatalogImages.skuImage(1302, "dessert", "饮品", "珍珠奶茶"), "中杯", 80);
            saveSku(1401, fruit, "鲜榨橙汁", "饮品", 1600, 1900, CatalogImages.skuImage(1401, "fresh", "饮品", "鲜榨橙汁"), "瓶", 40);
            saveSku(1402, fruit, "草莓杯", "鲜切水果", 2200, 2600, CatalogImages.skuImage(1402, "fresh", "鲜切水果", "草莓杯"), "杯", 30);
            saveSku(1501, night, "红烧牛肉面", "面食", 2680, 2980, CatalogImages.skuImage(1501, "food", "面食", "红烧牛肉面"), "碗", 40);
            saveSku(1601, market, "农夫山泉", "饮料", 300, 300, CatalogImages.skuImage(1601, "market", "饮料", "农夫山泉"), "550ml", 99);
            saveSku(1602, market, "方便面", "速食", 550, 650, CatalogImages.skuImage(1602, "market", "速食", "方便面"), "桶", 99);
            saveSku(1701, pharma, "感冒清热颗粒", "常用药", 1680, 1980, CatalogImages.skuImage(1701, "pharma", "常用药", "感冒清热颗粒"), "10 袋", 40);
            saveSku(1801, flower, "韩式花束", "鲜花", 12800, 15800, CatalogImages.skuImage(1801, "flower", "鲜花", "韩式花束"), "束", 12);
            saveSku(1802, flower, "草莓奶油蛋糕", "蛋糕", 8800, 10800, CatalogImages.skuImage(1802, "flower", "蛋糕", "草莓奶油蛋糕"), "6 寸", 10);
            saveSku(1901, cafe, "芝士蛋糕", "甜点", 3200, 3600, CatalogImages.skuImage(1901, "tea", "甜点", "芝士蛋糕"), "片", 20);
            saveSku(1902, cafe, "美式咖啡", "饮品", 1800, 2000, CatalogImages.skuImage(1902, "tea", "饮品", "美式咖啡"), "中杯", 40);
            saveSku(2001, errand, "代取快递", "跑腿", 1500, 1500, CatalogImages.skuImage(2001, "errand", "跑腿", "代取快递"), "件", 99);
            saveSku(2002, errand, "帮买文件", "跑腿", 2000, 2000, CatalogImages.skuImage(2002, "errand", "跑腿", "帮买文件"), "次", 99);
        }
        try (var c = dataSource.getConnection(); var s = c.createStatement()) {
            s.execute("UPDATE merchant_sku SET status='ONLINE' WHERE status IS NULL OR status=''");
            s.execute("UPDATE merchant_sku SET image_url='/images/dishes/food/1.jpg' WHERE image_url IS NULL OR image_url=''");
            s.execute("UPDATE merchant_sku SET description = name WHERE description IS NULL OR description=''");
            s.execute("UPDATE merchant_sku SET detail = CONCAT(name, '，规格 ', spec, '。') WHERE detail IS NULL OR detail=''");
            s.execute("UPDATE merchant SET completed_count = GREATEST(COALESCE(completed_count,0), 80) WHERE shop_name LIKE '闪送达鲜生%'");
        } catch (Exception ex) {
            log.warn("补齐商品文案失败: {}", ex.getMessage());
        }
    }

    private void seedCoupons() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2027-12-31T15:59:59Z");
        upsertCoupon(1L, "新客满减券", null, 2L, "AMOUNT", 2000, 500, 0, 200, start, end, "ACT_COUPON", false, "minus");
        upsertCoupon(2L, "鲜生满减", shopId("13800000003"), null, "AMOUNT", 3000, 800, 0, 80, start, end, "FRESH_30_8", false, "category");
        upsertCoupon(3L, "全场九折", null, null, "PERCENT", 1500, 0, 10, 200, start, end, "PERCENT_10", false, "percent");
        upsertCoupon(10L, "新客 15 元无门槛", null, null, "AMOUNT", 0, 1500, 0, 9999, start, end, "NEWCOMER_15", false, "newcomer");
        upsertCoupon(11L, "运费立减 3 元", null, null, "FREIGHT", 0, 300, 0, 9999, start, end, "FREIGHT_3", false, "free");
        seedShopPromo();
        upsertTemplate("NEWCOMER_15", "新客 15 元无门槛", "NEWCOMER", "AMOUNT", 0, 1500, false, "newcomer", 7 * 86400);
        upsertTemplate("LAPSED_30_20", "满 30 减 20", "LAPSED", "AMOUNT", 3000, 2000, false, "minus", 3 * 86400);
        upsertTemplate("LAPSED_50_25", "满 50 减 25", "LAPSED", "AMOUNT", 5000, 2500, false, "minus", 3 * 86400);
        upsertTemplate("FREQ_25_5", "满 25 减 5", "FREQUENT", "AMOUNT", 2500, 500, false, "minus", 3 * 86400);
        upsertTemplate("FREQ_40_8", "满 40 减 8", "FREQUENT", "AMOUNT", 4000, 800, false, "minus", 3 * 86400);
        upsertTemplate("RETURN_20_6", "满 20 减 6", "RETURN", "AMOUNT", 2000, 600, false, "minus", 3 * 86400);
        upsertTemplate("RETURN_35_10", "满 35 减 10", "RETURN", "AMOUNT", 3500, 1000, false, "minus", 3 * 86400);
        upsertTemplate("MEMBER_3", "会员 3 元无门槛红包", "MEMBER", "AMOUNT", 0, 300, true, "member", 0);
        upsertTemplate("MEMBER_5", "会员 5 元无门槛红包", "MEMBER", "AMOUNT", 0, 500, true, "member", 0);
        upsertTemplate("MEMBER_8", "会员 8 元无门槛红包", "MEMBER", "AMOUNT", 0, 800, true, "member", 0);
        upsertTemplate("HOME_20_6", "满 20 减 6", "HOME", "AMOUNT", 2000, 600, false, "minus", 3 * 86400);
        upsertTemplate("HOME_0_3", "3 元无门槛", "HOME", "AMOUNT", 0, 300, false, "free", 3 * 86400);
        upsertTemplate("HOME_FOOD_25_8", "美食满 25 减 8", "HOME", "AMOUNT", 2500, 800, false, "category", 3 * 86400);
        upsertTemplate("HOME_40_10", "满 40 减 10", "HOME", "AMOUNT", 4000, 1000, false, "minus", 3 * 86400);
        upsertTemplate("HOME_0_5", "5 元无门槛", "HOME", "AMOUNT", 0, 500, false, "free", 3 * 86400);
        upsertTemplate("HOME_FRESH_30_8", "生鲜满 30 减 8", "HOME", "AMOUNT", 3000, 800, false, "category", 3 * 86400);
        upsertTemplate("LOGIN_0_4", "登录礼 4 元无门槛", "LOGIN", "AMOUNT", 0, 400, false, "free", 86400);
        upsertTemplate("LOGIN_25_8", "登录礼满 25 减 8", "LOGIN", "AMOUNT", 2500, 800, false, "minus", 86400);
        upsertTemplate("LOGIN_35_12", "登录礼满 35 减 12", "LOGIN", "AMOUNT", 3500, 1200, false, "minus", 86400);
        upsertTemplate("LOGIN_0_6", "登录礼 6 元无门槛", "LOGIN", "AMOUNT", 0, 600, false, "free", 86400);
        upsertTemplate("LOGIN_FOOD_20_5", "登录礼美食满 20 减 5", "LOGIN", "AMOUNT", 2000, 500, false, "category", 86400);
        seedMemberAndNewcomer();
    }

    private void upsertTemplate(String code, String name, String scene, String type,
                                int minSpend, int discount, boolean memberOnly, String icon, int validSeconds) {
        try (var c = dataSource.getConnection();
             var ps = c.prepareStatement("""
                     INSERT INTO coupon_template (code, name, scene, type, min_spend_cents, discount_cents, percent_off, member_only, icon, valid_seconds)
                     VALUES (?,?,?,?,?,?,0,?,?,?)
                     ON DUPLICATE KEY UPDATE name=VALUES(name), scene=VALUES(scene), type=VALUES(type),
                       min_spend_cents=VALUES(min_spend_cents), discount_cents=VALUES(discount_cents),
                       member_only=VALUES(member_only), icon=VALUES(icon), valid_seconds=VALUES(valid_seconds)
                     """)) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, scene);
            ps.setString(4, type);
            ps.setInt(5, minSpend);
            ps.setInt(6, discount);
            ps.setInt(7, memberOnly ? 1 : 0);
            ps.setString(8, icon);
            ps.setInt(9, validSeconds);
            ps.executeUpdate();
        } catch (Exception ex) {
            log.warn("券模板 {} 写入失败: {}", code, ex.getMessage());
        }
    }

    private void seedMemberAndNewcomer() {
        long userId = shopId("13800000001");
        if (userId <= 0) {
            userId = 1L;
        }
        try (var c = dataSource.getConnection()) {
            try (var ps = c.prepareStatement("""
                    INSERT INTO member_sub (user_id, plan, level, paid_cents_net, expire_at, status, year_member, updated_at)
                    VALUES (?, 'YEAR', 3, 128000, DATE_ADD(NOW(), INTERVAL 300 DAY), 'ACTIVE', 1, NOW())
                    ON DUPLICATE KEY UPDATE plan='YEAR', level=GREATEST(level,3),
                      paid_cents_net=GREATEST(paid_cents_net, 128000), year_member=1, status='ACTIVE',
                      expire_at=GREATEST(expire_at, DATE_ADD(NOW(), INTERVAL 300 DAY))
                    """)) {
                ps.setLong(1, userId);
                ps.executeUpdate();
            }
            try (var ps = c.prepareStatement("""
                    INSERT IGNORE INTO user_coupon (coupon_id, user_id, status, claimed_at)
                    VALUES (10, ?, 'UNUSED', NOW())
                    """)) {
                ps.setLong(1, userId);
                ps.executeUpdate();
            }
            try (var ps = c.prepareStatement("""
                    INSERT IGNORE INTO user_coupon (coupon_id, user_id, status, claimed_at)
                    VALUES (11, ?, 'UNUSED', NOW())
                    """)) {
                ps.setLong(1, userId);
                ps.executeUpdate();
            }
            try (var ps = c.prepareStatement("""
                    INSERT IGNORE INTO coupon_grant_log (grant_key, user_id, scene, created_at)
                    VALUES (?, ?, 'NEWCOMER', NOW())
                    """)) {
                ps.setString(1, "GRANT:" + userId + ":ONCE:NEWCOMER");
                ps.setLong(2, userId);
                ps.executeUpdate();
            }
        } catch (Exception ex) {
            log.warn("会员/新客券种子失败: {}", ex.getMessage());
        }
    }

    private void upsertCoupon(long id, String name, Long merchantId, Long activityId, String type,
                              int minSpend, int discount, int percent, int stock, Instant start, Instant end,
                              String code, boolean memberOnly, String icon) {
        Coupon coupon = couponRepo.findById(id).orElseGet(Coupon::new);
        coupon.setId(id);
        coupon.setName(name);
        coupon.setMerchantId(merchantId);
        coupon.setActivityId(activityId);
        coupon.setType(type);
        coupon.setMinSpendCents(minSpend);
        coupon.setDiscountCents(discount);
        coupon.setPercentOff(percent);
        coupon.setStock(stock);
        coupon.setStartAt(start);
        coupon.setEndAt(end);
        coupon.setStatus("ONLINE");
        coupon.setCode(code);
        coupon.setMemberOnly(memberOnly);
        coupon.setIcon(icon);
        couponRepo.save(coupon);
    }

    private void seedShopPromo() {
        long merchantId = shopId("13800000003");
        if (merchantId <= 0) {
            merchantId = 3L;
        }
        try (var c = dataSource.getConnection();
             var ps = c.prepareStatement("""
                     INSERT INTO merchant_promo (merchant_id, min_spend_cents, off_cents, status)
                     VALUES (?, 2000, 200, 'ONLINE')
                     ON DUPLICATE KEY UPDATE min_spend_cents=VALUES(min_spend_cents),
                       off_cents=VALUES(off_cents), status='ONLINE'
                     """)) {
            ps.setLong(1, merchantId);
            ps.executeUpdate();
        } catch (Exception ex) {
            log.warn("店铺满减种子失败: {}", ex.getMessage());
        }
    }

    private void seedLandmarkAddresses() {
        record Spot(double lat, double lon, String detail, boolean def) {
        }
        List<Spot> spots = List.of(
                new Spot(31.2397, 121.4903, "上海市黄浦区外滩源 33 号", true),
                new Spot(31.1946, 121.4367, "上海市徐汇区徐家汇港汇广场", false),
                new Spot(31.2994, 121.5145, "上海市杨浦区五角场创智天地", false),
                new Spot(31.2354, 121.5056, "上海市浦东新区陆家嘴国金中心", false)
        );
        List<com.shansuda.account.domain.UserAddress> existing = addressRepo.findByUserId(1L);
        for (Spot spot : spots) {
            boolean has = existing.stream().anyMatch(a -> spot.detail.equals(a.getDetail())
                    || (Math.abs(a.getLat() - spot.lat) < 0.0008 && Math.abs(a.getLon() - spot.lon) < 0.0008));
            if (has) {
                continue;
            }
            com.shansuda.account.domain.UserAddress row = new com.shansuda.account.domain.UserAddress();
            row.setUserId(1L);
            row.setLat(spot.lat);
            row.setLon(spot.lon);
            row.setDetail(spot.detail);
            row.setIsDefault(spot.def && existing.stream().noneMatch(a -> Boolean.TRUE.equals(a.getIsDefault())));
            addressRepo.save(row);
        }
        List<com.shansuda.account.domain.UserAddress> all = addressRepo.findByUserId(1L);
        boolean anyDefault = all.stream().anyMatch(a -> Boolean.TRUE.equals(a.getIsDefault()));
        if (!anyDefault && !all.isEmpty()) {
            com.shansuda.account.domain.UserAddress first = all.get(0);
            first.setIsDefault(true);
            addressRepo.save(first);
        }
    }

    private void saveSku(long id, long merchantId, String name, String group, int price, int origin,
                         String image, String spec, int stock) {
        if (merchantId <= 0) {
            return;
        }
        MerchantSku sku = new MerchantSku();
        sku.setId(id);
        sku.setMerchantId(merchantId);
        sku.setName(name);
        sku.setGroupName(group);
        sku.setPriceCents(price);
        sku.setOriginPriceCents(origin);
        sku.setImageUrl(image);
        sku.setSpec(spec);
        sku.setStock(stock);
        sku.setStatus("ONLINE");
        sku.setDescription(name);
        sku.setDetail(name + "，规格 " + spec + "。");
        sku.setMonthSales(20);
        sku.setLikeCount(0);
        sku.setSkuKey("SSD-SEED:" + id);
        skuRepo.save(sku);
    }

    /**
     * 黄浦 OSM 范围内补一批演示骑手，密码 demo123456。主演示账号仍是 13800000002，大厅只登该号。
     * 坐标对齐现有店铺 / 骨架路网，不要堆在同一点。失败只打日志，不阻断启动。
     */
    private void seedDemoRiders() {
        String hash = encoder.encode("demo123456");
        List<DemoRider> extras = List.of(
                new DemoRider("13800000021", "陈浩", 31.2392, 121.4908, "ONLINE", "IDLE"),
                new DemoRider("13800000022", "林小雨", 31.2376, 121.4820, "ONLINE", "IDLE"),
                new DemoRider("13800000023", "王磊", 31.2324, 121.4742, "ONLINE", "IDLE"),
                new DemoRider("13800000024", "赵敏", 31.2210, 121.4658, "ONLINE", "IDLE"),
                new DemoRider("13800000025", "周杰", 31.2346, 121.4798, "ONLINE", "BUSY"),
                new DemoRider("13800000026", "吴芳", 31.2168, 121.4710, "ONLINE", "IDLE"),
                new DemoRider("13800000027", "孙强", 31.2330, 121.4772, "ONLINE", "IDLE"),
                new DemoRider("13800000028", "郑悦", 31.2300, 121.4598, "OFFLINE", "IDLE"),
                new DemoRider("13800000029", "黄伟", 31.2386, 121.4992, "ONLINE", "IDLE"),
                new DemoRider("13800000030", "马丽", 31.2358, 121.4876, "ONLINE", "IDLE"),
                new DemoRider("13800000004", "徐鹏", 31.2272, 121.4868, "OFFLINE", "IDLE")
        );
        Instant now = Instant.now();
        int n = 0;
        for (DemoRider row : extras) {
            AppUser user = userRepo.findByPhone(row.phone()).orElse(null);
            if (user == null) {
                user = new AppUser();
                user.setPhone(row.phone());
                user.setPasswordHash(hash);
                user.setRole("RIDER");
                user.setDisplayName(row.name());
                user.setStatus("ACTIVE");
                user.setCreatedAt(now);
                user = userRepo.save(user);
            } else if (!"RIDER".equals(user.getRole())) {
                log.warn("跳过演示骑手 {}：已存在角色 {}", row.phone(), user.getRole());
                continue;
            } else if (user.getDisplayName() == null || user.getDisplayName().isBlank()
                    || user.getDisplayName().contains("演示") || user.getDisplayName().equals(user.getPhone())) {
                user.setDisplayName(row.name());
                userRepo.save(user);
            }
            final long uid = user.getId();
            Rider rider = riderRepo.findById(uid).orElseGet(() -> {
                Rider created = new Rider();
                created.setUserId(uid);
                created.setVersion(1L);
                created.setAutoReport(false);
                created.setAutoReportIntervalSec(5);
                return created;
            });
            rider.setLat(row.lat());
            rider.setLon(row.lon());
            rider.setOnlineStatus(row.online());
            rider.setAcceptStatus(row.accept());
            rider.setUpdateTime(now);
            if (rider.getVersion() == null) {
                rider.setVersion(1L);
            }
            riderRepo.save(rider);
            n++;
        }
        log.info("演示骑手已补齐 {} 人（主账号仍为 13800000002）", n);
    }

    private record DemoRider(String phone, String name, double lat, double lon, String online, String accept) {
    }

    private void seedRiderProfiles() {
        try (var c = dataSource.getConnection(); var s = c.createStatement()) {
            s.execute("""
                    INSERT INTO rider_profile (user_id, bio, started_on, on_time_rate, tip_cents_total,
                      rating_avg, rating_count, completed_count)
                    SELECT r.user_id, '闪送达骑手，出餐后保温袋封装，按约定路线准时送达。',
                      DATE_SUB(CURDATE(), INTERVAL 6 MONTH), 0.9200, 0, 4.80, 0, 0
                    FROM rider r
                    LEFT JOIN rider_profile p ON p.user_id = r.user_id
                    WHERE p.user_id IS NULL
                    """);
            long riderId = shopId("13800000002");
            if (riderId <= 0) {
                riderId = 2L;
            }
            try (var ps = c.prepareStatement("""
                    UPDATE rider_profile SET
                      bio = ?,
                      started_on = '2022-03-18',
                      on_time_rate = 0.9780,
                      rating_avg = 4.92,
                      rating_count = GREATEST(COALESCE(rating_count,0), 164),
                      completed_count = GREATEST(COALESCE(completed_count,0), 186)
                    WHERE user_id = ?
                    """)) {
                ps.setString(1, "闪送达专职骑手，2022 年起在黄浦、静安片区接单。"
                        + "熟悉外滩、南京东路与陆家嘴写字楼动线，出餐后优先保温袋封装，雨天加防水面罩。"
                        + "准时率按完成单是否晚于约定 ETA 估算；演示环境无真实超时轨迹，以上为档案口径。");
                ps.setLong(2, riderId);
                ps.executeUpdate();
            }
            int[] cents = {200, 200, 500, 500, 1000, 2000};
            String[] gifts = {"WATER", "WATER", "MILKTEA", "MILKTEA", "GIFT", "CHICKEN"};
            try (var ps = c.prepareStatement("""
                    INSERT IGNORE INTO order_tip (order_id, rider_id, user_id, cents, gift_code, created_at)
                    VALUES (?,?,?,?,?,DATE_SUB(NOW(), INTERVAL ? DAY))
                    """)) {
                int sum = 0;
                for (int i = 0; i < cents.length; i++) {
                    long orderId = 8_000_000_000_000_001L + i;
                    ps.setLong(1, orderId);
                    ps.setLong(2, riderId);
                    ps.setLong(3, 1L);
                    ps.setInt(4, cents[i]);
                    ps.setString(5, gifts[i]);
                    ps.setInt(6, i + 1);
                    ps.addBatch();
                    sum += cents[i];
                }
                ps.executeBatch();
                try (var up = c.prepareStatement(
                        "UPDATE order_tip SET created_at = DATE_SUB(NOW(), INTERVAL (order_id - 8000000000000001) DAY) WHERE rider_id=? AND order_id BETWEEN 8000000000000001 AND 8000000000000006")) {
                    up.setLong(1, riderId);
                    up.executeUpdate();
                }
                try (var up = c.prepareStatement(
                        "UPDATE rider_profile SET tip_cents_total = GREATEST(COALESCE(tip_cents_total,0), ?) WHERE user_id=?")) {
                    up.setInt(1, sum);
                    up.setLong(2, riderId);
                    up.executeUpdate();
                }
            }
            try (var ps = c.prepareStatement("""
                    INSERT INTO rider_workday (user_id, work_date, worked_seconds, forced_offline)
                    VALUES (?, DATE_SUB(CURDATE(), INTERVAL ? DAY), ?, 0)
                    ON DUPLICATE KEY UPDATE worked_seconds = IF(work_date = CURDATE(), worked_seconds,
                      IF(worked_seconds > 0, worked_seconds, VALUES(worked_seconds)))
                    """)) {
                for (int d = 1; d <= 6; d++) {
                    ps.setLong(1, riderId);
                    ps.setInt(2, d);
                    ps.setInt(3, 4 * 3600 + (d % 3) * 1800);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (Exception ex) {
            log.warn("骑手简介种子失败: {}", ex.getMessage());
        }
    }

    private long shopId(String phone) {
        return userRepo.findByPhone(phone).map(AppUser::getId).orElse(0L);
    }

    private static Map<String, Object> shop(String phone, String name, double lat, double lon, String address,
                                            String category, String cover, double rating, String promo) {
        return Map.of(
                "phone", phone, "name", name, "lat", lat, "lon", lon, "address", address,
                "category", category, "cover", cover, "rating", rating, "promo", promo
        );
    }

    private static boolean isSeedPhone(String phone) {
        return phone != null && (phone.startsWith("1380000000") || phone.startsWith("1380000001") || phone.startsWith("1380000002"));
    }

    private static void safe(Statement s, String sql) {
        try {
            s.execute(sql);
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (!msg.contains("Duplicate column") && !msg.contains("already exists")) {
                LoggerFactory.getLogger(SeedRunner.class).warn("SQL 跳过: {} / {}", sql, msg);
            }
        }
    }
}
