package com.shansuda.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shansuda.common.api.ApiResult;
import com.shansuda.common.api.BizException;
import com.shansuda.common.api.ErrorCodes;
import com.shansuda.common.auth.AuthHolder;
import com.shansuda.common.auth.AuthUser;
import com.shansuda.common.catalog.TipGifts;
import com.shansuda.common.geo.Geo;
import com.shansuda.common.mq.GrabSuccessMessage;
import com.shansuda.common.mq.MqNames;
import com.shansuda.common.mq.OrderPaidMessage;
import com.shansuda.common.mq.SeckillSuccessMessage;
import com.shansuda.order.client.AccountClient;
import com.shansuda.order.client.ActivityClient;
import com.shansuda.order.domain.DeliveryOrder;
import com.shansuda.order.route.RouteService;
import com.shansuda.order.fulfill.OrderAccess;
import com.shansuda.order.fulfill.OrderStateMachine;
import com.shansuda.order.leaf.LeafAllocator;
import com.shansuda.order.query.OrderSearch;
import com.shansuda.order.repo.OrderRepo;
import com.shansuda.order.strategy.FreightSelector;
import com.shansuda.order.strategy.FreightStrategy;
import com.shansuda.order.stats.MerchantReport;
import com.shansuda.order.stats.MerchantStatsAggregator;
import com.shansuda.order.strategy.PenaltyStrategy;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepo orderRepo;
    private final LeafAllocator leafAllocator;
    private final AccountClient accountClient;
    private final RouteService routeService;
    private final ObjectProvider<ActivityClient> activityClient;
    private final FreightSelector freightSelector;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final double maxKm;

    public OrderService(OrderRepo orderRepo, LeafAllocator leafAllocator, AccountClient accountClient,
                        RouteService routeService, ObjectProvider<ActivityClient> activityClient,
                        FreightSelector freightSelector,
                        RabbitTemplate rabbitTemplate, ObjectMapper objectMapper, DataSource dataSource,
                        @Value("${ssd.recommend.max-km:5}") double maxKm) {
        this.orderRepo = orderRepo;
        this.leafAllocator = leafAllocator;
        this.accountClient = accountClient;
        this.routeService = routeService;
        this.activityClient = activityClient;
        this.freightSelector = freightSelector;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.jdbc = new JdbcTemplate(dataSource);
        this.maxKm = maxKm <= 0 ? Geo.DEFAULT_MAX_KM : maxKm;
    }

    @Transactional
    public Map<String, Object> create(long merchantId, long addressId) {
        return create(merchantId, addressId, List.of(), null, null);
    }

    public Map<String, Object> preview(long merchantId, long addressId, List<Map<String, Object>> items,
                                       Long couponId, Integer clientPayCents) {
        return buildQuote(merchantId, addressId, items, couponId, clientPayCents, false);
    }

    @Transactional
    public Map<String, Object> create(long merchantId, long addressId, List<Map<String, Object>> items,
                                      Long couponId, Integer clientPayCents) {
        AuthUser user = AuthHolder.require();
        Map<String, Object> quote = buildQuote(merchantId, addressId, items, couponId, clientPayCents, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> merchant = (Map<String, Object>) quote.get("merchant");
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) quote.get("address");
        DeliveryOrder order = newOrder(user.userId(), merchantId,
                num(address.get("lat")), num(address.get("lon")),
                num(merchant.get("lat")), num(merchant.get("lon")),
                String.valueOf(address.get("detail")),
                ((Number) quote.get("goodsAmountCents")).intValue(),
                ((Number) quote.get("freightCents")).intValue(),
                String.valueOf(quote.get("freightStrategy")),
                null, writeJson(quote.get("snapshot")),
                "ORDER:" + user.userId() + ":" + UUID.randomUUID());
        order.setPayAmountCents(((Number) quote.get("payCents")).intValue());
        order.setPayStatus("UNPAID");
        List<Map<String, Object>> stockItems = shopStockItems(quote.get("snapshot"));
        boolean deducted = applyShopStock("deduct", stockItems);
        try {
            orderRepo.save(order);
            if (couponId != null && couponId > 0) {
                accountClient.useCoupon(Map.of("userId", user.userId(), "couponId", couponId, "orderId", order.getId()));
            }
        } catch (RuntimeException ex) {
            if (deducted) {
                applyShopStock("restore", stockItems);
            }
            throw ex;
        }
        return view(order);
    }

    @Transactional
    public Map<String, Object> createFromSeckill(SeckillSuccessMessage msg) {
        return orderRepo.findByIdempotencyKey(msg.idempotencyKey())
                .map(this::view)
                .orElseGet(() -> persistSeckill(msg));
    }

    private Map<String, Object> persistSeckill(SeckillSuccessMessage msg) {
        Map<String, Object> merchant = requireData(accountClient.merchant(msg.merchantId()), "商家不存在");
        double mlat = num(merchant.get("lat"));
        double mlon = num(merchant.get("lon"));
        double ulat = 31.2240;
        double ulon = 121.4690;
        Instant now = Instant.now();
        FreightStrategy strategy = freightSelector.select(now);
        String snapshot = writeJson(Map.of(
                "items", List.of(Map.of(
                        "skuId", msg.skuId(),
                        "name", msg.skuName(),
                        "qty", 1,
                        "priceCents", msg.priceCents(),
                        "imageUrl", com.shansuda.common.catalog.CatalogImages.skuImage(msg.skuId(), "fresh", "水果", msg.skuName())
                )),
                "discountCents", 0,
                "seckill", true
        ));
        DeliveryOrder order = newOrder(msg.userId(), msg.merchantId(), ulat, ulon, mlat, mlon,
                "秒杀默认地址", msg.priceCents(), strategy.quote(mlat, mlon, ulat, ulon, now),
                strategy.name(), msg.activityId(), snapshot, msg.idempotencyKey());
        Map<String, Object> saved;
        try {
            orderRepo.save(order);
            saved = view(order);
        } catch (DataIntegrityViolationException ex) {
            saved = view(orderRepo.findByIdempotencyKey(msg.idempotencyKey()).orElseThrow());
        }
        bindSeckillIdem(msg, ((Number) saved.get("id")).longValue());
        return saved;
    }

    /** 秒杀落单后回写 activity.seckill_idem.order_id；冲突回放首次订单。 */
    private void bindSeckillIdem(SeckillSuccessMessage msg, long orderId) {
        ActivityClient client = activityClient.getIfAvailable();
        if (client == null) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("idemKey", msg.idempotencyKey());
            body.put("orderId", orderId);
            body.put("payload", writeJson(msg));
            client.bindSeckillOrder(body);
        } catch (Exception ex) {
            log.warn("回写 seckill_idem 失败 key={}: {}", msg.idempotencyKey(), ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> mockPay(long id) {
        return pay(id, "WECHAT", "147258");
    }

    @Transactional
    public Map<String, Object> pay(long id, String channel, String password) {
        if (!"147258".equals(password)) {
            throw BizException.badRequest("BAD_PASSWORD", "支付密码错误");
        }
        if (!"WECHAT".equals(channel) && !"ALIPAY".equals(channel)) {
            throw BizException.badRequest("BAD_CHANNEL", "请选择微信支付或支付宝");
        }
        DeliveryOrder order = loadOwned(id);
        order.setStatus(OrderStateMachine.pay(order.getStatus()));
        order.setPayChannel(channel);
        order.setPayStatus("PAID");
        order.setPaidAt(Instant.now());
        if (order.getPayAmountCents() == null || order.getPayAmountCents() == 0) {
            int discount = snapshotDiscount(order);
            order.setPayAmountCents(nzInt(order.getGoodsAmountCents()) + nzInt(order.getFreightCents()) - discount);
        }
        touch(order);
        orderRepo.save(order);
        rabbitTemplate.convertAndSend(MqNames.ORDER_EXCHANGE, MqNames.ORDER_PAID,
                new OrderPaidMessage(order.getId(), order.getUserId(), order.getMerchantId()));
        tryAutoAccept(order.getId());
        return view(orderRepo.findById(order.getId()).orElse(order));
    }

    @Transactional
    public Map<String, Object> cancel(long id, String reasonCode, String reasonText) {
        DeliveryOrder order = loadOwned(id);
        requireReason(reasonCode);
        if ("CREATED".equals(order.getStatus()) || "CANCELLING".equals(order.getStatus())) {
            int penalty = PenaltyStrategy.penaltyCents(order.getStatus(), order.getFreightCents());
            if ("CREATED".equals(order.getStatus())) {
                order.setStatus(OrderStateMachine.applyCancel(order.getStatus()));
            }
            order.setCancelReason(reasonLabel(reasonCode, reasonText));
            order.setPenaltyCents(penalty);
            touch(order);
            orderRepo.save(order);
            return view(order);
        }
        return applyRefund(id, reasonCode, reasonText);
    }

    @Transactional
    public Map<String, Object> confirmCancel(long id) {
        DeliveryOrder order = loadOwned(id);
        order.setStatus(OrderStateMachine.confirmCancel(order.getStatus()));
        touch(order);
        orderRepo.save(order);
        releaseRider(order);
        restoreShopStock(order);
        return view(order);
    }

    @Transactional
    public Map<String, Object> applyRefund(long id, String reasonCode, String reasonText) {
        DeliveryOrder order = loadOwned(id);
        requireReason(reasonCode);
        if ("DELIVERING".equals(order.getStatus())) {
            order.setPenaltyCents(PenaltyStrategy.refundPenaltyCents(order.getStatus(), order.getFreightCents()));
        } else if (!"COMPLETED".equals(order.getStatus())) {
            order.setPenaltyCents(PenaltyStrategy.refundPenaltyCents(order.getStatus(), order.getFreightCents()));
        }
        order.setResumeStatus(order.getStatus());
        order.setStatus(OrderStateMachine.applyRefund(order.getStatus()));
        order.setRefundReason(reasonLabel(reasonCode, reasonText));
        order.setPayStatus("REFUNDING");
        touch(order);
        orderRepo.save(order);
        return view(order);
    }

    @Transactional
    public Map<String, Object> reviewRefund(long id, boolean approve, String reasonText) {
        DeliveryOrder order = loadVisible(id);
        AuthUser auth = AuthHolder.require();
        if (!auth.isMerchant() || order.getMerchantId() != auth.userId()) {
            throw BizException.forbidden("仅本店商家可审核退款");
        }
        if (approve) {
            boolean wasCompleted = "COMPLETED".equals(order.getResumeStatus());
            order.setStatus(OrderStateMachine.approveRefund(order.getStatus()));
            order.setPayStatus("REFUNDED");
            releaseRider(order);
            restoreShopStock(order);
            notifyMemberSpend(order.getUserId(), -nzInt(order.getPayAmountCents()), wasCompleted);
        } else {
            OrderStateMachine.rejectRefund(order.getStatus());
            order.setRefundRejectReason(reasonText == null || reasonText.isBlank() ? "商家未同意退款" : reasonText.trim());
            order.setStatus(order.getResumeStatus() == null ? "PAID" : order.getResumeStatus());
            order.setPayStatus("PAID");
        }
        touch(order);
        orderRepo.save(order);
        return view(order);
    }

    @Transactional
    public Map<String, Object> merchantAccept(long id) {
        DeliveryOrder order = loadVisible(id);
        AuthUser auth = AuthHolder.require();
        if (!auth.isMerchant() || order.getMerchantId() != auth.userId()) {
            throw BizException.forbidden("仅本店商家可接单");
        }
        order.setStatus(OrderStateMachine.merchantAccept(order.getStatus()));
        touch(order);
        orderRepo.save(order);
        return view(order);
    }

    @Transactional
    public Map<String, Object> merchantReject(long id, String reasonText) {
        DeliveryOrder order = loadVisible(id);
        AuthUser auth = AuthHolder.require();
        if (!auth.isMerchant() || order.getMerchantId() != auth.userId()) {
            throw BizException.forbidden("仅本店商家可拒绝接单");
        }
        order.setResumeStatus(order.getStatus());
        order.setStatus(OrderStateMachine.applyRefund(order.getStatus()));
        order.setRefundReason(reasonLabel("STOCK", reasonText == null || reasonText.isBlank() ? "商家无法接单" : reasonText.trim()));
        order.setPayStatus("REFUNDING");
        order.setStatus(OrderStateMachine.approveRefund(order.getStatus()));
        order.setPayStatus("REFUNDED");
        touch(order);
        orderRepo.save(order);
        restoreShopStock(order);
        return view(order);
    }

    @Transactional
    public Map<String, Object> arrive(long id) {
        DeliveryOrder order = loadVisible(id);
        AuthUser auth = AuthHolder.require();
        if (!auth.isRider() || order.getRiderId() == null || order.getRiderId() != auth.userId()) {
            throw BizException.forbidden("仅接单骑手可到店");
        }
        order.setStatus(OrderStateMachine.arrive(order.getStatus()));
        touch(order);
        orderRepo.save(order);
        return view(order);
    }

    @Transactional
    public Map<String, Object> deliver(long id) {
        DeliveryOrder order = loadVisible(id);
        AuthUser auth = AuthHolder.require();
        if (!auth.isRider() || order.getRiderId() == null || order.getRiderId() != auth.userId()) {
            throw BizException.forbidden("仅接单骑手可开始配送");
        }
        order.setStatus(OrderStateMachine.deliver(order.getStatus()));
        touch(order);
        orderRepo.save(order);
        return view(order);
    }

    @Transactional
    public Map<String, Object> complete(long id) {
        DeliveryOrder order = loadVisible(id);
        AuthUser auth = AuthHolder.require();
        if (!auth.isRider() || order.getRiderId() == null || order.getRiderId() != auth.userId()) {
            throw BizException.forbidden("仅接单骑手可完成订单");
        }
        order.setStatus(OrderStateMachine.complete(order.getStatus()));
        touch(order);
        orderRepo.save(order);
        releaseRider(order);
        try {
            accountClient.bumpCompleted(order.getMerchantId());
        } catch (Exception ignored) {
            // 销量计数失败不影响履约终态
        }
        if (order.getRiderId() != null) {
            try {
                accountClient.bumpRiderCompleted(order.getRiderId());
            } catch (Exception ignored) {
                // 骑手完成单计数失败不影响履约终态
            }
        }
        notifyMemberSpend(order.getUserId(), nzInt(order.getPayAmountCents()), true);
        bumpSkuSales(order);
        return view(order);
    }

    @Transactional
    public Map<String, Object> acceptGrab(GrabSuccessMessage msg) {
        DeliveryOrder order = orderRepo.findById(msg.orderId()).orElseThrow(() -> BizException.notFound("订单不存在"));
        if ("ACCEPTED".equals(order.getStatus()) && msg.riderId() == nz(order.getRiderId())) {
            return view(order);
        }
        if (!"PAID".equals(order.getStatus())) {
            return view(order);
        }
        order.setStatus(OrderStateMachine.accept(order.getStatus()));
        order.setRiderId(msg.riderId());
        touch(order);
        orderRepo.save(order);
        return view(order);
    }

    public Map<String, Object> get(long id) {
        DeliveryOrder order = loadVisible(id);
        Map<String, Object> body = view(order);
        attachReviewed(body, id);
        attachTipAndRider(body, order);
        return body;
    }

    public Map<String, Object> getInternal(long id) {
        return view(orderRepo.findById(id).orElseThrow(() -> BizException.notFound("订单不存在")));
    }

    public Map<String, Object> routePreview(long id) {
        DeliveryOrder order = loadVisible(id);
        double[] rider = riderLatLon(order);
        return routeService.route(
                rider[0], rider[1],
                order.getMerchantLat(), order.getMerchantLon(),
                order.getUserLat(), order.getUserLon());
    }

    public Map<String, Object> assign(long orderId) {
        AuthUser auth = AuthHolder.require();
        if (!auth.isMerchant()) {
            throw BizException.forbidden("仅商家可自动指派");
        }
        DeliveryOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        if (!auth.isMerchant() || order.getMerchantId() != auth.userId()) {
            throw BizException.forbidden("仅本店商家可自动指派");
        }
        if (!"PAID".equals(order.getStatus())) {
            throw BizException.conflict("ILLEGAL_STATE", "仅已支付订单可指派");
        }
        List<Map<String, Object>> riders;
        try {
            riders = accountClient.nearby(
                    order.getMerchantLat(), order.getMerchantLon(), 5000, "ONLINE", "IDLE").data();
        } catch (Exception ex) {
            throw BizException.notFound("附近无空闲骑手");
        }
        if (riders == null || riders.isEmpty()) {
            throw BizException.notFound("附近无空闲骑手");
        }
        Map<String, Object> best = null;
        double bestCost = Double.MAX_VALUE;
        Map<String, Object> bestRoute = null;
        for (Map<String, Object> rider : riders) {
            double rlat = num(rider.get("lat"));
            double rlon = num(rider.get("lon"));
            Map<String, Object> planned = routeService.route(
                    rlat, rlon, order.getMerchantLat(), order.getMerchantLon(),
                    order.getUserLat(), order.getUserLon());
            double cost = ((Number) planned.get("cost")).doubleValue();
            if (cost < bestCost) {
                bestCost = cost;
                best = rider;
                bestRoute = planned;
            }
        }
        Object riderIdRaw = best.get("riderId") != null ? best.get("riderId") : best.get("userId");
        long riderId = ((Number) riderIdRaw).longValue();
        ActivityClient activity = activityClient.getIfAvailable();
        if (activity != null) {
            activity.occupy(Map.of("orderId", orderId, "riderId", riderId));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("riderId", riderId);
        body.put("route", bestRoute);
        return body;
    }

    public Map<String, Object> track(long id) {
        DeliveryOrder order = loadVisible(id);
        Map<String, Object> body = new LinkedHashMap<>(view(order));
        Map<String, Object> rider = null;
        if (order.getRiderId() != null) {
            try {
                rider = accountClient.rider(order.getRiderId()).data();
            } catch (Exception ignored) {
                rider = Map.of("userId", order.getRiderId());
            }
        }
        body.put("rider", rider);
        boolean live = "PAID".equals(order.getStatus())
                || "ACCEPTED".equals(order.getStatus())
                || "ARRIVED".equals(order.getStatus())
                || "DELIVERING".equals(order.getStatus());
        if (live) {
            try {
                Map<String, Object> route = routePreview(id);
                body.put("route", route);
                body.put("etaMs", route.get("etaMs"));
                body.put("algorithm", route.get("algorithm"));
            } catch (Exception ex) {
                log.warn("track 规划失败 orderId={}: {}", id, ex.getMessage());
                body.put("route", Map.of());
            }
        }
        attachReviewed(body, id);
        attachTipAndRider(body, order);
        return body;
    }

    public Map<String, Object> merchantStats(String range, Integer days) {
        MerchantWindow window = loadMerchantWindow(range, days);
        Map<String, Object> body = window.stats().toMap();
        body.putAll(MerchantReport.extras(window.stats(), window.top()));
        return body;
    }

    public byte[] merchantReportCsv(String range) {
        return MerchantReport.csv(loadMerchantWindow(range, null).stats());
    }

    public Map<String, Object> riderEarnings() {
        AuthUser auth = AuthHolder.require();
        if (!auth.isRider()) {
            throw BizException.forbidden("仅骑手可查看收入");
        }
        ZoneId zone = MerchantStatsAggregator.ZONE;
        LocalDate today = LocalDate.now(zone);
        Instant monthStart = YearMonth.from(today).atDay(1).atStartOfDay(zone).toInstant();
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT status, freight_cents, penalty_cents, created_at, updated_at FROM t_order WHERE rider_id = ? AND created_at >= ?",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("status", rs.getString("status"));
                    row.put("freight", rs.getInt("freight_cents"));
                    row.put("penalty", rs.getInt("penalty_cents"));
                    Instant updated = rs.getTimestamp("updated_at") == null
                            ? rs.getTimestamp("created_at").toInstant()
                            : rs.getTimestamp("updated_at").toInstant();
                    row.put("updatedAt", updated);
                    return row;
                },
                auth.userId(), java.sql.Timestamp.from(monthStart));
        long monthFreight = 0;
        long monthPenalty = 0;
        int monthCompleted = 0;
        int todayCompleted = 0;
        for (Map<String, Object> row : rows) {
            if (!"COMPLETED".equals(row.get("status"))) {
                continue;
            }
            monthFreight += ((Number) row.get("freight")).longValue();
            monthPenalty += ((Number) row.get("penalty")).longValue();
            monthCompleted++;
            Instant updated = (Instant) row.get("updatedAt");
            if (updated != null && !updated.isBefore(dayStart)) {
                todayCompleted++;
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("monthFreightCents", monthFreight);
        body.put("monthPenaltyCents", monthPenalty);
        body.put("monthIncomeNote", "本月收入口径=完成订单运费合计；penalty_cents 为取消补偿记账，另列不并入收入");
        body.put("completedThisMonth", monthCompleted);
        body.put("completedToday", todayCompleted);
        return body;
    }

    private double[] riderLatLon(DeliveryOrder order) {
        if (order.getRiderId() != null) {
            try {
                Map<String, Object> rider = accountClient.rider(order.getRiderId()).data();
                if (rider != null && rider.get("lat") != null && rider.get("lon") != null) {
                    return new double[]{num(rider.get("lat")), num(rider.get("lon"))};
                }
            } catch (Exception ignored) {
                // 骑手坐标读不到时退回商家附近，保证仍能规划
            }
        }
        return new double[]{order.getMerchantLat(), order.getMerchantLon()};
    }

    public Map<String, Object> list(Long cursor, int size, String status, Integer page, String q, String scene) {
        AuthUser auth = AuthHolder.require();
        int pageSize = OrderSearch.pageSize(size);
        String keyword = q == null ? "" : q.trim();
        boolean pageMode = page != null && page > 0;
        int pageNo = OrderSearch.pageNo(page);
        boolean needSearch = !keyword.isEmpty();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                needSearch
                        ? "SELECT id, status, address_detail, sku_snapshot FROM t_order WHERE "
                        : "SELECT id FROM t_order WHERE ");
        appendListScope(sql, args, auth, scene);
        if (!pageMode && cursor != null && cursor > 0) {
            sql.append(" AND id < ?");
            args.add(cursor);
        }
        String sc = scene == null ? "" : scene.trim().toLowerCase();
        if (status != null && !status.isBlank() && !"hall".equals(sc) && !"done".equals(sc)) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY id DESC");
        boolean sqlLimit = !pageMode && !needSearch;
        if (sqlLimit) {
            sql.append(" LIMIT ?");
            args.add(pageSize);
        }
        record Probe(long id, String status, String address, String snapshot) {
        }
        List<Probe> probes = jdbc.query(sql.toString(), (rs, i) -> needSearch
                ? new Probe(rs.getLong("id"), rs.getString("status"), rs.getString("address_detail"),
                rs.getString("sku_snapshot"))
                : new Probe(rs.getLong("id"), null, null, null), args.toArray());
        if (needSearch) {
            probes = probes.stream()
                    .filter(p -> OrderSearch.matches(p.id(), p.status(), p.address(), p.snapshot(), keyword))
                    .toList();
        }
        int total = probes.size();
        List<Probe> pageRows;
        boolean hasNext;
        boolean hasPrev;
        if (pageMode || needSearch) {
            if (!pageMode) {
                pageRows = probes.size() > pageSize ? probes.subList(0, pageSize) : probes;
                hasNext = probes.size() > pageSize;
                hasPrev = false;
                pageNo = 1;
            } else {
                OrderSearch.Slice<Probe> slice = OrderSearch.slice(probes, pageNo, pageSize, Probe::id);
                pageRows = slice.items();
                hasNext = slice.hasNext();
                hasPrev = slice.hasPrev();
                pageNo = slice.page();
            }
        } else {
            pageRows = probes;
            hasNext = probes.size() == pageSize;
            hasPrev = false;
        }
        List<Long> ids = pageRows.stream().map(Probe::id).toList();
        List<Map<String, Object>> items = ids.isEmpty() ? List.of() : orderRepo.findByIdIn(ids).stream()
                .sorted(Comparator.comparing(DeliveryOrder::getId).reversed())
                .map(o -> {
                    Map<String, Object> row = view(o);
                    if ("COMPLETED".equals(o.getStatus())) {
                        attachReviewed(row, o.getId());
                    }
                    return row;
                })
                .toList();
        Long next = ids.isEmpty() ? null : ids.get(ids.size() - 1);
        if (!hasNext) {
            next = null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("nextCursor", next);
        body.put("size", pageSize);
        body.put("page", pageMode ? pageNo : null);
        body.put("total", pageMode || needSearch ? total : null);
        body.put("hasNext", hasNext);
        body.put("hasPrev", hasPrev);
        body.put("q", keyword);
        body.put("scene", scene == null ? "" : scene);
        return body;
    }

    private static void appendListScope(StringBuilder sql, List<Object> args, AuthUser auth, String scene) {
        if (auth.isRider()) {
            String sc = scene == null ? "" : scene.trim().toLowerCase();
            if ("hall".equals(sc)) {
                sql.append("((status = 'PAID' AND rider_id IS NULL) OR (rider_id = ? AND status IN ('ACCEPTED','ARRIVED','DELIVERING')))");
                args.add(auth.userId());
                return;
            }
            if ("done".equals(sc)) {
                sql.append("rider_id = ? AND status = 'COMPLETED'");
                args.add(auth.userId());
                return;
            }
            sql.append("(rider_id = ? OR (status = 'PAID' AND rider_id IS NULL))");
            args.add(auth.userId());
            return;
        }
        if (auth.isMerchant()) {
            sql.append("merchant_id = ?");
            args.add(auth.userId());
            return;
        }
        sql.append("user_id = ?");
        args.add(auth.userId());
    }

    private DeliveryOrder newOrder(long userId, long merchantId, double ulat, double ulon, double mlat, double mlon,
                                   String address, int goods, int freight, String strategy, Long activityId,
                                   String snapshot, String idem) {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(leafAllocator.nextId());
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setStatus("CREATED");
        order.setGoodsAmountCents(goods);
        order.setFreightCents(freight);
        order.setFreightStrategy(strategy);
        order.setPenaltyCents(0);
        order.setUserLat(ulat);
        order.setUserLon(ulon);
        order.setMerchantLat(mlat);
        order.setMerchantLon(mlon);
        order.setAddressDetail(address);
        order.setActivityId(activityId);
        order.setSkuSnapshot(snapshot);
        order.setIdempotencyKey(idem);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setVersion(0L);
        order.setPayStatus("UNPAID");
        order.setPayAmountCents(goods + freight);
        return order;
    }

    private DeliveryOrder loadVisible(long id) {
        DeliveryOrder order = orderRepo.findById(id).orElseThrow(() -> BizException.notFound("订单不存在"));
        AuthUser auth = AuthHolder.require();
        if (!OrderAccess.canView(auth, order)) {
            throw BizException.forbidden("无权查看该订单");
        }
        return order;
    }

    private DeliveryOrder loadOwned(long id) {
        DeliveryOrder order = loadVisible(id);
        if (!OrderAccess.canPayOrCancel(AuthHolder.require(), order)) {
            throw BizException.forbidden("仅下单用户可操作");
        }
        return order;
    }

    private void releaseRider(DeliveryOrder order) {
        if (order.getRiderId() == null) {
            return;
        }
        try {
            accountClient.acceptStatus(order.getRiderId(), Map.of("acceptStatus", "IDLE"));
        } catch (Exception ignored) {
            // 骑手状态校准失败不阻断履约终态
        }
    }

    private void touch(DeliveryOrder order) {
        order.setUpdatedAt(Instant.now());
        order.setVersion(order.getVersion() + 1);
    }

    private Map<String, Object> view(DeliveryOrder order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", order.getId());
        map.put("userId", order.getUserId());
        map.put("merchantId", order.getMerchantId());
        map.put("riderId", order.getRiderId());
        map.put("status", order.getStatus());
        map.put("goodsAmountCents", order.getGoodsAmountCents());
        map.put("freightCents", order.getFreightCents());
        map.put("freightStrategy", order.getFreightStrategy());
        map.put("penaltyCents", order.getPenaltyCents());
        map.put("userLat", order.getUserLat());
        map.put("userLon", order.getUserLon());
        map.put("merchantLat", order.getMerchantLat());
        map.put("merchantLon", order.getMerchantLon());
        map.put("addressDetail", order.getAddressDetail());
        map.put("activityId", order.getActivityId());
        map.put("skuSnapshot", readSnapshot(order.getSkuSnapshot()));
        map.put("createdAt", order.getCreatedAt());
        map.put("updatedAt", order.getUpdatedAt());
        map.put("payChannel", order.getPayChannel());
        map.put("payStatus", order.getPayStatus());
        map.put("paidAt", order.getPaidAt());
        map.put("payAmountCents", order.getPayAmountCents());
        map.put("cancelReason", order.getCancelReason());
        map.put("refundReason", order.getRefundReason());
        map.put("refundRejectReason", order.getRefundRejectReason());
        map.put("resumeStatus", order.getResumeStatus());
        map.put("idempotencyKey", order.getIdempotencyKey());
        map.put("coverUrl", firstItemImage(readSnapshot(order.getSkuSnapshot())));
        return map;
    }

    @Transactional
    public Map<String, Object> reviewOrder(long id, int score, String content, List<Long> skuIds,
                                           List<String> photoUrls, Integer riderScore) {
        DeliveryOrder order = loadOwned(id);
        AuthUser auth = AuthHolder.require();
        if (!auth.isUser()) {
            throw BizException.forbidden("仅下单用户可评价");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw BizException.conflict("NOT_COMPLETED", "仅完成订单可评价");
        }
        Integer riderStars = riderScore;
        if (order.getRiderId() != null) {
            if (riderStars == null || riderStars < 1 || riderStars > 5) {
                throw BizException.badRequest("BAD_RIDER_SCORE", "请给骑手点星");
            }
        } else {
            riderStars = null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", auth.userId());
        payload.put("orderId", order.getId());
        payload.put("merchantId", order.getMerchantId());
        payload.put("score", score);
        payload.put("content", content);
        payload.put("skuIds", skuIds == null ? List.of() : skuIds);
        payload.put("photoUrls", photoUrls == null ? List.of() : photoUrls);
        payload.put("riderScore", riderStars);
        try {
            ApiResult<Map<String, Object>> res = accountClient.createReview(payload);
            if (res != null && Boolean.FALSE.equals(res.ok())) {
                throw BizException.conflict(res.code() == null ? "REVIEWED" : res.code(),
                        res.message() == null ? "评价失败" : res.message());
            }
            Map<String, Object> body = view(order);
            body.put("review", res == null ? Map.of() : res.data());
            body.put("reviewed", true);
            if (order.getRiderId() != null && riderStars != null) {
                try {
                    accountClient.rateRider(order.getRiderId(), Map.of("score", riderStars));
                } catch (Exception ignored) {
                    // 骑手评分失败不影响店铺评价
                }
            }
            attachTipAndRider(body, order);
            return body;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("409") || msg.contains("REVIEWED")) {
                throw BizException.conflict("REVIEWED", "该订单已评价");
            }
            throw BizException.conflict("REVIEW_FAIL", "评价失败");
        }
    }

    @Transactional
    public Map<String, Object> tip(long id, String giftCode) {
        DeliveryOrder order = loadOwned(id);
        AuthUser auth = AuthHolder.require();
        if (!auth.isUser()) {
            throw BizException.forbidden("仅下单用户可打赏");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw BizException.conflict("NOT_COMPLETED", "仅完成订单可打赏");
        }
        if (order.getRiderId() == null) {
            throw BizException.conflict("NO_RIDER", "该订单没有配送骑手");
        }
        TipGifts.Gift gift = TipGifts.requireCode(giftCode);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("riderId", order.getRiderId());
        payload.put("userId", auth.userId());
        payload.put("giftCode", gift.code());
        payload.put("cents", gift.cents());
        try {
            ApiResult<Map<String, Object>> res = accountClient.recordTip(payload);
            if (res != null && Boolean.FALSE.equals(res.ok())) {
                throw BizException.conflict(res.code() == null ? "TIPPED" : res.code(),
                        res.message() == null ? "打赏失败" : res.message());
            }
            Map<String, Object> body = view(order);
            attachReviewed(body, id);
            attachTipAndRider(body, order);
            body.put("tip", res == null ? Map.of() : res.data());
            return body;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("409") || msg.contains("TIPPED")) {
                throw BizException.conflict("TIPPED", "该订单已打赏");
            }
            throw BizException.conflict("TIP_FAIL", "打赏失败");
        }
    }

    public Map<String, Object> completedMerchants(long userId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT merchant_id, COUNT(*) c FROM t_order WHERE user_id = ? AND status = 'COMPLETED' GROUP BY merchant_id",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("merchantId", rs.getLong("merchant_id"));
                    row.put("count", rs.getInt("c"));
                    return row;
                },
                userId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put(String.valueOf(row.get("merchantId")), ((Number) row.get("count")).intValue());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("counts", counts);
        return body;
    }

    private void attachReviewed(Map<String, Object> body, long orderId) {
        try {
            Map<String, Object> data = accountClient.reviewByOrder(orderId).data();
            if (data != null) {
                body.put("reviewed", Boolean.TRUE.equals(data.get("reviewed")));
                if (data.get("reviewId") != null) {
                    body.put("reviewId", data.get("reviewId"));
                }
                if (data.get("review") instanceof Map<?, ?> review) {
                    body.put("review", review);
                }
            } else {
                body.put("reviewed", false);
            }
        } catch (Exception ex) {
            body.put("reviewed", false);
        }
    }

    private void attachTipAndRider(Map<String, Object> body, DeliveryOrder order) {
        if (order.getRiderId() != null && body.get("riderProfile") == null) {
            Object existing = body.get("rider");
            if (existing instanceof Map<?, ?> map && map.get("bio") != null) {
                body.put("riderProfile", existing);
            } else {
                try {
                    Map<String, Object> profile = accountClient.riderProfile(order.getRiderId()).data();
                    if (profile != null) {
                        body.put("riderProfile", profile);
                    }
                } catch (Exception ignored) {
                    // 简介读不到不阻断订单详情
                }
            }
        }
        boolean tipped = false;
        Integer tipCents = null;
        try {
            Map<String, Object> tip = accountClient.tipByOrder(order.getId()).data();
            if (tip != null) {
                tipped = Boolean.TRUE.equals(tip.get("tipped"));
                if (tip.get("cents") instanceof Number n) {
                    tipCents = n.intValue();
                }
                if (tip.get("giftCode") != null) {
                    body.put("tipGiftCode", tip.get("giftCode"));
                }
                if (tip.get("giftLabel") != null) {
                    body.put("tipGiftLabel", tip.get("giftLabel"));
                }
            }
        } catch (Exception ignored) {
            // 打赏状态读不到时按未打赏展示
        }
        body.put("tipped", tipped);
        body.put("tipCents", tipCents);
        AuthUser auth = AuthHolder.get();
        boolean canTip = auth != null && auth.isUser()
                && order.getUserId() == auth.userId()
                && "COMPLETED".equals(order.getStatus())
                && order.getRiderId() != null
                && !tipped;
        body.put("canTip", canTip);
    }

    private Map<String, Object> requireData(ApiResult<Map<String, Object>> result, String missing) {
        if (result == null || result.data() == null) {
            throw BizException.notFound(missing);
        }
        return result.data();
    }

    private static double num(Object value) {
        return ((Number) value).doubleValue();
    }

    private static long nz(Long value) {
        return value == null ? -1 : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildQuote(long merchantId, long addressId, List<Map<String, Object>> rawItems,
                                           Long couponId, Integer clientPayCents, boolean persistCtx) {
        AuthUser user = AuthHolder.require();
        Map<String, Object> merchant = requireData(accountClient.merchant(merchantId), "商家不存在");
        if (Boolean.FALSE.equals(merchant.get("open")) || "OFFLINE".equals(String.valueOf(merchant.get("onlineStatus")))) {
            throw BizException.conflict("SHOP_CLOSED", "商家休息，暂不可下单");
        }
        Map<String, Object> address = requireData(accountClient.address(user.userId(), addressId), "地址不存在");
        if (!Geo.inRange(num(address.get("lat")), num(address.get("lon")),
                num(merchant.get("lat")), num(merchant.get("lon")), maxKm)) {
            throw BizException.conflict("OUT_OF_RANGE", "超配送范围不可下单");
        }
        List<Map<String, Object>> catalog = merchant.get("items") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        List<Map<String, Object>> lines = new ArrayList<>();
        int goods = 0;
        if (rawItems != null && !rawItems.isEmpty()) {
            for (Map<String, Object> line : rawItems) {
                long skuId = ((Number) line.get("skuId")).longValue();
                int qty = Math.max(1, ((Number) line.getOrDefault("qty", 1)).intValue());
                Map<String, Object> sku = catalog.stream()
                        .filter(s -> ((Number) s.get("id")).longValue() == skuId)
                        .findFirst()
                        .orElseThrow(() -> BizException.badRequest("BAD_SKU", "商品不存在: " + skuId));
                if ("OFFLINE".equals(String.valueOf(sku.get("status")))) {
                    throw BizException.conflict("SKU_OFF", "商品已下架，请刷新后重选");
                }
                int price = ((Number) sku.get("priceCents")).intValue();
                if (line.get("priceCents") instanceof Number given && given.intValue() >= 0 && given.intValue() != price) {
                    throw BizException.conflict("PRICE_MISMATCH", "商品价格已变化，请刷新后下单");
                }
                if (sku.get("stock") instanceof Number st && st.intValue() < qty) {
                    throw BizException.conflict(ErrorCodes.STOCK_EMPTY, "库存不足，请减少数量后下单");
                }
                goods += price * qty;
                Map<String, Object> snap = new LinkedHashMap<>();
                snap.put("skuId", skuId);
                snap.put("name", sku.get("name"));
                snap.put("qty", qty);
                snap.put("priceCents", price);
                snap.put("imageUrl", sku.get("imageUrl"));
                snap.put("spec", sku.get("spec"));
                lines.add(snap);
            }
        } else {
            goods = 2500;
        }
        Instant now = Instant.now();
        FreightStrategy strategy = freightSelector.select(now);
        int freight = strategy.quote(num(merchant.get("lat")), num(merchant.get("lon")),
                num(address.get("lat")), num(address.get("lon")), now);
        List<Map<String, Object>> couponOptions = loadCouponOptions(user.userId(), merchantId, goods, freight);
        Long suggestedId = bestCouponId(couponOptions);
        Long applyId = couponId;
        if (!persistCtx && couponId == null) {
            applyId = suggestedId;
        }
        if (applyId != null && applyId == 0) {
            applyId = null;
        }
        int discount = 0;
        String couponName = null;
        if (applyId != null) {
            try {
                Map<String, Object> quoted = requireData(accountClient.quoteCoupon(Map.of(
                        "userId", user.userId(),
                        "couponId", applyId,
                        "merchantId", merchantId,
                        "goodsCents", goods
                )), "优惠券不可用");
                discount = ((Number) quoted.get("discountCents")).intValue();
                couponName = String.valueOf(quoted.get("name"));
            } catch (BizException ex) {
                throw ex;
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "优惠券不可用" : ex.getMessage();
                if (msg.contains("未满") || msg.contains("门槛")) {
                    throw BizException.badRequest("COUPON_MIN", "未满券门槛");
                }
                throw BizException.badRequest("COUPON", "优惠券不可用");
            }
        }
        int pay = goods + freight - discount;
        if (clientPayCents != null && clientPayCents != pay) {
            throw BizException.conflict("AMOUNT_MISMATCH", "应付金额不一致，请刷新结算页");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("items", lines);
        snapshot.put("couponId", applyId);
        snapshot.put("couponName", couponName);
        snapshot.put("discountCents", discount);
        snapshot.put("shopName", merchant.get("shopName"));
        snapshot.put("coverUrl", merchant.get("coverUrl"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("goodsAmountCents", goods);
        body.put("freightCents", freight);
        body.put("freightStrategy", strategy.name());
        body.put("discountCents", discount);
        body.put("couponName", couponName);
        body.put("couponId", applyId);
        body.put("payCents", pay);
        body.put("suggestedCouponId", suggestedId);
        body.put("couponOptions", couponOptions);
        body.put("snapshot", snapshot);
        if (persistCtx) {
            body.put("merchant", merchant);
            body.put("address", address);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadCouponOptions(long userId, long merchantId, int goods, int freight) {
        try {
            ApiResult<Map<String, Object>> res = accountClient.couponOptions(Map.of(
                    "userId", userId,
                    "couponId", 0,
                    "merchantId", merchantId,
                    "goodsCents", goods
            ));
            Object rawItems = res == null || res.data() == null ? List.of() : res.data().get("items");
            List<Map<String, Object>> raw = rawItems instanceof List<?> list
                    ? (List<Map<String, Object>>) list : List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : raw) {
                Map<String, Object> opt = new LinkedHashMap<>(row);
                boolean available = Boolean.TRUE.equals(opt.get("available"));
                int disc = opt.get("discountCents") instanceof Number n ? n.intValue() : 0;
                int pay = goods + freight - (available ? disc : 0);
                opt.put("payCents", pay);
                if (opt.get("couponId") == null && opt.get("id") != null) {
                    opt.put("couponId", opt.get("id"));
                }
                out.add(opt);
            }
            return out;
        } catch (Exception ex) {
            log.debug("券列表不可用: {}", ex.getMessage());
            return List.of();
        }
    }

    private static Long bestCouponId(List<Map<String, Object>> options) {
        Long best = null;
        int bestPay = Integer.MAX_VALUE;
        Instant bestEnd = Instant.MAX;
        for (Map<String, Object> opt : options) {
            if (!Boolean.TRUE.equals(opt.get("available"))) {
                continue;
            }
            long id = ((Number) opt.get("couponId")).longValue();
            int pay = opt.get("payCents") instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
            Instant end = parseInstant(opt.get("endAt"));
            if (pay < bestPay || (pay == bestPay && end.isBefore(bestEnd)) || (pay == bestPay && end.equals(bestEnd) && (best == null || id < best))) {
                best = id;
                bestPay = pay;
                bestEnd = end;
            }
        }
        return best;
    }

    private static Instant parseInstant(Object raw) {
        if (raw instanceof Instant i) {
            return i;
        }
        if (raw == null) {
            return Instant.MAX;
        }
        try {
            return Instant.parse(String.valueOf(raw));
        } catch (Exception ex) {
            return Instant.MAX;
        }
    }

    private void notifyMemberSpend(long userId, int deltaCents, boolean wasCompleted) {
        if (deltaCents == 0) {
            return;
        }
        try {
            accountClient.memberSpend(Map.of(
                    "userId", userId,
                    "deltaCents", deltaCents,
                    "wasCompleted", wasCompleted
            ));
        } catch (Exception ex) {
            log.debug("会员净实付回写失败: {}", ex.getMessage());
        }
    }

    private Object readSnapshot(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            return raw;
        }
    }

    @SuppressWarnings("unchecked")
    private int snapshotDiscount(DeliveryOrder order) {
        Object snap = readSnapshot(order.getSkuSnapshot());
        if (snap instanceof Map<?, ?> map && map.get("discountCents") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private String firstItemImage(Object snap) {
        if (snap instanceof Map<?, ?> map && map.get("items") instanceof List<?> items && !items.isEmpty()) {
            Object first = items.get(0);
            if (first instanceof Map<?, ?> row && row.get("imageUrl") != null) {
                return String.valueOf(row.get("imageUrl"));
            }
        }
        return null;
    }

    private static void requireReason(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw BizException.badRequest("NEED_REASON", "请选择原因");
        }
    }

    private static String reasonLabel(String code, String text) {
        String extra = text == null || text.isBlank() ? "" : ("：" + text.trim());
        return code + extra;
    }

    private static int nzInt(Integer value) {
        return value == null ? 0 : value;
    }

    @SuppressWarnings("unchecked")
    private MerchantWindow loadMerchantWindow(String range, Integer days) {
        AuthUser auth = AuthHolder.require();
        if (!auth.isMerchant()) {
            throw BizException.forbidden("仅商家可查看店铺统计");
        }
        int window = MerchantStatsAggregator.resolveDays(range, days);
        Instant from = LocalDate.now(MerchantStatsAggregator.ZONE)
                .minusDays(window - 1L)
                .atStartOfDay(MerchantStatsAggregator.ZONE)
                .toInstant();
        if (window >= 180) {
            from = LocalDate.now(MerchantStatsAggregator.ZONE)
                    .minusMonths(11)
                    .withDayOfMonth(1)
                    .atStartOfDay(MerchantStatsAggregator.ZONE)
                    .toInstant();
        }
        List<Map<String, Object>> completedSnaps = new ArrayList<>();
        List<MerchantStatsAggregator.OrderRow> rows = jdbc.query(
                "SELECT status, goods_amount_cents, freight_cents, pay_amount_cents, created_at, sku_snapshot FROM t_order WHERE merchant_id = ? AND created_at >= ?",
                (rs, i) -> {
                    int goods = rs.getInt("goods_amount_cents");
                    int freight = rs.getInt("freight_cents");
                    int pay = rs.getInt("pay_amount_cents");
                    if (rs.wasNull() || pay <= 0) {
                        pay = goods + freight;
                    }
                    String status = rs.getString("status");
                    if ("COMPLETED".equals(status)) {
                        Object snap = readSnapshot(rs.getString("sku_snapshot"));
                        if (snap instanceof Map<?, ?> map) {
                            completedSnaps.add((Map<String, Object>) map);
                        }
                    }
                    return new MerchantStatsAggregator.OrderRow(
                            status,
                            goods,
                            freight,
                            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant(),
                            pay);
                },
                auth.userId(), java.sql.Timestamp.from(from));
        MerchantStatsAggregator.Stats stats = MerchantStatsAggregator.aggregate(
                rows, LocalDate.now(MerchantStatsAggregator.ZONE), window);
        return new MerchantWindow(stats, MerchantReport.topSkus(completedSnaps, 3));
    }

    private record MerchantWindow(MerchantStatsAggregator.Stats stats, List<MerchantReport.SkuHit> top) {
    }

    @Transactional
    public void tryAutoAccept(long orderId) {
        try {
            DeliveryOrder order = orderRepo.findById(orderId).orElse(null);
            if (order == null || !"MERCHANT_PENDING".equals(order.getStatus())) {
                return;
            }
            Map<String, Object> merchant = accountClient.merchant(order.getMerchantId()).data();
            if (merchant == null || !Boolean.TRUE.equals(merchant.get("autoAccept"))) {
                return;
            }
            order.setStatus(OrderStateMachine.merchantAccept(order.getStatus()));
            touch(order);
            orderRepo.save(order);
        } catch (Exception ex) {
            log.warn("自动接单跳过 order={}: {}", orderId, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> snapshotLineItems(Object snapshot) {
        if (snapshot instanceof Map<?, ?> map && map.get("items") instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> row) {
                    out.add((Map<String, Object>) row);
                }
            }
            return out;
        }
        return List.of();
    }

    private List<Map<String, Object>> shopStockItems(Object snapshot) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Map<String, Object> it : snapshotLineItems(snapshot)) {
            if (!(it.get("skuId") instanceof Number id)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuId", id.longValue());
            row.put("qty", it.get("qty") instanceof Number q ? Math.max(1, q.intValue()) : 1);
            payload.add(row);
        }
        return payload;
    }

    private boolean applyShopStock(String action, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("action", action);
            body.put("items", items);
            accountClient.changeSkuStock(body);
            return true;
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if ("deduct".equals(action) && (msg.contains("409") || msg.contains("STOCK") || msg.contains("库存"))) {
                throw BizException.conflict(ErrorCodes.STOCK_EMPTY, "库存不足");
            }
            if ("deduct".equals(action)) {
                log.warn("扣减商家库存失败: {}", msg);
                throw BizException.conflict("STOCK_FAIL", "扣减库存失败");
            }
            log.warn("回补商家库存失败: {}", msg);
            return false;
        }
    }

    private void restoreShopStock(DeliveryOrder order) {
        if (order.getActivityId() != null) {
            return;
        }
        applyShopStock("restore", shopStockItems(readSnapshot(order.getSkuSnapshot())));
    }

    private void bumpSkuSales(DeliveryOrder order) {
        if (order.getActivityId() != null) {
            return;
        }
        List<Map<String, Object>> items = shopStockItems(readSnapshot(order.getSkuSnapshot()));
        if (items.isEmpty()) {
            return;
        }
        try {
            accountClient.bumpSkuSales(Map.of("items", items));
        } catch (Exception ex) {
            log.warn("回写商品已售失败: {}", ex.getMessage());
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
