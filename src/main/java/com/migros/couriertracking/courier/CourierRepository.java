package com.migros.couriertracking.courier;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CourierRepository extends JpaRepository<Courier, String> {

    /**
     * Row lock that serialises all updates of one courier (across threads and instances) while
     * leaving different couriers fully parallel.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Courier c where c.id = :id")
    Optional<Courier> findByIdForUpdate(@Param("id") String id);
}
