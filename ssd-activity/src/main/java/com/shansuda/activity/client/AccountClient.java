package com.shansuda.activity.client;

import com.shansuda.common.api.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ssd-account", url = "${ssd.client.account:http://127.0.0.1:18081}")
public interface AccountClient {
    @PutMapping("/internal/riders/{userId}/accept-status")
    ApiResult<Void> acceptStatus(@PathVariable("userId") long userId, @RequestBody Map<String, String> body);

    @GetMapping("/internal/riders/{id}/can-accept")
    ApiResult<Map<String, Object>> canAccept(@PathVariable("id") long id);

    @PostMapping("/internal/coupons/grant")
    ApiResult<Map<String, Object>> grantCoupon(@RequestBody Map<String, Object> body);
}
