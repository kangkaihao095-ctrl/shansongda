package com.shansuda.activity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.shansuda.activity.client.AccountClient;
import com.shansuda.activity.client.OrderClient;
import com.shansuda.activity.domain.Activity;
import com.shansuda.activity.domain.ActivitySku;
import com.shansuda.activity.domain.CouponGrant;
import com.shansuda.activity.domain.CouponIdem;
import com.shansuda.activity.domain.SeckillIdem;
import com.shansuda.activity.repo.ActivityRepo;
import com.shansuda.activity.repo.ActivitySkuRepo;
import com.shansuda.activity.repo.CouponGrantRepo;
import com.shansuda.activity.repo.CouponIdemRepo;
import com.shansuda.activity.repo.SeckillIdemRepo;
import com.shansuda.common.api.ApiResult;
import com.shansuda.common.api.BizException;
import com.shansuda.common.api.ErrorCodes;
import com.shansuda.common.auth.AuthHolder;
import com.shansuda.common.auth.AuthUser;
import com.shansuda.common.mq.CouponSuccessMessage;
import com.shansuda.common.mq.GrabSuccessMessage;
import com.shansuda.common.mq.MqNames;
import com.shansuda.common.mq.SeckillSuccessMessage;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityRepo activityRepo;
    private final ActivitySkuRepo skuRepo;
    private final SeckillIdemRepo seckillIdemRepo;
    private final CouponIdemRepo couponIdemRepo;
    private final CouponGrantRepo couponGrantRepo;
    private final Cache<String, Object> localCache;
    private final AtomicStock atomicStock;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RedissonClient> redisson;
    private final ObjectProvider<OrderClient> orderClient;
    private final ObjectProvider<AccountClient> accountClient;
    private final long seckillMerchantId;
    private final int rotateMinutes;
    private final String mode;

    public ActivityService(ActivityRepo activityRepo, ActivitySkuRepo skuRepo, SeckillIdemRepo seckillIdemRepo,
                           CouponIdemRepo couponIdemRepo, CouponGrantRepo couponGrantRepo,
                           Cache<String, Object> localCache,
                           AtomicStock atomicStock, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                           ObjectProvider<RedissonClient> redisson, ObjectProvider<OrderClient> orderClient,
                           ObjectProvider<AccountClient> accountClient,
                           @Value("${ssd.seckill.merchant-id:3}") long seckillMerchantId,
                           @Value("${ssd.seckill.rotate-minutes:10}") int rotateMinutes,
                           @Value("${ssd.mode:auto}") String mode) {
        this.activityRepo = activityRepo;
        this.skuRepo = skuRepo;
        this.seckillIdemRepo = seckillIdemRepo;
        this.couponIdemRepo = couponIdemRepo;
        this.couponGrantRepo = couponGrantRepo;
        this.localCache = localCache;
        this.atomicStock = atomicStock;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.redisson = redisson;
        this.orderClient = orderClient;
        this.accountClient = accountClient;
        this.seckillMerchantId = seckillMerchantId;
        this.rotateMinutes = Math.max(1, rotateMinutes);
        this.mode = mode;
    }

    public List<Map<String, Object>> list() {
        return activityRepo.findAll().stream().map(this::activityView).toList();
    }

    public Map<String, Object> get(long id) {
        return activityView(activityRepo.findById(id).orElseThrow(() -> BizException.notFound("活动不存在")));
    }

    public ApiResult<Map<String, Object>> grabCoupon(long activityId) {
        CriticalSection.requireAvailable(redisson.getIfAvailable(), mode, log);
        AuthUser user = AuthHolder.require();
        Activity activity = cachedActivity(activityId);
        if (!"COUPON".equals(activity.getType())) {
            throw BizException.badRequest("BAD_ACTIVITY", "不是抢券活动");
        }
        ActivitySku sku = skuRepo.findByActivityId(activityId).stream().findFirst()
                .orElseThrow(() -> BizException.notFound("活动无券"));
        String idemKey = ActivityKeys.coupon(activityId, user.userId());
        CouponIdem stored = couponIdemRepo.findById(idemKey).orElse(null);
        if (stored != null) {
            return ApiResult.replay(readJson(stored.getPayload()));
        }
        CouponGrant granted = couponGrantRepo.findByActivityIdAndUserIdAndScene(activityId, user.userId(), "COUPON")
                .orElse(null);
        if (granted != null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("idempotencyKey", idemKey);
            body.put("activityId", activityId);
            body.put("userId", user.userId());
            body.put("scene", "COUPON");
            body.put("couponCode", granted.getCouponCode());
            return ApiResult.replay(body);
        }
        String stockKey = stockKey(activityId, sku.getSkuId());
        atomicStock.initStock(stockKey, sku.getOriginStock());
        String couponCode = "CPN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CouponSuccessMessage msg = new CouponSuccessMessage(idemKey, activityId, user.userId(), "COUPON", couponCode);
        String payload = writeJson(msg);
        StockResult result = atomicStock.deduct(stockKey, "act:idem:" + idemKey, payload, ttlSeconds(activity));
        if (result.empty()) {
            throw BizException.conflict(ErrorCodes.STOCK_EMPTY, "券已抢完");
        }
        if (result.replay()) {
            ensureCouponIdem(idemKey, activityId, user.userId(), result.payload());
            return ApiResult.replay(readJson(result.payload()));
        }
        if (!tryInsertCouponIdem(idemKey, activityId, user.userId(), payload)) {
            CouponIdem first = couponIdemRepo.findById(idemKey).orElse(null);
            return ApiResult.replay(readJson(first != null ? first.getPayload() : payload));
        }
        withLock("lock:act:" + activityId + ":" + sku.getSkuId(), () ->
                rabbitTemplate.convertAndSend(MqNames.ACTIVITY_EXCHANGE, MqNames.COUPON_SUCCESS, msg));
        Map<String, Object> body = readJson(result.payload());
        return ApiResult.ok(body);
    }

    public ApiResult<Map<String, Object>> seckill(long activityId, long skuId) {
        CriticalSection.requireAvailable(redisson.getIfAvailable(), mode, log);
        AuthUser user = AuthHolder.require();
        Activity activity = cachedActivity(activityId);
        if (!"SECKILL".equals(activity.getType())) {
            throw BizException.badRequest("BAD_ACTIVITY", "不是秒杀活动");
        }
        ActivitySku sku = cachedSku(activityId, skuId);
        String idemKey = ActivityKeys.seckill(activityId, user.userId(), sku.getSkuId());
        String stockKey = stockKey(activityId, sku.getSkuId());
        atomicStock.initStock(stockKey, sku.getOriginStock());
        SeckillSuccessMessage msg = new SeckillSuccessMessage(
                idemKey, user.userId(), activityId, sku.getSkuId(), sku.getName(), sku.getPriceCents(), seckillMerchantId);
        String payload = writeJson(msg);
        // DB 唯一索引兜底：已有记录则回放这一档 SKU 的订单，避免 Redis TTL 过期后二次扣减
        SeckillIdem stored = seckillIdemRepo.findById(idemKey).orElse(null);
        if (stored != null) {
            return ApiResult.replay(seckillBody(stored.getPayload(), stored));
        }
        StockResult result = atomicStock.deduct(stockKey, "act:idem:" + idemKey, payload, ttlSeconds(activity));
        if (result.empty()) {
            throw BizException.conflict(ErrorCodes.STOCK_EMPTY, "已售罄");
        }
        if (result.replay()) {
            ensureSeckillIdem(idemKey, result.payload());
            SeckillIdem row = seckillIdemRepo.findById(idemKey).orElse(null);
            return ApiResult.replay(seckillBody(result.payload(), row));
        }
        if (!tryInsertSeckillIdem(idemKey, payload)) {
            SeckillIdem first = seckillIdemRepo.findById(idemKey).orElse(null);
            String json = first != null ? first.getPayload() : payload;
            return ApiResult.replay(seckillBody(json, first));
        }
        withLock("lock:act:" + activityId + ":" + sku.getSkuId(), () ->
                rabbitTemplate.convertAndSend(MqNames.ACTIVITY_EXCHANGE, MqNames.SECKILL_SUCCESS, msg));
        return ApiResult.ok(seckillBody(payload, seckillIdemRepo.findById(idemKey).orElse(null)));
    }

    /** 落单后回写 order_id；主键冲突则回放首次记录。 */
    public Map<String, Object> bindSeckillOrder(String idemKey, Long orderId, String payload) {
        SeckillIdem row = seckillIdemRepo.findById(idemKey).orElse(null);
        if (row == null) {
            String json = payload == null || payload.isBlank() ? "{}" : payload;
            tryInsertSeckillIdem(idemKey, json);
            row = seckillIdemRepo.findById(idemKey).orElseThrow();
        }
        if (row.getOrderId() != null && orderId != null && !row.getOrderId().equals(orderId)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("replayed", true);
            body.put("orderId", row.getOrderId());
            body.put("payload", row.getPayload());
            return body;
        }
        if (orderId != null && row.getOrderId() == null) {
            row.setOrderId(orderId);
            seckillIdemRepo.save(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("replayed", false);
        body.put("orderId", row.getOrderId());
        body.put("payload", row.getPayload());
        return body;
    }

    private boolean tryInsertSeckillIdem(String idemKey, String payload) {
        SeckillIdem row = new SeckillIdem();
        row.setIdemKey(idemKey);
        row.setPayload(payload);
        row.setCreatedAt(Instant.now());
        try {
            seckillIdemRepo.saveAndFlush(row);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private void ensureSeckillIdem(String idemKey, String payload) {
        if (seckillIdemRepo.existsById(idemKey) || payload == null || payload.isBlank()) {
            return;
        }
        tryInsertSeckillIdem(idemKey, payload);
    }

    public ApiResult<Map<String, Object>> grab(long orderId) {
        AuthUser user = AuthHolder.require();
        if (!user.isRider()) {
            throw BizException.forbidden("仅骑手可抢单");
        }
        Map<String, Object> order = requirePaidOrder(orderId);
        return occupyGrab(orderId, user.userId(), order);
    }

    public ApiResult<Map<String, Object>> occupyGrab(long orderId, long riderId, Map<String, Object> orderOrNull) {
        CriticalSection.requireAvailable(redisson.getIfAvailable(), mode, log);
        assertRiderCanAccept(riderId);
        if (orderOrNull == null) {
            requirePaidOrder(orderId);
        }
        String stockKey = "grab:stock:" + orderId;
        atomicStock.initStock(stockKey, 1);
        String idemKey = orderId + ":" + riderId;
        GrabSuccessMessage msg = new GrabSuccessMessage("GRAB:" + idemKey, orderId, riderId);
        String payload = writeJson(msg);
        StockResult result = atomicStock.deduct(stockKey, "grab:idem:" + idemKey, payload, Duration.ofDays(7).toSeconds());
        if (result.empty()) {
            throw BizException.conflict(ErrorCodes.STOCK_EMPTY, "订单已被抢走");
        }
        if (result.success()) {
            withLock("lock:order:" + orderId, () -> {
                AccountClient account = accountClient.getIfAvailable();
                if (account != null) {
                    account.acceptStatus(riderId, Map.of("acceptStatus", "BUSY"));
                }
                rabbitTemplate.convertAndSend(MqNames.ACTIVITY_EXCHANGE, MqNames.GRAB_SUCCESS, msg);
            });
        }
        Map<String, Object> body = readJson(result.payload());
        return result.replay() ? ApiResult.replay(body) : ApiResult.ok(body);
    }

    private void assertRiderCanAccept(long riderId) {
        AccountClient account = accountClient.getIfAvailable();
        if (account == null) {
            throw BizException.conflict("WORK_CHECK_FAILED", "工时校验不可用，拒绝抢单");
        }
        try {
            ApiResult<Map<String, Object>> res = account.canAccept(riderId);
            Map<String, Object> gate = res == null ? null : res.data();
            if (gate != null && Boolean.FALSE.equals(gate.get("allowed"))) {
                String reason = gate.get("reason") == null ? "今日工时已满或已强制下线，不可接单" : String.valueOf(gate.get("reason"));
                String code = gateCode(gate.get("code"));
                throw BizException.conflict(code, reason);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.conflict("WORK_CHECK_FAILED", "工时校验失败，拒绝抢单");
        }
    }

    private static String gateCode(Object raw) {
        if (raw == null) {
            return ErrorCodes.WORK_LIMIT;
        }
        String code = String.valueOf(raw).trim();
        if (code.isEmpty() || "null".equalsIgnoreCase(code)) {
            return ErrorCodes.WORK_LIMIT;
        }
        return code;
    }

    public void warmStock() {
        for (ActivitySku sku : skuRepo.findAll()) {
            atomicStock.initStock(stockKey(sku.getActivityId(), sku.getSkuId()), sku.getOriginStock());
        }
    }

    private Map<String, Object> requirePaidOrder(long orderId) {
        OrderClient client = orderClient.getIfAvailable();
        if (client == null) {
            throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "订单服务不可用");
        }
        ApiResult<Map<String, Object>> res = client.get(orderId);
        Map<String, Object> order = res.data();
        if (order == null || !"PAID".equals(String.valueOf(order.get("status")))) {
            throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "仅已支付订单可抢");
        }
        return order;
    }

    private Activity cachedActivity(long id) {
        Object hit = localCache.get("act:" + id, key -> activityRepo.findById(id).orElse(null));
        if (hit instanceof Activity activity) {
            return activity;
        }
        throw BizException.notFound("活动不存在");
    }

    private ActivitySku cachedSku(long activityId, long skuId) {
        Object hit = localCache.get("sku:" + activityId + ":" + skuId,
                key -> skuRepo.findByActivityIdAndSkuId(activityId, skuId).orElse(null));
        if (hit instanceof ActivitySku sku) {
            return sku;
        }
        throw BizException.notFound("SKU 不存在");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> activityView(Activity activity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", activity.getId());
        map.put("type", activity.getType());
        map.put("name", activity.getName());
        map.put("status", activity.getStatus());
        map.put("startAt", activity.getStartAt());
        map.put("endAt", activity.getEndAt());
        long countdown = activity.getEndAt() == null
                ? 0
                : Math.max(0, Duration.between(Instant.now(), activity.getEndAt()).getSeconds());
        map.put("countdownSeconds", countdown);
        map.put("skus", skuRepo.findByActivityId(activity.getId()).stream().map(sku -> {
            Map<String, Object> row = skuRow(activity, sku);
            return row;
        }).toList());
        if ("SECKILL".equals(activity.getType())) {
            List<ActivitySku> pool = skuRepo.findByActivityId(activity.getId());
            if (!pool.isEmpty()) {
                long windowMs = rotateMinutes * 60_000L;
                long nowMs = Instant.now().toEpochMilli();
                long slot = nowMs / windowMs;
                int idx = (int) Math.floorMod(slot, pool.size());
                Instant next = Instant.ofEpochMilli((slot + 1) * windowMs);
                ActivitySku current = pool.get(idx);
                map.put("currentSku", skuRow(activity, current));
                map.put("nextRotateAt", next);
                map.put("rotateMinutes", rotateMinutes);
                attachGrabbed((Map<String, Object>) map.get("currentSku"), activity.getId(), current.getSkuId());
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private void attachGrabbed(Map<String, Object> currentSku, long activityId, long skuId) {
        AuthUser user = AuthHolder.get();
        if (user == null || currentSku == null) {
            return;
        }
        String key = ActivityKeys.seckill(activityId, user.userId(), skuId);
        seckillIdemRepo.findById(key).ifPresent(row -> {
            currentSku.put("grabbed", true);
            if (row.getOrderId() != null) {
                currentSku.put("orderId", row.getOrderId());
            }
        });
    }

    private Map<String, Object> seckillBody(String payload, SeckillIdem row) {
        Map<String, Object> body = readJson(payload);
        if (row != null) {
            body.put("idempotencyKey", row.getIdemKey());
            if (row.getOrderId() != null) {
                body.put("orderId", row.getOrderId());
            }
        }
        return body;
    }

    private Map<String, Object> skuRow(Activity activity, ActivitySku sku) {
        Map<String, Object> row = new LinkedHashMap<>();
        String stockKey = stockKey(activity.getId(), sku.getSkuId());
        atomicStock.initStock(stockKey, sku.getOriginStock());
        int remain = atomicStock.remaining(stockKey);
        row.put("skuId", sku.getSkuId());
        row.put("name", sku.getName());
        row.put("priceCents", sku.getPriceCents());
        int origin = sku.getOriginPriceCents() != null ? sku.getOriginPriceCents()
                : (sku.getPriceCents() == null ? 0 : sku.getPriceCents() * 2);
        row.put("originPriceCents", origin);
        row.put("originStock", sku.getOriginStock());
        row.put("remainStock", remain < 0 ? sku.getOriginStock() : remain);
        String image = sku.getImageUrl();
        if (image == null || image.isBlank()) {
            image = com.shansuda.common.catalog.CatalogImages.skuImage(
                    sku.getSkuId() == null ? 0 : sku.getSkuId(), "fresh", "水果", sku.getName());
        }
        row.put("imageUrl", image);
        return row;
    }

    private long ttlSeconds(Activity activity) {
        long untilEnd = Duration.between(Instant.now(), activity.getEndAt()).toSeconds();
        return Math.max(untilEnd, 0) + 86_400;
    }

    private static String stockKey(long activityId, long skuId) {
        return "act:stock:" + activityId + ":" + skuId;
    }

    private void withLock(String name, Runnable action) {
        CriticalSection.run(redisson.getIfAvailable(), mode, name, action, log);
    }

    private boolean tryInsertCouponIdem(String idemKey, long activityId, long userId, String payload) {
        CouponIdem row = new CouponIdem();
        row.setIdemKey(idemKey);
        row.setActivityId(activityId);
        row.setUserId(userId);
        row.setPayload(payload);
        row.setCreatedAt(Instant.now());
        try {
            couponIdemRepo.saveAndFlush(row);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private void ensureCouponIdem(String idemKey, long activityId, long userId, String payload) {
        if (couponIdemRepo.existsById(idemKey) || payload == null || payload.isBlank()) {
            return;
        }
        tryInsertCouponIdem(idemKey, activityId, userId, payload);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }
}
