package com.shansuda.account;

import com.shansuda.common.api.BizException;
import com.shansuda.common.catalog.TipGifts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TipGiftsTest {

    @Test
    void mapsFourFixedGifts() {
        assertEquals(4, TipGifts.ALL.size());
        assertEquals(200, TipGifts.requireCode("WATER").cents());
        assertEquals(500, TipGifts.requireCode("milktea").cents());
        assertEquals(1000, TipGifts.requireCode("GIFT").cents());
        assertEquals(2000, TipGifts.requireCode("CHICKEN").cents());
        assertEquals("请喝奶茶", TipGifts.requireCode("MILKTEA").label());
    }

    @Test
    void rejectsArbitraryCentsAndUnknownCode() {
        assertThrows(BizException.class, () -> TipGifts.requireCode("CASH"));
        assertThrows(BizException.class, () -> TipGifts.requireCentsOrCode(null, 300));
        assertThrows(BizException.class, () -> TipGifts.requireCentsOrCode("", null));
        assertEquals("WATER", TipGifts.requireCentsOrCode(null, 200).code());
    }
}
