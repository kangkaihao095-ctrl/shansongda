package com.shansuda.order.web;

import com.shansuda.common.api.ApiResult;
import com.shansuda.order.service.OrderService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders/preview")
    public ApiResult<Map<String, Object>> preview(@RequestBody CreateRequest req) {
        return ApiResult.ok(orderService.preview(req.merchantId(), req.addressId(), req.itemMaps(), req.couponId(), req.clientPayCents()));
    }

    @PostMapping("/api/orders")
    public ApiResult<Map<String, Object>> create(@RequestBody CreateRequest req) {
        return ApiResult.ok(orderService.create(req.merchantId(), req.addressId(), req.itemMaps(), req.couponId(), req.clientPayCents()));
    }

    @PostMapping("/api/orders/{id}/mock-pay")
    public ApiResult<Map<String, Object>> mockPay(@PathVariable long id) {
        return ApiResult.ok(orderService.mockPay(id));
    }

    @PostMapping("/api/orders/{id}/pay")
    public ApiResult<Map<String, Object>> pay(@PathVariable long id, @RequestBody PayRequest req) {
        return ApiResult.ok(orderService.pay(id, req.channel(), req.password()));
    }

    @GetMapping("/api/orders")
    public ApiResult<Map<String, Object>> list(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scene) {
        return ApiResult.ok(orderService.list(cursor, size, status, page, q, scene));
    }

    @GetMapping("/api/orders/{id}")
    public ApiResult<Map<String, Object>> get(@PathVariable long id) {
        return ApiResult.ok(orderService.get(id));
    }

    @PostMapping("/api/orders/{id}/cancel")
    public ApiResult<Map<String, Object>> cancel(@PathVariable long id, @RequestBody(required = false) ReasonRequest req) {
        ReasonRequest body = req == null ? new ReasonRequest(null, null) : req;
        return ApiResult.ok(orderService.cancel(id, body.reasonCode(), body.reasonText()));
    }

    @PostMapping("/api/orders/{id}/cancel-confirm")
    public ApiResult<Map<String, Object>> confirmCancel(@PathVariable long id) {
        return ApiResult.ok(orderService.confirmCancel(id));
    }

    @PostMapping("/api/orders/{id}/refund")
    public ApiResult<Map<String, Object>> refund(@PathVariable long id, @RequestBody ReasonRequest req) {
        return ApiResult.ok(orderService.applyRefund(id, req.reasonCode(), req.reasonText()));
    }

    @PostMapping("/api/orders/{id}/refund-review")
    public ApiResult<Map<String, Object>> review(@PathVariable long id, @RequestBody ReviewRequest req) {
        return ApiResult.ok(orderService.reviewRefund(id, req.approve(), req.reasonText()));
    }

    @PostMapping("/api/orders/{id}/merchant-accept")
    public ApiResult<Map<String, Object>> merchantAccept(@PathVariable long id) {
        return ApiResult.ok(orderService.merchantAccept(id));
    }

    @PostMapping("/api/orders/{id}/merchant-reject")
    public ApiResult<Map<String, Object>> merchantReject(@PathVariable long id, @RequestBody(required = false) ReasonRequest req) {
        ReasonRequest body = req == null ? new ReasonRequest(null, null) : req;
        return ApiResult.ok(orderService.merchantReject(id, body.reasonText()));
    }

    @PostMapping("/api/orders/{id}/arrive")
    public ApiResult<Map<String, Object>> arrive(@PathVariable long id) {
        return ApiResult.ok(orderService.arrive(id));
    }

    @PostMapping("/api/orders/{id}/deliver")
    public ApiResult<Map<String, Object>> deliver(@PathVariable long id) {
        return ApiResult.ok(orderService.deliver(id));
    }

    @PostMapping("/api/orders/{id}/complete")
    public ApiResult<Map<String, Object>> complete(@PathVariable long id) {
        return ApiResult.ok(orderService.complete(id));
    }

    @PostMapping("/api/orders/{id}/review")
    public ApiResult<Map<String, Object>> reviewOrder(@PathVariable long id, @RequestBody OrderReviewRequest req) {
        return ApiResult.ok(orderService.reviewOrder(id, req.score(), req.content(), req.skuIds(),
                req.photoUrls(), req.riderScore()));
    }

    @PostMapping("/api/orders/{id}/tip")
    public ApiResult<Map<String, Object>> tip(@PathVariable long id, @RequestBody TipRequest req) {
        return ApiResult.ok(orderService.tip(id, req.giftCode()));
    }

    @GetMapping("/api/orders/{id}/route")
    public ApiResult<Map<String, Object>> route(@PathVariable long id) {
        return ApiResult.ok(orderService.routePreview(id));
    }

    @GetMapping("/api/orders/{id}/track")
    public ApiResult<Map<String, Object>> track(@PathVariable long id) {
        return ApiResult.ok(orderService.track(id));
    }

    @GetMapping("/api/merchant/stats")
    public ApiResult<Map<String, Object>> merchantStats(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) Integer days) {
        return ApiResult.ok(orderService.merchantStats(range, days));
    }

    @GetMapping(value = "/api/merchant/report.csv", produces = "text/csv")
    public ResponseEntity<byte[]> merchantReportCsv(@RequestParam(required = false) String range) {
        String key = range == null || range.isBlank() ? "7d" : range.trim();
        byte[] body = orderService.merchantReportCsv(key);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=shansuda-report-" + key + ".csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/api/rider/stats")
    public ApiResult<Map<String, Object>> riderStats() {
        return ApiResult.ok(orderService.riderEarnings());
    }

    public record CreateRequest(long merchantId, long addressId, List<Item> items, Long couponId, Integer clientPayCents) {
        public record Item(long skuId, int qty, Integer priceCents) {
        }

        List<Map<String, Object>> itemMaps() {
            if (items == null) {
                return List.of();
            }
            return items.stream().map(it -> Map.<String, Object>of(
                    "skuId", it.skuId(),
                    "qty", it.qty(),
                    "priceCents", it.priceCents() == null ? -1 : it.priceCents()
            )).toList();
        }
    }

    public record PayRequest(String channel, String password) {
    }

    public record ReasonRequest(String reasonCode, String reasonText) {
    }

    public record ReviewRequest(boolean approve, String reasonText) {
    }

    public record OrderReviewRequest(int score, String content, List<Long> skuIds, List<String> photoUrls,
                                     Integer riderScore) {
    }

    public record TipRequest(String giftCode) {
    }
}
