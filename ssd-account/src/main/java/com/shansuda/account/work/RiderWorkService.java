package com.shansuda.account.work;

import com.shansuda.account.domain.Rider;
import com.shansuda.account.domain.RiderWorkday;
import com.shansuda.account.repo.RiderRepo;
import com.shansuda.account.repo.RiderWorkdayRepo;
import com.shansuda.common.api.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RiderWorkService {

    private static final Logger log = LoggerFactory.getLogger(RiderWorkService.class);
    private static final ZoneId ZONE = ZoneId.of(RiderWorkPolicy.ZONE);

    private final RiderRepo riderRepo;
    private final RiderWorkdayRepo workdayRepo;
    private final int maxHours;
    private final int dailySubsidyCents;
    private final int completeSubsidyCents;

    public RiderWorkService(RiderRepo riderRepo, RiderWorkdayRepo workdayRepo,
                            @Value("${ssd.rider.max-work-hours:8}") int maxHours,
                            @Value("${ssd.rider.daily-subsidy-cents:1500}") int dailySubsidyCents,
                            @Value("${ssd.rider.complete-subsidy-cents:200}") int completeSubsidyCents) {
        this.riderRepo = riderRepo;
        this.workdayRepo = workdayRepo;
        this.maxHours = maxHours;
        this.dailySubsidyCents = dailySubsidyCents;
        this.completeSubsidyCents = completeSubsidyCents;
    }

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public int maxSeconds() {
        return RiderWorkPolicy.maxSeconds(maxHours);
    }

    @Transactional
    public RiderWorkday ensureToday(long userId) {
        LocalDate day = today();
        return workdayRepo.findByUserIdAndWorkDate(userId, day).orElseGet(() -> {
            RiderWorkday row = new RiderWorkday();
            row.setUserId(userId);
            row.setWorkDate(day);
            row.setWorkedSeconds(0);
            row.setForcedOffline(false);
            return workdayRepo.save(row);
        });
    }

    @Transactional
    public void assertCanGoOnline(long userId) {
        RiderWorkday row = ensureToday(userId);
        if (!RiderWorkPolicy.canGoOnline(row.getForcedOffline(), row.getWorkDate(), today())) {
            throw BizException.conflict("WORK_LIMIT", "今日工时已满，次日 0 点后可再次上线");
        }
    }

    @Transactional
    public void assertCanAccept(long userId) {
        RiderWorkday row = tick(userId, true);
        String deny = denyGrab(userId, row);
        if (deny != null) {
            throw BizException.conflict(deny, RiderWorkPolicy.grabDenyReason(deny));
        }
    }

    public Map<String, Object> canAccept(long userId) {
        RiderWorkday row = ensureToday(userId);
        String online = riderOnlineStatus(userId);
        String deny = RiderWorkPolicy.grabDenyCode(online, row.getForcedOffline(), row.getWorkedSeconds(), maxSeconds());
        Map<String, Object> body = snapshot(row, 0);
        body.put("onlineStatus", online);
        body.put("allowed", deny == null);
        body.put("code", deny);
        body.put("reason", RiderWorkPolicy.grabDenyReason(deny));
        return body;
    }

    private String denyGrab(long userId, RiderWorkday row) {
        return RiderWorkPolicy.grabDenyCode(
                riderOnlineStatus(userId), row.getForcedOffline(), row.getWorkedSeconds(), maxSeconds());
    }

    private String riderOnlineStatus(long userId) {
        return riderRepo.findById(userId).map(Rider::getOnlineStatus).orElse("OFFLINE");
    }

    @Transactional
    public Map<String, Object> onGoingOnline(long userId) {
        assertCanGoOnline(userId);
        RiderWorkday row = ensureToday(userId);
        row.setLastTickAt(Instant.now());
        workdayRepo.save(row);
        return snapshot(row, 0);
    }

    @Transactional
    public Map<String, Object> onGoingOffline(long userId) {
        RiderWorkday row = tick(userId, false);
        row.setLastTickAt(null);
        workdayRepo.save(row);
        return snapshot(row, 0);
    }

    @Transactional
    public RiderWorkday tick(long userId, boolean forceIfOver) {
        RiderWorkday row = ensureToday(userId);
        Instant now = Instant.now();
        if (row.getLastTickAt() != null) {
            int delta = (int) Math.min(Duration.between(row.getLastTickAt(), now).getSeconds(), 300);
            row.setWorkedSeconds(RiderWorkPolicy.accumulate(row.getWorkedSeconds(), delta, maxSeconds()));
        }
        row.setLastTickAt(now);
        if (forceIfOver && RiderWorkPolicy.shouldForceOffline(row.getWorkedSeconds(), maxSeconds())) {
            row.setForcedOffline(true);
            riderRepo.findById(userId).ifPresent(rider -> {
                rider.setOnlineStatus("OFFLINE");
                rider.setAutoReport(false);
                rider.setUpdateTime(now);
                rider.setVersion(rider.getVersion() + 1);
                riderRepo.save(rider);
            });
        }
        return workdayRepo.save(row);
    }

    @Scheduled(fixedDelay = 30_000)
    public void heartbeatOnlineRiders() {
        try {
            for (Rider rider : riderRepo.findAll()) {
                if (!"ONLINE".equals(rider.getOnlineStatus())) {
                    continue;
                }
                RiderWorkday row = tick(rider.getUserId(), true);
                if (row.getForcedOffline()) {
                    log.info("骑手 {} 达到当日工时上限，已强制下线", rider.getUserId());
                }
            }
        } catch (Exception ex) {
            log.warn("工时心跳失败: {}", ex.getMessage());
        }
    }

    public Map<String, Object> snapshot(RiderWorkday row, int completedToday) {
        int max = maxSeconds();
        int worked = row.getWorkedSeconds();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workDate", row.getWorkDate().toString());
        body.put("workedSecondsToday", worked);
        body.put("maxWorkSeconds", max);
        body.put("remainingSeconds", RiderWorkPolicy.remainingSeconds(worked, max));
        body.put("forcedOffline", row.getForcedOffline());
        body.put("canGoOnline", RiderWorkPolicy.canGoOnline(row.getForcedOffline(), row.getWorkDate(), today()));
        body.put("dailySubsidyCents", dailySubsidyCents);
        body.put("completeSubsidyCents", completeSubsidyCents);
        body.put("subsidyCents", RiderWorkPolicy.subsidyCents(dailySubsidyCents, completeSubsidyCents, completedToday));
        body.put("subsidyNote", "日补贴 " + dailySubsidyCents + " 分 + 完成单补贴 " + completeSubsidyCents + " 分/单");
        return body;
    }

    public Map<String, Object> workStats(long userId, int completedToday) {
        return snapshot(ensureToday(userId), completedToday);
    }

    public Map<String, Integer> secondsByDay(long userId, LocalDate from, LocalDate to) {
        Map<String, Integer> out = new LinkedHashMap<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            out.put(cursor.toString(), 0);
            cursor = cursor.plusDays(1);
        }
        for (RiderWorkday row : workdayRepo.findByUserIdAndWorkDateBetween(userId, from, to)) {
            if (row.getWorkDate() == null) {
                continue;
            }
            out.put(row.getWorkDate().toString(), row.getWorkedSeconds());
        }
        return out;
    }
}
