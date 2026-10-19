package com.fordprep.telemetry.repository;

import com.fordprep.telemetry.model.VehicleTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelemetryRepository extends JpaRepository<VehicleTelemetry, Long> {

    /** Spring builds the SQL from the name: WHERE engine_light_on = true. */
    List<VehicleTelemetry> findByEngineLightOnTrue();

    /** Vehicles needing attention for either low fuel or an engine warning. */
    List<VehicleTelemetry> findByEngineLightOnTrueOrFuelLevelLessThanOrderByVinAsc(double threshold);

    List<VehicleTelemetry> findAllByOrderByVinAsc();

    /** WHERE fuel_level < ?  (used by the low-fuel scheduler). */
    List<VehicleTelemetry> findByFuelLevelLessThan(double threshold);

    /** Looks a vehicle up by VIN so we can update it instead of creating a duplicate. */
    Optional<VehicleTelemetry> findByVin(String vin);
}
