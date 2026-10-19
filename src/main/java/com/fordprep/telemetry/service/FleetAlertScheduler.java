package com.fordprep.telemetry.service;

import com.fordprep.telemetry.model.VehicleTelemetry;
import com.fordprep.telemetry.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Periodically logs vehicles that need fleet-operator attention. */
@Component
public class FleetAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(FleetAlertScheduler.class);

    private static final double LOW_FUEL_THRESHOLD = 15.0;

    private final TelemetryRepository repository;

    public FleetAlertScheduler(TelemetryRepository repository) {
        this.repository = repository;
    }

    /** Runs 10s after startup, then every 60s. Errors are caught so the schedule never dies. */
    @Scheduled(fixedRate = 60000, initialDelay = 10000)
    public void checkLowFuel() {
        try {
            List<VehicleTelemetry> alerts = repository
                    .findByEngineLightOnTrueOrFuelLevelLessThanOrderByVinAsc(LOW_FUEL_THRESHOLD);
            if (alerts.isEmpty()) {
                log.info("Fleet check: no vehicles need attention");
                return;
            }
            for (VehicleTelemetry v : alerts) {
                StringBuilder reasons = new StringBuilder();
                if (v.getFuelLevel() < LOW_FUEL_THRESHOLD) {
                    reasons.append("fuel at ").append(v.getFuelLevel()).append('%');
                }
                if (v.isEngineLightOn()) {
                    if (!reasons.isEmpty()) reasons.append("; ");
                    reasons.append("engine light on");
                }
                log.warn("FLEET ALERT: {} (VIN {}): {}", v.getModelName(), v.getVin(), reasons);
            }
        } catch (Exception ex) {
            log.error("Fleet low-fuel check failed unexpectedly", ex);
        }
    }
}
