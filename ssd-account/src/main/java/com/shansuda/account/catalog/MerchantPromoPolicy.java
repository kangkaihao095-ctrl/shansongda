package com.shansuda.account.catalog;

/** 店铺满减：满 X 减 Y，在平台券之前叠加。 */
public final class MerchantPromoPolicy {

    private MerchantPromoPolicy() {
    }

    public static int offCents(int goodsCents, Integer minSpendCents, Integer offCents) {
        int min = minSpendCents == null ? 0 : Math.max(0, minSpendCents);
        int off = offCents == null ? 0 : Math.max(0, offCents);
        if (off <= 0 || goodsCents < min) {
            return 0;
        }
        return Math.min(off, Math.max(0, goodsCents));
    }
}
