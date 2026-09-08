package com.shansuda.account.service;

import com.shansuda.account.catalog.MemberRules;
import com.shansuda.account.domain.Coupon;
import com.shansuda.account.domain.CouponTemplate;
import com.shansuda.account.domain.MemberPayLog;
import com.shansuda.account.domain.MemberSub;
import com.shansuda.account.domain.UserCoupon;
import com.shansuda.account.repo.CouponRepo;
import com.shansuda.account.repo.CouponTemplateRepo;
import com.shansuda.account.repo.MemberPayLogRepo;
import com.shansuda.account.repo.MemberSubRepo;
import com.shansuda.account.repo.UserCouponRepo;
import com.shansuda.common.api.BizException;
import com.shansuda.common.auth.AuthHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemberService {

    private static final String[] WEEK_CODES = {"MEMBER_3", "MEMBER_5", "MEMBER_8"};

    private final MemberSubRepo memberRepo;
    private final MemberPayLogRepo payLogRepo;
    private final CouponTemplateRepo templateRepo;
    private final CouponRepo couponRepo;
    private final UserCouponRepo userCouponRepo;

    public MemberService(MemberSubRepo memberRepo, MemberPayLogRepo payLogRepo,
                         CouponTemplateRepo templateRepo, CouponRepo couponRepo,
                         UserCouponRepo userCouponRepo) {
        this.memberRepo = memberRepo;
        this.payLogRepo = payLogRepo;
        this.templateRepo = templateRepo;
        this.couponRepo = couponRepo;
        this.userCouponRepo = userCouponRepo;
    }

    public Map<String, Object> card(long userId) {
        renewIfDue(userId);
        MemberSub sub = memberRepo.findById(userId).orElse(null);
        return view(sub, true);
    }

    public Map<String, Object> badge(Long userId) {
        if (userId == null) {
            return null;
        }
        MemberSub sub = memberRepo.findById(userId).orElse(null);
        if (sub == null) {
            return null;
        }
        return view(sub, false);
    }

    @Transactional
    public Map<String, Object> subscribe(String plan, String channel, String password) {
        if (!"147258".equals(password)) {
            throw BizException.badRequest("BAD_PASSWORD", "支付密码错误");
        }
        if (!"WECHAT".equals(channel) && !"ALIPAY".equals(channel)) {
            throw BizException.badRequest("BAD_CHANNEL", "请选择微信支付或支付宝");
        }
        if (!MemberRules.validPlan(plan)) {
            throw BizException.badRequest("BAD_PLAN", "plan 只能是 MONTH、QUARTER、YEAR 或 AUTO_MONTH");
        }
        long userId = AuthHolder.require().userId();
        if (!AuthHolder.require().isUser()) {
            throw BizException.forbidden("仅用户可开通会员");
        }
        Instant now = Instant.now();
        MemberSub sub = memberRepo.findById(userId).orElseGet(() -> {
            MemberSub created = new MemberSub();
            created.setUserId(userId);
            created.setPaidCentsNet(0);
            created.setLevel(1);
            return created;
        });
        Instant base = sub.getExpireAt() != null && sub.getExpireAt().isAfter(now) ? sub.getExpireAt() : now;
        sub.setExpireAt(base.plus(MemberRules.duration(plan)));
        sub.setPlan(plan);
        sub.setYearMember("YEAR".equals(plan));
        sub.setAutoRenew("AUTO_MONTH".equals(plan));
        int paid = sub.getPaidCentsNet() == null ? 0 : sub.getPaidCentsNet();
        sub.setPaidCentsNet(paid);
        sub.setLevel(Math.max(1, MemberRules.level(paid)));
        sub.setStatus("ACTIVE");
        sub.setUpdatedAt(now);
        memberRepo.save(sub);
        writeLedger(userId, plan, channel, MemberRules.priceCents(plan), false);
        Map<String, Object> body = view(sub, true);
        body.put("channel", channel);
        body.put("message", MemberRules.planLabel(plan) + "已开通");
        return body;
    }

    @Transactional
    public void renewDue() {
        for (MemberSub sub : memberRepo.findByAutoRenewTrue()) {
            renewIfDue(sub.getUserId());
        }
    }

    @Transactional
    public void renewIfDue(long userId) {
        MemberSub sub = memberRepo.findById(userId).orElse(null);
        if (sub == null || !Boolean.TRUE.equals(sub.getAutoRenew())) {
            return;
        }
        Instant now = Instant.now();
        if (sub.getExpireAt() != null && sub.getExpireAt().isAfter(now)) {
            return;
        }
        sub.setExpireAt(now.plus(MemberRules.MONTH));
        sub.setPlan("AUTO_MONTH");
        sub.setYearMember(false);
        sub.setAutoRenew(true);
        sub.setStatus("ACTIVE");
        sub.setUpdatedAt(now);
        memberRepo.save(sub);
        writeLedger(userId, "AUTO_MONTH", "AUTO", MemberRules.AUTO_MONTH_CENTS, true);
    }

    @Transactional
    public void onCompleted(long userId, int payCents) {
        if (payCents <= 0) {
            return;
        }
        adjust(userId, payCents);
    }

    @Transactional
    public void onRefunded(long userId, int payCents, boolean wasCompleted) {
        if (!wasCompleted || payCents <= 0) {
            return;
        }
        adjust(userId, -payCents);
    }

    public Map<String, Object> benefits() {
        long userId = AuthHolder.require().userId();
        renewIfDue(userId);
        MemberSub sub = requireActive(userId);
        ensureWeekCoupons();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Coupon coupon : weekCoupons()) {
            boolean claimed = userCouponRepo.findByCouponIdAndUserId(coupon.getId(), userId).isPresent();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("couponId", coupon.getId());
            row.put("name", coupon.getName());
            row.put("minSpendCents", coupon.getMinSpendCents());
            row.put("discountCents", coupon.getDiscountCents());
            row.put("claimed", claimed);
            row.put("endAt", coupon.getEndAt());
            String icon = coupon.getIcon() == null || coupon.getIcon().isBlank() ? "member" : coupon.getIcon();
            row.put("icon", icon);
            row.put("iconUrl", "/images/coupons/" + icon + ".svg");
            row.put("memberOnly", true);
            row.put("type", "AMOUNT");
            items.add(row);
        }
        Map<String, Object> body = view(sub, true);
        body.put("items", items);
        body.put("week", weekId());
        return body;
    }

    @Transactional
    public Map<String, Object> claim() {
        long userId = AuthHolder.require().userId();
        requireActive(userId);
        ensureWeekCoupons();
        List<Map<String, Object>> granted = new ArrayList<>();
        for (Coupon coupon : weekCoupons()) {
            if (userCouponRepo.findByCouponIdAndUserId(coupon.getId(), userId).isPresent()) {
                continue;
            }
            UserCoupon row = new UserCoupon();
            row.setCouponId(coupon.getId());
            row.setUserId(userId);
            row.setStatus("UNUSED");
            row.setClaimedAt(Instant.now());
            try {
                userCouponRepo.saveAndFlush(row);
                Map<String, Object> brief = new LinkedHashMap<>();
                brief.put("couponId", coupon.getId());
                brief.put("name", coupon.getName());
                brief.put("discountCents", coupon.getDiscountCents());
                brief.put("minSpendCents", coupon.getMinSpendCents());
                brief.put("endAt", coupon.getEndAt());
                brief.put("icon", "member");
                brief.put("iconUrl", "/images/coupons/member.svg");
                brief.put("memberOnly", true);
                granted.add(brief);
            } catch (DataIntegrityViolationException ignored) {
                // 已领
            }
        }
        Map<String, Object> body = benefits();
        body.put("granted", granted);
        body.put("message", granted.isEmpty() ? "本周会员红包已领过" : "会员红包已入账");
        return body;
    }

    private void adjust(long userId, int delta) {
        MemberSub sub = memberRepo.findById(userId).orElse(null);
        if (sub == null) {
            return;
        }
        int paid = Math.max(0, (sub.getPaidCentsNet() == null ? 0 : sub.getPaidCentsNet()) + delta);
        sub.setPaidCentsNet(paid);
        sub.setLevel(Math.max(1, MemberRules.level(paid)));
        refreshStatus(sub, Instant.now());
        sub.setUpdatedAt(Instant.now());
        memberRepo.save(sub);
    }

    private MemberSub requireActive(long userId) {
        MemberSub sub = memberRepo.findById(userId).orElse(null);
        if (sub == null) {
            throw BizException.conflict("NOT_MEMBER", "请先开通闪送达会员");
        }
        refreshStatus(sub, Instant.now());
        if (!"ACTIVE".equals(sub.getStatus()) || !isActive(sub, Instant.now())) {
            memberRepo.save(sub);
            throw BizException.conflict("MEMBER_EXPIRED", "会员已过期，无法领取会员红包");
        }
        return sub;
    }

    private void refreshStatus(MemberSub sub, Instant now) {
        if (Boolean.TRUE.equals(sub.getAutoRenew()) && (sub.getExpireAt() == null || !sub.getExpireAt().isAfter(now))) {
            return;
        }
        if (sub.getExpireAt() == null || !sub.getExpireAt().isAfter(now)) {
            sub.setStatus("EXPIRED");
        } else if (!"ACTIVE".equals(sub.getStatus())) {
            sub.setStatus("EXPIRED".equals(sub.getStatus()) ? "EXPIRED" : "ACTIVE");
        }
    }

    private boolean isActive(MemberSub sub, Instant now) {
        return sub != null && sub.getExpireAt() != null && sub.getExpireAt().isAfter(now)
                && !"EXPIRED".equals(sub.getStatus());
    }

    private void ensureWeekCoupons() {
        int week = weekId();
        Instant end = weekEnd();
        Instant start = Instant.now().minusSeconds(60);
        for (int i = 0; i < WEEK_CODES.length; i++) {
            String code = WEEK_CODES[i];
            long id = weekCouponId(week, i + 1);
            if (couponRepo.existsById(id)) {
                continue;
            }
            CouponTemplate tpl = templateRepo.findById(code).orElse(null);
            if (tpl == null) {
                continue;
            }
            Coupon coupon = new Coupon();
            coupon.setId(id);
            coupon.setName(tpl.getName());
            coupon.setType("AMOUNT");
            coupon.setMinSpendCents(0);
            coupon.setDiscountCents(tpl.getDiscountCents());
            coupon.setPercentOff(0);
            coupon.setStock(9999);
            coupon.setStartAt(start);
            coupon.setEndAt(end);
            coupon.setStatus("ONLINE");
            coupon.setCode(code + ":" + week);
            coupon.setMemberOnly(true);
            coupon.setIcon("member");
            couponRepo.save(coupon);
        }
    }

    private List<Coupon> weekCoupons() {
        int week = weekId();
        List<Coupon> list = new ArrayList<>();
        for (int i = 0; i < WEEK_CODES.length; i++) {
            couponRepo.findById(weekCouponId(week, i + 1)).ifPresent(list::add);
        }
        return list;
    }

    static int weekId() {
        LocalDate today = LocalDate.now(CouponGrantService.SHANGHAI);
        return today.get(IsoFields.WEEK_BASED_YEAR) * 100 + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    static long weekCouponId(int week, int idx) {
        return 8_000_000L + week * 10L + idx;
    }

    private static Instant weekEnd() {
        LocalDate today = LocalDate.now(CouponGrantService.SHANGHAI);
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return sunday.plusDays(1).atStartOfDay(CouponGrantService.SHANGHAI).toInstant();
    }

    private Map<String, Object> view(MemberSub sub, boolean detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (sub == null) {
            body.put("subscribed", false);
            body.put("active", false);
            body.put("expired", false);
            body.put("level", 0);
            body.put("title", null);
            body.put("yearMember", false);
            body.put("autoRenew", false);
            body.put("plan", null);
            body.put("planLabel", null);
            if (detail) {
                body.put("plans", MemberRules.catalog());
            }
            return body;
        }
        Instant now = Instant.now();
        boolean active = isActive(sub, now);
        boolean expired = sub.getExpireAt() != null && !sub.getExpireAt().isAfter(now);
        int level = sub.getLevel() == null ? MemberRules.level(nz(sub.getPaidCentsNet())) : sub.getLevel();
        int paid = nz(sub.getPaidCentsNet());
        body.put("subscribed", true);
        body.put("active", active);
        body.put("expired", expired);
        body.put("level", level);
        body.put("title", MemberRules.title(level));
        boolean year = Boolean.TRUE.equals(sub.getYearMember()) && "YEAR".equals(sub.getPlan());
        body.put("yearMember", year && (active || expired));
        body.put("autoRenew", Boolean.TRUE.equals(sub.getAutoRenew()));
        body.put("plan", sub.getPlan());
        body.put("planLabel", MemberRules.planLabel(sub.getPlan()));
        body.put("planDays", MemberRules.days(sub.getPlan()));
        body.put("expireAt", sub.getExpireAt());
        body.put("status", expired ? "EXPIRED" : (active ? "ACTIVE" : sub.getStatus()));
        if (detail) {
            body.put("plans", MemberRules.catalog());
            body.put("paidCentsNet", paid);
            body.put("paidYuan", paid / 100);
            int next = MemberRules.nextThresholdYuan(level);
            body.put("nextThresholdYuan", next);
            int cur = MemberRules.thresholdYuan(level);
            body.put("currentThresholdYuan", cur);
            if (next > 0) {
                int span = Math.max(1, next - cur);
                int progress = Math.max(0, Math.min(100, (paid / 100 - cur) * 100 / span));
                body.put("progressPercent", progress);
            } else {
                body.put("progressPercent", 100);
            }
        }
        return body;
    }

    private void writeLedger(long userId, String plan, String channel, int cents, boolean auto) {
        MemberPayLog row = new MemberPayLog();
        row.setUserId(userId);
        row.setPlan(plan);
        row.setChannel(channel);
        row.setCents(cents);
        row.setAutoRenew(auto);
        row.setCreatedAt(Instant.now());
        payLogRepo.save(row);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
