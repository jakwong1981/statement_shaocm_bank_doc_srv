package com.reportcentre.repository;

import com.reportcentre.entity.SystemErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SystemErrorLogRepository extends JpaRepository<SystemErrorLog, Long> {

    @Query("SELECT e FROM SystemErrorLog e WHERE " +
           "(:from IS NULL OR e.createdAt >= :from) AND " +
           "(:to IS NULL OR e.createdAt <= :to) " +
           "ORDER BY e.createdAt DESC")
    Page<SystemErrorLog> findByTimeRange(@Param("from") Instant from,
                                         @Param("to") Instant to,
                                         Pageable pageable);

    Page<SystemErrorLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
