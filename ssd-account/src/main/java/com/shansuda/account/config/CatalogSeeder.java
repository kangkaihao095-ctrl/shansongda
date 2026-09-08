package com.shansuda.account.config;

import com.shansuda.account.catalog.CatalogData;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按店名 / sku_key 幂等扩到 8×15 店、每店 42 SKU。图片写入本站真实 JPG 路径，已有库 UPDATE。失败只打日志。
 */
@Component
@Order(2)
public class CatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);
    private static final int SKU_PER_SHOP = 42;
    private static final String[] REVIEW_NICKS = {"外滩夜食客", "陆家嘴白领", "思南路甜品控"};
    private static final String[] REVIEW_PHONES = {"13800000031", "13800000032", "13800000033"};
    private static final String[] REVIEW_TEXTS = {
            "出餐快，味道稳定，会再点。",
            "包装认真，分量够，适合工作日午餐。",
            "骑手送到还热乎，店铺评分配得上。",
            "第一次点，味道比预期好。",
            "环境一般但餐品扎实，同城值得回购。"
    };

    private final DataSource dataSource;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public CatalogSeeder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (Exception ex) {
            log.warn("目录扩容失败（不阻断启动）: {}", ex.getMessage());
        }
    }

    private void seed() throws Exception {
        long t0 = System.currentTimeMillis();
        String hash = encoder.encode("demo123456");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            Set<String> shopNames = loadStrings(c, "SELECT shop_name FROM merchant WHERE shop_name IS NOT NULL");
            Set<String> phones = loadStrings(c, "SELECT phone FROM app_user WHERE phone IS NOT NULL");
            Set<String> skuKeys = loadStrings(c, "SELECT sku_key FROM merchant_sku WHERE sku_key IS NOT NULL");
            Map<String, Long> shopIds = loadShopIds(c);
            seedReviewUsers(c, hash, phones);
            int shopIdx = 0;
            int createdShops = 0;
            for (String cat : CatalogData.CATEGORIES) {
                String[] shops = CatalogData.shops(cat);
                for (int i = 0; i < shops.length; i++) {
                    String name = shops[i];
                    if (!shopNames.contains(name)) {
                        String phone = "13900" + String.format("%06d", shopIdx + 1001);
                        if (phones.contains(phone)) {
                            phone = "13901" + String.format("%06d", shopIdx + 1001);
                        }
                        long userId = insertMerchantUser(c, phone, hash, name);
                        double[] xy = CatalogData.coord(shopIdx);
                        boolean online = shopIdx % 11 != 0;
                        int completed = 40 + Math.abs(name.hashCode() % 420);
                        insertMerchant(c, userId, name, xy[0], xy[1], CatalogData.address(shopIdx), cat,
                                CatalogImages.shopCover(userId, cat), 4.5 + (Math.abs(name.hashCode()) % 5) * 0.1,
                                CatalogData.promo(cat, i), online ? "ONLINE" : "OFFLINE", completed);
                        shopNames.add(name);
                        shopIds.put(name, userId);
                        phones.add(phone);
                        createdShops++;
                    } else if (!shopIds.containsKey(name)) {
                        // 店名已在但未进 map
                    }
                    shopIdx++;
                }
            }
            c.commit();

            shopIds = loadShopIds(c);
            Map<Long, Integer> skuCounts = loadSkuCounts(c);
            long nextSkuId = loadMaxSkuId(c) + 1;
            if (nextSkuId < 100000) {
                nextSkuId = 100000;
            }
            List<Object[]> skuRows = new ArrayList<>();
            shopIdx = 0;
            for (String cat : CatalogData.CATEGORIES) {
                String[] shops = CatalogData.shops(cat);
                String[] names = CatalogData.skuNames(cat);
                String[] groups = CatalogData.groups(cat);
                String[] specs = CatalogData.specs(cat);
                int[] range = CatalogData.priceRangeCents(cat);
                for (int i = 0; i < shops.length; i++) {
                    String shopName = shops[i];
                    Long merchantId = shopIds.get(shopName);
                    if (merchantId == null) {
                        shopIdx++;
                        continue;
                    }
                    int have = skuCounts.getOrDefault(merchantId, 0);
                    int need = Math.max(0, SKU_PER_SHOP - have);
                    for (int k = 0; k < need; k++) {
                        int skuIdx = have + k;
                        String key = "SSD:" + shopName + ":" + skuIdx;
                        if (skuKeys.contains(key)) {
                            continue;
                        }
                        String skuName = names[skuIdx % names.length];
                        if (skuIdx >= names.length) {
                            skuName = skuName + (skuIdx / names.length == 1 ? "（双人份）" : "（小份）");
                        }
                        String spec = specs[skuIdx % specs.length];
                        int spread = Math.max(1, range[1] - range[0]);
                        int price = range[0] + Math.abs((i * 37 + skuIdx * 91) % spread);
                        int origin = price + 180 + (skuIdx % 9) * 40;
                        long skuId = nextSkuId++;
                        skuRows.add(new Object[]{
                                skuId, merchantId, skuName, groups[skuIdx % groups.length],
                                price, origin, CatalogImages.skuImage(skuId, cat, groups[skuIdx % groups.length], skuName), spec, 40 + skuIdx % 60, "ONLINE",
                                CatalogData.description(skuName, cat), CatalogData.detail(skuName, spec, cat),
                                12 + (skuIdx * 17 + i * 3) % 220, skuIdx % 11, key
                        });
                        skuKeys.add(key);
                    }
                    shopIdx++;
                }
            }
            insertSkus(c, skuRows);
            c.commit();
            seedReviews(c, shopIds);
            c.commit();
            refreshRatings(c);
            c.commit();
            refreshUniqueImages(c);
            c.commit();
            log.info("目录扩容完成 shops+={} skus+={} {}ms", createdShops, skuRows.size(), System.currentTimeMillis() - t0);
        }
    }

    private void seedReviewUsers(Connection c, String hash, Set<String> phones) throws Exception {
        Timestamp now = Timestamp.from(Instant.now());
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO app_user (phone, password_hash, role, display_name, status, created_at) VALUES (?,?,?,?,?,?)")) {
            for (int i = 0; i < REVIEW_PHONES.length; i++) {
                if (phones.contains(REVIEW_PHONES[i])) {
                    continue;
                }
                ps.setString(1, REVIEW_PHONES[i]);
                ps.setString(2, hash);
                ps.setString(3, "USER");
                ps.setString(4, REVIEW_NICKS[i]);
                ps.setString(5, "ACTIVE");
                ps.setTimestamp(6, now);
                ps.addBatch();
                phones.add(REVIEW_PHONES[i]);
            }
            ps.executeBatch();
        }
    }

    private long insertMerchantUser(Connection c, String phone, String hash, String name) throws Exception {
        Timestamp now = Timestamp.from(Instant.now());
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO app_user (phone, password_hash, role, display_name, status, created_at) VALUES (?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, phone);
            ps.setString(2, hash);
            ps.setString(3, "MERCHANT");
            ps.setString(4, name);
            ps.setString(5, "ACTIVE");
            ps.setTimestamp(6, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM app_user WHERE phone=?")) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new IllegalStateException("无法写入商家账号 " + phone);
    }

    private void insertMerchant(Connection c, long userId, String name, double lat, double lon, String address,
                                String cat, String cover, double rating, String promo, String online, int completed)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO merchant (user_id, shop_name, lat, lon, address, category, cover_url, rating, promo,
                  online_status, rating_avg, rating_count, completed_count)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            ps.setDouble(3, lat);
            ps.setDouble(4, lon);
            ps.setString(5, address);
            ps.setString(6, cat);
            ps.setString(7, cover);
            ps.setDouble(8, rating);
            ps.setString(9, promo);
            ps.setString(10, online);
            ps.setDouble(11, rating);
            ps.setInt(12, 0);
            ps.setInt(13, completed);
            ps.executeUpdate();
        }
    }

    private void insertSkus(Connection c, List<Object[]> rows) throws Exception {
        if (rows.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO merchant_sku (id, merchant_id, name, group_name, price_cents, origin_price_cents,
                  image_url, spec, stock, status, description, detail, month_sales, like_count, sku_key)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            int n = 0;
            for (Object[] r : rows) {
                ps.setLong(1, (Long) r[0]);
                ps.setLong(2, (Long) r[1]);
                ps.setString(3, (String) r[2]);
                ps.setString(4, (String) r[3]);
                ps.setInt(5, (Integer) r[4]);
                ps.setInt(6, (Integer) r[5]);
                ps.setString(7, (String) r[6]);
                ps.setString(8, (String) r[7]);
                ps.setInt(9, (Integer) r[8]);
                ps.setString(10, (String) r[9]);
                ps.setString(11, (String) r[10]);
                ps.setString(12, (String) r[11]);
                ps.setInt(13, (Integer) r[12]);
                ps.setInt(14, (Integer) r[13]);
                ps.setString(15, (String) r[14]);
                ps.addBatch();
                n++;
                if (n % 500 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private void seedReviews(Connection c, Map<String, Long> shopIds) throws Exception {
        Set<Long> existingOrders = new HashSet<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT order_id FROM merchant_review")) {
            while (rs.next()) {
                existingOrders.add(rs.getLong(1));
            }
        }
        List<Long> reviewerIds = new ArrayList<>();
        reviewerIds.add(1L);
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id FROM app_user WHERE phone IN ('13800000031','13800000032','13800000033')")) {
            while (rs.next()) {
                reviewerIds.add(rs.getLong(1));
            }
        }
        Map<Long, String> firstSku = new HashMap<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT merchant_id, MIN(name) n FROM merchant_sku GROUP BY merchant_id")) {
            while (rs.next()) {
                firstSku.put(rs.getLong(1), rs.getString("n"));
            }
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO merchant_review (merchant_id, order_id, user_id, score, content, sku_names, like_count, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """)) {
            int n = 0;
            Timestamp now = Timestamp.from(Instant.now());
            for (Long merchantId : shopIds.values()) {
                int reviews = 3 + (int) (merchantId % 4);
                for (int i = 0; i < reviews; i++) {
                    long orderId = 9_000_000_000_000_000L + merchantId * 64 + i;
                    if (existingOrders.contains(orderId)) {
                        continue;
                    }
                    int score = 4 + (int) ((merchantId + i) % 2);
                    long uid = reviewerIds.get((int) ((merchantId + i) % reviewerIds.size()));
                    ps.setLong(1, merchantId);
                    ps.setLong(2, orderId);
                    ps.setLong(3, uid);
                    ps.setInt(4, score);
                    ps.setString(5, REVIEW_TEXTS[i % REVIEW_TEXTS.length]);
                    ps.setString(6, firstSku.getOrDefault(merchantId, "招牌"));
                    ps.setInt(7, (int) ((merchantId + i) % 18));
                    ps.setTimestamp(8, Timestamp.from(now.toInstant().minusSeconds(3600L * (i + 1) * 9)));
                    ps.addBatch();
                    n++;
                    if (n % 400 == 0) {
                        ps.executeBatch();
                    }
                }
            }
            ps.executeBatch();
        }
    }

    private void refreshRatings(Connection c) throws Exception {
        try (Statement s = c.createStatement()) {
            s.execute("""
                    UPDATE merchant m
                    JOIN (
                      SELECT merchant_id, AVG(score) avg_s, COUNT(*) cnt
                      FROM merchant_review GROUP BY merchant_id
                    ) r ON m.user_id = r.merchant_id
                    SET m.rating_avg = r.avg_s, m.rating_count = r.cnt, m.rating = r.avg_s
                    """);
            s.execute("""
                    UPDATE merchant SET rating_avg = COALESCE(rating_avg, rating, 4.6),
                      rating_count = COALESCE(rating_count, 0),
                      completed_count = COALESCE(completed_count, 40)
                    """);
        }
    }

    /** 已有库也 UPDATE：封面/菜品改写到本站真实 JPG，禁止 picsum / loremflickr。 */
    private void refreshUniqueImages(Connection c) throws Exception {
        try (Statement q = c.createStatement();
             ResultSet rs = q.executeQuery("SELECT user_id, category FROM merchant");
             PreparedStatement ps = c.prepareStatement("UPDATE merchant SET cover_url=? WHERE user_id=?")) {
            int n = 0;
            while (rs.next()) {
                long id = rs.getLong(1);
                String cat = rs.getString(2);
                ps.setString(1, CatalogImages.shopCover(id, cat));
                ps.setLong(2, id);
                ps.addBatch();
                n++;
            }
            if (n > 0) {
                ps.executeBatch();
            }
        }
        try (Statement q = c.createStatement();
             ResultSet rs = q.executeQuery("""
                     SELECT s.id, s.name, s.group_name, m.category
                     FROM merchant_sku s JOIN merchant m ON m.user_id = s.merchant_id
                     """);
             PreparedStatement ps = c.prepareStatement("UPDATE merchant_sku SET image_url=? WHERE id=?")) {
            int n = 0;
            while (rs.next()) {
                long id = rs.getLong(1);
                String name = rs.getString(2);
                String group = rs.getString(3);
                String cat = rs.getString(4);
                ps.setString(1, CatalogImages.skuImage(id, cat, group, name));
                ps.setLong(2, id);
                ps.addBatch();
                n++;
                if (n % 500 == 0) {
                    ps.executeBatch();
                }
            }
            if (n > 0) {
                ps.executeBatch();
            }
        }
    }

    private static Set<String> loadStrings(Connection c, String sql) throws Exception {
        Set<String> set = new HashSet<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                String v = rs.getString(1);
                if (v != null) {
                    set.add(v);
                }
            }
        }
        return set;
    }

    private static Map<String, Long> loadShopIds(Connection c) throws Exception {
        Map<String, Long> map = new HashMap<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT shop_name, user_id FROM merchant WHERE shop_name IS NOT NULL")) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getLong(2));
            }
        }
        return map;
    }

    private static Map<Long, Integer> loadSkuCounts(Connection c) throws Exception {
        Map<Long, Integer> map = new HashMap<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT merchant_id, COUNT(*) c FROM merchant_sku GROUP BY merchant_id")) {
            while (rs.next()) {
                map.put(rs.getLong(1), rs.getInt(2));
            }
        }
        return map;
    }

    private static long loadMaxSkuId(Connection c) throws Exception {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(id), 0) FROM merchant_sku")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }
}
