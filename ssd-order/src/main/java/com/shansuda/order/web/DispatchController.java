package com.shansuda.order.web;

import com.shansuda.common.api.ApiResult;
import com.shansuda.order.route.RouteService;
import com.shansuda.order.service.OrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 兼容原 ssd-dispatch 路径；网关 /api/dispatch/** 打到本服务 18083。
 */
@RestController
public class DispatchController {

    private final RouteService routeService;
    private final OrderService orderService;

    public DispatchController(RouteService routeService, OrderService orderService) {
        this.routeService = routeService;
        this.orderService = orderService;
    }

    @PostMapping({"/api/dispatch/route", "/internal/route"})
    public ApiResult<Map<String, Object>> route(@RequestBody RouteRequest req) {
        return ApiResult.ok(routeService.route(
                req.riderLat(), req.riderLon(), req.merchantLat(), req.merchantLon(), req.userLat(), req.userLon()));
    }

    @PostMapping("/api/dispatch/assign/{orderId}")
    public ApiResult<Map<String, Object>> assign(@PathVariable long orderId) {
        return ApiResult.ok(orderService.assign(orderId));
    }

    public record RouteRequest(double riderLat, double riderLon, double merchantLat, double merchantLon,
                               double userLat, double userLon) {
    }
}
