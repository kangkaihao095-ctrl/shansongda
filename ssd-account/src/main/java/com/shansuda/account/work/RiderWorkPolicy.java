package com.shansuda.account.work;

import java.time.LocalDate;

/** 骑手当日工时与强制下线规则，纯领域、不依赖 Spring / Docker。 */
public final class RiderWorkPolicy {

    public static final int DEFAULT_MAX_HOURS = 8;
    public static final String ZONE = "Asia/Shanghai";

    private RiderWorkPolicy() {
    }

    public static int maxSeconds(int maxHours) {
        int hours = maxHours > 0 ? maxHours : DEFAULT_MAX_HOURS;
        return hours * 3600;
    }

    public static int accumulate(int workedSeconds, int deltaSeconds, int maxSeconds) {
        int next = Math.max(0, workedSeconds) + Math.max(0, deltaSeconds);
        return Math.min(next, maxSeconds);
    }

    public static boolean shouldForceOffline(int workedSeconds, int maxSeconds) {
        return workedSeconds >= maxSeconds;
    }

    public static boolean canGoOnline(boolean forcedOffline, LocalDate workDate, LocalDate today) {
        if (workDate == null || today == null || !workDate.equals(today)) {
            return true;
        }
        return !forcedOffline;
    }

    public static int remainingSeconds(int workedSeconds, int maxSeconds) {
        return Math.max(0, maxSeconds - Math.max(0, workedSeconds));
    }

    public static int subsidyCents(int dailySubsidyCents, int completeSubsidyCents, int completedToday) {
        return Math.max(0, dailySubsidyCents) + Math.max(0, completeSubsidyCents) * Math.max(0, completedToday);
    }
}
