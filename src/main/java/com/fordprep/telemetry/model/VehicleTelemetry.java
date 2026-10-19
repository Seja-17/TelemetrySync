package com.fordprep.telemetry.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row per vehicle: its latest known position, fuel level and engine-light state. */
@Entity
@Table(name = "vehicle_telemetry")
public class VehicleTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Vehicle Identification Number: 17 characters, unique per vehicle. */
    @Column(nullable = false, unique = true, length = 17)
    private String vin;

    @Column(nullable = false)
    private String modelName;

    private double latitude;
    private double longitude;

    /** Fuel (or battery) level as a percentage, 0 to 100. */
    private double fuelLevel;

    private boolean engineLightOn;

    public VehicleTelemetry() {
    }

    public VehicleTelemetry(Long id, String vin, String modelName, double latitude,
                            double longitude, double fuelLevel, boolean engineLightOn) {
        this.id = id;
        this.vin = vin;
        this.modelName = modelName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.fuelLevel = fuelLevel;
        this.engineLightOn = engineLightOn;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getFuelLevel() { return fuelLevel; }
    public void setFuelLevel(double fuelLevel) { this.fuelLevel = fuelLevel; }

    public boolean isEngineLightOn() { return engineLightOn; }
    public void setEngineLightOn(boolean engineLightOn) { this.engineLightOn = engineLightOn; }
}