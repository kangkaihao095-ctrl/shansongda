package com.shansuda.account.repo;

import com.shansuda.account.domain.Rider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiderRepo extends JpaRepository<Rider, Long> {
    List<Rider> findByOnlineStatusAndAcceptStatus(String onlineStatus, String acceptStatus);
}
