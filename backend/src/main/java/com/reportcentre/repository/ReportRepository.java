package com.reportcentre.repository;

import com.reportcentre.entity.Report;
import com.reportcentre.entity.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository extends JpaRepository<Report, String>, JpaSpecificationExecutor<Report> {
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);
    Page<Report> findByBenchmarkTag(String benchmarkTag, Pageable pageable);
    long countByStatus(ReportStatus status);
}
