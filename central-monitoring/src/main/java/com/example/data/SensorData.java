package com.example.data;

public record SensorData(
        String warehouseId,
        String sensorId,
        double value,
        SensorType type
) {
    public enum SensorType {
        TEMPERATURE,
        HUMIDITY
    }
}
