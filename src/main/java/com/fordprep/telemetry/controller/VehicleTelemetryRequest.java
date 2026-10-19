package com.fordprep.telemetry.controller;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Validated input from a vehicle or fleet client. */
public record VehicleTelemetryRequest(
        @NotBlank(message = "VIN is required")
        @Pattern(regexp = "(?i)[A-HJ-NPR-Z0-9]{17}", message = "VIN must contain 17 valid characters")
        String vin,

        @NotBlank(message = "Model name is required")
        String modelName,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
        @DecimalMax(value = "90.0", message = "Latitude must be at most 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
        @DecimalMax(value = "180.0", message = "Longitude must be at most 180")
        Double longitude,

        @NotNull(message = "Fuel level is required")
        @DecimalMin(value = "0.0", message = "Fuel level must be at least 0")
        @DecimalMax(value = "100.0", message = "Fuel level must be at most 100")
        Double fuelLevel,

        @NotNull(message = "Engine-light state is required")
        Boolean engineLightOn) {
}
