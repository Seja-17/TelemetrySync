package com.fordprep.telemetry.repository;

import com.fordprep.telemetry.model.VehicleTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts a real (in-memory H2) database with only the JPA layer,
 * so we can check that our finder methods run the right queries.
 */
@DataJpaTest
class TelemetryRepositoryTest {

    @Autowired
    private TelemetryRepository repository;

    private VehicleTelemetry vehicle(String vin, String model, double fuel, boolean engineLight) {
        return new VehicleTelemetry(null, vin, model, 42.0, -83.0, fuel, engineLight);
    }

    @Test
    void findByEngineLightOnTrue_returnsOnlyWarningVehicles() {
        repository.save(vehicle("1FTFW1E50PFA00001", "F-150", 70, false));
        repository.save(vehicle("1FMSK8DH2PGA00003", "Explorer", 55, true));

        List<VehicleTelemetry> result = repository.findByEngineLightOnTrue();

        assertEquals(1, result.size());
        assertEquals("Explorer", result.get(0).getModelName());
    }

    @Test
    void findByFuelLevelLessThan_returnsOnlyLowFuelVehicles() {
        repository.save(vehicle("1FTFW1E50PFA00001", "F-150", 70, false));
        repository.save(vehicle("1FMTK4SX5PGA00002", "Mustang Mach-E", 9, false));

        List<VehicleTelemetry> result = repository.findByFuelLevelLessThan(15);

        assertEquals(1, result.size());
        assertEquals("Mustang Mach-E", result.get(0).getModelName());
    }

    @Test
    void findByVin_findsTheVehicle() {
        repository.save(vehicle("1FTFW1E50PFA00001", "F-150", 70, false));

        assertTrue(repository.findByVin("1FTFW1E50PFA00001").isPresent());
        assertTrue(repository.findByVin("DOESNOTEXIST000000").isEmpty());
    }
}