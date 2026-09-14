package com.shansuda.account.repo;

import com.shansuda.account.domain.Rider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RiderRepo extends JpaRepository<Rider, Long> {
    List<Rider> findByOnlineStatusAndAcceptStatus(String onlineStatus, String acceptStatus);

    @Query("""
            SELECT r FROM Rider r
            WHERE r.lat BETWEEN :minLat AND :maxLat
              AND r.lon BETWEEN :minLon AND :maxLon
              AND r.onlineStatus = :onlineStatus
              AND r.acceptStatus = :acceptStatus
            """)
    List<Rider> findInBoundingBox(@Param("minLat") double minLat,
                                  @Param("maxLat") double maxLat,
                                  @Param("minLon") double minLon,
                                  @Param("maxLon") double maxLon,
                                  @Param("onlineStatus") String onlineStatus,
                                  @Param("acceptStatus") String acceptStatus);

    @Query("""
            SELECT r FROM Rider r
            WHERE r.lat BETWEEN :minLat AND :maxLat
              AND r.lon BETWEEN :minLon AND :maxLon
            """)
    List<Rider> findInGeoBox(@Param("minLat") double minLat,
                             @Param("maxLat") double maxLat,
                             @Param("minLon") double minLon,
                             @Param("maxLon") double maxLon);
}
