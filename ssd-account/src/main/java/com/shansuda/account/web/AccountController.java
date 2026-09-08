package com.shansuda.account.web;

import com.shansuda.account.service.AccountService;
import com.shansuda.account.service.MemberService;
import com.shansuda.account.service.ReviewService;
import com.shansuda.account.service.RiderProfileService;
import com.shansuda.common.api.ApiResult;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accountService;
    private final ReviewService reviewService;
    private final RiderProfileService riderProfileService;
    private final MemberService memberService;
    private final String amapKey;

    public AccountController(AccountService accountService, ReviewService reviewService,
                             RiderProfileService riderProfileService, MemberService memberService,
                             @Value("${ssd.amap.key:}") String amapKey) {
        this.accountService = accountService;
        this.reviewService = reviewService;
        this.riderProfileService = riderProfileService;
        this.memberService = memberService;
        this.amapKey = amapKey;
    }

    @PostMapping("/auth/register")
    public ApiResult<Map<String, Object>> register(@RequestBody AuthRequest req) {
        return ApiResult.ok(accountService.register(req.phone(), req.password(), req.role()));
    }

    @PostMapping("/auth/login")
    public ApiResult<Map<String, Object>> login(@RequestBody AuthRequest req) {
        return ApiResult.ok(accountService.login(req.phone(), req.password()));
    }

    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me() {
        return ApiResult.ok(accountService.me());
    }

    @PutMapping("/me")
    public ApiResult<Map<String, Object>> updateMe(@RequestBody ProfileRequest req) {
        return ApiResult.ok(accountService.updateProfile(req.displayName()));
    }

    @PostMapping("/me/avatar")
    public ApiResult<Map<String, Object>> avatar(@RequestParam("file") MultipartFile file) {
        return ApiResult.ok(accountService.updateAvatar(file));
    }

    @GetMapping("/map/config")
    public ApiResult<Map<String, Object>> mapConfig() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean amap = amapKey != null && !amapKey.isBlank();
        body.put("provider", amap ? "amap" : "amap-tiles");
        body.put("key", amap ? amapKey : "");
        body.put("tile", "amap-webrd");
        body.put("note", "地图仅负责底图。路径权威是 A*/Dijkstra（本机无 Neo4j 时用同构内存路网，不是高德算路）。无 Key 用高德公开栅格瓦片，有 Key 走高德 JS API");
        return ApiResult.ok(body);
    }

    @GetMapping("/riders/nearby")
    public ApiResult<List<Map<String, Object>>> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false) Integer radiusMeters,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String onlineStatus,
            @RequestParam(required = false) String acceptStatus) {
        int meters = radiusMeters != null ? radiusMeters : (radius != null ? radius : 3000);
        return ApiResult.ok(accountService.nearbyRiders(lat, lon, meters, onlineStatus, acceptStatus));
    }

    @PutMapping("/riders/me/location")
    public ApiResult<Map<String, Object>> location(@RequestBody LocationRequest req) {
        return ApiResult.ok(accountService.updateLocation(req.lat(), req.lon()));
    }

    @PutMapping("/riders/me/status")
    public ApiResult<Map<String, Object>> status(@RequestBody StatusRequest req) {
        return ApiResult.ok(accountService.updateStatus(req.onlineStatus(), req.acceptStatus()));
    }

    @GetMapping("/riders/me/work-stats")
    public ApiResult<Map<String, Object>> workStats() {
        return ApiResult.ok(accountService.workStats());
    }

    @GetMapping("/riders/me/profile")
    public ApiResult<Map<String, Object>> myProfile() {
        return ApiResult.ok(riderProfileService.meProfile());
    }

    @GetMapping("/riders/{id}/profile")
    public ApiResult<Map<String, Object>> riderProfile(@PathVariable long id) {
        return ApiResult.ok(riderProfileService.card(id));
    }

    @PutMapping("/merchants/me/status")
    public ApiResult<Map<String, Object>> merchantStatus(@RequestBody MerchantStatusRequest req) {
        return ApiResult.ok(accountService.updateMerchantStatus(req.onlineStatus()));
    }

    @GetMapping("/merchants/me/skus")
    public ApiResult<Map<String, Object>> mySkus() {
        return ApiResult.ok(accountService.mySkus());
    }

    @PutMapping("/merchants/me/skus/{id}")
    public ApiResult<Map<String, Object>> skuStatus(@PathVariable long id, @RequestBody SkuStatusRequest req) {
        return ApiResult.ok(accountService.updateSku(id, req.status(), req.priceCents(), req.stock(), req.groupName()));
    }

    @PutMapping("/merchants/me/settings")
    public ApiResult<Map<String, Object>> merchantSettings(@RequestBody MerchantSettingsRequest req) {
        return ApiResult.ok(accountService.updateMerchantSettings(req.autoAccept()));
    }

    @GetMapping("/merchants")
    public ApiResult<Map<String, Object>> merchants(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResult.ok(accountService.merchants(category, q, page, size));
    }

    @GetMapping("/merchants/recommend")
    public ApiResult<Map<String, Object>> recommend(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer limit) {
        int sz = size != null ? size : (limit != null ? limit : 12);
        return ApiResult.ok(accountService.recommend(page, sz));
    }

    @GetMapping("/merchants/{id}")
    public ApiResult<Map<String, Object>> merchant(@PathVariable long id) {
        return ApiResult.ok(accountService.merchant(id));
    }

    @GetMapping("/merchants/{id}/skus/{skuId}")
    public ApiResult<Map<String, Object>> sku(@PathVariable long id, @PathVariable long skuId) {
        return ApiResult.ok(accountService.sku(id, skuId));
    }

    @PostMapping("/review-photos")
    public ApiResult<Map<String, Object>> reviewPhoto(@RequestParam("file") MultipartFile file) {
        return ApiResult.ok(reviewService.savePhoto(file));
    }

    @GetMapping("/merchants/{id}/reviews")
    public ApiResult<Map<String, Object>> reviews(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(reviewService.list(id, page, size));
    }

    @PostMapping("/reviews/{id}/like")
    public ApiResult<Map<String, Object>> like(@PathVariable long id) {
        return ApiResult.ok(reviewService.toggleLike(id));
    }

    @GetMapping("/me/reviews")
    public ApiResult<Map<String, Object>> myReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(reviewService.mine(page, size));
    }

    @GetMapping("/coupons")
    public ApiResult<List<Map<String, Object>>> coupons() {
        return ApiResult.ok(accountService.coupons());
    }

    @GetMapping("/coupons/today")
    public ApiResult<Map<String, Object>> couponsToday() {
        return ApiResult.ok(accountService.todayCoupons());
    }

    @GetMapping("/coupons/home-feed")
    public ApiResult<Map<String, Object>> homeFeed() {
        return ApiResult.ok(accountService.homeFeed());
    }

    @PostMapping("/coupons/home-feed/claim")
    public ApiResult<Map<String, Object>> claimHomeFeed() {
        return ApiResult.ok(accountService.claimHomeFeed());
    }

    @GetMapping("/coupons/login-gift")
    public ApiResult<Map<String, Object>> loginGift() {
        return ApiResult.ok(accountService.loginGift());
    }

    @PostMapping("/coupons/login-gift")
    public ApiResult<Map<String, Object>> claimLoginGift() {
        return ApiResult.ok(accountService.claimLoginGift());
    }

    @PostMapping("/coupons/grant-session")
    public ApiResult<Map<String, Object>> grantSession() {
        return ApiResult.ok(accountService.grantSession());
    }

    @PostMapping("/coupons/{id}/claim")
    public ApiResult<Map<String, Object>> claim(@PathVariable long id) {
        return ApiResult.ok(accountService.claimCoupon(id));
    }

    @GetMapping("/me/coupons")
    public ApiResult<List<Map<String, Object>>> myCoupons(@RequestParam(required = false) String status) {
        return ApiResult.ok(accountService.myCoupons(status));
    }

    @GetMapping("/member")
    public ApiResult<Map<String, Object>> member() {
        return ApiResult.ok(memberService.card(com.shansuda.common.auth.AuthHolder.require().userId()));
    }

    @GetMapping("/member/benefits")
    public ApiResult<Map<String, Object>> memberBenefits() {
        return ApiResult.ok(memberService.benefits());
    }

    @PostMapping("/member/claim")
    public ApiResult<Map<String, Object>> memberClaim() {
        return ApiResult.ok(memberService.claim());
    }

    @PostMapping("/member/subscribe")
    public ApiResult<Map<String, Object>> subscribe(@RequestBody SubscribeRequest req) {
        return ApiResult.ok(memberService.subscribe(req.plan(), req.channel(), req.password()));
    }

    @PostMapping("/me/addresses")
    public ApiResult<Map<String, Object>> addAddress(@RequestBody AddressRequest req) {
        return ApiResult.ok(accountService.addAddress(req.lat(), req.lon(), req.detail()));
    }

    @PutMapping("/me/addresses/{id}/default")
    public ApiResult<Map<String, Object>> defaultAddress(@PathVariable long id) {
        return ApiResult.ok(accountService.setDefaultAddress(id));
    }

    @PutMapping("/me/location")
    public ApiResult<Map<String, Object>> myLocation(@RequestBody UserLocationRequest req) {
        return ApiResult.ok(accountService.updateMyLocation(req.lat(), req.lon(), req.detail()));
    }

    public record AuthRequest(@NotBlank String phone, @NotBlank String password, String role) {
    }

    public record ProfileRequest(String displayName) {
    }

    public record LocationRequest(double lat, double lon) {
    }

    public record StatusRequest(String onlineStatus, String acceptStatus) {
    }

    public record MerchantStatusRequest(String onlineStatus) {
    }

    public record SkuStatusRequest(String status, Integer priceCents, Integer stock, String groupName) {
    }

    public record MerchantSettingsRequest(Boolean autoAccept) {
    }

    public record AddressRequest(double lat, double lon, String detail) {
    }

    public record UserLocationRequest(double lat, double lon, String detail) {
    }

    public record SubscribeRequest(String plan, String channel, String password) {
    }
}
