package com.shansuda.account;

import com.shansuda.account.catalog.MerchantPromoPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MerchantPromoPolicyTest {

    @Test
    void fullReductionAppliesAfterThreshold() {
        assertEquals(0, MerchantPromoPolicy.offCents(1999, 2000, 200));
        assertEquals(200, MerchantPromoPolicy.offCents(2000, 2000, 200));
        assertEquals(1500, MerchantPromoPolicy.offCents(1500, 0, 2000));
    }
}
