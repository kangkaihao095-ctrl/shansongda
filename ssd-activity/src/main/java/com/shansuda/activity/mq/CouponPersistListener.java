package com.shansuda.activity.mq;

import com.shansuda.activity.client.AccountClient;
import com.shansuda.activity.domain.CouponGrant;
import com.shansuda.activity.repo.CouponGrantRepo;
import com.shansuda.common.mq.CouponSuccessMessage;
import com.shansuda.common.mq.MqNames;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Component
public class CouponPersistListener {

    private final CouponGrantRepo couponGrantRepo;
    private final ObjectProvider<AccountClient> accountClient;

    public CouponPersistListener(CouponGrantRepo couponGrantRepo, ObjectProvider<AccountClient> accountClient) {
        this.couponGrantRepo = couponGrantRepo;
        this.accountClient = accountClient;
    }

    @RabbitListener(queues = MqNames.Q_COUPON)
    @Transactional
    public void onCoupon(CouponSuccessMessage msg) {
        if (!couponGrantRepo.existsByActivityIdAndUserIdAndScene(msg.activityId(), msg.userId(), msg.scene())) {
            CouponGrant grant = new CouponGrant();
            grant.setActivityId(msg.activityId());
            grant.setUserId(msg.userId());
            grant.setScene(msg.scene());
            grant.setCouponCode(msg.couponCode());
            grant.setCreatedAt(Instant.now());
            couponGrantRepo.save(grant);
        }
        AccountClient account = accountClient.getIfAvailable();
        if (account != null) {
            account.grantCoupon(Map.of(
                    "userId", msg.userId(),
                    "activityId", msg.activityId(),
                    "couponCode", msg.couponCode() == null ? "" : msg.couponCode()
            ));
        }
    }
}
