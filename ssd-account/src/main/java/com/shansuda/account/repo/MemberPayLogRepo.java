package com.shansuda.account.repo;

import com.shansuda.account.domain.MemberPayLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberPayLogRepo extends JpaRepository<MemberPayLog, Long> {
    List<MemberPayLog> findByUserIdOrderByCreatedAtDesc(long userId);
}
