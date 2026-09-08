package com.shansuda.dispatch.client;

import com.shansuda.common.api.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "ssd-order", url = "${ssd.client.order:http://127.0.0.1:18083}")
public interface OrderClient {
    @GetMapping("/internal/orders/{id}")
    ApiResult<Map<String, Object>> get(@PathVariable("id") long id);
}
