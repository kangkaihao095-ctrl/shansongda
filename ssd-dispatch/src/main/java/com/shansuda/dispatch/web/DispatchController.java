package com.shansuda.dispatch.web;

import com.shansuda.common.api.ApiResult;
import com.shansuda.dispatch.service.DispatchService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping({"/api/dispatch/route", "/internal/route"})
    public ApiResult<Map<String, Object>> route(@RequestBody RouteRequest req) {
        return ApiResult.ok(dispatchService.route(
                req.riderLat(), req.riderLon(), req.merchantLat(), req.merchantLon(), req.userLat(), req.userLon()));
    }

    @PostMapping("/api/dispatch/assign/{orderId}")
    public ApiResult<Map<String, Object>> assign(@PathVariable long orderId) {
        return ApiResult.ok(dispatchService.assign(orderId));
    }

    public record RouteRequest(double riderLat, double riderLon, double merchantLat, double merchantLon,
                               double userLat, double userLon) {
    }
}
