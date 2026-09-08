package com.shansuda.account.repo;

import com.shansuda.account.domain.MemberSub;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberSubRepo extends JpaRepository<MemberSub, Long> {
    List<MemberSub> findByAutoRenewTrue();
}
