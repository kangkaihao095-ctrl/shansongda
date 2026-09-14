package com.shansuda.order.strategy;

import java.time.Instant;

/**
 * 闪会员运费减免：Lv3+ 减 2 元，年卡再减 1 元，下限 0。
 * 包装在距离/高峰策略之上，快照名带 {@code +MemberFreight}。
 */
public final class MemberFreight implements FreightStrategy {

    public static final int LEVEL_OFF_CENTS = 200;
    public static final int YEAR_OFF_CENTS = 100;
    public static final int LEVEL_THRESHOLD = 3;

    private final FreightStrategy inner;
    private final Context member;
    private final int discountCents;

    private MemberFreight(FreightStrategy inner, Context member, int discountCents) {
        this.inner = inner;
        this.member = member;
        this.discountCents = discountCents;
    }

    public static FreightStrategy wrap(FreightStrategy inner, Context member) {
        int off = discountCents(member);
        if (inner == null) {
            return inner;
        }
        if (off <= 0) {
            return inner;
        }
        return new MemberFreight(inner, member == null ? Context.none() : member, off);
    }

    public static int discountCents(Context member) {
        if (member == null || !member.active()) {
            return 0;
        }
        int off = 0;
        if (member.level() >= LEVEL_THRESHOLD) {
            off += LEVEL_OFF_CENTS;
        }
        if (member.yearMember()) {
            off += YEAR_OFF_CENTS;
        }
        return off;
    }

    @Override
    public String name() {
        return inner.name() + "+MemberFreight";
    }

    @Override
    public int quote(double merchantLat, double merchantLon, double userLat, double userLon, Instant now) {
        int base = inner.quote(merchantLat, merchantLon, userLat, userLon, now);
        return Math.max(0, base - discountCents);
    }

    public int discountCents() {
        return discountCents;
    }

    public Context member() {
        return member;
    }

    public record Context(int level, boolean yearMember, boolean active) {
        public static Context none() {
            return new Context(0, false, false);
        }

        public static Context of(Integer level, Boolean yearMember, Boolean active) {
            return new Context(level == null ? 0 : level,
                    Boolean.TRUE.equals(yearMember),
                    Boolean.TRUE.equals(active));
        }
    }
}
