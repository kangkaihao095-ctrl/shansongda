package com.shansuda.order.web;

import com.shansuda.common.api.ApiResult;
import com.shansuda.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders/{id}")
    public ApiResult<Map<String, Object>> get(@PathVariable long id) {
        return ApiResult.ok(orderService.getInternal(id));
    }

    @GetMapping("/riders/{id}/daily-income")
    public ApiResult<Map<String, Object>> riderDailyIncome(
            @PathVariable long id,
            @RequestParam(defaultValue = "7") int days) {
        return ApiResult.ok(orderService.riderDailyIncome(id, days));
    }

    @GetMapping("/users/{id}/completed-merchants")
    public ApiResult<Map<String, Object>> completedMerchants(@PathVariable long id) {
        return ApiResult.ok(orderService.completedMerchants(id));
    }
}
