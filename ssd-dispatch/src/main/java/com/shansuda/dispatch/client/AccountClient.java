package com.shansuda.dispatch.client;

import com.shansuda.common.api.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "ssd-account", url = "${ssd.client.account:http://127.0.0.1:18081}")
public interface AccountClient {
    @GetMapping("/internal/riders/locations")
    ApiResult<List<Map<String, Object>>> riderLocations(@RequestParam("withinSeconds") int withinSeconds);

    @GetMapping("/internal/riders/nearby")
    ApiResult<List<Map<String, Object>>> nearby(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam("radiusMeters") int radiusMeters,
            @RequestParam("onlineStatus") String onlineStatus,
            @RequestParam("acceptStatus") String acceptStatus);
}
