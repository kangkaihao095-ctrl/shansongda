package com.shansuda.activity;

import com.shansuda.activity.service.CriticalSection;
import com.shansuda.common.api.BizException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalSectionTest {

    private static final Logger log = LoggerFactory.getLogger(CriticalSectionTest.class);

    @Test
    void autoModeWithoutRedissonRefusesCriticalSection() {
        assertTrue(CriticalSection.lockRequired("auto"));
        assertTrue(CriticalSection.lockRequired("live"));
        assertFalse(CriticalSection.lockRequired("dry-run"));
        assertThrows(BizException.class, () -> CriticalSection.requireAvailable(null, "auto", log));
        assertThrows(BizException.class, () -> CriticalSection.requireAvailable(null, "live", log));
        CriticalSection.requireAvailable(null, "dry-run", log);
    }
}
