package com.shansuda.account.work;

import com.shansuda.common.api.ErrorCodes;

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

    /** 强制下线或当日工时已满则不可派单/抢单。 */
    public static boolean canAccept(boolean forcedOffline, int workedSeconds, int maxSeconds) {
        if (forcedOffline) {
            return false;
        }
        return workedSeconds < maxSeconds;
    }

    public static boolean isOnline(String onlineStatus) {
        return "ONLINE".equals(onlineStatus);
    }

    /** 必须在线且未触达工时上限才可抢新单；位置上报开关不参与判定。 */
    public static boolean canGrab(String onlineStatus, boolean forcedOffline, int workedSeconds, int maxSeconds) {
        return grabDenyCode(onlineStatus, forcedOffline, workedSeconds, maxSeconds) == null;
    }

    public static String grabDenyCode(String onlineStatus, boolean forcedOffline, int workedSeconds, int maxSeconds) {
        if (!canAccept(forcedOffline, workedSeconds, maxSeconds)) {
            return ErrorCodes.WORK_LIMIT;
        }
        if (!isOnline(onlineStatus)) {
            return ErrorCodes.NEED_ONLINE;
        }
        return null;
    }

    public static String grabDenyReason(String denyCode) {
        if (ErrorCodes.NEED_ONLINE.equals(denyCode)) {
            return "请先上线";
        }
        if (ErrorCodes.WORK_LIMIT.equals(denyCode)) {
            return "今日工时已满或已强制下线，不可接单";
        }
        return null;
    }

    public static int remainingSeconds(int workedSeconds, int maxSeconds) {
        return Math.max(0, maxSeconds - Math.max(0, workedSeconds));
    }

    public static int subsidyCents(int dailySubsidyCents, int completeSubsidyCents, int completedToday) {
        return Math.max(0, dailySubsidyCents) + Math.max(0, completeSubsidyCents) * Math.max(0, completedToday);
    }
}
