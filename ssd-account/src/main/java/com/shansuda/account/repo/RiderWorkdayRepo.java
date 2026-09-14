package com.shansuda.account.repo;

import com.shansuda.account.domain.RiderWorkday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RiderWorkdayRepo extends JpaRepository<RiderWorkday, RiderWorkday.Key> {
    Optional<RiderWorkday> findByUserIdAndWorkDate(Long userId, LocalDate workDate);

    List<RiderWorkday> findByUserIdAndWorkDateBetween(Long userId, LocalDate from, LocalDate to);
}
