package com.fordprep.telemetry.controller;

import com.fordprep.telemetry.model.VehicleTelemetry;
import com.fordprep.telemetry.repository.TelemetryRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryRestController {

    private static final Logger log = LoggerFactory.getLogger(TelemetryRestController.class);
    private final TelemetryRepository repository;

    public TelemetryRestController(TelemetryRepository repository) {
        this.repository = repository;
    }

    /** Creates a vehicle or updates its latest telemetry when its VIN already exists. */
    @PostMapping
    public ResponseEntity<VehicleTelemetry> receive(@Valid @RequestBody VehicleTelemetryRequest payload) {
        String vin = payload.vin().trim().toUpperCase(Locale.ROOT);
        VehicleTelemetry vehicle = repository.findByVin(vin).orElseGet(VehicleTelemetry::new);
        boolean isNew = vehicle.getId() == null;

        // The entity is built from allowed telemetry fields; client IDs are never accepted.
        vehicle.setVin(vin);
        vehicle.setModelName(payload.modelName().trim());
        vehicle.setLatitude(payload.latitude());
        vehicle.setLongitude(payload.longitude());
        vehicle.setFuelLevel(payload.fuelLevel());
        vehicle.setEngineLightOn(payload.engineLightOn());

        VehicleTelemetry saved = repository.save(vehicle);
        log.info("{} telemetry for VIN {}", isNew ? "Created" : "Updated", saved.getVin());
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK).body(saved);
    }

    /** Lists the latest record for every vehicle, ordered by VIN. */
    @GetMapping
    public List<VehicleTelemetry> listAll() {
        return repository.findAllByOrderByVinAsc();
    }

    /** Looks up one vehicle; VIN lookup is case-insensitive. */
    @GetMapping("/{vin}")
    public ResponseEntity<VehicleTelemetry> findByVin(@PathVariable String vin) {
        return repository.findByVin(vin.trim().toUpperCase(Locale.ROOT))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Vehicles with either an engine warning or fuel below the supplied threshold. */
    @GetMapping("/alerts")
    public List<VehicleTelemetry> alerts(
            @RequestParam(defaultValue = "15") double lowFuelThreshold) {
        if (!Double.isFinite(lowFuelThreshold) || lowFuelThreshold < 0 || lowFuelThreshold > 100) {
            throw new IllegalArgumentException("lowFuelThreshold must be between 0 and 100");
        }
        return repository.findByEngineLightOnTrueOrFuelLevelLessThanOrderByVinAsc(lowFuelThreshold);
    }

    /** Removes a vehicle from the tracked fleet. */
    @DeleteMapping("/{vin}")
    public ResponseEntity<Void> deleteByVin(@PathVariable String vin) {
        return repository.findByVin(vin.trim().toUpperCase(Locale.ROOT))
                .map(vehicle -> {
                    repository.delete(vehicle);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
