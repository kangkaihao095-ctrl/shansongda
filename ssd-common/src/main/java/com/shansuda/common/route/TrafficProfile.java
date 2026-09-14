package com.shansuda.common.route;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 黄浦时段路况画像：道路等级 + 本地时段规律写入 congestion/cost。
 * 可复现（道路 id + 日期小时 hash），不是 Random，也不是高德历史接口。
 */
public final class TrafficProfile {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final String AMAP = "AMAP";
    public static final String LAST_SUCCESS = "LAST_SUCCESS";
    public static final String PROFILE = "PROFILE";
    public static final String DEFAULT = "DEFAULT";

    public enum Period {
        MORNING_PEAK("早高峰"),
        EVENING_PEAK("晚高峰"),
        MIDDAY("午间缓行"),
        WEEKEND_DAY("周末景区"),
        OFF_PEAK("平峰");

        public final String label;

        Period(String label) {
            this.label = label;
        }
    }

    public record ApplyResult(List<Map<String, Object>> rows, Period period, int congestedEdges) {
    }

    public record Decision(String source, String periodLabel, List<Map<String, Object>> rows, boolean wrote) {
    }

    private TrafficProfile() {
    }

    public static Period period(Clock clock) {
        ZonedDateTime z = now(clock);
        int minuteOfDay = z.getHour() * 60 + z.getMinute();
        DayOfWeek dow = z.getDayOfWeek();
        boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        if (weekend) {
            if (minuteOfDay >= 10 * 60 && minuteOfDay < 21 * 60) {
                return Period.WEEKEND_DAY;
            }
            return Period.OFF_PEAK;
        }
        if (minuteOfDay >= 7 * 60 + 30 && minuteOfDay < 9 * 60 + 30) {
            return Period.MORNING_PEAK;
        }
        if (minuteOfDay >= 17 * 60 && minuteOfDay < 19 * 60 + 30) {
            return Period.EVENING_PEAK;
        }
        if (minuteOfDay >= 11 * 60 + 30 && minuteOfDay < 13 * 60 + 30) {
            return Period.MIDDAY;
        }
        return Period.OFF_PEAK;
    }

    public static ApplyResult apply(GridPathFinder graph, Clock clock) {
        return apply(graph, clock, true);
    }

    public static ApplyResult apply(GridPathFinder graph, Clock clock, boolean seedSupplement) {
        Period period = period(clock);
        ZonedDateTime z = now(clock);
        List<Map<String, Object>> rows = new ArrayList<>();
        int congested = 0;
        if (graph != null) {
            graph.adj().forEach((fromId, edges) -> {
                for (GridPathFinder.Edge edge : edges) {
                    double prev = edge.congestion;
                    double next = congestionOf(edge, z, period);
                    edge.setCongestion(next);
                    if (Math.abs(next - prev) < 0.03) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("roadId", edge.roadId);
                    row.put("congestion", next);
                    rows.add(row);
                }
            });
            if (seedSupplement && (period == Period.MORNING_PEAK || period == Period.EVENING_PEAK
                    || period == Period.WEEKEND_DAY)) {
                CongestionAggregator.boostNear(graph, CongestionAggregator.seedTraces(), 0.08, rows);
            }
            for (List<GridPathFinder.Edge> edges : graph.adj().values()) {
                for (GridPathFinder.Edge edge : edges) {
                    if (edge.congestion > 1.01) {
                        congested++;
                    }
                }
            }
        }
        return new ApplyResult(rows, period, congested);
    }

    /**
     * 高德成功只写高德；失败有上次则保留；否则画像。不要把 PROFILE 叠到 AMAP 上。
     */
    public static Decision refresh(GridPathFinder graph, AmapTrafficClient.TrafficSnapshot snapshot,
                                   Clock clock, boolean amapEverSucceeded) {
        if (snapshot != null && snapshot.ok()) {
            AmapTrafficClient.ApplyResult applied = AmapTrafficClient.apply(graph, snapshot.roads());
            return new Decision(AMAP, "", applied.rows(), true);
        }
        if (amapEverSucceeded) {
            return new Decision(LAST_SUCCESS, "", List.of(), false);
        }
        ApplyResult profile = apply(graph, clock);
        return new Decision(PROFILE, profile.period().label, profile.rows(), true);
    }

    public static String hint(String source, String periodLabel) {
        if (AMAP.equals(source)) {
            return "当前路况已计入 ETA（高德态势）";
        }
        if (LAST_SUCCESS.equals(source)) {
            return "当前路况已计入 ETA（沿用上次高德态势）";
        }
        if (PROFILE.equals(source)) {
            String label = periodLabel == null || periodLabel.isBlank() ? "时段规律" : periodLabel;
            return "当前按黄浦时段路况画像计入 ETA（" + label + "）";
        }
        return "路况暂未接入，按畅通路网算路";
    }

    static double congestionOf(GridPathFinder.Edge edge, Clock clock) {
        ZonedDateTime z = now(clock);
        return congestionOf(edge, z, period(clock));
    }

    static double congestionOf(GridPathFinder.Edge edge, ZonedDateTime z, Period period) {
        String highway = edge == null ? "" : edge.highway;
        String name = edge == null ? "" : edge.name;
        String roadId = edge == null ? "" : edge.roadId;
        double base = highwayBase(highway, period);
        base += nameBoost(name, period);
        base += weekdayTilt(z.getDayOfWeek(), period);
        base += directionTilt(roadId, period);
        base += stableNoise(roadId, z);
        base += minuteWobble(roadId, z);
        return clamp(base);
    }

    static double highwayBase(String highway, Period period) {
        String hw = highway == null ? "" : highway.trim().toLowerCase();
        return switch (hw) {
            case "motorway", "trunk" -> switch (period) {
                case MORNING_PEAK -> 1.78;
                case EVENING_PEAK -> 1.82;
                case MIDDAY -> 1.14;
                case WEEKEND_DAY -> 1.10;
                case OFF_PEAK -> 1.08;
            };
            case "primary" -> switch (period) {
                case MORNING_PEAK -> 1.68;
                case EVENING_PEAK -> 1.72;
                case MIDDAY -> 1.16;
                case WEEKEND_DAY -> 1.22;
                case OFF_PEAK -> 1.12;
            };
            case "secondary" -> switch (period) {
                case MORNING_PEAK -> 1.42;
                case EVENING_PEAK -> 1.46;
                case MIDDAY -> 1.34;
                case WEEKEND_DAY -> 1.24;
                case OFF_PEAK -> 1.06;
            };
            case "tertiary" -> switch (period) {
                case MORNING_PEAK -> 1.26;
                case EVENING_PEAK -> 1.28;
                case MIDDAY -> 1.18;
                case WEEKEND_DAY -> 1.14;
                case OFF_PEAK -> 1.04;
            };
            case "residential", "living_street", "pedestrian", "unclassified", "service" -> switch (period) {
                case MORNING_PEAK -> 1.12;
                case EVENING_PEAK -> 1.14;
                case MIDDAY -> 1.08;
                case WEEKEND_DAY -> 1.16;
                case OFF_PEAK -> 1.02;
            };
            default -> switch (period) {
                case MORNING_PEAK, EVENING_PEAK -> 1.05;
                case MIDDAY, WEEKEND_DAY -> 1.03;
                case OFF_PEAK -> 1.01;
            };
        };
    }

    static double nameBoost(String name, Period period) {
        if (!hotName(name)) {
            return 0;
        }
        boolean bundish = containsAny(name, "南京东路", "中山东一路", "外滩", "延安");
        return switch (period) {
            case MORNING_PEAK -> bundish ? 0.14 : 0.10;
            case EVENING_PEAK -> bundish ? 0.20 : 0.14;
            case WEEKEND_DAY -> bundish ? 0.28 : 0.16;
            case MIDDAY -> 0.06;
            case OFF_PEAK -> 0.04;
        };
    }

    static boolean hotName(String name) {
        return containsAny(name, "南京东路", "中山东一路", "人民大道", "延安", "西藏中路", "外滩", "淮海");
    }

    private static double weekdayTilt(DayOfWeek dow, Period period) {
        if (period != Period.EVENING_PEAK) {
            return 0;
        }
        if (dow == DayOfWeek.FRIDAY) {
            return 0.08;
        }
        if (dow == DayOfWeek.MONDAY) {
            return -0.04;
        }
        return 0;
    }

    /** a 进城、b 出城的轻微不对称。 */
    private static double directionTilt(String roadId, Period period) {
        boolean inbound = roadId != null && roadId.endsWith("a");
        boolean outbound = roadId != null && roadId.endsWith("b");
        if (period == Period.MORNING_PEAK) {
            if (inbound) {
                return 0.07;
            }
            if (outbound) {
                return -0.05;
            }
        }
        if (period == Period.EVENING_PEAK) {
            if (inbound) {
                return -0.05;
            }
            if (outbound) {
                return 0.07;
            }
        }
        return 0;
    }

    static double stableNoise(String roadId, ZonedDateTime z) {
        String key = (roadId == null ? "" : roadId) + "|" + z.getYear() + "-" + z.getMonthValue()
                + "-" + z.getDayOfMonth() + "T" + z.getHour();
        int h = 0;
        for (int i = 0; i < key.length(); i++) {
            h = 31 * h + key.charAt(i);
        }
        return ((h & 0xffff) / 65535.0 - 0.5) * 0.10;
    }

    static double minuteWobble(String roadId, ZonedDateTime z) {
        int h = roadId == null ? 0 : roadId.hashCode();
        return 0.02 * Math.sin(z.getMinute() / 60.0 * 2 * Math.PI + (h & 15));
    }

    private static double clamp(double value) {
        if (value < 1.0) {
            return 1.0;
        }
        if (value > 2.4) {
            return 2.4;
        }
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static ZonedDateTime now(Clock clock) {
        Clock c = clock == null ? Clock.system(ZONE) : clock;
        return ZonedDateTime.now(c).withZoneSameInstant(ZONE);
    }

    private static boolean containsAny(String name, String... keys) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (String key : keys) {
            if (name.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
