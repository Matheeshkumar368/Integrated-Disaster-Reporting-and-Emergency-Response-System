package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.disaster.management.entity.CitizenReport;

public interface CitizenReportRepository extends JpaRepository<CitizenReport, Long> {
    List<CitizenReport> findByStatusOrderByTimestampDesc(String status);
    List<CitizenReport> findAllByOrderByTimestampDesc();
}
