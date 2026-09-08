package com.shansuda.activity.repo;

import com.shansuda.activity.domain.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepo extends JpaRepository<Activity, Long> {
}
