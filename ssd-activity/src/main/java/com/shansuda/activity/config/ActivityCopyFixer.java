package com.shansuda.activity.config;

import com.shansuda.activity.domain.Activity;
import com.shansuda.activity.repo.ActivityRepo;
import com.shansuda.activity.repo.ActivitySkuRepo;
import com.shansuda.common.catalog.CatalogImages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class ActivityCopyFixer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ActivityCopyFixer.class);

    private static final Object[][] SECKILL_SKUS = {
            {1L, 1L, 1001L, "时令水果拼盘", 990, 20, 1990},
            {3L, 1L, 1003L, "鲜切西瓜", 790, 18, 1290},
            {4L, 1L, 1402L, "草莓杯", 1290, 16, 2200},
            {5L, 1L, 1010L, "阳光玫瑰", 1990, 15, 2880},
            {6L, 1L, 1011L, "车厘子", 2590, 12, 3580},
            {7L, 1L, 1012L, "蓝莓盒", 1690, 14, 2480}
    };

    private final DataSource dataSource;
    private final ActivityRepo activityRepo;
    private final ActivitySkuRepo skuRepo;

    public ActivityCopyFixer(DataSource dataSource, ActivityRepo activityRepo, ActivitySkuRepo skuRepo) {
        this.dataSource = dataSource;
        this.activityRepo = activityRepo;
        this.skuRepo = skuRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("ALTER TABLE activity_sku ADD COLUMN origin_price_cents INT NULL");
        } catch (Exception ignored) {
        }
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("ALTER TABLE activity_sku ADD COLUMN image_url VARCHAR(512) NULL");
        } catch (Exception ignored) {
        }
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("ALTER TABLE activity_sku MODIFY image_url VARCHAR(512) NULL");
        } catch (Exception ignored) {
        }
        activityRepo.findById(1L).ifPresent(a -> fixName(a, "午餐爆品秒杀"));
        activityRepo.findById(2L).ifPresent(a -> fixName(a, "新客满减券"));
        seedSeckillPool();
        skuRepo.findById(2L).ifPresent(sku -> {
            sku.setName("满 20 减 5 券");
            skuRepo.save(sku);
        });
        log.info("活动文案已按 UTF-8 校准，秒杀 SKU 池={}", SECKILL_SKUS.length);
    }

    private void seedSeckillPool() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO activity_sku (id, activity_id, sku_id, name, price_cents, origin_stock, origin_price_cents, image_url)
                     VALUES (?,?,?,?,?,?,?,?)
                     ON DUPLICATE KEY UPDATE name=VALUES(name), price_cents=VALUES(price_cents),
                       origin_stock=VALUES(origin_stock), origin_price_cents=VALUES(origin_price_cents),
                       image_url=VALUES(image_url)
                     """)) {
            for (Object[] row : SECKILL_SKUS) {
                long skuId = (Long) row[2];
                String name = (String) row[3];
                ps.setLong(1, (Long) row[0]);
                ps.setLong(2, (Long) row[1]);
                ps.setLong(3, skuId);
                ps.setString(4, name);
                ps.setInt(5, (Integer) row[4]);
                ps.setInt(6, (Integer) row[5]);
                ps.setInt(7, (Integer) row[6]);
                ps.setString(8, CatalogImages.skuImage(skuId, "fresh", "水果", name));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception ex) {
            log.warn("秒杀 SKU 池写入失败: {}", ex.getMessage());
        }
    }

    private void fixName(Activity activity, String name) {
        String current = activity.getName() == null ? "" : activity.getName();
        if (!name.equals(current) || looksMojibake(current)) {
            activity.setName(name);
            activityRepo.save(activity);
        }
    }

    private static boolean looksMojibake(String text) {
        byte[] latin = text.getBytes(StandardCharsets.ISO_8859_1);
        String asUtf8 = new String(latin, StandardCharsets.UTF_8);
        return asUtf8.contains("午餐") || asUtf8.contains("新客") || text.contains("å") || text.contains("é");
    }
}
