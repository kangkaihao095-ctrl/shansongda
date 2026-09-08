package com.shansuda.activity.web;

import com.shansuda.activity.service.ActivityService;
import com.shansuda.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/api/activities")
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(activityService.list());
    }

    @GetMapping("/api/activities/{id}")
    public ApiResult<Map<String, Object>> get(@PathVariable long id) {
        return ApiResult.ok(activityService.get(id));
    }

    @PostMapping("/api/activities/{id}/grab-coupon")
    public ApiResult<Map<String, Object>> grabCoupon(@PathVariable long id) {
        return activityService.grabCoupon(id);
    }

    @PostMapping("/api/activities/{id}/seckill")
    public ApiResult<Map<String, Object>> seckill(@PathVariable long id, @RequestBody SeckillRequest req) {
        return activityService.seckill(id, req.skuId());
    }

    @PostMapping("/api/orders/{orderId}/grab")
    public ApiResult<Map<String, Object>> grab(@PathVariable long orderId) {
        return activityService.grab(orderId);
    }

    public record SeckillRequest(long skuId) {
    }
}
