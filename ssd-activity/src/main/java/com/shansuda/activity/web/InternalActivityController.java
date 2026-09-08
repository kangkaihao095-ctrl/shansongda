package com.shansuda.activity.web;

import com.shansuda.activity.service.ActivityService;
import com.shansuda.common.api.ApiResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal")
public class InternalActivityController {

    private final ActivityService activityService;

    public InternalActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping("/grab/occupy")
    public ApiResult<Map<String, Object>> occupy(@RequestBody OccupyRequest req) {
        return activityService.occupyGrab(req.orderId(), req.riderId(), null);
    }

    @PostMapping("/seckill-idem/bind")
    public ApiResult<Map<String, Object>> bindSeckill(@RequestBody BindSeckillRequest req) {
        return ApiResult.ok(activityService.bindSeckillOrder(req.idemKey(), req.orderId(), req.payload()));
    }

    public record OccupyRequest(long orderId, long riderId) {
    }

    public record BindSeckillRequest(String idemKey, Long orderId, String payload) {
    }
}
