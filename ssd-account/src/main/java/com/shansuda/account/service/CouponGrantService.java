package com.shansuda.account.service;

import com.shansuda.account.catalog.CouponIcons;
import com.shansuda.account.catalog.CouponPolicy;
import com.shansuda.account.client.OrderInternalClient;
import com.shansuda.account.domain.AppUser;
import com.shansuda.account.domain.Coupon;
import com.shansuda.account.domain.CouponGrantLog;
import com.shansuda.account.domain.CouponTemplate;
import com.shansuda.account.domain.UserCoupon;
import com.shansuda.account.repo.AppUserRepo;
import com.shansuda.account.repo.CouponGrantLogRepo;
import com.shansuda.account.repo.CouponRepo;
import com.shansuda.account.repo.CouponTemplateRepo;
import com.shansuda.account.repo.UserCouponRepo;
import com.shansuda.common.api.BizException;
import com.shansuda.common.auth.AuthHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 美团风格发券：新客固定、久未登录大额随机、活跃用户常规券、普通回流中等力度。
 * 幂等键 GRANT:{userId}:{date}:{scene}，同一自然日不刷大额。
 */
@Service
public class CouponGrantService {

    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    public static final String SCENE_NEWCOMER = "NEWCOMER";
    public static final String SCENE_LAPSED = "LAPSED";
    public static final String SCENE_FREQUENT = "FREQUENT";
    public static final String SCENE_RETURN = "RETURN";
    public static final String SCENE_MEMBER = "MEMBER";
    public static final String SCENE_LOGIN = "LOGIN";
    public static final String SCENE_HOME = "HOME";
    public static final int HOME_SLOT_SECONDS = 60;

    private static final String[] LAPSED = {"LAPSED_30_20", "LAPSED_50_25"};
    private static final String[] FREQUENT = {"FREQ_25_5", "FREQ_40_8"};
    private static final String[] RETURNING = {"RETURN_20_6", "RETURN_35_10"};
    private static final String[] HOME = {
            "HOME_20_6", "HOME_0_3", "HOME_FOOD_25_8", "HOME_40_10", "HOME_0_5", "HOME_FRESH_30_8"
    };
    private static final String[] LOGIN = {
            "LOGIN_0_4", "LOGIN_25_8", "LOGIN_35_12", "LOGIN_0_6", "LOGIN_FOOD_20_5"
    };

    private final CouponTemplateRepo templateRepo;
    private final CouponRepo couponRepo;
    private final UserCouponRepo userCouponRepo;
    private final CouponGrantLogRepo grantLogRepo;
    private final AppUserRepo userRepo;
    private final OrderInternalClient orderInternalClient;
    private final AtomicLong instanceSeq = new AtomicLong(System.currentTimeMillis() % 100_000);

    public CouponGrantService(CouponTemplateRepo templateRepo, CouponRepo couponRepo,
                              UserCouponRepo userCouponRepo, CouponGrantLogRepo grantLogRepo,
                              AppUserRepo userRepo, OrderInternalClient orderInternalClient) {
        this.templateRepo = templateRepo;
        this.couponRepo = couponRepo;
        this.userCouponRepo = userCouponRepo;
        this.grantLogRepo = grantLogRepo;
        this.userRepo = userRepo;
        this.orderInternalClient = orderInternalClient;
    }

    public String resolveScene(AppUser user) {
        if (user == null || !"USER".equals(user.getRole())) {
            return null;
        }
        Instant last = user.getLastLoginAt();
        if (last != null && last.isBefore(Instant.now().minusSeconds(14L * 86400))) {
            return SCENE_LAPSED;
        }
        long used = userCouponRepo.countByUserIdAndStatus(user.getId(), "USED");
        int completed = orderInternalClient.completedMerchantCounts(user.getId())
                .values().stream().mapToInt(Integer::intValue).sum();
        if (used >= 3 || completed >= 8) {
            return SCENE_FREQUENT;
        }
        return SCENE_RETURN;
    }

    public Map<String, Object> todayOffer(long userId) {
        AppUser user = userRepo.findById(userId).orElseThrow(() -> BizException.notFound("用户不存在"));
        String scene = resolveScene(user);
        String key = grantKey(userId, scene);
        boolean claimed = scene != null && grantLogRepo.existsById(key);
        List<String> codes = pickCodes(scene, userId, today());
        List<Map<String, Object>> items = new ArrayList<>();
        for (String code : codes) {
            templateRepo.findById(code).ifPresent(t -> items.add(templateView(t, CouponIcons.validSeconds(t))));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", scene);
        body.put("claimed", claimed);
        body.put("grantKey", key);
        body.put("items", items);
        body.put("date", today().toString());
        return body;
    }

    public Map<String, Object> homeFeed(long userId) {
        Instant now = Instant.now();
        long slot = homeSlot(now);
        List<String> codes = pickHomeCodes(userId, slot);
        String key = homeGrantKey(userId, slot);
        boolean claimed = grantLogRepo.existsById(key);
        List<Map<String, Object>> items = new ArrayList<>();
        for (String code : codes) {
            templateRepo.findById(code).ifPresent(t -> items.add(templateView(t, CouponIcons.validSeconds(t))));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", SCENE_HOME);
        body.put("slot", slot);
        body.put("slotSeconds", HOME_SLOT_SECONDS);
        body.put("nextRefreshAt", Instant.ofEpochSecond((slot + 1) * HOME_SLOT_SECONDS));
        body.put("claimed", claimed);
        body.put("grantKey", key);
        body.put("items", items);
        body.put("date", today().toString());
        return body;
    }

    @Transactional
    public Map<String, Object> claimHomeFeed(long userId) {
        Instant now = Instant.now();
        long slot = homeSlot(now);
        List<String> codes = pickHomeCodes(userId, slot);
        return grantByCodes(userId, SCENE_HOME, codes, homeGrantKey(userId, slot));
    }

    public Map<String, Object> loginGiftOffer(long userId) {
        AppUser user = userRepo.findById(userId).orElseThrow(() -> BizException.notFound("用户不存在"));
        if (!"USER".equals(user.getRole())) {
            Map<String, Object> body = emptyGrant(SCENE_LOGIN);
            body.put("eligible", false);
            body.put("alreadyClaimed", false);
            body.put("items", List.of());
            body.put("date", today().toString());
            return body;
        }
        LocalDate date = today();
        String key = loginGiftKey(userId, date);
        boolean claimed = grantLogRepo.existsById(key);
        List<String> codes = pickLoginCodes(userId, date);
        List<Map<String, Object>> items = new ArrayList<>();
        for (String code : codes) {
            templateRepo.findById(code).ifPresent(t -> items.add(templateView(t, CouponIcons.validSeconds(t))));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", SCENE_LOGIN);
        body.put("claimed", claimed);
        body.put("alreadyClaimed", claimed);
        body.put("eligible", !claimed && !items.isEmpty());
        body.put("grantKey", key);
        body.put("items", items);
        body.put("date", date.toString());
        body.put("title", "登录礼");
        body.put("subtitle", "点券面或查看详情，确认后再领取");
        return body;
    }

    @Transactional
    public Map<String, Object> claimLoginGift(long userId) {
        AppUser user = userRepo.findById(userId).orElseThrow(() -> BizException.notFound("用户不存在"));
        if (!"USER".equals(user.getRole())) {
            return emptyGrant(SCENE_LOGIN);
        }
        LocalDate date = today();
        List<String> codes = pickLoginCodes(userId, date);
        return grantByCodes(userId, SCENE_LOGIN, codes, loginGiftKey(userId, date));
    }

    @Transactional
    public Map<String, Object> grantNewcomer(long userId) {
        return grantByCodes(userId, SCENE_NEWCOMER, List.of("NEWCOMER_15"), grantKeyOnce(userId, SCENE_NEWCOMER));
    }

    @Transactional
    public Map<String, Object> grantSession(long userId) {
        AppUser user = userRepo.findById(userId).orElseThrow(() -> BizException.notFound("用户不存在"));
        if (!"USER".equals(user.getRole())) {
            return emptyGrant(null);
        }
        String scene = resolveScene(user);
        List<String> codes = pickCodes(scene, userId, today());
        return grantByCodes(userId, scene, codes, grantKey(userId, scene));
    }

    @Transactional
    public Map<String, Object> grantSessionForMe() {
        return grantSession(AuthHolder.require().userId());
    }

    @Transactional
    public Map<String, Object> grantCodes(long userId, String scene, List<String> codes, String grantKey) {
        return grantByCodes(userId, scene, codes, grantKey);
    }

    private Map<String, Object> grantByCodes(long userId, String scene, List<String> codes, String key) {
        if (scene == null || codes == null || codes.isEmpty()) {
            return emptyGrant(scene);
        }
        if (grantLogRepo.existsById(key)) {
            Map<String, Object> replay = emptyGrant(scene);
            replay.put("claimed", true);
            replay.put("replayed", true);
            replay.put("message", "今日已发放");
            replay.put("granted", myGrantsOfCodes(userId, codes));
            return replay;
        }
        List<Map<String, Object>> granted = new ArrayList<>();
        Instant start = Instant.now().minusSeconds(60);
        for (String code : codes) {
            CouponTemplate tpl = templateRepo.findById(code).orElse(null);
            if (tpl == null) {
                continue;
            }
            Instant end = Instant.now().plusSeconds(CouponIcons.validSeconds(tpl));
            Coupon coupon = instantiate(tpl, start, end, SCENE_NEWCOMER.equals(scene));
            if (userCouponRepo.findByCouponIdAndUserId(coupon.getId(), userId).isPresent()) {
                granted.add(couponBrief(coupon, "UNUSED"));
                continue;
            }
            UserCoupon row = new UserCoupon();
            row.setCouponId(coupon.getId());
            row.setUserId(userId);
            row.setStatus("UNUSED");
            row.setClaimedAt(Instant.now());
            try {
                userCouponRepo.saveAndFlush(row);
            } catch (DataIntegrityViolationException ex) {
                // 已领过同一张
            }
            granted.add(couponBrief(coupon, "UNUSED"));
        }
        CouponGrantLog log = new CouponGrantLog();
        log.setGrantKey(key);
        log.setUserId(userId);
        log.setScene(scene);
        log.setCreatedAt(Instant.now());
        try {
            grantLogRepo.saveAndFlush(log);
        } catch (DataIntegrityViolationException ex) {
            Map<String, Object> replay = emptyGrant(scene);
            replay.put("claimed", true);
            replay.put("replayed", true);
            replay.put("granted", granted);
            return replay;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", scene);
        body.put("claimed", true);
        body.put("replayed", false);
        body.put("granted", granted);
        body.put("message", grantMessage(scene, granted));
        return body;
    }

    public List<Map<String, Object>> options(long userId, long merchantId, int goodsCents) {
        Instant now = Instant.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserCoupon grant : userCouponRepo.findByUserIdAndStatus(userId, "UNUSED")) {
            Coupon coupon = couponRepo.findById(grant.getCouponId()).orElse(null);
            if (coupon == null) {
                continue;
            }
            CouponPolicy.Quote q = CouponPolicy.quote(coupon, goodsCents, merchantId, now);
            Map<String, Object> row = couponBrief(coupon, grant.getStatus());
            row.put("grantId", grant.getId());
            row.put("available", q.available());
            row.put("reason", q.available() ? null : q.reason());
            row.put("discountCents", q.discountCents());
            row.put("endAt", coupon.getEndAt());
            out.add(row);
        }
        return out;
    }

    private Coupon instantiate(CouponTemplate tpl, Instant start, Instant end, boolean sharedNewcomer) {
        if (sharedNewcomer) {
            return couponRepo.findFirstByCode(tpl.getCode()).orElseGet(() -> saveCoupon(nextId(), tpl, start, end));
        }
        return saveCoupon(nextId(), tpl, start, end);
    }

    private Coupon saveCoupon(long id, CouponTemplate tpl, Instant start, Instant end) {
        Coupon coupon = new Coupon();
        coupon.setId(id);
        coupon.setName(tpl.getName());
        coupon.setType(tpl.getType() == null ? "AMOUNT" : tpl.getType());
        coupon.setMinSpendCents(nz(tpl.getMinSpendCents()));
        coupon.setDiscountCents(nz(tpl.getDiscountCents()));
        coupon.setPercentOff(nz(tpl.getPercentOff()));
        coupon.setStock(9999);
        coupon.setStartAt(start);
        coupon.setEndAt(end);
        coupon.setStatus("ONLINE");
        coupon.setCode(tpl.getCode() + ":" + id);
        coupon.setMemberOnly(Boolean.TRUE.equals(tpl.getMemberOnly()));
        coupon.setIcon(CouponIcons.resolve(tpl));
        return couponRepo.save(coupon);
    }

    private long nextId() {
        long id = 1_000_000L + Instant.now().toEpochMilli() % 100_000_000L + instanceSeq.incrementAndGet();
        while (couponRepo.existsById(id)) {
            id++;
        }
        return id;
    }

    private List<String> pickCodes(String scene, long userId, LocalDate date) {
        if (scene == null) {
            return List.of();
        }
        Random r = new Random(userId * 31 + date.toEpochDay() * 17);
        return switch (scene) {
            case SCENE_NEWCOMER -> List.of("NEWCOMER_15");
            case SCENE_LAPSED -> pickN(LAPSED, 1 + r.nextInt(2), r);
            case SCENE_FREQUENT -> List.of(FREQUENT[r.nextInt(FREQUENT.length)]);
            case SCENE_RETURN -> List.of(RETURNING[r.nextInt(RETURNING.length)]);
            case SCENE_HOME -> pickN(HOME, 1 + r.nextInt(2), r);
            case SCENE_LOGIN -> pickN(LOGIN, 2 + r.nextInt(2), r);
            default -> List.of();
        };
    }

    private List<String> pickHomeCodes(long userId, long slot) {
        Random r = new Random(userId * 31 + slot * 17);
        return pickN(HOME, 1 + r.nextInt(2), r);
    }

    private List<String> pickLoginCodes(long userId, LocalDate date) {
        Random r = new Random(userId * 31 + date.toEpochDay() * 41);
        return pickN(LOGIN, 2 + r.nextInt(2), r);
    }

    public static long homeSlot(Instant now) {
        return (now == null ? Instant.now() : now).getEpochSecond() / HOME_SLOT_SECONDS;
    }

    public static String homeGrantKey(long userId, long slot) {
        return "GRANT:" + userId + ":" + today() + ":HOME:" + slot;
    }

    public static String loginGiftKey(long userId, LocalDate date) {
        return "LOGIN_GIFT:" + userId + ":" + date;
    }

    private static List<String> pickN(String[] pool, int n, Random r) {
        List<String> copy = new ArrayList<>(List.of(pool));
        List<String> out = new ArrayList<>();
        int take = Math.min(n, copy.size());
        for (int i = 0; i < take; i++) {
            out.add(copy.remove(r.nextInt(copy.size())));
        }
        return out;
    }

    public static String grantKey(long userId, String scene) {
        if (scene == null) {
            return "GRANT:" + userId + ":" + today() + ":NONE";
        }
        if (SCENE_NEWCOMER.equals(scene)) {
            return grantKeyOnce(userId, scene);
        }
        return "GRANT:" + userId + ":" + today() + ":" + scene;
    }

    public static String grantKeyOnce(long userId, String scene) {
        return "GRANT:" + userId + ":ONCE:" + scene;
    }

    public static LocalDate today() {
        return LocalDate.now(SHANGHAI);
    }

    private List<Map<String, Object>> myGrantsOfCodes(long userId, List<String> codes) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserCoupon grant : userCouponRepo.findByUserId(userId)) {
            Coupon coupon = couponRepo.findById(grant.getCouponId()).orElse(null);
            if (coupon == null || coupon.getCode() == null) {
                continue;
            }
            for (String code : codes) {
                if (coupon.getCode().startsWith(code)) {
                    out.add(couponBrief(coupon, grant.getStatus()));
                    break;
                }
            }
        }
        return out;
    }

    private Map<String, Object> emptyGrant(String scene) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", scene);
        body.put("claimed", false);
        body.put("replayed", false);
        body.put("granted", List.of());
        body.put("message", "");
        return body;
    }

    private static String grantMessage(String scene, List<Map<String, Object>> granted) {
        if (granted == null || granted.isEmpty()) {
            return "";
        }
        if (SCENE_NEWCOMER.equals(scene)) {
            return "新客 15 元无门槛券已入账";
        }
        if (SCENE_LOGIN.equals(scene)) {
            return "登录礼已放入我的优惠券";
        }
        if (SCENE_HOME.equals(scene)) {
            return "首页券已放入我的优惠券";
        }
        return "已发放 " + granted.size() + " 张优惠券";
    }

    private Map<String, Object> couponBrief(Coupon coupon, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", coupon.getId());
        body.put("couponId", coupon.getId());
        body.put("name", coupon.getName());
        body.put("type", coupon.getType());
        body.put("minSpendCents", coupon.getMinSpendCents());
        body.put("discountCents", coupon.getDiscountCents());
        body.put("percentOff", coupon.getPercentOff());
        body.put("endAt", coupon.getEndAt());
        body.put("code", coupon.getCode());
        body.put("grantStatus", status);
        body.put("memberOnly", Boolean.TRUE.equals(coupon.getMemberOnly()));
        String icon = CouponIcons.resolve(coupon);
        body.put("icon", icon);
        body.put("iconUrl", CouponIcons.url(icon));
        body.put("scene", CouponIcons.sceneOf(coupon.getCode()));
        return body;
    }

    private Map<String, Object> templateView(CouponTemplate t, long validSeconds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", t.getCode());
        body.put("name", t.getName());
        body.put("scene", t.getScene());
        body.put("type", t.getType());
        body.put("minSpendCents", t.getMinSpendCents());
        body.put("discountCents", t.getDiscountCents());
        body.put("percentOff", t.getPercentOff());
        body.put("memberOnly", Boolean.TRUE.equals(t.getMemberOnly()));
        String icon = CouponIcons.resolve(t);
        body.put("icon", icon);
        body.put("iconUrl", CouponIcons.url(icon));
        body.put("validSeconds", validSeconds);
        body.put("endAt", Instant.now().plusSeconds(validSeconds));
        return body;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
