package com.shansuda.account.work;

import com.shansuda.account.client.OrderInternalClient;
import com.shansuda.account.domain.OrderTip;
import com.shansuda.account.repo.OrderTipRepo;
import com.shansuda.common.api.BizException;
import com.shansuda.common.auth.AuthHolder;
import com.shansuda.common.auth.AuthUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 骑手近 N 日工时 + 完成单运费/打赏曲线。 */
@Service
public class RiderChartService {

    private static final ZoneId ZONE = ZoneId.of(RiderWorkPolicy.ZONE);

    private final RiderWorkService riderWorkService;
    private final OrderInternalClient orderInternalClient;
    private final OrderTipRepo tipRepo;

    public RiderChartService(RiderWorkService riderWorkService, OrderInternalClient orderInternalClient,
                             OrderTipRepo tipRepo) {
        this.riderWorkService = riderWorkService;
        this.orderInternalClient = orderInternalClient;
        this.tipRepo = tipRepo;
    }

    public Map<String, Object> charts(Integer days) {
        AuthUser auth = AuthHolder.require();
        if (!auth.isRider()) {
            throw BizException.forbidden("仅骑手可查看工时收入曲线");
        }
        int window = days == null ? 7 : Math.min(Math.max(days, 1), 14);
        return charts(auth.userId(), window);
    }

    public Map<String, Object> charts(long riderId, int days) {
        int window = Math.min(Math.max(days, 1), 14);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = today.minusDays(window - 1L);
        Map<String, Integer> work = riderWorkService.secondsByDay(riderId, start, today);
        Map<String, Long> freight = new LinkedHashMap<>();
        Map<String, Integer> completed = new LinkedHashMap<>();
        Map<String, Object> order = orderInternalClient.riderDailyIncome(riderId, window);
        Object seriesRaw = order.get("series");
        if (seriesRaw instanceof List<?> list) {
            for (Object row : list) {
                if (!(row instanceof Map<?, ?> map)) {
                    continue;
                }
                String date = String.valueOf(map.get("date"));
                freight.put(date, asLong(map.get("freightCents")));
                completed.put(date, (int) asLong(map.get("completedCount")));
            }
        }
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant();
        Instant chartStart = start.atStartOfDay(ZONE).toInstant();
        Instant tipFrom = monthStart.isBefore(chartStart) ? monthStart : chartStart;
        Map<String, Long> tips = tipsByDay(riderId, tipFrom);
        List<Map<String, Object>> income = new ArrayList<>();
        List<Map<String, Object>> workSeries = new ArrayList<>();
        long windowFreight = 0;
        long windowTip = 0;
        int windowWorked = 0;
        for (int i = 0; i < window; i++) {
            String date = start.plusDays(i).toString();
            long f = freight.getOrDefault(date, 0L);
            long t = tips.getOrDefault(date, 0L);
            int seconds = work.getOrDefault(date, 0);
            windowFreight += f;
            windowTip += t;
            windowWorked += seconds;
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("date", date);
            in.put("freightCents", f);
            in.put("tipCents", t);
            in.put("totalCents", f + t);
            in.put("completedCount", completed.getOrDefault(date, 0));
            income.add(in);
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("date", date);
            w.put("workedSeconds", seconds);
            workSeries.add(w);
        }
        Map<String, Object> todayWork = riderWorkService.workStats(riderId, 0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("days", window);
        body.put("grain", "day");
        body.put("income", income);
        body.put("work", workSeries);
        body.put("windowFreightCents", windowFreight);
        body.put("windowTipCents", windowTip);
        body.put("windowWorkedSeconds", windowWorked);
        body.put("monthFreightCents", asLong(order.get("monthFreightCents")));
        body.put("monthTipCents", monthTips(tips, today));
        body.put("dailySubsidyCents", todayWork.get("dailySubsidyCents"));
        body.put("completeSubsidyCents", todayWork.get("completeSubsidyCents"));
        body.put("workedSecondsToday", todayWork.get("workedSecondsToday"));
        body.put("maxWorkSeconds", todayWork.get("maxWorkSeconds"));
        body.put("remainingSeconds", todayWork.get("remainingSeconds"));
        body.put("incomeNote", "近七日收入=完成单运费 + 打赏；日补贴与完成单补贴另列，不并入柱图");
        return body;
    }

    private Map<String, Long> tipsByDay(long riderId, Instant from) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (OrderTip tip : tipRepo.findByRiderIdAndCreatedAtGreaterThanEqual(riderId, from)) {
            if (tip.getCreatedAt() == null) {
                continue;
            }
            String date = LocalDate.ofInstant(tip.getCreatedAt(), ZONE).toString();
            out.merge(date, (long) (tip.getCents() == null ? 0 : tip.getCents()), Long::sum);
        }
        return out;
    }

    private static long monthTips(Map<String, Long> tips, LocalDate today) {
        String prefix = today.getYear() + "-" + (today.getMonthValue() < 10 ? "0" : "") + today.getMonthValue();
        long sum = 0;
        for (Map.Entry<String, Long> e : tips.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith(prefix)) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }
}
