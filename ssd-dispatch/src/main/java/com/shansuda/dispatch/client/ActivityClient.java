package com.shansuda.dispatch.client;

import com.shansuda.common.api.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ssd-activity", url = "${ssd.client.activity:http://127.0.0.1:18082}")
public interface ActivityClient {
    @PostMapping("/internal/grab/occupy")
    ApiResult<Map<String, Object>> occupy(@RequestBody Map<String, Object> body);
}
