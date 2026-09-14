package com.shansuda.account.repo;

import com.shansuda.account.domain.UserCart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCartRepo extends JpaRepository<UserCart, Long> {
}
