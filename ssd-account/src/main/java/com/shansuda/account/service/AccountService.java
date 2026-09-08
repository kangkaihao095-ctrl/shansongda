package com.shansuda.account.service;

import com.shansuda.account.catalog.CouponIcons;
import com.shansuda.account.catalog.CouponPolicy;
import com.shansuda.account.catalog.RecommendScorer;
import com.shansuda.account.client.OrderInternalClient;
import com.shansuda.account.config.AvatarStorage;
import com.shansuda.account.domain.AppUser;
import com.shansuda.account.domain.Coupon;
import com.shansuda.account.domain.Merchant;
import com.shansuda.account.domain.MerchantSku;
import com.shansuda.account.domain.Rider;
import com.shansuda.account.domain.UserAddress;
import com.shansuda.account.domain.UserCoupon;
import com.shansuda.account.lbs.RiderNearby;
import com.shansuda.account.repo.AppUserRepo;
import com.shansuda.account.repo.CouponRepo;
import com.shansuda.account.repo.MerchantRepo;
import com.shansuda.account.repo.MerchantSkuRepo;
import com.shansuda.account.repo.RiderRepo;
import com.shansuda.account.repo.UserAddressRepo;
import com.shansuda.account.repo.UserCouponRepo;
import com.shansuda.account.work.RiderWorkService;
import com.shansuda.common.api.BizException;
import com.shansuda.common.api.ErrorCodes;
import com.shansuda.common.auth.AuthHolder;
import com.shansuda.common.auth.AuthUser;
import com.shansuda.common.auth.JwtService;
import com.shansuda.common.geo.Geo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AccountService {

    private static final Set<String> ROLES = Set.of("USER", "RIDER", "MERCHANT");
    private static final double DEFAULT_LAT = 31.2304;
    private static final double DEFAULT_LON = 121.4737;

    private final AppUserRepo userRepo;
    private final RiderRepo riderRepo;
    private final MerchantRepo merchantRepo;
    private final MerchantSkuRepo skuRepo;
    private final CouponRepo couponRepo;
    private final UserCouponRepo userCouponRepo;
    private final UserAddressRepo addressRepo;
    private final JwtService jwtService;
    private final AvatarStorage avatarStorage;
    private final RiderWorkService riderWorkService;
    private final RiderProfileService riderProfileService;
    private final OrderInternalClient orderInternalClient;
    private final CouponGrantService couponGrantService;
    private final MemberService memberService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final double maxKm;

    public AccountService(AppUserRepo userRepo, RiderRepo riderRepo, MerchantRepo merchantRepo,
                          MerchantSkuRepo skuRepo, CouponRepo couponRepo, UserCouponRepo userCouponRepo,
                          UserAddressRepo addressRepo, JwtService jwtService, AvatarStorage avatarStorage,
                          RiderWorkService riderWorkService, RiderProfileService riderProfileService,
                          OrderInternalClient orderInternalClient, CouponGrantService couponGrantService,
                          MemberService memberService,
                          @Value("${ssd.recommend.max-km:5}") double maxKm) {
        this.userRepo = userRepo;
        this.riderRepo = riderRepo;
        this.merchantRepo = merchantRepo;
        this.skuRepo = skuRepo;
        this.couponRepo = couponRepo;
        this.userCouponRepo = userCouponRepo;
        this.addressRepo = addressRepo;
        this.jwtService = jwtService;
        this.avatarStorage = avatarStorage;
        this.riderWorkService = riderWorkService;
        this.riderProfileService = riderProfileService;
        this.orderInternalClient = orderInternalClient;
        this.couponGrantService = couponGrantService;
        this.memberService = memberService;
        this.maxKm = maxKm <= 0 ? Geo.DEFAULT_MAX_KM : maxKm;
    }

    @Transactional
    public Map<String, Object> register(String phone, String password, String role) {
        if (!ROLES.contains(role)) {
            throw BizException.badRequest("BAD_ROLE", "role 只能是 USER / RIDER / MERCHANT");
        }
        if (userRepo.findByPhone(phone).isPresent()) {
            throw BizException.conflict(ErrorCodes.DUPLICATE_PHONE, "手机号已注册");
        }
        AppUser user = new AppUser();
        user.setPhone(phone);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        user.setDisplayName(phone);
        user.setStatus("ACTIVE");
        user.setCreatedAt(Instant.now());
        user = userRepo.save(user);
        if ("RIDER".equals(role)) {
            Rider rider = new Rider();
            rider.setUserId(user.getId());
            rider.setOnlineStatus("OFFLINE");
            rider.setAcceptStatus("IDLE");
            rider.setLat(DEFAULT_LAT);
            rider.setLon(DEFAULT_LON);
            rider.setUpdateTime(Instant.now());
            rider.setVersion(1L);
            riderRepo.save(rider);
            riderProfileService.ensure(user.getId());
        }
        if ("MERCHANT".equals(role)) {
            Merchant merchant = new Merchant();
            merchant.setUserId(user.getId());
            merchant.setShopName(phone + "的店");
            merchant.setLat(DEFAULT_LAT);
            merchant.setLon(DEFAULT_LON);
            merchant.setAddress("待完善");
            merchant.setOnlineStatus("ONLINE");
            merchant.setAutoAccept(false);
            merchantRepo.save(merchant);
        }
        if ("USER".equals(role)) {
            UserAddress address = new UserAddress();
            address.setUserId(user.getId());
            address.setLat(31.2240);
            address.setLon(121.4690);
            address.setDetail("上海市黄浦区外滩源 33 号");
            address.setIsDefault(true);
            addressRepo.save(address);
        }
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);
        Map<String, Object> body = tokenBody(user);
        if ("USER".equals(role)) {
            Map<String, Object> grants = couponGrantService.grantNewcomer(user.getId());
            body.put("grants", grants);
            if (grants.get("message") instanceof String msg && !msg.isBlank()) {
                body.put("grantMessage", msg);
            }
        }
        return body;
    }

    public Map<String, Object> login(String phone, String password) {
        AppUser user = userRepo.findByPhone(phone)
                .orElseThrow(() -> BizException.unauthorized("账号或密码错误"));
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw BizException.unauthorized("账号或密码错误");
        }
        Map<String, Object> body = tokenBody(user);
        if ("USER".equals(user.getRole())) {
            body.put("loginGift", couponGrantService.loginGiftOffer(user.getId()));
        }
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);
        return body;
    }

    public Map<String, Object> me() {
        AuthUser auth = AuthHolder.require();
        AppUser user = userRepo.findById(auth.userId()).orElseThrow(() -> BizException.notFound("用户不存在"));
        Map<String, Object> body = tokenBody(user);
        body.put("addresses", addressesOf(user.getId()));
        riderRepo.findById(user.getId()).ifPresent(r -> {
            body.put("rider", riderView(r));
            body.put("riderProfile", riderProfileService.card(r.getUserId()));
        });
        merchantRepo.findById(user.getId()).ifPresent(m -> body.put("merchant", merchantView(m)));
        body.put("member", memberService.card(user.getId()));
        return body;
    }

    @Transactional
    public Map<String, Object> updateProfile(String displayName) {
        AuthUser auth = AuthHolder.require();
        if (displayName == null || displayName.isBlank()) {
            throw BizException.badRequest("BAD_NAME", "昵称不能为空");
        }
        String name = displayName.trim();
        if (name.length() > 32) {
            throw BizException.badRequest("BAD_NAME", "昵称不能超过 32 字");
        }
        AppUser user = userRepo.findById(auth.userId()).orElseThrow(() -> BizException.notFound("用户不存在"));
        user.setDisplayName(name);
        userRepo.save(user);
        return me();
    }

    @Transactional
    public Map<String, Object> updateAvatar(MultipartFile file) {
        AuthUser auth = AuthHolder.require();
        AppUser user = userRepo.findById(auth.userId()).orElseThrow(() -> BizException.notFound("用户不存在"));
        String url = avatarStorage.save(user.getId(), file);
        user.setAvatarUrl(url);
        userRepo.save(user);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("avatarUrl", url);
        body.put("displayName", user.getDisplayName());
        return body;
    }

    @Transactional
    public Map<String, Object> updateLocation(double lat, double lon) {
        Rider rider = requireSelfRider();
        rider.setLat(lat);
        rider.setLon(lon);
        rider.setUpdateTime(Instant.now());
        rider.setVersion(rider.getVersion() + 1);
        riderRepo.save(rider);
        if ("ONLINE".equals(rider.getOnlineStatus())) {
            riderWorkService.tick(rider.getUserId(), true);
        }
        return riderView(rider);
    }

    @Transactional
    public Map<String, Object> updateStatus(String onlineStatus, String acceptStatus) {
        Rider rider = requireSelfRider();
        if ("ONLINE".equals(onlineStatus) && !"ONLINE".equals(rider.getOnlineStatus())) {
            riderWorkService.assertCanGoOnline(rider.getUserId());
        }
        if (onlineStatus != null) {
            rider.setOnlineStatus(onlineStatus);
        }
        if (acceptStatus != null) {
            rider.setAcceptStatus(acceptStatus);
        }
        rider.setUpdateTime(Instant.now());
        rider.setVersion(rider.getVersion() + 1);
        riderRepo.save(rider);
        if ("ONLINE".equals(rider.getOnlineStatus())) {
            riderWorkService.onGoingOnline(rider.getUserId());
        } else if ("OFFLINE".equals(onlineStatus)) {
            riderWorkService.onGoingOffline(rider.getUserId());
        }
        return riderView(rider);
    }

    @Transactional
    public Map<String, Object> updateMerchantStatus(String onlineStatus) {
        AuthUser auth = AuthHolder.require();
        if (!auth.isMerchant()) {
            throw BizException.forbidden("仅商家可切换营业状态");
        }
        if (!"ONLINE".equals(onlineStatus) && !"OFFLINE".equals(onlineStatus)) {
            throw BizException.badRequest("BAD_STATUS", "onlineStatus 只能是 ONLINE 或 OFFLINE");
        }
        Merchant merchant = merchantRepo.findById(auth.userId()).orElseThrow(() -> BizException.notFound("商家档案不存在"));
        merchant.setOnlineStatus(onlineStatus);
        merchantRepo.save(merchant);
        return merchantCard(merchant);
    }

    @Transactional
    public Map<String, Object> updateSkuStatus(long skuId, String status) {
        return updateSku(skuId, status, null, null, null);
    }

    @Transactional
    public Map<String, Object> updateSku(long skuId, String status, Integer priceCents, Integer stock, String groupName) {
        AuthUser auth = AuthHolder.require();
        if (!auth.isMerchant()) {
            throw BizException.forbidden("仅商家可改本店商品");
        }
        MerchantSku sku = skuRepo.findById(skuId).orElseThrow(() -> BizException.notFound("商品不存在"));
        if (sku.getMerchantId() == null || sku.getMerchantId() != auth.userId()) {
            throw BizException.forbidden("仅本店商品可操作");
        }
        if (status != null && !status.isBlank()) {
            if (!"ONLINE".equals(status) && !"OFFLINE".equals(status)) {
                throw BizException.badRequest("BAD_STATUS", "status 只能是 ONLINE 或 OFFLINE");
            }
            sku.setStatus(status);
        }
        if (priceCents != null) {
            if (priceCents <= 0) {
                throw BizException.badRequest("BAD_PRICE", "价格须大于 0");
            }
            sku.setPriceCents(priceCents);
        }
        if (stock != null) {
            if (stock < 0) {
                throw BizException.badRequest("BAD_STOCK", "库存不能为负");
            }
            sku.setStock(stock);
        }
        if (groupName != null && !groupName.isBlank()) {
            sku.setGroupName(groupName.trim());
        }
        skuRepo.save(sku);
        return skuView(sku);
    }

    @Transactional
    public Map<String, Object> updateMerchantSettings(Boolean autoAccept) {
        AuthUser auth = AuthHolder.require();
        if (!auth.isMerchant()) {
            throw BizException.forbidden("仅商家可改接单设置");
        }
        if (autoAccept == null) {
            throw BizException.badRequest("BAD_SETTING", "请指定 autoAccept");
        }
        Merchant merchant = merchantRepo.findById(auth.userId()).orElseThrow(() -> BizException.notFound("商家档案不存在"));
        merchant.setAutoAccept(autoAccept);
        merchantRepo.save(merchant);
        return merchantCard(merchant);
    }

    public Map<String, Object> mySkus() {
        AuthUser auth = AuthHolder.require();
        if (!auth.isMerchant()) {
            throw BizException.forbidden("仅商家可查看本店商品");
        }
        List<MerchantSku> skus = skuRepo.findByMerchantId(auth.userId()).stream()
                .sorted(Comparator
                        .comparing((MerchantSku s) -> s.getGroupName() == null || s.getGroupName().isBlank()
                                ? "推荐" : s.getGroupName())
                        .thenComparing(s -> s.getId() == null ? 0L : s.getId()))
                .toList();
        List<Map<String, Object>> items = skus.stream().map(this::skuView).toList();
        Map<String, Integer> groupCount = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String g = String.valueOf(item.get("groupName"));
            groupCount.merge(g, 1, Integer::sum);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        groupCount.forEach((name, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("count", count);
            groups.add(row);
        });
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("groups", groups);
        return body;
    }

    @Transactional
    public void changeSkuStock(String action, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        boolean deduct = "deduct".equalsIgnoreCase(action);
        for (Map<String, Object> item : items) {
            if (item == null || !(item.get("skuId") instanceof Number idNum)) {
                continue;
            }
            int qty = item.get("qty") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            long skuId = idNum.longValue();
            if (deduct) {
                int n = skuRepo.deductStock(skuId, qty);
                if (n == 0) {
                    MerchantSku sku = skuRepo.findById(skuId).orElse(null);
                    String name = sku == null ? String.valueOf(skuId) : sku.getName();
                    throw BizException.conflict(ErrorCodes.STOCK_EMPTY, "库存不足：" + name);
                }
            } else {
                skuRepo.restoreStock(skuId, qty);
            }
        }
    }

    @Transactional
    public void bumpSkuSales(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (Map<String, Object> item : items) {
            if (item == null || !(item.get("skuId") instanceof Number idNum)) {
                continue;
            }
            int qty = item.get("qty") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            skuRepo.bumpMonthSales(idNum.longValue(), qty);
        }
    }

    @Transactional
    public void updateAcceptStatus(long userId, String acceptStatus) {
        Rider rider = riderRepo.findById(userId).orElseThrow(() -> BizException.notFound("骑手不存在"));
        rider.setAcceptStatus(acceptStatus);
        rider.setUpdateTime(Instant.now());
        rider.setVersion(rider.getVersion() + 1);
        riderRepo.save(rider);
    }

    public Map<String, Object> merchants(String category, String q, int page, int size) {
        int p = Math.max(1, page);
        int sz = Math.min(30, Math.max(1, size <= 0 ? 12 : size));
        String cat = category == null ? "" : category.trim();
        String query = q == null ? "" : q.trim();
        double[] origin = userOrigin();
        List<Merchant> all = merchantRepo.findAll().stream()
                .filter(m -> cat.isBlank() || cat.equals(m.getCategory()))
                .filter(m -> query.isBlank() || (m.getShopName() != null && m.getShopName().contains(query)))
                .sorted(Comparator.comparing(m -> m.getUserId() == null ? 0L : m.getUserId()))
                .toList();
        int total = all.size();
        int from = Math.min((p - 1) * sz, total);
        int to = Math.min(from + sz, total);
        List<Map<String, Object>> items = all.subList(from, to).stream()
                .map(m -> merchantCard(m, origin[0], origin[1]))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", p);
        body.put("size", sz);
        body.put("total", total);
        body.put("hasNext", to < total);
        body.put("maxKm", maxKm);
        return body;
    }

    public Map<String, Object> recommend(int page, int size) {
        int p = Math.max(1, page);
        int sz = Math.min(30, Math.max(1, size <= 0 ? 12 : size));
        AuthUser auth = AuthHolder.get();
        double[] origin = userOrigin();
        double lat = origin[0];
        double lon = origin[1];
        Map<String, Integer> catFreq = new HashMap<>();
        if (auth != null) {
            Map<Long, Integer> merchantCounts = orderInternalClient.completedMerchantCounts(auth.userId());
            Map<Long, Merchant> byId = new HashMap<>();
            for (Merchant m : merchantRepo.findAll()) {
                byId.put(m.getUserId(), m);
            }
            merchantCounts.forEach((mid, cnt) -> {
                Merchant m = byId.get(mid);
                if (m != null && m.getCategory() != null) {
                    catFreq.merge(m.getCategory(), cnt, Integer::sum);
                }
            });
        }
        boolean hasHistory = catFreq.values().stream().mapToInt(Integer::intValue).sum() > 0;
        int maxAff = catFreq.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Merchant> shops = merchantRepo.findAll();
        int maxSales = shops.stream().mapToInt(m -> m.getCompletedCount() == null ? 0 : m.getCompletedCount()).max().orElse(1);
        double uLat = lat;
        double uLon = lon;
        record Ranked(Merchant merchant, double score) {
        }
        List<Ranked> ranked = shops.stream()
                .filter(m -> Geo.inRange(uLat, uLon, m.getLat(), m.getLon(), maxKm))
                .map(m -> {
                    int freq = catFreq.getOrDefault(m.getCategory(), 0);
                    boolean online = !"OFFLINE".equals(m.getOnlineStatus());
                    Double rating = m.getRatingAvg() != null ? m.getRatingAvg() : m.getRating();
                    double s = RecommendScorer.score(uLat, uLon, m.getLat(), m.getLon(), freq, maxAff,
                            m.getCompletedCount() == null ? 0 : m.getCompletedCount(), maxSales,
                            rating, online, hasHistory);
                    return new Ranked(m, s);
                })
                .sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparing(r -> r.merchant().getUserId() == null ? 0L : r.merchant().getUserId()))
                .toList();
        int total = ranked.size();
        int from = Math.min((p - 1) * sz, total);
        int to = Math.min(from + sz, total);
        List<Map<String, Object>> items = ranked.subList(from, to).stream().map(r -> {
            Map<String, Object> card = merchantCard(r.merchant(), uLat, uLon);
            card.put("recommendScore", Math.round(r.score() * 1000) / 1000.0);
            return card;
        }).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", p);
        body.put("size", sz);
        body.put("total", total);
        body.put("hasNext", to < total);
        body.put("maxKm", maxKm);
        body.put("originLat", uLat);
        body.put("originLon", uLon);
        return body;
    }

    public Map<String, Object> merchant(long id) {
        Merchant merchant = merchantRepo.findById(id).orElseThrow(() -> BizException.notFound("商家不存在"));
        double[] origin = userOrigin();
        Map<String, Object> body = merchantCard(merchant, origin[0], origin[1]);
        body.put("items", skuRepo.findByMerchantId(id).stream().map(this::skuView).toList());
        return body;
    }

    public Map<String, Object> sku(long merchantId, long skuId) {
        MerchantSku sku = skuRepo.findByMerchantIdAndId(merchantId, skuId)
                .orElseThrow(() -> BizException.notFound("商品不存在"));
        return skuView(sku);
    }

    @Transactional
    public void bumpCompleted(long merchantId) {
        jdbcBumpCompleted(merchantId);
    }

    private void jdbcBumpCompleted(long merchantId) {
        // 用 repo 避免再引 JdbcTemplate；completed_count 只做推荐销量信号
        merchantRepo.findById(merchantId).ifPresent(m -> {
            m.setCompletedCount((m.getCompletedCount() == null ? 0 : m.getCompletedCount()) + 1);
            merchantRepo.save(m);
        });
    }

    public List<Map<String, Object>> coupons() {
        AuthUser auth = AuthHolder.require();
        return couponRepo.findAll().stream()
                .filter(c -> c.getId() != null && c.getId() < 1000)
                .filter(c -> !Boolean.TRUE.equals(c.getMemberOnly()))
                .map(c -> couponView(c, auth.userId()))
                .toList();
    }

    public Map<String, Object> todayCoupons() {
        AuthUser auth = AuthHolder.require();
        return couponGrantService.todayOffer(auth.userId());
    }

    public Map<String, Object> grantSession() {
        return couponGrantService.grantSessionForMe();
    }

    public Map<String, Object> homeFeed() {
        return couponGrantService.homeFeed(AuthHolder.require().userId());
    }

    public Map<String, Object> claimHomeFeed() {
        return couponGrantService.claimHomeFeed(AuthHolder.require().userId());
    }

    public Map<String, Object> loginGift() {
        return couponGrantService.loginGiftOffer(AuthHolder.require().userId());
    }

    public Map<String, Object> claimLoginGift() {
        return couponGrantService.claimLoginGift(AuthHolder.require().userId());
    }

    public List<Map<String, Object>> couponOptions(long userId, long merchantId, int goodsCents) {
        return couponGrantService.options(userId, merchantId, goodsCents);
    }

    @Transactional
    public Map<String, Object> claimCoupon(long couponId) {
        AuthUser auth = AuthHolder.require();
        Coupon coupon = couponRepo.findById(couponId).orElseThrow(() -> BizException.notFound("券不存在"));
        if (userCouponRepo.findByCouponIdAndUserId(couponId, auth.userId()).isPresent()) {
            throw BizException.conflict("COUPON_CLAIMED", "已领取过该券");
        }
        if (coupon.getStock() != null && coupon.getStock() <= 0) {
            throw BizException.conflict("STOCK_EMPTY", "券已领完");
        }
        if (coupon.getStock() != null) {
            int n = couponRepo.deductStock(couponId);
            if (n == 0) {
                throw BizException.conflict("STOCK_EMPTY", "券已领完");
            }
            coupon.setStock(coupon.getStock() - 1);
        }
        UserCoupon grant = new UserCoupon();
        grant.setCouponId(couponId);
        grant.setUserId(auth.userId());
        grant.setStatus("UNUSED");
        grant.setClaimedAt(Instant.now());
        try {
            userCouponRepo.save(grant);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw BizException.conflict("COUPON_CLAIMED", "已领取过该券");
        }
        return couponView(coupon, auth.userId());
    }

    @Transactional
    public Map<String, Object> grantFromActivity(long userId, long activityId, String couponCode) {
        Coupon coupon = couponRepo.findFirstByActivityId(activityId)
                .orElseThrow(() -> BizException.notFound("活动未绑定优惠券"));
        if (userCouponRepo.findByCouponIdAndUserId(coupon.getId(), userId).isPresent()) {
            return couponView(coupon, userId);
        }
        UserCoupon grant = new UserCoupon();
        grant.setCouponId(coupon.getId());
        grant.setUserId(userId);
        grant.setStatus("UNUSED");
        grant.setClaimedAt(Instant.now());
        try {
            userCouponRepo.save(grant);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            return couponView(coupon, userId);
        }
        Map<String, Object> body = couponView(coupon, userId);
        body.put("couponCode", couponCode);
        return body;
    }

    @Transactional
    public Map<String, Object> addAddress(double lat, double lon, String detail) {
        AuthUser auth = AuthHolder.require();
        if (!auth.isUser()) {
            throw BizException.forbidden("仅用户可添加收货地址");
        }
        if (detail == null || detail.isBlank()) {
            throw BizException.badRequest("BAD_ADDRESS", "请填写详细地址");
        }
        UserAddress address = new UserAddress();
        address.setUserId(auth.userId());
        address.setLat(lat);
        address.setLon(lon);
        address.setDetail(detail.trim());
        boolean first = addressRepo.findByUserId(auth.userId()).isEmpty();
        address.setIsDefault(first);
        addressRepo.save(address);
        return addressView(address);
    }

    @Transactional
    public Map<String, Object> setDefaultAddress(long addressId) {
        AuthUser auth = AuthHolder.require();
        if (!auth.isUser()) {
            throw BizException.forbidden("仅用户可切换默认地址");
        }
        UserAddress target = addressRepo.findByIdAndUserId(addressId, auth.userId())
                .orElseThrow(() -> BizException.notFound("地址不存在"));
        for (UserAddress row : addressRepo.findByUserId(auth.userId())) {
            row.setIsDefault(row.getId().equals(target.getId()));
            addressRepo.save(row);
        }
        return me();
    }

    @Transactional
    public Map<String, Object> updateMyLocation(double lat, double lon, String detail) {
        AuthUser auth = AuthHolder.require();
        if (!auth.isUser()) {
            throw BizException.forbidden("仅用户可改定位");
        }
        List<UserAddress> all = addressRepo.findByUserId(auth.userId());
        UserAddress match = all.stream().filter(a ->
                (detail != null && !detail.isBlank() && detail.trim().equals(a.getDetail()))
                        || (a.getLat() != null && a.getLon() != null
                        && Math.abs(a.getLat() - lat) < 0.0008 && Math.abs(a.getLon() - lon) < 0.0008)
        ).findFirst().orElse(null);
        if (match != null) {
            return setDefaultAddress(match.getId());
        }
        UserAddress address = defaultAddress(auth.userId());
        if (address == null) {
            address = new UserAddress();
            address.setUserId(auth.userId());
        }
        address.setLat(lat);
        address.setLon(lon);
        if (detail != null && !detail.isBlank()) {
            address.setDetail(detail.trim());
        } else if (address.getDetail() == null || address.getDetail().isBlank()) {
            address.setDetail("自定义定位");
        }
        address.setIsDefault(true);
        addressRepo.save(address);
        for (UserAddress row : addressRepo.findByUserId(auth.userId())) {
            if (!row.getId().equals(address.getId()) && Boolean.TRUE.equals(row.getIsDefault())) {
                row.setIsDefault(false);
                addressRepo.save(row);
            }
        }
        return me();
    }

    public List<Map<String, Object>> myCoupons() {
        return myCoupons(null);
    }

    public List<Map<String, Object>> myCoupons(String status) {
        AuthUser auth = AuthHolder.require();
        Instant now = Instant.now();
        Set<String> want = parseStatuses(status);
        return userCouponRepo.findByUserId(auth.userId()).stream().map(grant -> {
            Coupon coupon = couponRepo.findById(grant.getCouponId()).orElse(null);
            Map<String, Object> row = coupon == null ? new LinkedHashMap<>() : couponView(coupon, auth.userId());
            String effective = effectiveStatus(grant, coupon, now);
            row.put("grantId", grant.getId());
            row.put("grantStatus", effective);
            row.put("usedOrderId", grant.getUsedOrderId());
            row.put("claimedAt", grant.getClaimedAt());
            return Map.entry(effective, row);
        }).filter(e -> want.isEmpty() || want.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    public Map<String, Object> quoteCoupon(long userId, long couponId, long merchantId, int goodsCents) {
        Coupon coupon = couponRepo.findById(couponId).orElseThrow(() -> BizException.notFound("券不存在"));
        if (coupon.getMerchantId() != null && coupon.getMerchantId() != merchantId) {
            throw BizException.badRequest("COUPON_SHOP", "该券仅限指定店铺");
        }
        userCouponRepo.findByCouponIdAndUserIdAndStatus(couponId, userId, "UNUSED")
                .orElseThrow(() -> BizException.badRequest("COUPON_UNUSED", "请先领取优惠券"));
        CouponPolicy.Quote q = CouponPolicy.quote(coupon, goodsCents, merchantId, Instant.now());
        if (!q.available()) {
            throw BizException.badRequest(q.reasonCode(), q.reason());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("couponId", coupon.getId());
        body.put("name", coupon.getName());
        body.put("discountCents", q.discountCents());
        body.put("endAt", coupon.getEndAt());
        return body;
    }

    public void useCoupon(long userId, long couponId, long orderId) {
        UserCoupon grant = userCouponRepo.findByCouponIdAndUserIdAndStatus(couponId, userId, "UNUSED")
                .orElseThrow(() -> BizException.badRequest("COUPON_UNUSED", "优惠券不可用"));
        grant.setStatus("USED");
        grant.setUsedOrderId(orderId);
        userCouponRepo.save(grant);
    }

    public Map<String, Object> rider(long id) {
        Rider rider = riderRepo.findById(id).orElseThrow(() -> BizException.notFound("骑手不存在"));
        Map<String, Object> body = new LinkedHashMap<>(riderView(rider));
        userRepo.findById(id).ifPresent(u -> {
            body.put("displayName", u.getDisplayName());
            body.put("avatarUrl", u.getAvatarUrl());
        });
        try {
            body.putAll(riderProfileService.card(id));
        } catch (Exception ignored) {
            // 位置接口仍可用
        }
        return body;
    }

    /**
     * 本机无 ES 时的附近骑手：MySQL {@code rider} + Haversine。
     * 未传状态时默认 ONLINE/IDLE。
     */
    public List<Map<String, Object>> nearbyRiders(double lat, double lon, int radiusMeters,
                                                  String onlineStatus, String acceptStatus) {
        String online = (onlineStatus == null || onlineStatus.isBlank()) ? "ONLINE" : onlineStatus.trim();
        String accept = (acceptStatus == null || acceptStatus.isBlank()) ? "IDLE" : acceptStatus.trim();
        List<Rider> riders = riderRepo.findByOnlineStatusAndAcceptStatus(online, accept);
        return RiderNearby.filterAndSort(riders, lat, lon, radiusMeters, online, accept);
    }

    /** 最近位置窗口，供 dispatch 把坐标映射到路段做拥堵聚合。 */
    public List<Map<String, Object>> recentRiderLocations(int withinSeconds) {
        int window = Math.max(30, withinSeconds);
        Instant cutoff = Instant.now().minusSeconds(window);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Rider rider : riderRepo.findAll()) {
            if (rider.getLat() == null || rider.getLon() == null) {
                continue;
            }
            Instant updated = rider.getUpdateTime();
            if (updated != null && updated.isBefore(cutoff)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", rider.getUserId());
            row.put("lat", rider.getLat());
            row.put("lon", rider.getLon());
            row.put("updateTime", updated);
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> address(long userId, long addressId) {
        UserAddress address = addressRepo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> BizException.notFound("地址不存在"));
        return addressView(address);
    }

    public Map<String, Object> workStats() {
        AuthUser auth = AuthHolder.require();
        if (!auth.isRider()) {
            throw BizException.forbidden("仅骑手可查看工时");
        }
        return riderWorkService.workStats(auth.userId(), 0);
    }

    private Rider requireSelfRider() {
        AuthUser auth = AuthHolder.require();
        if (!auth.isRider()) {
            throw BizException.forbidden("仅骑手可操作");
        }
        return riderRepo.findById(auth.userId()).orElseThrow(() -> BizException.notFound("骑手档案不存在"));
    }

    private Map<String, Object> tokenBody(AppUser user) {
        Long riderId = "RIDER".equals(user.getRole()) ? user.getId() : null;
        String token = jwtService.issue(user.getId(), user.getRole(), riderId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("userId", user.getId());
        body.put("phone", user.getPhone());
        body.put("role", user.getRole());
        body.put("displayName", user.getDisplayName());
        body.put("avatarUrl", user.getAvatarUrl());
        return body;
    }

    private Map<String, Object> riderView(Rider rider) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", rider.getUserId());
        body.put("onlineStatus", rider.getOnlineStatus());
        body.put("acceptStatus", rider.getAcceptStatus());
        body.put("lat", rider.getLat());
        body.put("lon", rider.getLon());
        body.put("updateTime", rider.getUpdateTime());
        body.put("version", rider.getVersion());
        return body;
    }

    private Map<String, Object> merchantView(Merchant merchant) {
        double[] origin = userOrigin();
        return merchantCard(merchant, origin[0], origin[1]);
    }

    private Map<String, Object> merchantCard(Merchant merchant) {
        double[] origin = userOrigin();
        return merchantCard(merchant, origin[0], origin[1]);
    }

    private Map<String, Object> merchantCard(Merchant merchant, double userLat, double userLon) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", merchant.getUserId());
        body.put("shopName", merchant.getShopName());
        body.put("lat", merchant.getLat());
        body.put("lon", merchant.getLon());
        body.put("address", merchant.getAddress());
        body.put("category", merchant.getCategory());
        body.put("coverUrl", merchant.getCoverUrl());
        body.put("rating", merchant.getRatingAvg() != null ? round1(merchant.getRatingAvg())
                : (merchant.getRating() == null ? 4.8 : merchant.getRating()));
        body.put("ratingAvg", merchant.getRatingAvg() != null ? round1(merchant.getRatingAvg()) : body.get("rating"));
        body.put("ratingCount", merchant.getRatingCount() == null ? 0 : merchant.getRatingCount());
        body.put("completedCount", merchant.getCompletedCount() == null ? 0 : merchant.getCompletedCount());
        body.put("promo", merchant.getPromo());
        String online = merchant.getOnlineStatus() == null || merchant.getOnlineStatus().isBlank()
                ? "ONLINE" : merchant.getOnlineStatus();
        body.put("onlineStatus", online);
        body.put("open", "ONLINE".equals(online));
        double km = Geo.haversineKm(userLat, userLon, merchant.getLat(), merchant.getLon());
        boolean inRange = Double.isFinite(km) && km <= maxKm;
        body.put("distanceKm", Geo.roundKm(km));
        body.put("inRange", inRange);
        body.put("maxKm", maxKm);
        body.put("autoAccept", Boolean.TRUE.equals(merchant.getAutoAccept()));
        return body;
    }

    private Map<String, Object> skuView(MerchantSku sku) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", sku.getId());
        body.put("merchantId", sku.getMerchantId());
        body.put("name", sku.getName());
        String group = sku.getGroupName() == null || sku.getGroupName().isBlank() ? "推荐" : sku.getGroupName();
        int sold = sku.getMonthSales() == null ? 0 : sku.getMonthSales();
        body.put("groupName", group);
        body.put("priceCents", sku.getPriceCents());
        body.put("originPriceCents", sku.getOriginPriceCents());
        body.put("imageUrl", sku.getImageUrl());
        body.put("spec", sku.getSpec());
        body.put("stock", sku.getStock() == null ? 0 : sku.getStock());
        body.put("status", sku.getStatus() == null || sku.getStatus().isBlank() ? "ONLINE" : sku.getStatus());
        body.put("description", sku.getDescription() == null || sku.getDescription().isBlank()
                ? sku.getName() : sku.getDescription());
        body.put("detail", sku.getDetail() == null || sku.getDetail().isBlank()
                ? (sku.getName() + "，规格 " + sku.getSpec() + "。") : sku.getDetail());
        body.put("monthSales", sold);
        body.put("soldCount", sold);
        body.put("likeCount", sku.getLikeCount() == null ? 0 : sku.getLikeCount());
        return body;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private Map<String, Object> couponView(Coupon coupon, long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", coupon.getId());
        body.put("name", coupon.getName());
        body.put("merchantId", coupon.getMerchantId());
        body.put("activityId", coupon.getActivityId());
        body.put("type", coupon.getType());
        body.put("minSpendCents", coupon.getMinSpendCents());
        body.put("discountCents", coupon.getDiscountCents());
        body.put("percentOff", coupon.getPercentOff());
        body.put("stock", coupon.getStock());
        body.put("startAt", coupon.getStartAt());
        body.put("endAt", coupon.getEndAt());
        body.put("claimed", userCouponRepo.findByCouponIdAndUserId(coupon.getId(), userId).isPresent());
        body.put("memberOnly", Boolean.TRUE.equals(coupon.getMemberOnly()));
        String icon = CouponIcons.resolve(coupon);
        body.put("icon", icon);
        body.put("iconUrl", CouponIcons.url(icon));
        body.put("scene", CouponIcons.sceneOf(coupon.getCode()));
        body.put("expired", coupon.getEndAt() != null && Instant.now().isAfter(coupon.getEndAt()));
        return body;
    }

    private List<Map<String, Object>> addressesOf(long userId) {
        return addressRepo.findByUserId(userId).stream()
                .sorted(Comparator.comparing((UserAddress a) -> Boolean.TRUE.equals(a.getIsDefault()) ? 0 : 1)
                        .thenComparing(a -> a.getId() == null ? 0L : a.getId()))
                .map(this::addressView)
                .toList();
    }

    private UserAddress defaultAddress(long userId) {
        List<UserAddress> all = addressRepo.findByUserId(userId);
        return all.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsDefault()))
                .findFirst()
                .orElse(all.isEmpty() ? null : all.get(0));
    }

    private double[] userOrigin() {
        AuthUser auth = AuthHolder.get();
        if (auth != null) {
            UserAddress address = defaultAddress(auth.userId());
            if (address != null && address.getLat() != null && address.getLon() != null) {
                return new double[]{address.getLat(), address.getLon()};
            }
        }
        return new double[]{DEFAULT_LAT, DEFAULT_LON};
    }

    private static String effectiveStatus(UserCoupon grant, Coupon coupon, Instant now) {
        if (grant != null && "USED".equals(grant.getStatus())) {
            return "USED";
        }
        if (coupon != null && coupon.getEndAt() != null && now.isAfter(coupon.getEndAt())) {
            return "EXPIRED";
        }
        return grant == null || grant.getStatus() == null ? "UNUSED" : grant.getStatus();
    }

    private static Set<String> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return Set.of();
        }
        Set<String> out = new java.util.LinkedHashSet<>();
        for (String part : status.split("[,|]")) {
            String t = part.trim().toUpperCase();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private Map<String, Object> addressView(UserAddress address) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", address.getId());
        body.put("userId", address.getUserId());
        body.put("lat", address.getLat());
        body.put("lon", address.getLon());
        body.put("detail", address.getDetail());
        body.put("isDefault", Boolean.TRUE.equals(address.getIsDefault()));
        return body;
    }
}
