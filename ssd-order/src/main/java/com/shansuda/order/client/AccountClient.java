package com.shansuda.order.client;

import com.shansuda.common.api.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "ssd-account", url = "${ssd.client.account:http://127.0.0.1:18081}")
public interface AccountClient {
    @GetMapping("/internal/merchants/{id}")
    ApiResult<Map<String, Object>> merchant(@PathVariable("id") long id);

    @GetMapping("/internal/addresses/{userId}/{addressId}")
    ApiResult<Map<String, Object>> address(@PathVariable("userId") long userId, @PathVariable("addressId") long addressId);

    @GetMapping("/internal/riders/{id}")
    ApiResult<Map<String, Object>> rider(@PathVariable("id") long id);

    @GetMapping("/internal/riders/nearby")
    ApiResult<List<Map<String, Object>>> nearby(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam(value = "radiusMeters", defaultValue = "5000") int radiusMeters,
            @RequestParam(value = "onlineStatus", required = false) String onlineStatus,
            @RequestParam(value = "acceptStatus", required = false) String acceptStatus);

    @GetMapping("/internal/riders/{id}/profile")
    ApiResult<Map<String, Object>> riderProfile(@PathVariable("id") long id);

    @PostMapping("/internal/riders/{id}/completed")
    ApiResult<Void> bumpRiderCompleted(@PathVariable("id") long id);

    @PostMapping("/internal/riders/{id}/rate")
    ApiResult<Void> rateRider(@PathVariable("id") long id, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/tips")
    ApiResult<Map<String, Object>> recordTip(@RequestBody Map<String, Object> body);

    @GetMapping("/internal/tips/order/{orderId}")
    ApiResult<Map<String, Object>> tipByOrder(@PathVariable("orderId") long orderId);

    @PostMapping("/internal/coupons/quote")
    ApiResult<Map<String, Object>> quoteCoupon(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/coupons/options")
    ApiResult<Map<String, Object>> couponOptions(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/members/spend")
    ApiResult<Void> memberSpend(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/coupons/use")
    ApiResult<Void> useCoupon(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/reviews")
    ApiResult<Map<String, Object>> createReview(@RequestBody Map<String, Object> body);

    @GetMapping("/internal/reviews/order/{orderId}")
    ApiResult<Map<String, Object>> reviewByOrder(@PathVariable("orderId") long orderId);

    @PostMapping("/internal/merchants/{id}/completed")
    ApiResult<Void> bumpCompleted(@PathVariable("id") long id);

    @PostMapping("/internal/skus/stock")
    ApiResult<Void> changeSkuStock(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/skus/sales")
    ApiResult<Void> bumpSkuSales(@RequestBody Map<String, Object> body);

    @PutMapping("/internal/riders/{userId}/accept-status")
    ApiResult<Void> acceptStatus(@PathVariable("userId") long userId, @RequestBody Map<String, Object> body);
}
