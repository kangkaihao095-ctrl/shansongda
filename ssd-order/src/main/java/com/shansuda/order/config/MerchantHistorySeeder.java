package com.shansuda.order.config;

import com.shansuda.common.catalog.CatalogImages;
import com.shansuda.order.domain.DeliveryOrder;
import com.shansuda.order.leaf.LeafAllocator;
import com.shansuda.order.repo.OrderRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** 按日期回填商家历史订单到分片表；号段走 Leaf，失败不弄死进程。 */
@Component
@Order(2)
public class MerchantHistorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MerchantHistorySeeder.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long USER_ID = 1L;
    private static final long MERCHANT_ID = 3L;
    private static final long RIDER_ID = 2L;
    private static final double USER_LAT = 31.2240;
    private static final double USER_LON = 121.4690;
    private static final double SHOP_LAT = 31.2380;
    private static final double SHOP_LON = 121.4840;

    private static final List<int[]> SKUS = List.of(
            new int[]{1001, 1990},
            new int[]{1002, 680},
            new int[]{1003, 990}
    );
    private static final String[] SKU_NAMES = {"时令水果拼盘", "有机生菜", "鲜切西瓜"};
    private static final String[] SKU_SPECS = {"大份", "250g", "盒"};

    private final OrderRepo orderRepo;
    private final LeafAllocator leafAllocator;
    private final JdbcTemplate jdbc;

    public MerchantHistorySeeder(OrderRepo orderRepo, LeafAllocator leafAllocator, DataSource dataSource) {
        this.orderRepo = orderRepo;
        this.leafAllocator = leafAllocator;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (Exception ex) {
            log.warn("回填商家历史订单失败: {}", ex.getMessage());
        }
    }

    private void seed() {
        if (alreadySeeded()) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);
        int seq = 0;
        for (int monthOffset = 11; monthOffset >= 1; monthOffset--) {
            LocalDate month = today.minusMonths(monthOffset).withDayOfMonth(8 + (monthOffset % 5));
            int count = 3 + (monthOffset % 3);
            for (int i = 0; i < count; i++) {
                LocalDate day = month.plusDays(i * 3L);
                if (day.isAfter(today.minusDays(32))) {
                    continue;
                }
                upsert(key(day, seq++), "COMPLETED", "PAID", day, 8 + i, SKUS.get(i % SKUS.size()), RIDER_ID, 600 + i * 40);
            }
        }
        for (int d = 29; d >= 1; d--) {
            LocalDate day = today.minusDays(d);
            int n = 1 + (d % 3);
            for (int i = 0; i < n; i++) {
                upsert(key(day, seq++), "COMPLETED", "PAID", day, 10 + i, SKUS.get((d + i) % SKUS.size()), RIDER_ID, 500 + d * 10);
            }
        }
        upsert(key(today.minusDays(2), seq++), "REFUNDING", "REFUNDING", today.minusDays(2), 15, SKUS.get(0), RIDER_ID, 600);
        upsert(key(today.minusDays(1), seq++), "REFUNDED", "REFUNDED", today.minusDays(1), 16, SKUS.get(1), RIDER_ID, 500);
        upsert(key(today, seq++), "COMPLETED", "PAID", today, 9, SKUS.get(0), RIDER_ID, 600);
        upsert(key(today, seq++), "COMPLETED", "PAID", today, 11, SKUS.get(2), RIDER_ID, 550);
        upsert(key(today, seq++), "MERCHANT_PENDING", "PAID", today, 12, SKUS.get(0), null, 600);
        upsert(key(today, seq++), "MERCHANT_PENDING", "PAID", today, 13, SKUS.get(1), null, 500);
        upsert(key(today, seq++), "DELIVERING", "PAID", today, 14, SKUS.get(2), RIDER_ID, 620);
        upsert(key(today, seq++), "ACCEPTED", "PAID", today, 15, SKUS.get(0), RIDER_ID, 580);
        upsert(key(today, seq++), "REFUNDING", "REFUNDING", today, 16, SKUS.get(1), RIDER_ID, 500);
        upsert(key(today, seq++), "REFUNDED", "REFUNDED", today, 17, SKUS.get(2), RIDER_ID, 540);
        log.info("商家历史订单已按日期回填");
    }

    private boolean alreadySeeded() {
        try {
            List<Integer> parts = jdbc.query(
                    "SELECT COUNT(1) FROM t_order WHERE idempotency_key LIKE 'SEED:HIST:%'",
                    (rs, i) -> rs.getInt(1));
            int n = parts.stream().mapToInt(Integer::intValue).sum();
            return n >= 40;
        } catch (Exception ex) {
            return orderRepo.findByIdempotencyKey("SEED:HIST:probe").isPresent();
        }
    }

    private void upsert(String idem, String status, String payStatus, LocalDate day, int hour,
                        int[] sku, Long riderId, int freight) {
        try {
            if (orderRepo.findByIdempotencyKey(idem).isPresent()) {
                return;
            }
            int skuIdx = 0;
            for (int i = 0; i < SKUS.size(); i++) {
                if (SKUS.get(i)[0] == sku[0]) {
                    skuIdx = i;
                    break;
                }
            }
            int goods = sku[1];
            DeliveryOrder order = new DeliveryOrder();
            order.setId(leafAllocator.nextId());
            order.setUserId(USER_ID);
            order.setMerchantId(MERCHANT_ID);
            order.setRiderId(riderId);
            order.setStatus(status);
            order.setGoodsAmountCents(goods);
            order.setFreightCents(freight);
            order.setFreightStrategy("DistanceFreight");
            order.setPenaltyCents(0);
            order.setUserLat(USER_LAT);
            order.setUserLon(USER_LON);
            order.setMerchantLat(SHOP_LAT);
            order.setMerchantLon(SHOP_LON);
            order.setAddressDetail("上海市黄浦区外滩源 33 号");
            order.setSkuSnapshot(snapshot(skuIdx, goods));
            order.setIdempotencyKey(idem);
            var at = day.atTime(LocalTime.of(Math.min(hour, 22), 18)).atZone(ZONE).toInstant();
            order.setCreatedAt(at);
            order.setUpdatedAt(at);
            order.setVersion(0L);
            order.setPayChannel("WECHAT");
            order.setPayStatus(payStatus);
            order.setPaidAt(at);
            order.setPayAmountCents(goods + freight);
            if ("REFUNDING".equals(status) || "REFUNDED".equals(status)) {
                order.setRefundReason("STOCK：出餐调整");
            }
            orderRepo.save(order);
        } catch (Exception ex) {
            log.warn("回填订单跳过 {}: {}", idem, ex.getMessage());
        }
    }

    private static String key(LocalDate day, int seq) {
        return "SEED:HIST:" + day + ":" + seq;
    }

    private static String snapshot(int skuIdx, int price) {
        return "{\"items\":[{\"skuId\":" + SKUS.get(skuIdx)[0]
                + ",\"name\":\"" + SKU_NAMES[skuIdx]
                + "\",\"qty\":1,\"priceCents\":" + price
                + ",\"imageUrl\":\"" + CatalogImages.skuImage(SKUS.get(skuIdx)[0])
                + "\",\"spec\":\"" + SKU_SPECS[skuIdx]
                + "\"}],\"discountCents\":0,\"shopName\":\"闪送达鲜生·南京东路\",\"coverUrl\":\""
                + CatalogImages.shopCover(MERCHANT_ID) + "\"}";
    }
}
