package com.fordprep.telemetry.repository;

import com.fordprep.telemetry.model.MonitoredService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    /** Derived query: Spring generates the SQL from the method name. */
    List<MonitoredService> findByStatus(String status);
}