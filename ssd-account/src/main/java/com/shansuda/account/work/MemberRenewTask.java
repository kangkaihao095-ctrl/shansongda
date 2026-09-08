package com.shansuda.account.work;

import com.shansuda.account.service.MemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 连续包月到期自动续 31 天；假支付不真扣款，但状态续上并写流水。 */
@Component
public class MemberRenewTask {

    private static final Logger log = LoggerFactory.getLogger(MemberRenewTask.class);

    private final MemberService memberService;

    public MemberRenewTask(MemberService memberService) {
        this.memberService = memberService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void tick() {
        try {
            memberService.renewDue();
        } catch (Exception ex) {
            log.warn("连续包月续期失败: {}", ex.getMessage());
        }
    }
}
