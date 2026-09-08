package com.shansuda.account.web;

import com.shansuda.account.service.AccountService;
import com.shansuda.account.service.MemberService;
import com.shansuda.account.service.ReviewService;
import com.shansuda.account.service.RiderProfileService;
import com.shansuda.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal")
public class InternalAccountController {

    private final AccountService accountService;
    private final ReviewService reviewService;
    private final RiderProfileService riderProfileService;
    private final MemberService memberService;

    public InternalAccountController(AccountService accountService, ReviewService reviewService,
                                     RiderProfileService riderProfileService, MemberService memberService) {
        this.accountService = accountService;
        this.reviewService = reviewService;
        this.riderProfileService = riderProfileService;
        this.memberService = memberService;
    }

    @GetMapping("/merchants/{id}")
    public ApiResult<Map<String, Object>> merchant(@PathVariable long id) {
        return ApiResult.ok(accountService.merchant(id));
    }

    @PostMapping("/coupons/quote")
    public ApiResult<Map<String, Object>> quoteCoupon(@RequestBody QuoteBody body) {
        return ApiResult.ok(accountService.quoteCoupon(body.userId(), body.couponId(), body.merchantId(), body.goodsCents()));
    }

    @PostMapping("/coupons/options")
    public ApiResult<Map<String, Object>> couponOptions(@RequestBody QuoteBody body) {
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("items", accountService.couponOptions(body.userId(), body.merchantId(), body.goodsCents()));
        return ApiResult.ok(wrap);
    }

    @PostMapping("/coupons/use")
    public ApiResult<Void> useCoupon(@RequestBody UseBody body) {
        accountService.useCoupon(body.userId(), body.couponId(), body.orderId());
        return ApiResult.ok(null);
    }

    @PostMapping("/coupons/grant")
    public ApiResult<Map<String, Object>> grant(@RequestBody GrantBody body) {
        return ApiResult.ok(accountService.grantFromActivity(body.userId(), body.activityId(), body.couponCode()));
    }

    public record QuoteBody(long userId, long couponId, long merchantId, int goodsCents) {
    }

    public record UseBody(long userId, long couponId, long orderId) {
    }

    public record GrantBody(long userId, long activityId, String couponCode) {
    }

    @GetMapping("/riders/nearby")
    public ApiResult<List<Map<String, Object>>> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false) Integer radiusMeters,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String onlineStatus,
            @RequestParam(required = false) String acceptStatus) {
        int meters = radiusMeters != null ? radiusMeters : (radius != null ? radius : 3000);
        return ApiResult.ok(accountService.nearbyRiders(lat, lon, meters, onlineStatus, acceptStatus));
    }

    @GetMapping("/riders/locations")
    public ApiResult<List<Map<String, Object>>> riderLocations(
            @RequestParam(defaultValue = "300") int withinSeconds) {
        return ApiResult.ok(accountService.recentRiderLocations(withinSeconds));
    }

    @GetMapping("/riders/{id}")
    public ApiResult<Map<String, Object>> rider(@PathVariable long id) {
        return ApiResult.ok(accountService.rider(id));
    }

    @GetMapping("/riders/{id}/profile")
    public ApiResult<Map<String, Object>> riderProfile(@PathVariable long id) {
        return ApiResult.ok(riderProfileService.card(id));
    }

    @PostMapping("/riders/{id}/completed")
    public ApiResult<Void> bumpRiderCompleted(@PathVariable long id) {
        riderProfileService.bumpCompleted(id);
        return ApiResult.ok(null);
    }

    @PostMapping("/riders/{id}/rate")
    public ApiResult<Void> rateRider(@PathVariable long id, @RequestBody RateBody body) {
        riderProfileService.rate(id, body.score());
        return ApiResult.ok(null);
    }

    @PostMapping("/tips")
    public ApiResult<Map<String, Object>> recordTip(@RequestBody TipBody body) {
        return ApiResult.ok(riderProfileService.recordTip(body.orderId(), body.riderId(), body.userId(),
                body.cents(), body.giftCode()));
    }

    @GetMapping("/tips/order/{orderId}")
    public ApiResult<Map<String, Object>> tipByOrder(@PathVariable long orderId) {
        return ApiResult.ok(riderProfileService.tipByOrder(orderId));
    }

    @GetMapping("/addresses/{userId}/{addressId}")
    public ApiResult<Map<String, Object>> address(@PathVariable long userId, @PathVariable long addressId) {
        return ApiResult.ok(accountService.address(userId, addressId));
    }

    @PutMapping("/riders/{userId}/accept-status")
    public ApiResult<Void> acceptStatus(@PathVariable long userId, @RequestBody StatusBody body) {
        accountService.updateAcceptStatus(userId, body.acceptStatus());
        return ApiResult.ok(null);
    }

    @PostMapping("/merchants/{id}/completed")
    public ApiResult<Void> bumpCompleted(@PathVariable long id) {
        accountService.bumpCompleted(id);
        return ApiResult.ok(null);
    }

    @PostMapping("/skus/stock")
    public ApiResult<Void> changeSkuStock(@RequestBody SkuStockBody body) {
        accountService.changeSkuStock(body.action(), body.items());
        return ApiResult.ok(null);
    }

    @PostMapping("/skus/sales")
    public ApiResult<Void> bumpSkuSales(@RequestBody SkuSalesBody body) {
        accountService.bumpSkuSales(body.items());
        return ApiResult.ok(null);
    }

    @PostMapping("/reviews")
    public ApiResult<Map<String, Object>> createReview(@RequestBody ReviewBody body) {
        return ApiResult.ok(reviewService.create(body.userId(), body.orderId(), body.merchantId(),
                body.score(), body.content(), body.skuIds(), body.photoUrls(), body.riderScore()));
    }

    @GetMapping("/reviews/order/{orderId}")
    public ApiResult<Map<String, Object>> reviewByOrder(@PathVariable long orderId) {
        return ApiResult.ok(reviewService.byOrder(orderId));
    }

    @PostMapping("/members/spend")
    public ApiResult<Void> memberSpend(@RequestBody MemberSpendBody body) {
        if (body.deltaCents() >= 0) {
            memberService.onCompleted(body.userId(), body.deltaCents());
        } else {
            memberService.onRefunded(body.userId(), -body.deltaCents(), body.wasCompleted());
        }
        return ApiResult.ok(null);
    }

    public record ReviewBody(long userId, long orderId, long merchantId, int score, String content, List<Long> skuIds,
                             List<String> photoUrls, Integer riderScore) {
    }

    public record StatusBody(String acceptStatus) {
    }

    public record TipBody(long orderId, long riderId, long userId, Integer cents, String giftCode) {
    }

    public record RateBody(int score) {
    }

    public record MemberSpendBody(long userId, int deltaCents, boolean wasCompleted) {
    }

    public record SkuStockBody(String action, List<Map<String, Object>> items) {
    }

    public record SkuSalesBody(List<Map<String, Object>> items) {
    }
}
