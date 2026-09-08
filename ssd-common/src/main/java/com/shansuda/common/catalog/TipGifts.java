package com.shansuda.common.catalog;

import com.shansuda.common.api.BizException;

import java.util.List;
import java.util.Locale;

/**
 * 打赏固定礼物档，禁止任意金额。
 * WATER ¥2 / MILKTEA ¥5 / GIFT ¥10 / CHICKEN ¥20。
 */
public final class TipGifts {

    public record Gift(String code, int cents, String label, String icon) {
    }

    public static final Gift WATER = new Gift("WATER", 200, "送瓶水", "water");
    public static final Gift MILKTEA = new Gift("MILKTEA", 500, "请喝奶茶", "milktea");
    public static final Gift GIFT = new Gift("GIFT", 1000, "送份小礼物", "gift");
    public static final Gift CHICKEN = new Gift("CHICKEN", 2000, "加个鸡腿", "chicken");

    public static final List<Gift> ALL = List.of(WATER, MILKTEA, GIFT, CHICKEN);

    private TipGifts() {
    }

    public static Gift requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("BAD_TIP", "请选择打赏礼物");
        }
        String key = code.trim().toUpperCase(Locale.ROOT);
        return ALL.stream()
                .filter(g -> g.code().equals(key))
                .findFirst()
                .orElseThrow(() -> BizException.badRequest("BAD_TIP", "请选择打赏礼物"));
    }

    public static Gift fromCents(int cents) {
        return ALL.stream().filter(g -> g.cents() == cents).findFirst().orElse(null);
    }

    public static Gift requireCentsOrCode(String giftCode, Integer cents) {
        if (giftCode != null && !giftCode.isBlank()) {
            return requireCode(giftCode);
        }
        if (cents == null) {
            throw BizException.badRequest("BAD_TIP", "请选择打赏礼物");
        }
        Gift gift = fromCents(cents);
        if (gift == null) {
            throw BizException.badRequest("BAD_TIP", "打赏须为固定礼物档，不能自定义金额");
        }
        return gift;
    }
}
