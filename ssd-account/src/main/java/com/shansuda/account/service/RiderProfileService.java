package com.shansuda.account.service;

import com.shansuda.account.domain.AppUser;
import com.shansuda.account.domain.OrderTip;
import com.shansuda.account.domain.Rider;
import com.shansuda.account.domain.RiderProfile;
import com.shansuda.account.repo.AppUserRepo;
import com.shansuda.account.repo.OrderTipRepo;
import com.shansuda.account.repo.RiderProfileRepo;
import com.shansuda.account.repo.RiderRepo;
import com.shansuda.account.rider.RiderBadgeRules;
import com.shansuda.common.api.BizException;
import com.shansuda.common.auth.AuthHolder;
import com.shansuda.common.auth.AuthUser;
import com.shansuda.common.catalog.TipGifts;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RiderProfileService {

    public static final String ON_TIME_NOTE = "准时率按完成单是否晚于约定 ETA 估算；演示环境无真实超时轨迹，采用档案口径 on_time_rate。";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String DEFAULT_BIO = "闪送达骑手，出餐后保温袋封装，按约定路线准时送达。";

    private final RiderRepo riderRepo;
    private final RiderProfileRepo profileRepo;
    private final OrderTipRepo tipRepo;
    private final AppUserRepo userRepo;

    public RiderProfileService(RiderRepo riderRepo, RiderProfileRepo profileRepo, OrderTipRepo tipRepo,
                               AppUserRepo userRepo) {
        this.riderRepo = riderRepo;
        this.profileRepo = profileRepo;
        this.tipRepo = tipRepo;
        this.userRepo = userRepo;
    }

    public Map<String, Object> meProfile() {
        AuthUser auth = AuthHolder.require();
        if (!auth.isRider()) {
            throw BizException.forbidden("仅骑手可查看自己的简介");
        }
        return card(auth.userId());
    }

    public Map<String, Object> credit(long riderId) {
        RiderProfile profile = profileRepo.findById(riderId).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("onTimeRate", profile == null || profile.getOnTimeRate() == null ? 0.92 : profile.getOnTimeRate());
        body.put("ratingAvg", profile == null || profile.getRatingAvg() == null ? 4.8 : profile.getRatingAvg());
        body.put("ratingCount", profile == null ? 0 : nz(profile.getRatingCount()));
        return body;
    }

    public Map<String, Object> card(long riderId) {
        riderRepo.findById(riderId).orElseThrow(() -> BizException.notFound("骑手不存在"));
        RiderProfile profile = ensure(riderId);
        AppUser user = userRepo.findById(riderId).orElseThrow(() -> BizException.notFound("骑手不存在"));
        LocalDate started = profile.getStartedOn() == null ? LocalDate.now(ZONE).minusYears(1) : profile.getStartedOn();
        LocalDate today = LocalDate.now(ZONE);
        long days = Math.max(1, ChronoUnit.DAYS.between(started, today) + 1);
        int years = Math.max(0, (int) ChronoUnit.YEARS.between(started, today));
        int completed = nz(profile.getCompletedCount());
        double rating = profile.getRatingAvg() == null ? 4.8 : profile.getRatingAvg();
        int ratingCount = nz(profile.getRatingCount());
        double onTime = profile.getOnTimeRate() == null ? 0.92 : profile.getOnTimeRate();
        int tips = nz(profile.getTipCentsTotal());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("riderId", riderId);
        body.put("displayName", user.getDisplayName());
        body.put("avatarUrl", user.getAvatarUrl());
        body.put("bio", profile.getBio() == null || profile.getBio().isBlank() ? DEFAULT_BIO : profile.getBio());
        body.put("startedOn", started.toString());
        body.put("yearsOnJob", years);
        body.put("daysOnJob", days);
        body.put("onTimeRate", Math.round(onTime * 1000.0) / 1000.0);
        body.put("onTimeRateNote", ON_TIME_NOTE);
        body.put("tipCentsTotal", tips);
        body.put("ratingAvg", Math.round(rating * 100.0) / 100.0);
        body.put("ratingCount", ratingCount);
        body.put("completedCount", completed);
        body.put("badges", RiderBadgeRules.badges(completed, rating, ratingCount, onTime, tips));
        return body;
    }

    public Map<String, Object> tipByOrder(long orderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        return tipRepo.findById(orderId).map(tip -> {
            body.put("tipped", true);
            body.put("orderId", tip.getOrderId());
            body.put("riderId", tip.getRiderId());
            body.put("cents", tip.getCents());
            TipGifts.Gift gift = resolveStoredGift(tip.getGiftCode(), tip.getCents());
            if (gift != null) {
                body.put("giftCode", gift.code());
                body.put("giftLabel", gift.label());
            } else if (tip.getGiftCode() != null) {
                body.put("giftCode", tip.getGiftCode());
            }
            return body;
        }).orElseGet(() -> {
            body.put("tipped", false);
            body.put("orderId", orderId);
            return body;
        });
    }

    private static TipGifts.Gift resolveStoredGift(String giftCode, Integer cents) {
        if (giftCode != null && !giftCode.isBlank()) {
            try {
                return TipGifts.requireCode(giftCode);
            } catch (BizException ignored) {
                // 历史脏数据按金额回退
            }
        }
        return cents == null ? null : TipGifts.fromCents(cents);
    }

    @Transactional
    public Map<String, Object> recordTip(long orderId, long riderId, long userId, Integer cents, String giftCode) {
        TipGifts.Gift gift = TipGifts.requireCentsOrCode(giftCode, cents);
        riderRepo.findById(riderId).orElseThrow(() -> BizException.notFound("骑手不存在"));
        if (tipRepo.existsByOrderId(orderId)) {
            throw BizException.conflict("TIPPED", "该订单已打赏");
        }
        OrderTip tip = new OrderTip();
        tip.setOrderId(orderId);
        tip.setRiderId(riderId);
        tip.setUserId(userId);
        tip.setCents(gift.cents());
        tip.setGiftCode(gift.code());
        tip.setCreatedAt(Instant.now());
        try {
            tipRepo.saveAndFlush(tip);
        } catch (DataIntegrityViolationException ex) {
            throw BizException.conflict("TIPPED", "该订单已打赏");
        }
        RiderProfile profile = ensure(riderId);
        profile.setTipCentsTotal(nz(profile.getTipCentsTotal()) + gift.cents());
        profileRepo.save(profile);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tipped", true);
        body.put("orderId", orderId);
        body.put("riderId", riderId);
        body.put("cents", gift.cents());
        body.put("giftCode", gift.code());
        body.put("giftLabel", gift.label());
        body.put("tipCentsTotal", profile.getTipCentsTotal());
        return body;
    }

    @Transactional
    public void bumpCompleted(long riderId) {
        if (riderRepo.findById(riderId).isEmpty()) {
            return;
        }
        RiderProfile profile = ensure(riderId);
        profile.setCompletedCount(nz(profile.getCompletedCount()) + 1);
        profileRepo.save(profile);
    }

    @Transactional
    public void rate(long riderId, int score) {
        if (score < 1 || score > 5 || riderRepo.findById(riderId).isEmpty()) {
            return;
        }
        RiderProfile profile = ensure(riderId);
        int count = nz(profile.getRatingCount());
        double avg = profile.getRatingAvg() == null ? 0 : profile.getRatingAvg();
        double next = (avg * count + score) / (count + 1);
        profile.setRatingCount(count + 1);
        profile.setRatingAvg(Math.round(next * 100.0) / 100.0);
        profileRepo.save(profile);
    }

    @Transactional
    public RiderProfile ensure(long riderId) {
        return profileRepo.findById(riderId).orElseGet(() -> {
            RiderProfile created = new RiderProfile();
            created.setUserId(riderId);
            created.setBio(DEFAULT_BIO);
            Instant createdAt = userRepo.findById(riderId).map(AppUser::getCreatedAt).orElse(null);
            created.setStartedOn(createdAt == null ? LocalDate.now(ZONE).minusMonths(6) : LocalDate.ofInstant(createdAt, ZONE));
            created.setOnTimeRate(0.92);
            created.setTipCentsTotal(0);
            created.setRatingAvg(4.80);
            created.setRatingCount(0);
            created.setCompletedCount(0);
            return profileRepo.save(created);
        });
    }

    public void ensureFor(Rider rider) {
        if (rider != null && rider.getUserId() != null) {
            ensure(rider.getUserId());
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
