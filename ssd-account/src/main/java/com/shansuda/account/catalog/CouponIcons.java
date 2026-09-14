package com.shansuda.account.catalog;

import com.shansuda.account.domain.Coupon;
import com.shansuda.account.domain.CouponTemplate;

/**
 * 券图标：满减 / 无门槛 / 会员 / 新客 / 品类 / 折扣。
 * 列表、弹窗、首页券面都走同一套 key，前端映射 {@code /images/coupons/{icon}.svg}。
 */
public final class CouponIcons {

    public static final String MINUS = "minus";
    public static final String FREE = "free";
    public static final String MEMBER = "member";
    public static final String NEWCOMER = "newcomer";
    public static final String CATEGORY = "category";
    public static final String PERCENT = "percent";

    public static final long NEWCOMER_SECONDS = 7L * 86400;
    public static final long LOGIN_SECONDS = 24L * 3600;
    public static final long REGULAR_SECONDS = 3L * 86400;

    private CouponIcons() {
    }

    public static String resolve(CouponTemplate tpl) {
        if (tpl == null) {
            return MINUS;
        }
        if (tpl.getIcon() != null && !tpl.getIcon().isBlank()) {
            return tpl.getIcon().trim();
        }
        return resolve(tpl.getScene(), tpl.getType(), tpl.getMinSpendCents(), tpl.getCode(),
                Boolean.TRUE.equals(tpl.getMemberOnly()));
    }

    public static String resolve(Coupon coupon) {
        if (coupon == null) {
            return MINUS;
        }
        if (coupon.getIcon() != null && !coupon.getIcon().isBlank()) {
            return coupon.getIcon().trim();
        }
        String code = coupon.getCode() == null ? coupon.getName() : coupon.getCode();
        String scene = sceneOf(code);
        return resolve(scene, coupon.getType(), coupon.getMinSpendCents(), code,
                Boolean.TRUE.equals(coupon.getMemberOnly()));
    }

    public static String resolve(String scene, String type, Integer minSpend, String code, boolean memberOnly) {
        if (memberOnly || "MEMBER".equals(scene)) {
            return MEMBER;
        }
        if ("NEWCOMER".equals(scene) || (code != null && code.contains("NEWCOMER"))) {
            return NEWCOMER;
        }
        if ("PERCENT".equals(type)) {
            return PERCENT;
        }
        if ("FREIGHT".equals(type)) {
            return FREE;
        }
        if (isCategory(code)) {
            return CATEGORY;
        }
        if (minSpend == null || minSpend <= 0) {
            return FREE;
        }
        return MINUS;
    }

    public static String url(String icon) {
        String key = icon == null || icon.isBlank() ? MINUS : icon.trim();
        return "/images/coupons/" + key + ".svg";
    }

    public static long validSeconds(CouponTemplate tpl) {
        if (tpl != null && tpl.getValidSeconds() != null && tpl.getValidSeconds() > 0) {
            return tpl.getValidSeconds();
        }
        return validSeconds(tpl == null ? null : tpl.getScene());
    }

    public static long validSeconds(String scene) {
        if ("NEWCOMER".equals(scene)) {
            return NEWCOMER_SECONDS;
        }
        if ("LOGIN".equals(scene)) {
            return LOGIN_SECONDS;
        }
        return REGULAR_SECONDS;
    }

    public static String sceneOf(String code) {
        if (code == null) {
            return null;
        }
        if (code.startsWith("NEWCOMER")) {
            return "NEWCOMER";
        }
        if (code.startsWith("LOGIN")) {
            return "LOGIN";
        }
        if (code.startsWith("HOME")) {
            return "HOME";
        }
        if (code.startsWith("MEMBER")) {
            return "MEMBER";
        }
        if (code.startsWith("LAPSED")) {
            return "LAPSED";
        }
        if (code.startsWith("FREQ")) {
            return "FREQUENT";
        }
        if (code.startsWith("RETURN")) {
            return "RETURN";
        }
        return null;
    }

    private static boolean isCategory(String code) {
        if (code == null) {
            return false;
        }
        String u = code.toUpperCase();
        return u.contains("FOOD") || u.contains("FRESH") || u.contains("TEA")
                || u.contains("PHARMA") || u.contains("FLOWER") || u.contains("MARKET")
                || u.contains("DESSERT") || u.contains("ERRAND");
    }
}
