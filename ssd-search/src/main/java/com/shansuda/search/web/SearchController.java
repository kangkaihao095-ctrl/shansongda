package com.shansuda.search.web;

import com.shansuda.common.api.ApiResult;
import com.shansuda.search.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping({"/api/riders/nearby", "/internal/riders/nearby"})
    public ApiResult<List<Map<String, Object>>> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "3000") int radiusMeters,
            @RequestParam(required = false) String onlineStatus,
            @RequestParam(required = false) String acceptStatus) {
        return ApiResult.ok(searchService.nearby(lat, lon, radiusMeters, onlineStatus, acceptStatus));
    }
}
