package com.shansuda.account.repo;

import com.shansuda.account.domain.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAddressRepo extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserId(Long userId);
    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);
}
